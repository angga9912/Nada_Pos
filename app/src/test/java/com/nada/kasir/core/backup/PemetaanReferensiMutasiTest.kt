package com.nada.kasir.core.backup

import com.nada.kasir.core.data.local.entity.StockMovementEntity
import com.nada.kasir.core.data.local.entity.TipeMutasiStok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PemetaanReferensiMutasiTest {

    private fun mutasi(id: Long, ref: Long?) = StockMovementEntity(
        id = id, productId = 5, tipe = TipeMutasiStok.KELUAR, qty = 2,
        referensiTransaksiId = ref, supplier = "S", keterangan = "k", tanggalWaktu = 123L
    )

    @Test
    fun `referensi dipetakan ke id transaksi baru`() {
        // Di backup transaksi punya id 1..3; setelah restore menjadi 41..43 (urutan autoincrement lama berlanjut).
        val peta = mapOf(1L to 41L, 2L to 42L, 3L to 43L)
        val hasil = PemetaanReferensiMutasi.petakan(listOf(mutasi(1, 1), mutasi(2, 3)), peta)
        assertEquals(41L, hasil[0].referensiTransaksiId)
        assertEquals(43L, hasil[1].referensiTransaksiId)
    }

    @Test
    fun `referensi tidak boleh tersisa sebagai id lama yang kebetulan cocok dengan transaksi lain`() {
        // id lama 2 kebetulan juga ada sebagai id BARU milik transaksi lain (peta 1->2, 2->3):
        // hasilnya harus mengikuti peta, bukan membiarkan angka 2 apa adanya.
        val peta = mapOf(1L to 2L, 2L to 3L)
        val hasil = PemetaanReferensiMutasi.petakan(listOf(mutasi(1, 2)), peta)
        assertEquals(3L, hasil.single().referensiTransaksiId)
    }

    @Test
    fun `referensi ke transaksi yang tidak ada di backup menjadi null`() {
        val hasil = PemetaanReferensiMutasi.petakan(listOf(mutasi(1, 99)), mapOf(1L to 41L))
        assertNull(hasil.single().referensiTransaksiId)
    }

    @Test
    fun `mutasi tanpa referensi tetap tanpa referensi`() {
        val hasil = PemetaanReferensiMutasi.petakan(listOf(mutasi(1, null)), mapOf(1L to 41L))
        assertNull(hasil.single().referensiTransaksiId)
    }

    @Test
    fun `field lain tidak berubah`() {
        val asli = mutasi(id = 9, ref = 1)
        val hasil = PemetaanReferensiMutasi.petakan(listOf(asli), mapOf(1L to 41L)).single()
        assertEquals(asli.copy(referensiTransaksiId = 41L), hasil)
    }

    @Test
    fun `daftar kosong tetap kosong`() {
        assertEquals(emptyList<StockMovementEntity>(), PemetaanReferensiMutasi.petakan(emptyList(), mapOf(1L to 2L)))
    }
}
