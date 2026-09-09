package com.nada.kasir.core.printer

import org.junit.Assert.assertTrue
import org.junit.Test

class EscPosBuilderTest {

    @Test
    fun `baris2Kolom menghasilkan label dan nilai dalam satu baris sesuai lebar kertas`() {
        val bytes = EscPosBuilder().baris2Kolom("TOTAL", "13.000", lebarKolom = 32).build()
        val teks = String(bytes, Charsets.US_ASCII)
        assertTrue(teks.startsWith("TOTAL"))
        assertTrue(teks.trimEnd('\n').endsWith("13.000"))
        // Total panjang baris (sebelum newline) harus pas dengan lebar kertas
        assertTrue(teks.trimEnd('\n').length == 32)
    }

    @Test
    fun `garis menghasilkan tanda pemisah sepanjang lebar kolom`() {
        val bytes = EscPosBuilder().garis(lebarKolom = 32).build()
        val teks = String(bytes, Charsets.US_ASCII).trimEnd('\n')
        assertTrue(teks.length == 32)
        assertTrue(teks.all { it == '-' })
    }
}
