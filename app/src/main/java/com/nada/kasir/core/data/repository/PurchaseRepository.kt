package com.nada.kasir.core.data.repository

import com.nada.kasir.core.data.local.AppDatabase
import com.nada.kasir.core.data.local.dao.PurchaseDao
import com.nada.kasir.core.data.local.dao.ProductDao
import com.nada.kasir.core.data.local.dao.StockMovementDao
import com.nada.kasir.core.data.local.entity.PurchaseEntity
import com.nada.kasir.core.data.local.entity.PurchaseItemEntity
import com.nada.kasir.core.data.local.entity.PurchaseStatus
import com.nada.kasir.core.data.local.entity.StockMovementEntity
import com.nada.kasir.core.data.local.entity.TipeMutasiStok
import com.nada.kasir.core.util.AppError
import com.nada.kasir.core.util.Result
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PurchaseRepository @Inject constructor(
    private val appDatabase: AppDatabase,
    private val purchaseDao: PurchaseDao,
    private val productDao: ProductDao,
    private val stockMovementDao: StockMovementDao
) {
    fun observeAll(): Flow<List<PurchaseEntity>> = purchaseDao.observeAll()

    fun observeBySupplier(supplierId: Long): Flow<List<PurchaseEntity>> = purchaseDao.observeBySupplier(supplierId)

    fun observeByStatus(status: String): Flow<List<PurchaseEntity>> = purchaseDao.observeByStatus(status)

    suspend fun simpanPembelian(
        supplierId: Long,
        items: List<Pair<Long, Int>>, // (productId, qty)
        outletId: Long? = null
    ): Result<Long> {
        if (items.isEmpty()) {
            return Result.Failure(AppError.Lainnya("Tidak ada item pembelian."))
        }

        return try {
            val purchaseId = appDatabase.withTransaction {
                val sekarang = System.currentTimeMillis()
                val noPembelian = "PUR-${System.currentTimeMillis()}-${(1..9999).random()}"

                // Hitung total dari product.hargaBeli
                var subtotal = 0.0
                val itemEntities = mutableListOf<PurchaseItemEntity>()
                for ((productId, qty) in items) {
                    val product = productDao.findById(productId) ?: continue
                    val itemSubtotal = product.hargaBeli * qty
                    subtotal += itemSubtotal
                    itemEntities.add(
                        PurchaseItemEntity(
                            purchaseId = 0, // akan diisi setelah purchase dibuat
                            productId = productId,
                            namaProdukSnapshot = product.nama,
                            qty = qty,
                            hargaBeli = product.hargaBeli,
                            subtotal = itemSubtotal
                        )
                    )
                }

                val purchase = PurchaseEntity(
                    noPembelian = noPembelian,
                    supplierId = supplierId,
                    outletId = outletId,
                    tanggalPembelian = sekarang,
                    subtotal = subtotal,
                    totalBayar = subtotal,
                    hutangSisa = subtotal,
                    status = PurchaseStatus.PENDING
                )
                val purchaseId = purchaseDao.insertPurchase(purchase)

                // Insert items dengan purchase id
                val itemsWithId = itemEntities.map { it.copy(purchaseId = purchaseId) }
                purchaseDao.insertItems(itemsWithId)

                purchaseId
            }
            Result.Success(purchaseId)
        } catch (e: Exception) {
            Result.Failure(AppError.Lainnya("Gagal menyimpan pembelian: ${e.message}"))
        }
    }

    suspend fun terimaBarang(purchaseId: Long): Result<Unit> {
        return try {
            appDatabase.withTransaction {
                val purchase = purchaseDao.findById(purchaseId)
                    ?: return@withTransaction Result.Failure(AppError.Lainnya("Pembelian tidak ditemukan."))

                if (purchase.status != PurchaseStatus.PENDING) {
                    return@withTransaction Result.Failure(AppError.Lainnya("Status pembelian harus PENDING untuk diterima."))
                }

                val items = purchaseDao.getItems(purchaseId)
                val sekarang = System.currentTimeMillis()

                // Tambah stok + catat mutasi masuk
                items.forEach { item ->
                    productDao.increaseStock(item.productId, item.qty)
                    stockMovementDao.insert(
                        StockMovementEntity(
                            productId = item.productId,
                            tipe = TipeMutasiStok.MASUK,
                            qty = item.qty,
                            referensiPembelianId = purchaseId,
                            supplierId = purchase.supplierId,
                            outletId = purchase.outletId,
                            keterangan = "Penerimaan barang pembelian ${purchase.noPembelian}",
                            tanggalWaktu = sekarang
                        )
                    )
                }

                // Update purchase status
                val updated = purchase.copy(
                    status = PurchaseStatus.RECEIVED,
                    tanggalTerima = sekarang,
                    updatedAt = sekarang
                )
                purchaseDao.updatePurchase(updated)
                Result.Success(Unit)
            }
        } catch (e: Exception) {
            Result.Failure(AppError.Lainnya("Gagal menerima barang: ${e.message}"))
        }
    }

    suspend fun lunasiHutang(purchaseId: Long, jumlahBayar: Double): Result<Unit> {
        return try {
            val purchase = purchaseDao.findById(purchaseId)
                ?: return Result.Failure(AppError.Lainnya("Pembelian tidak ditemukan."))

            if (jumlahBayar <= 0 || jumlahBayar > purchase.hutangSisa) {
                return Result.Failure(AppError.Lainnya("Jumlah pembayaran tidak valid."))
            }

            val sisaBaru = purchase.hutangSisa - jumlahBayar
            purchaseDao.updateHutangSisa(purchaseId, sisaBaru, System.currentTimeMillis())
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(AppError.Lainnya("Gagal lunasi hutang: ${e.message}"))
        }
    }
}
