package com.nada.kasir.core.pantau

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KodeUndanganLogicTest {

    @Test
    fun `kode undangan dirapikan dari spasi, tanda hubung, dan huruf kecil`() {
        assertEquals("ABCDE23456", RekapHarianLogic.bersihkanKodeUndangan("  abcde-23456 \n"))
        assertEquals("ABCDE23456", RekapHarianLogic.bersihkanKodeUndangan("ABCDE 23456"))
    }

    @Test
    fun `kode undangan sah hanya 10 huruf atau angka`() {
        assertNull(RekapHarianLogic.validasiKodeUndangan("ABCDE23456"))
        assertNull(RekapHarianLogic.validasiKodeUndangan(" abcde 23456 "))
        assertNotNull(RekapHarianLogic.validasiKodeUndangan(""))
        assertNotNull(RekapHarianLogic.validasiKodeUndangan("ABC123"))
        assertNotNull(RekapHarianLogic.validasiKodeUndangan("ABCDE234567"))
        assertNotNull(RekapHarianLogic.validasiKodeUndangan("ABCDE2345!"))
    }

    @Test
    fun `identitas undangan dianggap bergabung hanya bila kodeDokumen terisi`() {
        assertTrue(PantauCabangPengaturan.Konfigurasi(kodeDokumen = "CBG03", namaCabang = "Cabang Tiga").bergabungLewatUndangan)
        assertEquals(false, PantauCabangPengaturan.Konfigurasi().bergabungLewatUndangan)
    }
}
