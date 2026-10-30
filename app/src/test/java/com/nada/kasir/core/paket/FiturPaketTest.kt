package com.nada.kasir.core.paket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FiturPaketTest {

    private val fiturPro = listOf(
        FiturPaket.SUPPLIER, FiturPaket.PEMBELIAN, FiturPaket.OUTLET, FiturPaket.HUTANG_PIUTANG, FiturPaket.PENGGUNA,
        FiturPaket.PANTAU_CABANG
    )

    @Test
    fun `supplier outlet hutang piutang dan pengguna hanya untuk pro`() {
        fiturPro.forEach { fitur ->
            assertFalse("${fitur.name} tidak boleh tersedia di Basic", PaketAplikasi.BASIC.mengizinkan(fitur))
            assertFalse("${fitur.name} tidak boleh tersedia di Custom", PaketAplikasi.CUSTOM.mengizinkan(fitur))
            assertTrue("${fitur.name} harus tersedia di Pro", PaketAplikasi.PRO.mengizinkan(fitur))
        }
    }

    @Test
    fun `backup tersedia di custom dan pro tetapi tidak di basic`() {
        assertFalse(PaketAplikasi.BASIC.mengizinkan(FiturPaket.BACKUP))
        assertTrue(PaketAplikasi.CUSTOM.mengizinkan(FiturPaket.BACKUP))
        assertTrue(PaketAplikasi.PRO.mengizinkan(FiturPaket.BACKUP))
    }

    @Test
    fun `label paket untuk pesan terkunci`() {
        assertEquals("Pro", FiturPaket.SUPPLIER.labelPaketTersedia)
        assertEquals("Custom dan Pro", FiturPaket.BACKUP.labelPaketTersedia)
    }

    @Test
    fun `logo toko hanya untuk custom dan pro`() {
        assertFalse(PaketAplikasi.BASIC.mengizinkan(FiturPaket.LOGO_TOKO))
        assertTrue(PaketAplikasi.CUSTOM.mengizinkan(FiturPaket.LOGO_TOKO))
        assertTrue(PaketAplikasi.PRO.mengizinkan(FiturPaket.LOGO_TOKO))
    }
}
