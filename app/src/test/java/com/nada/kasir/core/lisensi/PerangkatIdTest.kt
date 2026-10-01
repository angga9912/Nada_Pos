package com.nada.kasir.core.lisensi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerangkatIdTest {

    @Test
    fun `turunkan menghasilkan 12 karakter hex huruf besar dan konsisten dengan SHA-256`() {
        // Vektor uji dihitung terpisah (Python hashlib): sha256("NADA-DEV-V1:<sumber>") -> 12 hex pertama.
        assertEquals("1A2BCF0740DF", PerangkatId.turunkan("abc"))
        assertEquals("A6D4CDE158FC", PerangkatId.turunkan("0123456789abcdef"))
    }

    @Test
    fun `sumber berbeda menghasilkan ID berbeda dan sumber sama selalu menghasilkan ID sama`() {
        assertNotEquals(PerangkatId.turunkan("perangkat-1"), PerangkatId.turunkan("perangkat-2"))
        assertEquals(PerangkatId.turunkan("perangkat-1"), PerangkatId.turunkan("perangkat-1"))
        assertTrue(PerangkatId.turunkan("x").matches(Regex("[0-9A-F]{12}")))
    }

    @Test
    fun `tampil mengelompokkan per empat karakter`() {
        assertEquals("A1B2-C3D4-E5F6", PerangkatId.tampil("A1B2C3D4E5F6"))
    }

    @Test
    fun `normalisasi menerima huruf kecil spasi dan tanda hubung`() {
        assertEquals("A1B2C3D4E5F6", PerangkatId.normalisasi("a1b2-c3d4-e5f6"))
        assertEquals("A1B2C3D4E5F6", PerangkatId.normalisasi("  A1B2 C3D4 E5F6\n"))
        assertEquals("A1B2C3D4E5F6", PerangkatId.normalisasi("A1B2C3D4E5F6"))
    }

    @Test
    fun `normalisasi menolak panjang salah atau karakter bukan hex`() {
        assertNull(PerangkatId.normalisasi(""))
        assertNull(PerangkatId.normalisasi("A1B2-C3D4"))
        assertNull(PerangkatId.normalisasi("A1B2C3D4E5F6A"))
        assertNull(PerangkatId.normalisasi("ZZZZ-ZZZZ-ZZZZ"))
    }

    @Test
    fun `hasil turunkan selalu lolos normalisasi`() {
        val id = PerangkatId.turunkan("apa-saja")
        assertEquals(id, PerangkatId.normalisasi(PerangkatId.tampil(id)))
    }
}
