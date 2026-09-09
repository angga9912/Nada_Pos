package com.nada.kasir.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.nada.kasir.core.data.local.entity.StockMovementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockMovementDao {
    @Insert
    suspend fun insert(movement: StockMovementEntity): Long

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY tanggalWaktu DESC")
    fun observeByProduct(productId: Long): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements ORDER BY tanggalWaktu DESC")
    fun observeAll(): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements")
    suspend fun getAllForBackup(): List<StockMovementEntity>

    @Insert
    suspend fun insertAll(movements: List<StockMovementEntity>): List<Long>

    @Query("DELETE FROM stock_movements")
    suspend fun clearAll()

}
