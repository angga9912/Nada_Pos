package com.nada.kasir.core.domain.logic

import com.nada.kasir.core.data.local.entity.PurchaseStatus
import com.nada.kasir.core.domain.logic.PembelianLogic.ItemInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PembelianLogicTest {

    private val kopi = ItemInput(productId = 1, qty = 10, hargaBeli = 9_000.0)
    private val gula = ItemInput(productId = 2, qty = 5, hargaBeli = 12_000.0, diskon = 5_000.0)

    @Test
    fun `subtotal item memperhitungkan diskon baris`() {
        assertEquals(90_000.0, PembelianLogic.subtotalItem(kopi), 0.0)
        assertEquals(55_000.0, PembelianLogic.subtotalItem(gula), 0.0)
    }

    @Test
    fun `ringkasan menjumlahkan item lalu mengurangi diskon supplier`() {
        val r = PembelianLogic.hitungRingkasan(listOf(kopi, gula), diskonSupplier = 5_000.0)
        assertEquals(145_000.0, r.subtotal, 0.0)
        assertEquals(140_000.0, r.total, 0.0)
    }

    @Test
    fun `pembelian valid tidak menghasilkan pesan error`() {
        assertNull(PembelianLogic.validasiPembelian(listOf(kopi, gula), diskonSupplier = 0.0, bayarSekarang = 50_000.0))
    }

    @Test
    fun `pembelian kosong ditolak`() {
        assertNotNull(PembelianLogic.validasiPembelian(emptyList(), 0.0, 0.0))
    }

    @Test
    fun `barang yang sama dua kali ditolak`() {
        assertNotNull(PembelianLogic.validasiPembelian(listOf(kopi, kopi.copy(qty = 2)), 0.0, 0.0))
    }

    @Test
    fun `qty nol atau negatif ditolak`() {
        assertNotNull(PembelianLogic.validasiPembelian(listOf(kopi.copy(qty = 0)), 0.0, 0.0))
        assertNotNull(PembelianLogic.validasiPembelian(listOf(kopi.copy(qty = -3)), 0.0, 0.0))
    }

    @Test
    fun `harga negatif atau NaN ditolak, harga nol diperbolehkan untuk barang bonus`() {
        assertNotNull(PembelianLogic.validasiPembelian(listOf(kopi.copy(hargaBeli = -1.0)), 0.0, 0.0))
        assertNotNull(PembelianLogic.validasiPembelian(listOf(kopi.copy(hargaBeli = Double.NaN)), 0.0, 0.0))
        assertNull(PembelianLogic.validasiPembelian(listOf(kopi.copy(hargaBeli = 0.0)), 0.0, 0.0))
    }

    @Test
    fun `diskon barang tidak boleh melebihi harga barang`() {
        assertNotNull(PembelianLogic.validasiPembelian(listOf(kopi.copy(diskon = 90_001.0)), 0.0, 0.0))
        assertNull(PembelianLogic.validasiPembelian(listOf(kopi.copy(diskon = 90_000.0)), 0.0, 0.0))
    }

    @Test
    fun `diskon supplier tidak boleh negatif atau melebihi subtotal`() {
        assertNotNull(PembelianLogic.validasiPembelian(listOf(kopi), -1.0, 0.0))
        assertNotNull(PembelianLogic.validasiPembelian(listOf(kopi), 90_001.0, 0.0))
    }

    @Test
    fun `pembayaran tidak boleh negatif atau melebihi total setelah diskon`() {
        assertNotNull(PembelianLogic.validasiPembelian(listOf(kopi), 0.0, -1.0))
        assertNotNull(PembelianLogic.validasiPembelian(listOf(kopi), 10_000.0, 80_001.0))
        assertNull(PembelianLogic.validasiPembelian(listOf(kopi), 10_000.0, 80_000.0))
    }

    @Test
    fun `sisa hutang dihitung dan selisih sangat kecil dianggap lunas`() {
        assertEquals(40_000.0, PembelianLogic.sisaHutang(90_000.0, 50_000.0), 0.0)
        assertEquals(0.0, PembelianLogic.sisaHutang(90_000.0, 90_000.0), 0.0)
        assertEquals(0.0, PembelianLogic.sisaHutang(90_000.0, 89_999.8), 0.0)
        assertEquals(90_000.0, PembelianLogic.sisaHutang(90_000.0, 0.0), 0.0)
    }

    @Test
    fun `nomor pembelian berurutan per hari dan tidak acak`() {
        val awalan = "PUR-20261007-"
        assertEquals(1, PembelianLogic.urutBerikutnya(null, awalan))
        assertEquals(1, PembelianLogic.urutBerikutnya("PUR-20261006-0042", awalan)) // hari lain
        assertEquals(43, PembelianLogic.urutBerikutnya("PUR-20261007-0042", awalan))
        assertEquals("PUR-20261007-0043", PembelianLogic.formatNomor(awalan, 43))
        assertEquals("PUR-20261007-0001", PembelianLogic.formatNomor(awalan, 1))
    }

    @Test
    fun `awalan nomor berformat PUR-tanggal`() {
        assertTrue(PembelianLogic.awalanNomor(1_800_000_000_000L).matches(Regex("PUR-\\d{8}-")))
    }

    @Test
    fun `label status dalam bahasa Indonesia`() {
        assertEquals("Menunggu barang", PembelianLogic.labelStatus(PurchaseStatus.PENDING))
        assertEquals("Diterima", PembelianLogic.labelStatus(PurchaseStatus.RECEIVED))
        assertEquals("Dibatalkan", PembelianLogic.labelStatus(PurchaseStatus.CANCELLED))
    }
}
