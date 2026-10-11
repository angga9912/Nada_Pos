package com.nada.kasir.core.domain.logic

import com.nada.kasir.core.data.local.entity.StockMovementEntity
import com.nada.kasir.core.data.local.entity.TipeMutasiStok
import com.nada.kasir.core.domain.logic.StokLogic.AlasanKeluar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StokLogicTest {

    @Test
    fun `qty nol negatif atau terlalu besar ditolak`() {
        assertNotNull(StokLogic.validasiQty(0))
        assertNotNull(StokLogic.validasiQty(-5))
        assertNotNull(StokLogic.validasiQty(StokLogic.QTY_MAKS + 1))
        assertNull(StokLogic.validasiQty(1))
        assertNull(StokLogic.validasiQty(StokLogic.QTY_MAKS))
    }

    @Test
    fun `harga beli kosong boleh, negatif atau NaN ditolak, nol boleh untuk barang bonus`() {
        assertNull(StokLogic.validasiHargaBeli(null))
        assertNull(StokLogic.validasiHargaBeli(0.0))
        assertNull(StokLogic.validasiHargaBeli(1500.0))
        assertNotNull(StokLogic.validasiHargaBeli(-1.0))
        assertNotNull(StokLogic.validasiHargaBeli(Double.NaN))
    }

    @Test
    fun `stok keluar tidak boleh melebihi stok sistem`() {
        assertNull(StokLogic.validasiStokKeluar(10, 10, AlasanKeluar.RUSAK, null))
        val pesan = StokLogic.validasiStokKeluar(10, 11, AlasanKeluar.RUSAK, null)
        assertNotNull(pesan)
        assertTrue(pesan!!.contains("10"))
        assertTrue(pesan.contains("11"))
    }

    @Test
    fun `stok keluar boleh minus bila diizinkan`() {
        assertNull(StokLogic.validasiStokKeluar(2, 5, AlasanKeluar.HILANG, null, izinkanMinus = true))
    }

    @Test
    fun `stok keluar dengan qty tidak valid ditolak walau stok cukup`() {
        assertNotNull(StokLogic.validasiStokKeluar(10, 0, AlasanKeluar.RUSAK, null))
        assertNotNull(StokLogic.validasiStokKeluar(10, -1, AlasanKeluar.RUSAK, null))
    }

    @Test
    fun `alasan Lainnya wajib dicatat tetapi alasan lain tidak`() {
        assertNotNull(StokLogic.validasiStokKeluar(10, 1, AlasanKeluar.LAINNYA, null))
        assertNotNull(StokLogic.validasiStokKeluar(10, 1, AlasanKeluar.LAINNYA, "   "))
        assertNull(StokLogic.validasiStokKeluar(10, 1, AlasanKeluar.LAINNYA, "sampel untuk pelanggan"))
        assertNull(StokLogic.validasiStokKeluar(10, 1, AlasanKeluar.KADALUARSA, null))
    }

    @Test
    fun `opname menolak stok fisik negatif atau sama dengan stok sistem`() {
        assertNotNull(StokLogic.validasiOpname(10, -1))
        assertNotNull(StokLogic.validasiOpname(10, 10))
        assertNotNull(StokLogic.validasiOpname(10, StokLogic.QTY_MAKS + 1))
        assertNull(StokLogic.validasiOpname(10, 0)) // stok dihitung habis itu sah
        assertNull(StokLogic.validasiOpname(0, 7))
    }

    @Test
    fun `selisih opname bertanda sesuai arah`() {
        assertEquals(5, StokLogic.selisihOpname(10, 15))
        assertEquals(-4, StokLogic.selisihOpname(10, 6))
        assertEquals(0, StokLogic.selisihOpname(3, 3))
    }

    @Test
    fun `opname menghasilkan mutasi dengan arah yang benar lewat MutasiStokManual`() {
        val naik = MutasiStokManual.buat(1, 10, 15, StokLogic.keteranganOpname("hitung ulang"), 0L)!!
        assertEquals(TipeMutasiStok.MASUK, naik.tipe)
        assertEquals(5, naik.qty)
        val turun = MutasiStokManual.buat(1, 10, 6, StokLogic.keteranganOpname(null), 0L)!!
        assertEquals(TipeMutasiStok.KELUAR, turun.tipe)
        assertEquals(4, turun.qty)
    }

    @Test
    fun `keterangan menyertakan catatan hanya bila ada`() {
        assertEquals("Stok masuk manual", StokLogic.keteranganMasuk(null))
        assertEquals("Stok masuk manual", StokLogic.keteranganMasuk("   "))
        assertEquals("Stok masuk manual — hadiah", StokLogic.keteranganMasuk(" hadiah "))
        assertEquals("Stok keluar: Rusak", StokLogic.keteranganKeluar(AlasanKeluar.RUSAK, null))
        assertEquals("Stok keluar: Hilang — dicuri", StokLogic.keteranganKeluar(AlasanKeluar.HILANG, "dicuri"))
        assertEquals("Stok opname", StokLogic.keteranganOpname(null))
    }

    @Test
    fun `mutasi manual dibedakan dari mutasi akibat penjualan dan pembelian`() {
        fun m(trx: Long? = null, beli: Long? = null) = StockMovementEntity(
            productId = 1, tipe = TipeMutasiStok.KELUAR, qty = 1,
            referensiTransaksiId = trx, referensiPembelianId = beli, tanggalWaktu = 0L
        )
        assertTrue(StokLogic.mutasiManual(m()))
        assertFalse(StokLogic.mutasiManual(m(trx = 5)))
        assertFalse(StokLogic.mutasiManual(m(beli = 2)))
    }

    @Test
    fun `arah dan teks qty konsisten untuk semua tipe`() {
        assertTrue(StokLogic.menambah(TipeMutasiStok.MASUK))
        assertTrue(StokLogic.menambah(TipeMutasiStok.PEMBATALAN))
        assertFalse(StokLogic.menambah(TipeMutasiStok.KELUAR))
        assertFalse(StokLogic.menambah(TipeMutasiStok.PENJUALAN))
        assertEquals("+5", StokLogic.teksQty(TipeMutasiStok.MASUK, 5))
        assertEquals("+2", StokLogic.teksQty(TipeMutasiStok.PEMBATALAN, 2))
        assertEquals("−3", StokLogic.teksQty(TipeMutasiStok.KELUAR, 3))
        assertEquals("−1", StokLogic.teksQty(TipeMutasiStok.PENJUALAN, 1))
        assertEquals("±4", StokLogic.teksQty(TipeMutasiStok.PENYESUAIAN, 4))
    }
}
