package com.nada.kasir.core.data.repository

import com.nada.kasir.core.data.local.AppDatabase
import com.nada.kasir.core.data.local.dao.ProductDao
import com.nada.kasir.core.data.local.entity.ProductEntity
import com.nada.kasir.core.excel.ProdukRowValidationResult
import com.nada.kasir.core.util.AppError
import com.nada.kasir.core.util.Result
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val productDao: ProductDao,
    private val appDatabase: AppDatabase
) {
    fun observeActive(): Flow<List<ProductEntity>> = productDao.observeActiveProducts()

    fun search(query: String): Flow<List<ProductEntity>> = productDao.search(query)

    fun observeStokMenipis(): Flow<List<ProductEntity>> = productDao.observeStokMenipis()

    fun observeStokHabis(): Flow<List<ProductEntity>> = productDao.observeStokHabis()

    suspend fun cariByBarcode(barcode: String): ProductEntity? = productDao.findByBarcode(barcode)

    suspend fun simpan(product: ProductEntity): Result<Long> {
        // Barcode tidak boleh duplikat (poin 5), baik saat tambah baru maupun edit produk lama
        val barcode = product.barcode
        if (!barcode.isNullOrBlank()) {
            val duplikat = productDao.countByBarcodeExcludingId(barcode, product.id)
            if (duplikat > 0) {
                return Result.Failure(AppError.BarcodeDuplikat)
            }
        }
        return try {
            val id = if (product.id == 0L) {
                productDao.insert(product)
            } else {
                productDao.update(product)
                product.id
            }
            Result.Success(id)
        } catch (e: Exception) {
            Result.Failure(AppError.TransaksiGagalDisimpan)
        }
    }

    suspend fun hapus(id: Long) {
        productDao.softDelete(id, System.currentTimeMillis()) // soft delete, poin 26
    }

    /**
     * Import massal dari Excel (poin 6 & 15). Baris dengan barcode yang sudah
     * ada di database DILEWATI (bukan menimpa), supaya import ulang tidak
     * merusak data yang sudah ada. Semua baris valid disimpan dalam satu
     * DB transaction.
     */
    suspend fun importBanyak(baris: List<ProdukRowValidationResult.Valid>): Pair<Int, List<String>> {
        var jumlahBerhasil = 0
        val dilewati = mutableListOf<String>()

        appDatabase.withTransaction {
            baris.forEach { b ->
                val kodeSudahAda = productDao.countByKodeProduk(b.kodeProduk) > 0
                val barcodeSudahAda = !b.barcode.isNullOrBlank() && productDao.countByBarcodeAll(b.barcode) > 0

                when {
                    kodeSudahAda -> {
                        dilewati.add("${b.kodeProduk} (kode produk sudah terdaftar)")
                    }
                    barcodeSudahAda -> {
                        dilewati.add("${b.kodeProduk} (barcode sudah terdaftar)")
                    }
                    else -> {
                        productDao.insert(
                            ProductEntity(
                                kodeProduk = b.kodeProduk, barcode = b.barcode, nama = b.nama,
                                categoryId = null, hargaBeli = b.hargaBeli, hargaJual = b.hargaJual,
                                stok = b.stok, stokMinimum = 5
                            )
                        )
                        jumlahBerhasil++
                    }
                }
            }
        }
        return jumlahBerhasil to dilewati
    }
}
