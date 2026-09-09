package com.nada.kasir.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.nada.kasir.core.data.local.entity.StoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreDao {
    @Query("SELECT * FROM stores LIMIT 1")
    fun observeStore(): Flow<StoreEntity?>

    @Query("SELECT * FROM stores LIMIT 1")
    suspend fun getStoreOnce(): StoreEntity?

    @Insert
    suspend fun insert(store: StoreEntity): Long

    @Update
    suspend fun update(store: StoreEntity)

    @Query("SELECT * FROM stores")
    suspend fun getAllForBackup(): List<StoreEntity>

    @Insert
    suspend fun insertAll(stores: List<StoreEntity>): List<Long>

    @Query("DELETE FROM stores")
    suspend fun clearAll()

}
