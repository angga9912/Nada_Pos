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
    suspend fun laporanPeriode(start: Long, end: Long, sertakanProdukTerlaris: Boolean = false): LaporanPeriode {
        val items: List<TransactionItemEntity> = transactionDao.getItemsSelesaiDalamRentang(start, end)
        val hargaBeliMap = productDao.getSemuaHargaBeli().associate { it.id to it.hargaBeli }
        val totalDiskon = transactionDao.getTotalDiskonDalamRentang(start, end) ?: 0.0
        val ringkasanMetode = transactionDao.getRingkasanMetodePembayaran(start, end)
        val jumlahTransaksi = transactionDao.getJumlahTransaksiDalamRentang(start, end)
        val totalPenjualan = transactionDao.getTotalPenjualanDalamRentang(start, end)
        val produkTerlaris = if (sertakanProdukTerlaris) transactionDao.getProdukTerlaris(start, end, 10) else emptyList()

        return LaporanPeriode(
            totalPenjualan = totalPenjualan,
            jumlahTransaksi = jumlahTransaksi,
            produkTerjual = items.sumOf { it.qty },
            totalDiskon = totalDiskon,
            estimasiKeuntungan = KeuntunganCalculator.hitungTotalKeuntungan(items, hargaBeliMap),
            produkTerlaris = produkTerlaris,
            ringkasanMetodePembayaran = ringkasanMetode
        )
    }
}
