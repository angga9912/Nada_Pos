package com.nada.kasir.core.backup

import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Hasil membuka file backup terenkripsi. */
sealed class HasilDekripsi {
    class Berhasil(val data: ByteArray) : HasilDekripsi()

    /**
     * Password salah ATAU isi file berubah/rusak. Keduanya sengaja tidak dibedakan: AES-GCM memang
     * tidak bisa membedakannya, dan memisahkan keduanya hanya memberi petunjuk ke penyerang.
     */
    object PasswordSalahAtauRusak : HasilDekripsi()

    /** Bukan file backup terenkripsi NADA POS (penanda, panjang, atau parameter header tidak valid). */
    object FormatTidakDikenal : HasilDekripsi()
}

/** Jenis file yang dipilih pengguna saat restore. */
enum class JenisBackup { TERENKRIPSI, ZIP_LAMA, TIDAK_DIKENAL }

/**
 * Enkripsi file backup dengan password dari pemilik toko (F-08).
 *
 * Backup berisi seluruh omzet, daftar transaksi, dan hash password semua akun, lalu dibagikan lewat
 * share sheet / Google Drive. Tanpa enkripsi, siapa pun yang memegang file itu bisa membacanya.
 *
 * Skema: AES-256-GCM (terautentikasi: perubahan sekecil apa pun pada file terdeteksi), kunci
 * diturunkan dari password dengan PBKDF2-HMAC-SHA256 + salt acak per file. Tidak ada kunci yang
 * tersimpan di aplikasi atau di file, jadi **password yang hilang tidak bisa dipulihkan siapa pun**.
 *
 * Format file (big-endian):
 *   [0..7]   penanda "NADAENC1"
 *   [8..11]  jumlah iterasi PBKDF2 (Int)
 *   [12..27] salt acak (16 byte)
 *   [28..39] IV/nonce acak (12 byte)
 *   [40..]   ciphertext AES-GCM + tag 16 byte (berisi file zip backup)
 * Seluruh header (byte 0..39) ikut diautentikasi sebagai AAD.
 *
 * Objek ini murni Kotlin/JVM (tanpa Android) supaya bisa diuji unit.
 */
object BackupEncryption {

    private val PENANDA = "NADAENC1".toByteArray(Charsets.US_ASCII)
    private const val PANJANG_SALT = 16
    private const val PANJANG_IV = 12
    private const val PANJANG_TAG_BIT = 128
    private const val PANJANG_TAG_BYTE = 16
    private const val UKURAN_HEADER = 8 + 4 + PANJANG_SALT + PANJANG_IV // 40

    private const val ITERASI_DEFAULT = 150_000
    // Batas wajar untuk iterasi yang dibaca dari file: mencegah file buatan orang lain memuat
    // iterasi raksasa yang membuat aplikasi "hang" saat mencoba membukanya.
    private const val ITERASI_MIN = 10_000
    private const val ITERASI_MAKS = 2_000_000

    const val PASSWORD_MIN = 8
    const val EKSTENSI = "nadabak"

    /** Null jika password boleh dipakai untuk backup baru. */
    fun pesanPasswordTidakValid(password: String): String? =
        if (password.length < PASSWORD_MIN) "Password backup minimal $PASSWORD_MIN karakter." else null

    fun apakahTerenkripsi(awalFile: ByteArray): Boolean =
        awalFile.size >= PENANDA.size && PENANDA.indices.all { awalFile[it] == PENANDA[it] }

    /** Backup lama (sebelum enkripsi) berupa file zip biasa: diawali "PK". */
    fun apakahZipLama(awalFile: ByteArray): Boolean =
        awalFile.size >= 2 && awalFile[0] == 'P'.code.toByte() && awalFile[1] == 'K'.code.toByte()

    fun jenis(awalFile: ByteArray): JenisBackup = when {
        apakahTerenkripsi(awalFile) -> JenisBackup.TERENKRIPSI
        apakahZipLama(awalFile) -> JenisBackup.ZIP_LAMA
        else -> JenisBackup.TIDAK_DIKENAL
    }

    /** Jumlah byte awal file yang cukup untuk menentukan [jenis]. */
    val PANJANG_PENGENAL: Int get() = PENANDA.size

    /**
     * Membungkus [tujuan] sehingga semua yang ditulis ke stream hasil terenkripsi. Header ditulis
     * lebih dulu. **Stream hasil harus di-close** (menulis tag autentikasi di akhir) agar file valid.
     * Karena itu isi zip tidak pernah menyentuh disk dalam bentuk polos.
     */
    fun bungkusTulis(tujuan: OutputStream, password: CharArray): OutputStream {
        val acak = SecureRandom()
        val salt = ByteArray(PANJANG_SALT).also { acak.nextBytes(it) }
        val iv = ByteArray(PANJANG_IV).also { acak.nextBytes(it) }
        val header = ByteBuffer.allocate(UKURAN_HEADER)
            .put(PENANDA).putInt(ITERASI_DEFAULT).put(salt).put(iv).array()

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, turunkanKunci(password, salt, ITERASI_DEFAULT), GCMParameterSpec(PANJANG_TAG_BIT, iv))
        cipher.updateAAD(header)

        tujuan.write(header)
        return CipherOutputStream(tujuan, cipher)
    }

    /** Membuka seluruh isi file backup terenkripsi. */
    fun dekripsi(data: ByteArray, password: CharArray): HasilDekripsi {
        if (!apakahTerenkripsi(data) || data.size < UKURAN_HEADER + PANJANG_TAG_BYTE) {
            return HasilDekripsi.FormatTidakDikenal
        }
        val buf = ByteBuffer.wrap(data)
        buf.position(PENANDA.size)
        val iterasi = buf.int
        if (iterasi !in ITERASI_MIN..ITERASI_MAKS) return HasilDekripsi.FormatTidakDikenal
        val salt = ByteArray(PANJANG_SALT).also { buf.get(it) }
        val iv = ByteArray(PANJANG_IV).also { buf.get(it) }

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, turunkanKunci(password, salt, iterasi), GCMParameterSpec(PANJANG_TAG_BIT, iv))
            cipher.updateAAD(data, 0, UKURAN_HEADER)
            HasilDekripsi.Berhasil(cipher.doFinal(data, UKURAN_HEADER, data.size - UKURAN_HEADER))
        } catch (e: Exception) {
            // Termasuk AEADBadTagException: tag tidak cocok (password salah atau file diubah).
            HasilDekripsi.PasswordSalahAtauRusak
        }
    }

    private fun turunkanKunci(password: CharArray, salt: ByteArray, iterasi: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterasi, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
