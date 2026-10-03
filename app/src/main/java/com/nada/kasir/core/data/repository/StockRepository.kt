package com.nada.kasir.core.data.repository

import com.nada.kasir.core.data.local.dao.ProductDao
import com.nada.kasir.core.data.local.dao.StockMovementDao
import com.nada.kasir.core.data.local.entity.StockMovementEntity
import com.nada.kasir.core.data.local.entity.TipeMutasiStok
import com.nada.kasir.core.util.AppError
import com.nada.kasir.core.util.Result
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StockRepository @Inject constructor(
    private val productDao: ProductDao,
    private val stockMovementDao: StockMovementDao
) {
    fun observeRiwayat(): Flow<List<StockMovementEntity>> = stockMovementDao.observeAll()

    /** Stok Masuk - menambah stok produk dan mencatat mutasi. */
    suspend fun stokMasuk(productId: Long, qty: Int, hargaBeli: Double?, supplier: String?, keterangan: String?): Result<Unit> {
        return try {
            productDao.increaseStock(productId, qty)
            stockMovementDao.insert(
                StockMovementEntity(
                    productId = productId,
                    tipe = TipeMutasiStok.MASUK,
                    qty = qty,
                    supplier = supplier,
                    keterangan = keterangan,
                    tanggalWaktu = System.currentTimeMillis()
                )
            )
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(AppError.TransaksiGagalDisimpan)
        }
    }

    /** Stok Keluar / Penyesuaian manual. Menolak stok minus kecuali diizinkan admin (poin 7). */
    suspend fun stokKeluar(productId: Long, qty: Int, keterangan: String?, izinkanMinus: Boolean = false): Result<Unit> {
        val stokSaatIni = productDao.getStok(productId)
        if (!izinkanMinus && stokSaatIni < qty) {
            return Result.Failure(AppError.StokTidakCukup)
        }
        return try {
            productDao.decreaseStock(productId, qty)
            stockMovementDao.insert(
                StockMovementEntity(
                    productId = productId,
                    tipe = TipeMutasiStok.KELUAR,
                    qty = qty,
                    keterangan = keterangan,
                    tanggalWaktu = System.currentTimeMillis()
                )
            )
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(AppError.TransaksiGagalDisimpan)
        }
    }
}
