package com.nada.kasir.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LabelMetodePembayaranTest {
    @Test
    fun `hutang ditampilkan sebagai piutang belum dibayar`() {
        assertEquals("Piutang (belum dibayar)", labelMetodePembayaran("HUTANG"))
    }

    @Test
    fun `metode lain tidak berubah`() {
        assertEquals("TUNAI", labelMetodePembayaran("TUNAI"))
        assertEquals("QRIS", labelMetodePembayaran("QRIS"))
    }
}
