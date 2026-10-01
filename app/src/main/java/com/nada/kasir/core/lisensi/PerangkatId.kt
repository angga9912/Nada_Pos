package com.nada.kasir.core.lisensi

import java.security.MessageDigest

/**
 * ID Perangkat untuk mengikat kode lisensi ke satu HP (F-12: kode tidak bisa dibagikan ke banyak HP).
 *
 * Bentuk kanonik : 12 karakter hex huruf besar, mis. "A1B2C3D4E5F6" (48 bit - cukup untuk
 *                  membedakan HP pelanggan satu sama lain).
 * Bentuk tampilan: dikelompokkan per 4 karakter, mis. "A1B2-C3D4-E5F6" (mudah dibaca/disalin).
 *
 * ID diturunkan dengan SHA-256 dari identitas perangkat, jadi nilai asli ANDROID_ID tidak
 * pernah tampil di layar atau dikirim ke penjual. Objek ini sengaja murni Kotlin (tanpa Context)
 * supaya bisa diuji unit.
 */
object PerangkatId {

    private const val DOMAIN = "NADA-DEV-V1"
    const val PANJANG = 12

    /** Turunkan ID kanonik dari identitas mentah perangkat. */
    fun turunkan(sumber: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest("$DOMAIN:$sumber".toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02X".format(it) }.take(PANJANG)
    }

    /** "A1B2C3D4E5F6" -> "A1B2-C3D4-E5F6" */
    fun tampil(idKanonik: String): String = idKanonik.chunked(4).joinToString("-")

    /**
     * Ubah masukan bebas (huruf kecil, spasi, tanda hubung hasil salin-tempel) ke bentuk kanonik.
     * Mengembalikan null jika bukan 12 karakter hex yang valid.
     */
    fun normalisasi(masukan: String): String? {
        val bersih = masukan.uppercase().filter { it.isLetterOrDigit() }
        if (bersih.length != PANJANG) return null
        if (bersih.any { it !in '0'..'9' && it !in 'A'..'F' }) return null
        return bersih
    }
}
