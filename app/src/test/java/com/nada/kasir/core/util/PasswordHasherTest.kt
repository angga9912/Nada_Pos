package com.nada.kasir.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun `password tidak pernah disimpan sebagai plain text`() {
        val hash = PasswordHasher.hash("rahasia123")
        assertNotEquals("rahasia123", hash)
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
}
