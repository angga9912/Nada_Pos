package com.nada.kasir.core.util

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Hashing password sederhana (SHA-256 + salt). Jangan pernah simpan password
 * plain text (poin 26). Untuk produksi, pertimbangkan BCrypt/Argon2.
 */
object PasswordHasher {
    fun hash(password: String, salt: String = generateSalt()): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashed = digest.digest((salt + password).toByteArray())
        return "$salt:${Base64.getEncoder().encodeToString(hashed)}"
    }

    fun verify(password: String, storedHash: String): Boolean {
        val salt = storedHash.substringBefore(":")
        return hash(password, salt) == storedHash
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }
}
