package com.nada.kasir.core.domain.logic

import com.nada.kasir.core.data.local.entity.StockMovementEntity
import com.nada.kasir.core.data.local.entity.TipeMutasiStok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MutasiStokManualTest {

    @Test
    fun `stok naik dicatat sebagai MASUK dengan qty selisih`() {
        val m = MutasiStokManual.buat(productId = 7, stokLama = 10, stokBaru = 25, keterangan = "x", waktu = 1000L)
        assertNotNull(m)
        assertEquals(TipeMutasiStok.MASUK, m!!.tipe)
        assertEquals(15, m.qty)
        assertEquals(7L, m.productId)
        assertEquals(1000L, m.tanggalWaktu)
        assertEquals("x", m.keterangan)
        assertNull(m.referensiTransaksiId)
    }

    @Test
    fun `stok turun dicatat sebagai KELUAR dengan qty POSITIF`() {
        val m = MutasiStokManual.buat(7, stokLama = 10, stokBaru = 4, keterangan = "x", waktu = 1L)
        assertEquals(TipeMutasiStok.KELUAR, m!!.tipe)
        assertEquals(6, m.qty) // qty selalu positif, arah ada di tipe
    }

    @Test
    fun `stok tidak berubah tidak menghasilkan mutasi`() {
        assertNull(MutasiStokManual.buat(7, stokLama = 10, stokBaru = 10, keterangan = "x", waktu = 1L))
        assertNull(MutasiStokManual.buat(7, stokLama = 0, stokBaru = 0, keterangan = "x", waktu = 1L))
    }

    @Test
    fun `produk baru dengan stok awal nol tidak menghasilkan mutasi`() {
        assertNull(MutasiStokManual.buat(7, 0, 0, MutasiStokManual.KET_STOK_AWAL, 1L))
    }

    @Test
    fun `produk baru dengan stok awal positif dicatat MASUK`() {
        val m = MutasiStokManual.buat(7, 0, 12, MutasiStokManual.KET_STOK_AWAL, 1L)
        assertEquals(TipeMutasiStok.MASUK, m!!.tipe)
        assertEquals(12, m.qty)
        assertEquals(MutasiStokManual.KET_STOK_AWAL, m.keterangan)
    }

    @Test
    fun `stok diubah ke nol dicatat KELUAR sebesar stok sebelumnya`() {
        val m = MutasiStokManual.buat(7, stokLama = 9, stokBaru = 0, keterangan = "x", waktu = 1L)
        assertEquals(TipeMutasiStok.KELUAR, m!!.tipe)
        assertEquals(9, m.qty)
    }

    /** Rumus stok yang berlaku di seluruh aplikasi, dipakai sebagai "penguji kebenaran riwayat". */
    private fun stokDariMutasi(mutasi: List<StockMovementEntity>): Int = mutasi.sumOf {
        when (it.tipe) {
            TipeMutasiStok.MASUK, TipeMutasiStok.PEMBATALAN -> it.qty
            TipeMutasiStok.KELUAR, TipeMutasiStok.PENJUALAN -> -it.qty
            TipeMutasiStok.PENYESUAIAN -> 0
        }
    }

    @Test
    fun `rangkaian perubahan stok - jumlah mutasi selalu sama dengan stok akhir`() {
        // Skenario nyata: produk dibuat (stok 20), dijual 3 (KELUAR via transaksi), stok diedit ke 30,
        // transaksi dibatalkan (MASUK 3), lalu stok dikoreksi turun ke 25.
        val riwayat = mutableListOf<StockMovementEntity>()
        var stok = 0
        fun ubahManual(baru: Int, ket: String) {
            MutasiStokManual.buat(1, stok, baru, ket, 0L)?.let { riwayat.add(it) }
            stok = baru
        }
        ubahManual(20, MutasiStokManual.KET_STOK_AWAL)
        riwayat.add(StockMovementEntity(productId = 1, tipe = TipeMutasiStok.KELUAR, qty = 3, referensiTransaksiId = 1, tanggalWaktu = 0L)); stok -= 3
        ubahManual(30, MutasiStokManual.KET_PENYESUAIAN)
        riwayat.add(StockMovementEntity(productId = 1, tipe = TipeMutasiStok.MASUK, qty = 3, referensiTransaksiId = 1, tanggalWaktu = 0L)); stok += 3
        ubahManual(25, MutasiStokManual.KET_PENYESUAIAN)

        assertEquals(25, stok)
        assertEquals(stok, stokDariMutasi(riwayat))
        assertTrue(riwayat.all { it.qty > 0 })
    }
}
