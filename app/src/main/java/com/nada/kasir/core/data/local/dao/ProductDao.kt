package com.nada.kasir.core.data.local.dao

import androidx.room.*
import com.nada.kasir.core.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE isActive = 1 AND deletedAt IS NULL ORDER BY nama ASC")
    fun observeActiveProducts(): Flow<List<ProductEntity>>

    @Query("""
        SELECT * FROM products 
        WHERE isActive = 1 AND deletedAt IS NULL 
        AND (nama LIKE '%' || :query || '%' OR kodeProduk LIKE '%' || :query || '%')
    """)
    fun search(query: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE barcode = :barcode AND deletedAt IS NULL LIMIT 1")
    suspend fun findByBarcode(barcode: String): ProductEntity?

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun findById(id: Long): ProductEntity?

    @Query("SELECT COUNT(*) FROM products WHERE barcode = :barcode AND deletedAt IS NULL")
    suspend fun countByBarcode(barcode: String): Int

    @Query("SELECT * FROM products WHERE stok <= stokMinimum AND stok > 0 AND isActive = 1 AND deletedAt IS NULL")
    fun observeStokMenipis(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE stok = 0 AND isActive = 1 AND deletedAt IS NULL")
    fun observeStokHabis(): Flow<List<ProductEntity>>

    @Insert
    suspend fun insert(product: ProductEntity): Long

    @Update
    suspend fun update(product: ProductEntity)

    // Soft delete (poin 26) - jangan pernah hapus permanen
    @Query("UPDATE products SET isActive = 0, deletedAt = :timestamp WHERE id = :id")
    suspend fun softDelete(id: Long, timestamp: Long)

    @Query("UPDATE products SET stok = stok - :qty WHERE id = :productId")
    suspend fun decreaseStock(productId: Long, qty: Int)

    @Query("UPDATE products SET stok = stok + :qty WHERE id = :productId")
    suspend fun increaseStock(productId: Long, qty: Int)

    @Query("SELECT stok FROM products WHERE id = :productId")
    suspend fun getStok(productId: Long): Int

    // === Untuk Backup/Restore & Import Excel (Phase 3) ===
    @Insert
    suspend fun insertAll(products: List<ProductEntity>): List<Long>

    @Query("SELECT * FROM products") // termasuk yang non-aktif/soft-deleted, untuk backup lengkap
    suspend fun getAllForBackup(): List<ProductEntity>

    @Query("DELETE FROM products")
    suspend fun clearAll()


    @Query("SELECT id, hargaBeli FROM products")
    suspend fun getSemuaHargaBeli(): List<HargaBeliProduk>

}

data class HargaBeliProduk(val id: Long, val hargaBeli: Double)
