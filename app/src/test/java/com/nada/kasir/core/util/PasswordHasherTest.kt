package com.nada.kasir.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class PasswordHasherTest {

    @Test
    fun `password tidak pernah disimpan sebagai plain text`() {
        val hash = PasswordHasher.hash("rahasia123")
        assertNotEquals("rahasia123", hash)
        assertTrue("Format harus PBKDF2", hash.startsWith("PBKDF2:"))
    }

    @Test
    fun `verify berhasil untuk password yang benar`() {
        val hash = PasswordHasher.hash("rahasia123")
        assertTrue(PasswordHasher.verify("rahasia123", hash))
    }

    @Test
    fun `verify gagal untuk password yang salah`() {
        val hash = PasswordHasher.hash("rahasia123")
        assertFalse(PasswordHasher.verify("salahpassword", hash))
    }

    @Test
    fun `mendukung kompatibilitas mundur dengan hash legacy SHA-256`() {
        val salt = "testSalt12345678"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashed = digest.digest((salt + "admin123").toByteArray(Charsets.UTF_8))
        val legacyHash = "$salt:${Base64.getEncoder().encodeToString(hashed)}"

        assertTrue("Hash lama harus dapat diverifikasi", PasswordHasher.verify("admin123", legacyHash))
        assertFalse("Password salah pada hash lama harus ditolak", PasswordHasher.verify("wrongpass", legacyHash))
        assertTrue("Hash lama harus ditandai butuh upgrade", PasswordHasher.butuhUpgrade(legacyHash))
    }

    @Test
    fun `hash PBKDF2 tidak ditandai butuh upgrade`() {
        val hash = PasswordHasher.hash("admin123")
        assertFalse(PasswordHasher.butuhUpgrade(hash))
    }
}
