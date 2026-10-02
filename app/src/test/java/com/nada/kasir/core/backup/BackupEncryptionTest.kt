package com.nada.kasir.core.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.random.Random

class BackupEncryptionTest {

    private val password = "PasswordKuat-123".toCharArray()
    private val headerUkuran = 40 // 8 penanda + 4 iterasi + 16 salt + 12 iv

    private fun enkripsi(data: ByteArray, pw: CharArray = password): ByteArray {
        val keluar = ByteArrayOutputStream()
        BackupEncryption.bungkusTulis(keluar, pw).use { it.write(data) }
        return keluar.toByteArray()
    }

    private fun berhasil(hasil: HasilDekripsi): ByteArray {
        assertTrue("seharusnya berhasil", hasil is HasilDekripsi.Berhasil)
        return (hasil as HasilDekripsi.Berhasil).data
    }

    // ---- Dasar ----

    @Test
    fun `enkripsi lalu dekripsi mengembalikan data yang sama`() {
        val asli = Random(1).nextBytes(300_000)
        val hasil = BackupEncryption.dekripsi(enkripsi(asli), password)
        assertArrayEquals(asli, berhasil(hasil))
    }

    @Test
    fun `data kosong tetap bisa dienkripsi dan dibuka`() {
        val file = enkripsi(ByteArray(0))
        assertEquals(0, berhasil(BackupEncryption.dekripsi(file, password)).size)
    }

    @Test
    fun `ukuran file = header + data + tag 16 byte`() {
        val asli = ByteArray(1234) { it.toByte() }
        assertEquals(headerUkuran + asli.size + 16, enkripsi(asli).size)
    }

    // ---- Password salah dan file diubah ----

    @Test
    fun `password salah ditolak`() {
        val file = enkripsi("rahasia".toByteArray())
        assertSame(HasilDekripsi.PasswordSalahAtauRusak, BackupEncryption.dekripsi(file, "PasswordSalah-999".toCharArray()))
    }

    @Test
    fun `password yang beda satu huruf ditolak`() {
        val file = enkripsi("rahasia".toByteArray())
        assertSame(HasilDekripsi.PasswordSalahAtauRusak, BackupEncryption.dekripsi(file, "PasswordKuat-124".toCharArray()))
    }

    @Test
    fun `ciphertext yang diubah satu bit ditolak`() {
        val file = enkripsi(ByteArray(500) { 7 })
        file[headerUkuran + 100] = (file[headerUkuran + 100].toInt() xor 1).toByte()
        assertSame(HasilDekripsi.PasswordSalahAtauRusak, BackupEncryption.dekripsi(file, password))
    }

    @Test
    fun `tag autentikasi yang diubah ditolak`() {
        val file = enkripsi(ByteArray(500) { 7 })
        file[file.size - 1] = (file[file.size - 1].toInt() xor 1).toByte()
        assertSame(HasilDekripsi.PasswordSalahAtauRusak, BackupEncryption.dekripsi(file, password))
    }

    @Test
    fun `salt atau IV di header yang diubah ditolak`() {
        val dasar = enkripsi(ByteArray(200) { 3 })
        val saltDiubah = dasar.clone().also { it[8 + 4 + 3] = (it[8 + 4 + 3].toInt() xor 1).toByte() }
        val ivDiubah = dasar.clone().also { it[8 + 4 + 16 + 2] = (it[8 + 4 + 16 + 2].toInt() xor 1).toByte() }
        assertSame(HasilDekripsi.PasswordSalahAtauRusak, BackupEncryption.dekripsi(saltDiubah, password))
        assertSame(HasilDekripsi.PasswordSalahAtauRusak, BackupEncryption.dekripsi(ivDiubah, password))
    }

    @Test
    fun `file terpotong ditolak`() {
        val file = enkripsi(ByteArray(300) { 9 })
        val hasil = BackupEncryption.dekripsi(file.copyOf(file.size - 1), password)
        assertSame(HasilDekripsi.PasswordSalahAtauRusak, hasil)
    }

    // ---- Penjaga header ----

    @Test
    fun `jumlah iterasi raksasa di header ditolak cepat tanpa menghitung`() {
        val file = enkripsi(ByteArray(100))
        ByteBuffer.wrap(file).putInt(8, Int.MAX_VALUE)
        val mulai = System.nanoTime()
        val hasil = BackupEncryption.dekripsi(file, password)
        val ms = (System.nanoTime() - mulai) / 1_000_000
        assertSame(HasilDekripsi.FormatTidakDikenal, hasil)
        assertTrue("harus langsung ditolak, bukan menghitung PBKDF2 raksasa (butuh $ms ms)", ms < 1000)
    }

    @Test
    fun `jumlah iterasi terlalu kecil atau negatif ditolak`() {
        val file = enkripsi(ByteArray(100))
        ByteBuffer.wrap(file).putInt(8, 1)
        assertSame(HasilDekripsi.FormatTidakDikenal, BackupEncryption.dekripsi(file, password))
        ByteBuffer.wrap(file).putInt(8, -5)
        assertSame(HasilDekripsi.FormatTidakDikenal, BackupEncryption.dekripsi(file, password))
    }

    @Test
    fun `file yang bukan backup terenkripsi dikenali sebagai format tidak dikenal`() {
        assertSame(HasilDekripsi.FormatTidakDikenal, BackupEncryption.dekripsi("halo dunia".toByteArray(), password))
        assertSame(HasilDekripsi.FormatTidakDikenal, BackupEncryption.dekripsi(ByteArray(0), password))
        assertSame(HasilDekripsi.FormatTidakDikenal, BackupEncryption.dekripsi(ByteArray(100) { 'P'.code.toByte() }, password))
    }

    // ---- Keacakan ----

    @Test
    fun `data sama dienkripsi dua kali menghasilkan file berbeda`() {
        val asli = ByteArray(100) { 5 }
        val a = enkripsi(asli)
        val b = enkripsi(asli)
        assertFalse(a.contentEquals(b))
        assertFalse(a.copyOfRange(12, 28).contentEquals(b.copyOfRange(12, 28))) // salt berbeda
        assertFalse(a.copyOfRange(28, 40).contentEquals(b.copyOfRange(28, 40))) // iv berbeda
    }

    // ---- Alur nyata BackupManager: zip di atas stream terenkripsi ----

    @Test
    fun `zip yang ditulis lewat stream terenkripsi bisa dibaca kembali dan teksnya tersembunyi`() {
        val rahasia = "OMZET-RAHASIA-12345"
        val keluar = ByteArrayOutputStream()
        ZipOutputStream(BackupEncryption.bungkusTulis(keluar, password)).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write("""{"versiBackup":1,"x":"$rahasia"}""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("produk_foto/a.png"))
            zip.write(byteArrayOf(1, 2, 3, 4, 5))
            zip.closeEntry()
        }
        val file = keluar.toByteArray()

        // Teks rahasia tidak boleh terlihat di file yang tersimpan.
        assertFalse(String(file, Charsets.ISO_8859_1).contains(rahasia))
        assertEquals("NADAENC1", String(file, 0, 8, Charsets.US_ASCII))

        val zipBytes = berhasil(BackupEncryption.dekripsi(file, password))
        val nama = mutableListOf<String>()
        var json: String? = null
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                nama += e.name
                if (e.name == "backup.json") json = String(zip.readBytes())
                e = zip.nextEntry
            }
        }
        assertEquals(listOf("backup.json", "produk_foto/a.png"), nama)
        assertNotNull(json)
        assertTrue(json!!.contains(rahasia))
    }

    // ---- Pengenalan jenis file ----

    @Test
    fun `jenis file dikenali dari byte awalnya`() {
        assertEquals(JenisBackup.TERENKRIPSI, BackupEncryption.jenis(enkripsi(ByteArray(10)).copyOf(BackupEncryption.PANJANG_PENGENAL)))
        assertEquals(JenisBackup.ZIP_LAMA, BackupEncryption.jenis(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4)))
        assertEquals(JenisBackup.TIDAK_DIKENAL, BackupEncryption.jenis("%PDF-1.4".toByteArray()))
        assertEquals(JenisBackup.TIDAK_DIKENAL, BackupEncryption.jenis(ByteArray(0)))
        assertEquals(JenisBackup.TIDAK_DIKENAL, BackupEncryption.jenis(byteArrayOf('P'.code.toByte())))
    }

    @Test
    fun `zip asli buatan ZipOutputStream dikenali sebagai backup lama`() {
        val zip = ByteArrayOutputStream().also { ZipOutputStream(it).use { z -> z.putNextEntry(ZipEntry("a")); z.closeEntry() } }
        assertEquals(JenisBackup.ZIP_LAMA, BackupEncryption.jenis(zip.toByteArray()))
    }

    // ---- Aturan password backup ----

    @Test
    fun `password backup minimal delapan karakter`() {
        assertNotNull(BackupEncryption.pesanPasswordTidakValid(""))
        assertNotNull(BackupEncryption.pesanPasswordTidakValid("1234567"))
        assertNull(BackupEncryption.pesanPasswordTidakValid("12345678"))
        assertNull(BackupEncryption.pesanPasswordTidakValid("Password-yang-panjang"))
    }
}
