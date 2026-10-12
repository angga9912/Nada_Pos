package com.nada.kasir.core.data.repository

import androidx.room.withTransaction
import com.nada.kasir.core.data.local.AppDatabase
import com.nada.kasir.core.data.local.dao.ProductDao
import com.nada.kasir.core.data.local.dao.StockMovementDao
import com.nada.kasir.core.data.local.entity.StockMovementEntity
import com.nada.kasir.core.data.local.entity.TipeMutasiStok
import com.nada.kasir.core.domain.logic.MutasiStokManual
import com.nada.kasir.core.domain.logic.StokLogic
import com.nada.kasir.core.util.AppError
import com.nada.kasir.core.util.Result
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Perubahan stok manual: stok masuk, stok keluar, dan opname (Phase 5 tahap 3).
 *
 * Setiap fungsi mengubah stok dan mencatat mutasinya dalam SATU transaksi database. Penting: nilai
 * `Result.Failure` yang dikembalikan dari dalam blok transaksi TIDAK membatalkan penulisan sebelumnya,
 * jadi semua pengecekan dilakukan SEBELUM penulisan pertama.
 */
@Singleton
class StockRepository @Inject constructor(
    private val appDatabase: AppDatabase,
    private val productDao: ProductDao,
    private val stockMovementDao: StockMovementDao,
    private val outletRepository: OutletRepository
) {
    fun observeRiwayat(): Flow<List<StockMovementEntity>> = stockMovementDao.observeAll()

    /**
     * Stok Masuk manual (stok awal tambahan, hadiah, retur pelanggan, dll). Untuk belanja dari supplier
     * gunakan menu Pembelian agar hutangnya ikut tercatat.
     *
     * @param hargaBeli harga beli per unit (opsional). Hanya dipakai bila [perbaruiHargaBeli] true.
     */
    suspend fun stokMasuk(
        productId: Long,
        qty: Int,
        hargaBeli: Double?,
        supplierId: Long?,
        keterangan: String?,
        perbaruiHargaBeli: Boolean = false
    ): Result<Unit> {
        StokLogic.validasiQty(qty)?.let { return Result.Failure(AppError.Lainnya(it)) }
        StokLogic.validasiHargaBeli(hargaBeli)?.let { return Result.Failure(AppError.Lainnya(it)) }
        val outletId = outletRepository.idAktifEfektif()
        return try {
            appDatabase.withTransaction<Result<Unit>> {
                val produk = productDao.findById(productId)
                if (produk == null || produk.deletedAt != null) {
                    return@withTransaction Result.Failure(AppError.Lainnya("Produk tidak ditemukan atau sudah dihapus."))
                }
                productDao.increaseStock(productId, qty)
                stockMovementDao.insert(
                    StockMovementEntity(
                        productId = productId,
                        tipe = TipeMutasiStok.MASUK,
                        qty = qty,
                        supplierId = supplierId,
                        outletId = outletId,
                        keterangan = keterangan,
                        tanggalWaktu = System.currentTimeMillis()
                    )
                )
                if (perbaruiHargaBeli && hargaBeli != null) {
                    productDao.updateHargaBeli(productId, hargaBeli)
                }
                Result.Success(Unit)
            }
        } catch (e: Exception) {
            Result.Failure(AppError.TransaksiGagalDisimpan)
        }
    }

    /** Stok Keluar manual (rusak, hilang, dll). Menolak stok minus kecuali diizinkan admin (poin 7). */
    suspend fun stokKeluar(
        productId: Long,
        qty: Int,
        keterangan: String?,
        izinkanMinus: Boolean = false
    ): Result<Unit> {
        StokLogic.validasiQty(qty)?.let { return Result.Failure(AppError.Lainnya(it)) }
        val outletId = outletRepository.idAktifEfektif()
        return try {
            appDatabase.withTransaction<Result<Unit>> {
                val produk = productDao.findById(productId)
                if (produk == null || produk.deletedAt != null) {
                    return@withTransaction Result.Failure(AppError.Lainnya("Produk tidak ditemukan atau sudah dihapus."))
                }
                val stokSaatIni = productDao.getStok(productId)
                if (!izinkanMinus && stokSaatIni < qty) {
                    return@withTransaction Result.Failure(
                        AppError.Lainnya("Stok ${produk.nama} sekarang $stokSaatIni, tidak cukup untuk dikurangi $qty.")
                    )
                }
                productDao.decreaseStock(productId, qty)
                stockMovementDao.insert(
                    StockMovementEntity(
                        productId = productId,
                        tipe = TipeMutasiStok.KELUAR,
                        qty = qty,
                        outletId = outletId,
                        keterangan = keterangan,
                        tanggalWaktu = System.currentTimeMillis()
                    )
                )
                Result.Success(Unit)
            }
        } catch (e: Exception) {
            Result.Failure(AppError.TransaksiGagalDisimpan)
        }
    }

    /**
     * Opname / penyesuaian: samakan stok sistem dengan hasil hitung fisik. Selisihnya dicatat sebagai
     * mutasi (naik = MASUK, turun = KELUAR; konvensi [MutasiStokManual]).
     *
     * @return selisih yang diterapkan (positif = bertambah, negatif = berkurang).
     */
    suspend fun opname(productId: Long, stokFisik: Int, keterangan: String?): Result<Int> {
        val outletId = outletRepository.idAktifEfektif()
        return try {
            appDatabase.withTransaction<Result<Int>> {
                val produk = productDao.findById(productId)
                if (produk == null || produk.deletedAt != null) {
                    return@withTransaction Result.Failure(AppError.Lainnya("Produk tidak ditemukan atau sudah dihapus."))
                }
                val stokSistem = productDao.getStok(productId)
                StokLogic.validasiOpname(stokSistem, stokFisik)?.let {
                    return@withTransaction Result.Failure(AppError.Lainnya(it))
                }
                val selisih = StokLogic.selisihOpname(stokSistem, stokFisik)
                if (selisih > 0) productDao.increaseStock(productId, selisih) else productDao.decreaseStock(productId, -selisih)
                MutasiStokManual.buat(productId, stokSistem, stokFisik, keterangan ?: StokLogic.keteranganOpname(null), System.currentTimeMillis())
                    ?.let { stockMovementDao.insert(it.copy(outletId = outletId)) }
                Result.Success(selisih)
            }
        } catch (e: Exception) {
            Result.Failure(AppError.TransaksiGagalDisimpan)
        }
    }
}
