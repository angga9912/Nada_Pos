package com.nada.kasir.core.util

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hashing password dengan PBKDF2-HMAC-SHA256 (120.000 iterasi) + salt 16-byte kriptografis.
 * Mendukung verifikasi kompatibel mundur untuk hash lama (SHA-256 tunggal) agar akun
 * yang sudah dibuat sebelumnya tidak terkunci.
 */
object PasswordHasher {
    private const val ALGORITMA_PBKDF2 = "PBKDF2WithHmacSHA256"
    private const val ITERASI = 120_000
    private const val PANJANG_KUNCI_BIT = 256
    private const val PANJANG_SALT_BYTE = 16

    /**
     * Menghasilkan hash PBKDF2 baru dengan salt acak 16 byte.
     * Format: PBKDF2:{iterasi}:{saltBase64}:{hashBase64}
     */
    fun hash(password: String): String {
        val salt = ByteArray(PANJANG_SALT_BYTE)
        SecureRandom().nextBytes(salt)
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERASI, PANJANG_KUNCI_BIT)
        val factory = SecretKeyFactory.getInstance(ALGORITMA_PBKDF2)
        val hashedBytes = factory.generateSecret(spec).encoded
        val saltB64 = Base64.getEncoder().encodeToString(salt)
        val hashB64 = Base64.getEncoder().encodeToString(hashedBytes)
        return "PBKDF2:$ITERASI:$saltB64:$hashB64"
    }

    /**
     * Memverifikasi kecocokan password dengan hash yang tersimpan.
     * Menggunakan MessageDigest.isEqual() untuk perbandingan waktu-konstan (mencegah timing attack).
     */
    fun verify(password: String, storedHash: String): Boolean {
        if (storedHash.isBlank()) return false

        // Format standar baru: PBKDF2:{iterasi}:{salt}:{hash}
        if (storedHash.startsWith("PBKDF2:")) {
            val bagian = storedHash.split(":")
            if (bagian.size != 4) return false
            val iterasi = bagian[1].toIntOrNull() ?: return false
            val salt = runCatching { Base64.getDecoder().decode(bagian[2]) }.getOrNull() ?: return false
            val expectedHash = runCatching { Base64.getDecoder().decode(bagian[3]) }.getOrNull() ?: return false

            val spec = PBEKeySpec(password.toCharArray(), salt, iterasi, expectedHash.size * 8)
            val factory = SecretKeyFactory.getInstance(ALGORITMA_PBKDF2)
            val calculatedHash = factory.generateSecret(spec).encoded
            return MessageDigest.isEqual(expectedHash, calculatedHash)
        }

        // Kompatibilitas mundur untuk hash legacy (SHA-256 + salt tunggal format: {salt}:{hash})
        return verifyLegacySha256(password, storedHash)
    }

    /** Menandai apakah sebuah hash masih memakai format lama yang perlu diperbarui */
    fun butuhUpgrade(storedHash: String): Boolean = !storedHash.startsWith("PBKDF2:")

    private fun verifyLegacySha256(password: String, storedHash: String): Boolean {
        val salt = storedHash.substringBefore(":")
        val expectedB64 = storedHash.substringAfter(":", "")
        if (expectedB64.isBlank()) return false

        val digest = MessageDigest.getInstance("SHA-256")
        val hashed = digest.digest((salt + password).toByteArray(Charsets.UTF_8))
        val expectedBytes = runCatching { Base64.getDecoder().decode(expectedB64) }.getOrNull() ?: return false
        return MessageDigest.isEqual(expectedBytes, hashed)
    }
}
