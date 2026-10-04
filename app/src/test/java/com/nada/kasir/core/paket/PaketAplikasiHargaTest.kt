package com.nada.kasir.core.paket

import org.junit.Assert.assertEquals
import org.junit.Test

class PaketAplikasiHargaTest {

    @Test
    fun `basic gratis`() {
        assertEquals("Gratis", PaketAplikasi.BASIC.hargaLabel)
        assertEquals("Gratis", PaketAplikasi.BASIC.hargaSingkat)
    }

    @Test
    fun `custom mulai dari 25 ribu per bulan`() {
        assertEquals("Mulai Rp25.000/bulan", PaketAplikasi.CUSTOM.hargaLabel)
        assertEquals("Mulai Rp25rb/bln", PaketAplikasi.CUSTOM.hargaSingkat)
    }

    @Test
    fun `pro 50 ribu per bulan harga tetap`() {
        assertEquals("Rp50.000/bulan", PaketAplikasi.PRO.hargaLabel)
        assertEquals("Rp50rb/bln", PaketAplikasi.PRO.hargaSingkat)
    }
}
