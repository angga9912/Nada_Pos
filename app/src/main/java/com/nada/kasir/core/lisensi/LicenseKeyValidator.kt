package com.nada.kasir.core.lisensi

import com.nada.kasir.core.paket.PaketAplikasi
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

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
 * Format kode: NADA-{TIER}-{EXPIRY}-{CHECKSUM}
 *  - TIER      : PRO atau CUSTOM. BASIC tidak butuh kode aktivasi (gratis) sehingga
 *                kode dengan tier BASIC selalu ditolak.
 *  - EXPIRY    : "LIFETIME" (berlaku selamanya) atau tanggal kadaluarsa "yyyyMMdd".
 *  - CHECKSUM  : 8 karakter pertama dari hex HMAC-SHA256("TIER:EXPIRY", SECRET).
 *
 * Kode tidak sensitif huruf besar/kecil. Pembuatan kode dilakukan lewat
 * tools/generate_license.py yang harus memakai SECRET yang sama persis dengan di sini.
 */
object LicenseKeyValidator {

    // PENTING: SECRET ini harus identik dengan yang dipakai tools/generate_license.py
    // untuk membuat kode lisensi. Jangan pernah membagikan/commit nilai produksi asli
    // ke repo publik - siapapun yang tahu SECRET ini bisa membuat kode lisensi palsu.
    private const val SECRET = "NADA-KASIR-LICENSE-SECRET-2024-GANTI-SEBELUM-RILIS"

    private const val PREFIX = "NADA"
    private const val EXPIRY_LIFETIME = "LIFETIME"
    private const val EXPIRY_DATE_PATTERN = "yyyyMMdd"
    private const val PANJANG_CHECKSUM = 8

    /**
     * Validasi sebuah kode lisensi.
     *
     * @return [HasilValidasiLisensi] jika kode valid (format benar, checksum cocok,
     *   dan tier bukan BASIC), atau null jika kode tidak valid dalam bentuk apapun.
     */
    fun validasi(kode: String): HasilValidasiLisensi? {
        if (kode.isBlank()) return null

        val bagian = kode.trim().uppercase(Locale.ROOT).split("-")
        if (bagian.size != 4) return null

        val (prefix, tierMentah, expiry, checksum) = bagian
        if (prefix != PREFIX) return null

        val tier = runCatching { PaketAplikasi.valueOf(tierMentah) }.getOrNull() ?: return null
        // BASIC gratis dan tidak pernah butuh kode aktivasi - tolak meski checksum-nya "valid".
        if (tier == PaketAplikasi.BASIC) return null

        val checksumSeharusnya = hitungChecksum(tierMentah, expiry)
        if (!checksum.equals(checksumSeharusnya, ignoreCase = true)) return null

        val kadaluarsaMillis = if (expiry == EXPIRY_LIFETIME) {
            null
        } else {
            parseTanggalExpiry(expiry) ?: return null
        }

        return HasilValidasiLisensi(tier = tier, kadaluarsaMillis = kadaluarsaMillis)
    }

    private fun hitungChecksum(tier: String, expiry: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(SECRET.toByteArray(), "HmacSHA256"))
        val hash = mac.doFinal("$tier:$expiry".toByteArray())
        return hash.joinToString("") { "%02X".format(it) }.take(PANJANG_CHECKSUM)
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
