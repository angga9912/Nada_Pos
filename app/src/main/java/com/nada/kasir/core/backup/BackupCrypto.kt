package com.nada.kasir.core.backup

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Enkripsi file backup dengan password pemilik toko (F-08).
 *
 * Backup memuat omzet/transaksi, hash password semua pengguna, dan data toko, lalu biasanya
 * dibagikan lewat WhatsApp/Drive. Tanpa enkripsi, siapa pun yang menerima file itu bisa membacanya.
 *
 * FORMAT FILE (semua angka big-endian):
 *   [ 8 byte ] "NADAENC1"           penanda format (juga pembeda dari backup .zip lama)
 *   [ 4 byte ] jumlah iterasi PBKDF2
 *   [16 byte ] salt acak
 *   [12 byte ] IV acak
 *   [ sisanya] AES-256-GCM(isi zip) + tag 16 byte
 *
 * Kunci = PBKDF2-HMAC-SHA256(password, salt, iterasi) -> 256 bit. Seluruh header (penanda, iterasi,
 * salt, IV) ikut diautentikasi sebagai AAD, jadi mengubah satu byte pun di header membuat file ditolak.
 * Mode GCM juga mendeteksi file yang dimodifikasi/rusak: password salah dan file rusak sama-sama
 * gagal di pemeriksaan tag (dan memang tidak bisa dibedakan secara kriptografis).
 *
 * Objek ini murni JVM (tanpa Android) supaya bisa diuji unit.
 */
object BackupCrypto {

    private val PENANDA = "NADAENC1".toByteArray(Charsets.US_ASCII)
    private const val PANJANG_ITERASI = 4
    private const val PANJANG_SALT = 16
    private const val PANJANG_IV = 12
    private const val PANJANG_TAG_BYTE = 16
    private const val PANJANG_TAG_BIT = 128
    private val PANJANG_HEADER = PENANDA.size + PANJANG_ITERASI + PANJANG_SALT + PANJANG_IV

    /** 600.000 = rekomendasi OWASP untuk PBKDF2-HMAC-SHA256. Dijalankan di luar thread utama. */
    const val ITERASI_DEFAULT = 600_000

    /** Batas wajar saat MEMBACA header, supaya file jahat tidak bisa membuat HP "menggantung" berjam-jam. */
    const val ITERASI_MIN = 1_000
    const val ITERASI_MAKS = 3_000_000

    const val PANJANG_PASSWORD_MIN = 8

    /** Password salah, ATAU isi file diubah/rusak (keduanya tidak bisa dibedakan). */
    class PasswordSalahException : Exception("Password salah atau file backup rusak.")

    /** Struktur file tidak sesuai (terpotong, header tidak masuk akal). */
    class FormatTidakValidException(pesan: String) : Exception(pesan)

    /** true jika [data] diawali penanda format terenkripsi. Backup .zip lama (diawali "PK") -> false. */
    fun terenkripsi(data: ByteArray): Boolean =
        data.size >= PENANDA.size && PENANDA.indices.all { data[it] == PENANDA[it] }

    fun enkripsi(
        isi: ByteArray,
        password: String,
        iterasi: Int = ITERASI_DEFAULT,
        acak: SecureRandom = SecureRandom()
    ): ByteArray {
        require(password.isNotEmpty()) { "Password tidak boleh kosong." }
        require(iterasi in ITERASI_MIN..ITERASI_MAKS) { "Jumlah iterasi di luar batas." }

        val salt = ByteArray(PANJANG_SALT).also { acak.nextBytes(it) }
        val iv = ByteArray(PANJANG_IV).also { acak.nextBytes(it) }
        val header = buatHeader(iterasi, salt, iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, turunkanKunci(password, salt, iterasi), GCMParameterSpec(PANJANG_TAG_BIT, iv))
        cipher.updateAAD(header)
        return header + cipher.doFinal(isi)
    }

    /**
     * @throws FormatTidakValidException jika struktur file tidak valid
     * @throws PasswordSalahException jika password salah atau isi file telah diubah
     */
    fun dekripsi(data: ByteArray, password: String): ByteArray {
        if (!terenkripsi(data)) throw FormatTidakValidException("Bukan file backup terenkripsi.")
        if (data.size < PANJANG_HEADER + PANJANG_TAG_BYTE) throw FormatTidakValidException("File backup terpotong.")

        val iterasi = ByteBuffer.wrap(data, PENANDA.size, PANJANG_ITERASI).int
        if (iterasi !in ITERASI_MIN..ITERASI_MAKS) throw FormatTidakValidException("Header file backup tidak valid.")

        val salt = data.copyOfRange(PENANDA.size + PANJANG_ITERASI, PENANDA.size + PANJANG_ITERASI + PANJANG_SALT)
        val iv = data.copyOfRange(PANJANG_HEADER - PANJANG_IV, PANJANG_HEADER)
        val header = data.copyOfRange(0, PANJANG_HEADER)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, turunkanKunci(password, salt, iterasi), GCMParameterSpec(PANJANG_TAG_BIT, iv))
        cipher.updateAAD(header)
        return try {
            cipher.doFinal(data, PANJANG_HEADER, data.size - PANJANG_HEADER)
        } catch (e: BadPaddingException) { // termasuk AEADBadTagException
            throw PasswordSalahException()
        }
    }

    private fun buatHeader(iterasi: Int, salt: ByteArray, iv: ByteArray): ByteArray =
        PENANDA + ByteBuffer.allocate(PANJANG_ITERASI).putInt(iterasi).array() + salt + iv

    private fun turunkanKunci(password: String, salt: ByteArray, iterasi: Int): SecretKeySpec {
        val spesifikasi = PBEKeySpec(password.toCharArray(), salt, iterasi, 256)
        try {
            val kunci = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spesifikasi).encoded
            return SecretKeySpec(kunci, "AES")
        } finally {
            spesifikasi.clearPassword()
        }
    }
}
