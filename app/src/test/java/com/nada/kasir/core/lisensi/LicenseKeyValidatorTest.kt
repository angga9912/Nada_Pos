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

    /** Tanpa [idPerangkat] = kode lama V1; dengan [idPerangkat] (12 hex kanonik) = kode V2 terikat perangkat. */
    private fun buatKode(
        tier: String,
        expiry: String,
        kunci: KeyPair = pasangan,
        idPerangkat: String? = null
    ): String {
        val pesan = if (idPerangkat == null) "NADA-LIC-V1:$tier:$expiry" else "NADA-LIC-V2:$tier:$expiry:$idPerangkat"
        val penanda = Signature.getInstance("SHA256withECDSA")
        penanda.initSign(kunci.private)
        penanda.update(pesan.toByteArray(Charsets.UTF_8))
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

    // ---------------------------------------------------------------------------------
    // F-12: kode terikat perangkat (V2)
    // ---------------------------------------------------------------------------------
    private val perangkatA = "A1B2C3D4E5F6"
    private val perangkatB = "0F0E0D0C0B0A"

    private fun validasiUntuk(kode: String, id: String?, izinkanLama: Boolean = true) =
        LicenseKeyValidator.validasi(kode, kunciPublik, id, izinkanLama)

    @Test
    fun `kode terikat perangkat diterima di perangkat yang benar`() {
        val hasil = validasiUntuk(buatKode("PRO", "LIFETIME", idPerangkat = perangkatA), perangkatA)
        assertNotNull(hasil)
        assertEquals(PaketAplikasi.PRO, hasil!!.tier)
        assertEquals(true, hasil.terikatPerangkat)
    }

    @Test
    fun `kode terikat perangkat ditolak di perangkat lain`() {
        val kode = buatKode("PRO", "LIFETIME", idPerangkat = perangkatA)
        assertNull(validasiUntuk(kode, perangkatB))
        // juga ditolak walau masa peralihan kode lama sedang dibuka (kodenya memang bukan kode lama)
        assertNull(validasiUntuk(kode, perangkatB, izinkanLama = true))
    }

    @Test
    fun `kode terikat perangkat ditolak kalau ID perangkat tidak diketahui`() {
        val kode = buatKode("PRO", "LIFETIME", idPerangkat = perangkatA)
        assertNull(validasiUntuk(kode, null))
        assertNull(validasiUntuk(kode, "bukan-id-valid"))
    }

    @Test
    fun `ID perangkat boleh ditulis huruf kecil dan dengan tanda hubung`() {
        val kode = buatKode("CUSTOM", "20271231", idPerangkat = perangkatA)
        assertNotNull(validasiUntuk(kode, "a1b2-c3d4-e5f6"))
    }

    @Test
    fun `kode lama tanpa perangkat diterima selama masa peralihan dan ditandai tidak terikat`() {
        val hasil = validasiUntuk(buatKode("PRO", "LIFETIME"), perangkatA, izinkanLama = true)
        assertNotNull(hasil)
        assertEquals(false, hasil!!.terikatPerangkat)
    }

    @Test
    fun `kode lama ditolak setelah masa peralihan ditutup`() {
        assertNull(validasiUntuk(buatKode("PRO", "LIFETIME"), perangkatA, izinkanLama = false))
    }

    @Test
    fun `kode terikat perangkat tetap diterima setelah masa peralihan ditutup`() {
        val kode = buatKode("PRO", "LIFETIME", idPerangkat = perangkatA)
        assertNotNull(validasiUntuk(kode, perangkatA, izinkanLama = false))
    }

    @Test
    fun `tier atau expiry kode terikat perangkat yang diubah ditolak`() {
        val kode = buatKode("PRO", "20260101", idPerangkat = perangkatA)
        assertNull(validasiUntuk(kode.replace("20260101", "20991231"), perangkatA))
        assertNull(validasiUntuk(kode.replaceFirst("NADA-PRO-", "NADA-CUSTOM-"), perangkatA))
    }

    // ---- Kode trial otomatis (kunci terpisah, terikat perangkat, umur pendek) ----

    private val pasanganTrial = buatPasangan()
    private val kunciPublikTrial: String = Base64.getEncoder().encodeToString(pasanganTrial.public.encoded)
    private val idHp = "A1B2C3D4E5F6"
    private val sekarang: Long = java.time.LocalDate.of(2026, 10, 7)
        .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun kodeTrial(tier: String = "PRO", expiry: String = "20261014", id: String? = idHp) =
        buatKode(tier, expiry, kunci = pasanganTrial, idPerangkat = id)

    private fun validasiTrial(kode: String, id: String? = idHp, kunci: String = kunciPublikTrial) =
        LicenseKeyValidator.validasiTrial(kode, kunci, id, sekarang)

    @Test
    fun `kode trial 7 hari untuk perangkat ini diterima dan ditandai trial`() {
        val hasil = validasiTrial(kodeTrial())
        assertNotNull(hasil)
        assertEquals(PaketAplikasi.PRO, hasil!!.tier)
        assertEquals(true, hasil.trial)
        assertEquals(true, hasil.terikatPerangkat)
    }

    @Test
    fun `kode trial 14 hari tetap diterima walau jam HP tertinggal satu hari`() {
        // Server membuat expiry = hari ini + 14 (berlaku sampai akhir hari UTC itu).
        val kode = kodeTrial(tier = "CUSTOM", expiry = "20261021")
        assertNotNull(validasiTrial(kode))
        val satuHariMundur = LicenseKeyValidator.validasiTrial(kode, kunciPublikTrial, idHp, sekarang - 24L * 60 * 60 * 1000)
        assertNotNull(satuHariMundur)
        // Tetapi jam yang tertinggal jauh (3 hari) tidak diberi toleransi.
        assertNull(LicenseKeyValidator.validasiTrial(kode, kunciPublikTrial, idHp, sekarang - 3L * 24 * 60 * 60 * 1000))
    }

    @Test
    fun `kode trial untuk perangkat lain ditolak`() {
        assertNull(validasiTrial(kodeTrial(id = "FFFFFFFFFFFF")))
    }

    @Test
    fun `kode trial lifetime ditolak`() {
        assertNull(validasiTrial(kodeTrial(expiry = "LIFETIME")))
    }

    @Test
    fun `kode trial yang berlaku terlalu lama ditolak walau tanda tangannya benar`() {
        // Seandainya kunci trial bocor: kode 1 tahun tetap tidak diterima aplikasi.
        assertNull(validasiTrial(kodeTrial(expiry = "20271007")))
        assertNull(validasiTrial(kodeTrial(expiry = "20261101")))
    }

    @Test
    fun `kode trial tanpa ikatan perangkat (V1) ditolak`() {
        assertNull(validasiTrial(kodeTrial(id = null)))
    }

    @Test
    fun `kode trial ditandatangani kunci lisensi utama tidak diterima sebagai trial`() {
        // Kunci trial dan kunci utama saling terpisah: kode dari satu kunci tidak lolos di kunci lain.
        val kodeDariKunciUtama = buatKode("PRO", "20261014", kunci = pasangan, idPerangkat = idHp)
        assertNull(validasiTrial(kodeDariKunciUtama))
        assertNull(LicenseKeyValidator.validasi(kodeTrial(), kunciPublik, idHp, izinkanTanpaPerangkat = false))
    }

    @Test
    fun `kunci trial kosong menonaktifkan trial`() {
        assertNull(validasiTrial(kodeTrial(), kunci = ""))
    }

    @Test
    fun `kode trial yang sudah lewat masih terbaca valid agar bisa dinilai kadaluarsa oleh repository`() {
        // Penolakan "kadaluarsa" dilakukan LicenseRepository; validator hanya membatasi umur ke DEPAN.
        val hasil = validasiTrial(kodeTrial(expiry = "20260101"))
        assertNotNull(hasil)
    }
}
