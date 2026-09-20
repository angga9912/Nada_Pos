package com.nada.kasir.core.lisensi

import com.nada.kasir.core.paket.PaketAplikasi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * Test memakai pasangan kunci EC buatan test sendiri (bukan kunci asli Anda), jadi
 * kunci privat asli tidak dibutuhkan di CI dan test tidak bergantung pada license.public.
 * Format kode yang dibuat di sini HARUS sama dengan tools/generate_license.py.
 */
class LicenseKeyValidatorTest {

    private val alfabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    private fun buatPasangan(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    private val pasangan = buatPasangan()
    private val kunciPublik: String = Base64.getEncoder().encodeToString(pasangan.public.encoded)

    /** DER (30 len 02 rlen r 02 slen s) -> mentah r||s 64 byte. */
    private fun derKeMentah(der: ByteArray): ByteArray {
        var i = 3
        val panjangR = der[i++].toInt()
        val r = der.copyOfRange(i, i + panjangR)
        i += panjangR + 1
        val panjangS = der[i++].toInt()
        val s = der.copyOfRange(i, i + panjangS)
        fun jadi32(b: ByteArray): ByteArray {
            val potong = if (b.size > 32) b.copyOfRange(b.size - 32, b.size) else b
            return ByteArray(32 - potong.size) + potong
        }
        return jadi32(r) + jadi32(s)
    }

    private fun base32(data: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bit = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bit += 8
            while (bit >= 5) {
                bit -= 5
                sb.append(alfabet[(buffer shr bit) and 31])
            }
            buffer = buffer and ((1 shl bit) - 1)
        }
        if (bit > 0) sb.append(alfabet[(buffer shl (5 - bit)) and 31])
        return sb.toString()
    }

    private fun buatKode(tier: String, expiry: String, kunci: KeyPair = pasangan): String {
        val penanda = Signature.getInstance("SHA256withECDSA")
        penanda.initSign(kunci.private)
        penanda.update("NADA-LIC-V1:$tier:$expiry".toByteArray(Charsets.UTF_8))
        val tandaTangan = base32(derKeMentah(penanda.sign())).chunked(8).joinToString("-")
        return "NADA-$tier-$expiry-$tandaTangan"
    }

    private fun validasi(kode: String) = LicenseKeyValidator.validasi(kode, kunciPublik)

    /** Ubah 1 karakter tepat di awal tanda tangan (5 bit data penuh, bukan bit padding). */
    private fun rusakiTandaTangan(kode: String, expiry: String): String {
        val awal = kode.indexOf("-$expiry-") + expiry.length + 2
        val pengganti = if (kode[awal] != 'A') 'A' else 'B'
        return kode.substring(0, awal) + pengganti + kode.substring(awal + 1)
    }

    @Test
    fun `kode lifetime yang valid diterima`() {
        val hasil = validasi(buatKode("PRO", "LIFETIME"))
        assertNotNull(hasil)
        assertEquals(PaketAplikasi.PRO, hasil!!.tier)
        assertNull(hasil.kadaluarsaMillis)
    }

    @Test
    fun `kode dengan tanggal expiry valid diterima dan tanggal terparsir benar`() {
        val hasil = validasi(buatKode("CUSTOM", "20271231"))
        assertNotNull(hasil)
        assertEquals(PaketAplikasi.CUSTOM, hasil!!.tier)
        assertNotNull(hasil.kadaluarsaMillis)
    }

    @Test
    fun `tanda tangan yang diubah (dipalsukan) ditolak`() {
        val kode = buatKode("PRO", "LIFETIME")
        assertNull(validasi(rusakiTandaTangan(kode, "LIFETIME")))
    }

    @Test
    fun `tier atau expiry yang diubah tanpa tanda tangan baru ditolak`() {
        val kodePro = buatKode("PRO", "LIFETIME")
        assertNull(validasi(kodePro.replaceFirst("NADA-PRO-", "NADA-CUSTOM-")))
        val kodeBerTanggal = buatKode("PRO", "20260101")
        assertNull(validasi(kodeBerTanggal.replace("20260101", "20991231")))
        assertNull(validasi(kodeBerTanggal.replace("20260101", "LIFETIME")))
    }

    @Test
    fun `kode buatan kunci lain ditolak`() {
        val kodeDariKunciLain = buatKode("PRO", "LIFETIME", kunci = buatPasangan())
        assertNull(validasi(kodeDariKunciLain))
    }

    @Test
    fun `kunci publik kosong atau rusak menolak semua kode`() {
        val kode = buatKode("PRO", "LIFETIME")
        assertNull(LicenseKeyValidator.validasi(kode, ""))
        assertNull(LicenseKeyValidator.validasi(kode, "bukan-kunci-yang-valid"))
    }

    @Test
    fun `kode dengan format salah ditolak`() {
        assertNull(validasi("BUKAN-KODE-VALID"))
        assertNull(validasi(""))
        assertNull(validasi("NADA-PRO-LIFETIME")) // kurang bagian tanda tangan
        assertNull(validasi("NADA-PRO-LIFETIME-AAAA")) // tanda tangan terlalu pendek
    }

    @Test
    fun `tier BASIC selalu ditolak karena tidak butuh kode aktivasi`() {
        assertNull(validasi(buatKode("BASIC", "LIFETIME")))
    }

    @Test
    fun `kode tidak sensitif huruf besar-kecil`() {
        assertNotNull(validasi(buatKode("PRO", "LIFETIME").lowercase()))
    }

    @Test
    fun `spasi dan baris baru hasil salin-tempel diabaikan`() {
        val kode = buatKode("PRO", "LIFETIME")
        assertNotNull(validasi("  $kode \n"))
        assertNotNull(validasi(kode.replace("-", " - ")))
        assertNotNull(validasi(kode.replaceFirst("LIFETIME-", "LIFETIME-\n")))
    }

    @Test
    fun `tanggal expiry yang mustahil ditolak walau tanda tangannya benar`() {
        // Tanda tangan valid untuk "20271345" (bulan 13), tapi bukan tanggal nyata.
        assertNull(validasi(buatKode("PRO", "20271345")))
    }
}
