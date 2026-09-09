package com.nada.kasir.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyFormatterTest {

    @Test
    fun `format menampilkan simbol dan pemisah ribuan yang benar`() {
        val hasil = CurrencyFormatter.format(13000.0, "Rp")
        assertEquals("Rp 13.000", hasil)
    }

    @Test
    fun `format membulatkan tanpa desimal`() {
        val hasil = CurrencyFormatter.format(7000.0)
        assertEquals("Rp 7.000", hasil)
    }
}
