package com.nada.kasir.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.nada.kasir.core.data.local.entity.PurchaseEntity
import com.nada.kasir.core.data.local.entity.PurchaseItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {
    @Insert
    suspend fun insertPurchase(entity: PurchaseEntity): Long

    @Insert
    suspend fun insertItems(items: List<PurchaseItemEntity>)

    @Query("SELECT * FROM purchases ORDER BY tanggalPembelian DESC")
    fun observeAll(): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases WHERE supplierId = :supplierId ORDER BY tanggalPembelian DESC")
    fun observeBySupplier(supplierId: Long): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases WHERE status = :status ORDER BY tanggalPembelian DESC")
    fun observeByStatus(status: String): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): PurchaseEntity?

    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId")
    suspend fun getItems(purchaseId: Long): List<PurchaseItemEntity>

    @Update
    suspend fun updatePurchase(entity: PurchaseEntity)

    @Query("UPDATE purchases SET hutangSisa = :sisa, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateHutangSisa(id: Long, sisa: Double, updatedAt: Long)
}
