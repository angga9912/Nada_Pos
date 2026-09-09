package com.nada.kasir.core.lisensi

import com.nada.kasir.core.paket.PaketAplikasi
import java.text.SimpleDateFormat
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Validasi kode aktivasi lisensi secara OFFLINE (tanpa server/internet).
 *
 * Format kode: NADA-{TIER}-{EXPIRY}-{CHECKSUM}
 *   TIER     = CUSTOM atau PRO
 *   EXPIRY   = YYYYMMDD (tanggal kadaluarsa langganan) ATAU "LIFETIME" (sekali bayar, tidak pernah expired)
 *   CHECKSUM = 8 karakter HMAC-SHA256, dihitung dari TIER+EXPIRY memakai SECRET yang sama
 *              dengan generator (lihat tools/generate_license.py)
 *
 * PENTING - batasan keamanan yang harus dipahami pemilik bisnis:
 * Karena SECRET ini ikut ter-bundle di dalam APK, secara teori APK bisa
 * di-reverse-engineer untuk menemukan SECRET dan membuat kode palsu sendiri.
 * Ini adalah trade-off umum untuk software kecil-menengah tanpa server lisensi
 * terpusat (banyak software indie/UMKM memakai pendekatan serupa). Untuk
 * proteksi lebih kuat di masa depan, pertimbangkan validasi online ke server.
 */
object LicenseKeyValidator {

    // GANTI nilai ini dengan string rahasia Anda sendiri sebelum merilis ke publik,
    // dan JANGAN commit nilai asli ke repository publik (pakai gradle.properties
    // lokal + BuildConfig kalau mau lebih rapi).
    private const val SECRET = "GANTI_DENGAN_SECRET_RAHASIA_ANDA_SEBELUM_RILIS"

    data class HasilAktivasi(val tier: PaketAplikasi, val kadaluarsaMillis: Long?) // null = LIFETIME

    fun validasi(kodeMentah: String): HasilAktivasi? {
        val kode = kodeMentah.trim().uppercase(Locale.ROOT)
        val bagian = kode.split("-")
        if (bagian.size != 4 || bagian[0] != "NADA") return null

        val tierStr = bagian[1]
        val expiryStr = bagian[2]
        val checksumDiberikan = bagian[3]

        val tier = runCatching { PaketAplikasi.valueOf(tierStr) }.getOrNull() ?: return null
        if (tier == PaketAplikasi.BASIC) return null // Basic tidak butuh kode, selalu gratis

        val checksumBenar = hitungChecksum(tierStr, expiryStr)
        if (checksumDiberikan != checksumBenar) return null

        val kadaluarsaMillis: Long? = if (expiryStr == "LIFETIME") {
            null
        } else {
            val sdf = SimpleDateFormat("yyyyMMdd", Locale.US)
            sdf.isLenient = false
            val tanggal = runCatching { sdf.parse(expiryStr) }.getOrNull() ?: return null
            tanggal.time
        }

        return HasilAktivasi(tier, kadaluarsaMillis)
    }

    private fun hitungChecksum(tier: String, expiry: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val hasil = mac.doFinal("$tier:$expiry".toByteArray(Charsets.UTF_8))
        return hasil.joinToString("") { "%02X".format(it) }.take(8)
    }
}
