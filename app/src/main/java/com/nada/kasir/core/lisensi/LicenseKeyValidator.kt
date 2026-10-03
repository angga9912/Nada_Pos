package com.nada.kasir.core.lisensi

import com.nada.kasir.BuildConfig
import com.nada.kasir.core.paket.PaketAplikasi
import java.math.BigInteger
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Locale
import java.util.TimeZone

/**
 * Hasil validasi kode lisensi yang berhasil diparsing.
 *
 * @param tier Paket aplikasi yang diaktifkan oleh kode ini (PRO / CUSTOM).
 * @param kadaluarsaMillis Waktu kadaluarsa lisensi dalam epoch millis (UTC),
 *   atau null jika lisensi berlaku seumur hidup (LIFETIME).
 */
data class HasilValidasiLisensi(
    val tier: PaketAplikasi,
    val kadaluarsaMillis: Long?
)

/**
 * Validator kode aktivasi lisensi offline untuk fitur berbayar (PRO / CUSTOM).
 *
 * Format kode: NADA-{TIER}-{EXPIRY}-{TANDA-TANGAN}
 *  - TIER          : PRO atau CUSTOM. BASIC gratis dan selalu ditolak.
 *  - EXPIRY        : "LIFETIME" atau tanggal kadaluarsa "yyyyMMdd".
 *  - TANDA-TANGAN  : tanda tangan digital ECDSA P-256 (64 byte, ditulis Base32 dan
 *                    dipotong per 8 karakter dengan tanda "-") atas pesan
 *                    "NADA-LIC-V1:TIER:EXPIRY".
 *
 * KEAMANAN: APK hanya membawa kunci PUBLIK (BuildConfig.LICENSE_PUBLIC_KEY, dari file
 * license.public). Kunci publik hanya bisa MEMERIKSA tanda tangan, bukan membuatnya -
 * jadi walaupun APK dibongkar, kode lisensi palsu tetap tidak bisa dibuat. Kunci privat
 * (license.private) hanya ada pada penjual; kode dibuat lewat tools/generate_license.py.
 *
 * Kode tidak sensitif huruf besar/kecil, dan spasi/baris baru hasil salin-tempel diabaikan.
 */
object LicenseKeyValidator {

    private const val PREFIX = "NADA"
    private const val DOMAIN = "NADA-LIC-V1"
    private const val EXPIRY_LIFETIME = "LIFETIME"
    private const val EXPIRY_DATE_PATTERN = "yyyyMMdd"
    private const val ALGORITMA_TANDA_TANGAN = "SHA256withECDSA"
    private const val PANJANG_TANDA_TANGAN_BYTE = 64
    private const val ALFABET_BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /**
     * Validasi sebuah kode lisensi memakai kunci publik yang tertanam di aplikasi.
     *
     * @return [HasilValidasiLisensi] jika kode valid (format benar, tanda tangan cocok,
     *   dan tier bukan BASIC), atau null jika kode tidak valid dalam bentuk apapun.
     */
    fun validasi(kode: String): HasilValidasiLisensi? = validasi(kode, BuildConfig.LICENSE_PUBLIC_KEY)

    /** Versi dengan kunci publik yang bisa diganti - dipakai unit test (kunci uji buatan test). */
    internal fun validasi(kode: String, publicKeyBase64: String): HasilValidasiLisensi? {
        if (kode.isBlank() || publicKeyBase64.isBlank()) return null

        val bersih = kode.uppercase(Locale.ROOT).filterNot { it.isWhitespace() }
        val bagian = bersih.split("-")
        if (bagian.size < 4) return null

        val prefix = bagian[0]
        val tierMentah = bagian[1]
        val expiry = bagian[2]
        if (prefix != PREFIX) return null

        val tier = runCatching { PaketAplikasi.valueOf(tierMentah) }.getOrNull() ?: return null
        // BASIC gratis dan tidak pernah butuh kode aktivasi - tolak apapun tanda tangannya.
        if (tier == PaketAplikasi.BASIC) return null

        val tandaTangan = decodeBase32(bagian.drop(3).joinToString("")) ?: return null
        if (tandaTangan.size != PANJANG_TANDA_TANGAN_BYTE) return null
        if (!tandaTanganValid("$DOMAIN:$tierMentah:$expiry", tandaTangan, publicKeyBase64)) return null

        val kadaluarsaMillis = if (expiry == EXPIRY_LIFETIME) {
            null
        } else {
            parseTanggalExpiry(expiry) ?: return null
        }

        return HasilValidasiLisensi(tier = tier, kadaluarsaMillis = kadaluarsaMillis)
    }

    private fun tandaTanganValid(pesan: String, tandaTanganMentah: ByteArray, publicKeyBase64: String): Boolean {
        return runCatching {
            val kunciPublik = KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64.trim())))
            val verifier = Signature.getInstance(ALGORITMA_TANDA_TANGAN)
            verifier.initVerify(kunciPublik)
            verifier.update(pesan.toByteArray(Charsets.UTF_8))
            verifier.verify(mentahKeDer(tandaTanganMentah))
        }.getOrDefault(false)
    }

    /** Ubah tanda tangan mentah r||s (64 byte) ke format DER yang diminta java.security. */
    private fun mentahKeDer(mentah: ByteArray): ByteArray {
        val r = BigInteger(1, mentah.copyOfRange(0, 32)).toByteArray()
        val s = BigInteger(1, mentah.copyOfRange(32, 64)).toByteArray()
        val isi = byteArrayOf(0x02, r.size.toByte()) + r + byteArrayOf(0x02, s.size.toByte()) + s
        return byteArrayOf(0x30, isi.size.toByte()) + isi
    }

    /**
     * Decode Base32 (RFC 4648, tanpa padding). Salah ketik yang umum dimaafkan:
     * 0 dibaca O, 1 dibaca I, 8 dibaca B (angka-angka itu memang tidak dipakai di alfabet Base32).
     * Mengembalikan null jika ada karakter tidak dikenal atau sisa bit tidak nol (bukan kode asli kita).
     */
    private fun decodeBase32(teks: String): ByteArray? {
        var buffer = 0
        var bit = 0
        val hasil = java.io.ByteArrayOutputStream()
        for (c in teks) {
            val karakter = when (c) {
                '0' -> 'O'
                '1' -> 'I'
                '8' -> 'B'
                else -> c
            }
            val nilai = ALFABET_BASE32.indexOf(karakter)
            if (nilai < 0) return null
            buffer = (buffer shl 5) or nilai
            bit += 5
            if (bit >= 8) {
                bit -= 8
                hasil.write((buffer shr bit) and 0xFF)
                buffer = buffer and ((1 shl bit) - 1)
            }
        }
        if (buffer != 0) return null
        return hasil.toByteArray()
    }

    private fun parseTanggalExpiry(expiry: String): Long? {
        if (expiry.length != 8 || expiry.any { !it.isDigit() }) return null
        val format = SimpleDateFormat(EXPIRY_DATE_PATTERN, Locale.ROOT).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return runCatching { format.parse(expiry)?.time }.getOrNull()
    }
}
