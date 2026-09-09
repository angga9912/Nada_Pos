package com.nada.kasir.core.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PembayaranCalculatorTest {

    @Test
    fun `kembalian dihitung dengan benar - contoh dari brief`() {
        // TOTAL 13.000, TUNAI 20.000, KEMBALI 7.000 (sesuai contoh struk di brief)
        val kembalian = PembayaranCalculator.hitungKembalian(uangDiterima = 20000.0, total = 13000.0)
        assertEquals(7000.0, kembalian, 0.0)
    }

    @Test
    fun `pembayaran pas dianggap cukup`() {
        assertTrue(PembayaranCalculator.cukup(uangDiterima = 13000.0, total = 13000.0))
    }

    @Test
    fun `pembayaran kurang ditolak`() {
        assertFalse(PembayaranCalculator.cukup(uangDiterima = 10000.0, total = 13000.0))
    }
}
