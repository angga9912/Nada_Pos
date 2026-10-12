package com.nada.kasir.core.data.repository

import com.nada.kasir.core.data.local.dao.ProductDao
import com.nada.kasir.core.data.local.dao.ProdukTerlaris
import com.nada.kasir.core.data.local.dao.RingkasanMetodePembayaran
import com.nada.kasir.core.data.local.dao.TransactionDao
import com.nada.kasir.core.data.local.entity.TransactionItemEntity
import com.nada.kasir.core.domain.logic.KeuntunganCalculator
import javax.inject.Inject
import javax.inject.Singleton

data class LaporanPeriode(
    val totalPenjualan: Double,
    val jumlahTransaksi: Int,
    val produkTerjual: Int,
    val totalDiskon: Double,
    val estimasiKeuntungan: Double,
    val produkTerlaris: List<ProdukTerlaris> = emptyList(),
    val ringkasanMetodePembayaran: List<RingkasanMetodePembayaran> = emptyList()
)

/** LAPORAN (poin 14): Harian, Bulanan, Stok (Stok pakai ProductRepository yang sudah ada). */
@Singleton
class ReportRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val productDao: ProductDao
) {
    /** @param outletId null = semua cabang; selain itu hanya transaksi berlabel cabang tersebut. */
    suspend fun laporanPeriode(start: Long, end: Long, sertakanProdukTerlaris: Boolean = false, outletId: Long? = null): LaporanPeriode {
        val items: List<TransactionItemEntity> = transactionDao.getItemsSelesaiDalamRentang(start, end, outletId)
        val hargaBeliMap = productDao.getSemuaHargaBeli().associate { it.id to it.hargaBeli }
        val totalDiskon = transactionDao.getTotalDiskonDalamRentang(start, end, outletId) ?: 0.0
        val ringkasanMetode = transactionDao.getRingkasanMetodePembayaran(start, end, outletId)
        val jumlahTransaksi = transactionDao.getJumlahTransaksiDalamRentang(start, end, outletId)
        val totalPenjualan = transactionDao.getTotalPenjualanDalamRentang(start, end, outletId)
        val produkTerlaris = if (sertakanProdukTerlaris) transactionDao.getProdukTerlaris(start, end, 10, outletId) else emptyList()

        return LaporanPeriode(
            totalPenjualan = totalPenjualan,
            jumlahTransaksi = jumlahTransaksi,
            produkTerjual = items.sumOf { it.qty },
            totalDiskon = totalDiskon,
            estimasiKeuntungan = KeuntunganCalculator.hitungTotalKeuntungan(items, hargaBeliMap, totalDiskon),
            produkTerlaris = produkTerlaris,
            ringkasanMetodePembayaran = ringkasanMetode
        )
    }
}
