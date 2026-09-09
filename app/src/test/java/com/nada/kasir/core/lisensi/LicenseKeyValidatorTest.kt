package com.nada.kasir.core.lisensi

import com.nada.kasir.core.paket.PaketAplikasi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class LicenseKeyValidatorTest {

    // Helper: hitung checksum yang sama seperti tools/generate_license.py, memakai
    // reflection kecil untuk membaca SECRET privat di LicenseKeyValidator supaya
    // test ini tidak perlu hardcode SECRET dua kali (kalau SECRET diganti, test tetap valid).
    private fun buatKodeValid(tier: String, expiry: String): String {
        val field = LicenseKeyValidator::class.java.getDeclaredField("SECRET")
        field.isAccessible = true
        val secret = field.get(LicenseKeyValidator) as String
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val hash = mac.doFinal("$tier:$expiry".toByteArray())
        val checksum = hash.joinToString("") { "%02X".format(it) }.take(8)
        return "NADA-$tier-$expiry-$checksum"
    }

    @Test
    fun `kode lifetime yang valid diterima`() {
        val kode = buatKodeValid("PRO", "LIFETIME")
        val hasil = LicenseKeyValidator.validasi(kode)
        assertNotNull(hasil)
        assertEquals(PaketAplikasi.PRO, hasil!!.tier)
        assertNull(hasil.kadaluarsaMillis)
    }

    @Test
    fun `kode dengan tanggal expiry valid diterima dan tanggal terparsir benar`() {
        val kode = buatKodeValid("CUSTOM", "20271231")
        val hasil = LicenseKeyValidator.validasi(kode)
        assertNotNull(hasil)
        assertEquals(PaketAplikasi.CUSTOM, hasil!!.tier)
        assertNotNull(hasil.kadaluarsaMillis)
    }

    @Test
    fun `kode dengan checksum yang diubah (dipalsukan) ditolak`() {
        val kode = buatKodeValid("PRO", "LIFETIME")
        val karakterTerakhir = kode.last()
        val penggantiBerbeda = if (karakterTerakhir != '0') '0' else '1'
        val kodePalsu = kode.dropLast(1) + penggantiBerbeda
        val hasil = LicenseKeyValidator.validasi(kodePalsu)
        assertNull(hasil)
    }

    @Test
    fun `kode dengan format salah ditolak`() {
        assertNull(LicenseKeyValidator.validasi("BUKAN-KODE-VALID"))
        assertNull(LicenseKeyValidator.validasi(""))
        assertNull(LicenseKeyValidator.validasi("NADA-PRO-LIFETIME")) // kurang 1 bagian
    }

    @Test
    fun `tier BASIC selalu ditolak karena tidak butuh kode aktivasi`() {
        val kode = buatKodeValid("BASIC", "LIFETIME")
        assertNull(LicenseKeyValidator.validasi(kode))
    }

    @Test
    fun `kode tidak sensitif huruf besar-kecil`() {
        val kode = buatKodeValid("PRO", "LIFETIME")
        val hasil = LicenseKeyValidator.validasi(kode.lowercase())
        assertNotNull(hasil)
    }
}
