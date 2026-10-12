package com.nada.kasir.core.domain.logic

import com.nada.kasir.core.data.local.entity.OutletEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OutletAktifLogicTest {

    private fun outlet(id: Long, nama: String, kode: String, aktif: Boolean = true) =
        OutletEntity(id = id, nama = nama, kode = kode, aktif = aktif)

    private val pusat = outlet(1, "Pusat", "PST")
    private val cabangA = outlet(2, "Cabang A", "CBA")
    private val mati = outlet(3, "Cabang Lama", "CBL", aktif = false)

    @Test
    fun `parseId membaca angka positif dan mengabaikan nilai kosong atau rusak`() {
        assertEquals(5L, OutletAktifLogic.parseId("5"))
        assertEquals(5L, OutletAktifLogic.parseId(" 5 "))
        assertNull(OutletAktifLogic.parseId(""))
        assertNull(OutletAktifLogic.parseId(null))
        assertNull(OutletAktifLogic.parseId("abc"))
        assertNull(OutletAktifLogic.parseId("0"))
        assertNull(OutletAktifLogic.parseId("-3"))
    }

    @Test
    fun `outlet aktif dipakai bila paket mengizinkan dan outletnya aktif`() {
        val hasil = OutletAktifLogic.tentukan(2L, listOf(pusat, cabangA), paketMengizinkan = true)
        assertEquals(cabangA, hasil)
    }

    @Test
    fun `tanpa label bila paket tidak mencakup fitur outlet`() {
        assertNull(OutletAktifLogic.tentukan(2L, listOf(pusat, cabangA), paketMengizinkan = false))
    }

    @Test
    fun `tanpa label bila belum ada pilihan`() {
        assertNull(OutletAktifLogic.tentukan(null, listOf(pusat, cabangA), paketMengizinkan = true))
    }

    @Test
    fun `tanpa label bila outlet pilihan sudah nonaktif atau tidak ada`() {
        assertNull(OutletAktifLogic.tentukan(3L, listOf(pusat, cabangA, mati), paketMengizinkan = true))
        assertNull(OutletAktifLogic.tentukan(99L, listOf(pusat, cabangA), paketMengizinkan = true))
    }

    @Test
    fun `form outlet wajib nama dan kode`() {
        assertNotNull(OutletAktifLogic.validasiForm("  ", "X", emptyList()))
        assertNotNull(OutletAktifLogic.validasiForm("Cabang", "  ", emptyList()))
        assertNull(OutletAktifLogic.validasiForm("Cabang", "CBG", emptyList()))
    }

    @Test
    fun `nama dan kode harus unik tanpa membedakan huruf besar kecil`() {
        val semua = listOf(pusat, cabangA)
        assertNotNull(OutletAktifLogic.validasiForm("pusat", "BARU", semua))
        assertNotNull(OutletAktifLogic.validasiForm("Baru", "cba", semua))
        assertNull(OutletAktifLogic.validasiForm("Baru", "BRU", semua))
    }

    @Test
    fun `mengedit outlet sendiri dengan nama dan kode yang sama tidak dianggap bentrok`() {
        val semua = listOf(pusat, cabangA)
        assertNull(OutletAktifLogic.validasiForm("Pusat", "PST", semua, idSendiri = 1L))
        assertNotNull(OutletAktifLogic.validasiForm("Cabang A", "PST", semua, idSendiri = 1L))
    }

    @Test
    fun `label filter laporan`() {
        val semua = listOf(pusat, cabangA)
        assertEquals("Semua cabang", OutletAktifLogic.labelFilter(null, semua))
        assertEquals("Cabang A", OutletAktifLogic.labelFilter(2L, semua))
        assertEquals("Cabang tidak ditemukan", OutletAktifLogic.labelFilter(77L, semua))
    }
}
