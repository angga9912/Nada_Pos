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

    @Test
    fun `diskon di antara nol dan subtotal valid`() {
        assertTrue(PembayaranCalculator.diskonValid(diskon = 0.0, subtotal = 10000.0))
        assertTrue(PembayaranCalculator.diskonValid(diskon = 2500.0, subtotal = 10000.0))
        assertTrue(PembayaranCalculator.diskonValid(diskon = 10000.0, subtotal = 10000.0))
    }

    @Test
    fun `diskon negatif atau melebihi subtotal ditolak`() {
        assertFalse(PembayaranCalculator.diskonValid(diskon = -1.0, subtotal = 10000.0))
        assertFalse(PembayaranCalculator.diskonValid(diskon = 10001.0, subtotal = 10000.0))
        assertFalse(PembayaranCalculator.diskonValid(diskon = 1.0, subtotal = 0.0))
    }

    @Test
    fun `diskon bukan angka nyata ditolak`() {
        assertFalse(PembayaranCalculator.diskonValid(diskon = Double.NaN, subtotal = 10000.0))
        assertFalse(PembayaranCalculator.diskonValid(diskon = Double.POSITIVE_INFINITY, subtotal = 10000.0))
    }

    @Test
    fun `batasiDiskon memotong diskon ke subtotal setelah isi keranjang berkurang`() {
        assertEquals(5000.0, PembayaranCalculator.batasiDiskon(diskon = 8000.0, subtotal = 5000.0), 0.0)
        assertEquals(3000.0, PembayaranCalculator.batasiDiskon(diskon = 3000.0, subtotal = 5000.0), 0.0)
        assertEquals(0.0, PembayaranCalculator.batasiDiskon(diskon = 3000.0, subtotal = 0.0), 0.0)
    }
}
