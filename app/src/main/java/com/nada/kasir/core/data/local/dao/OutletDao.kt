package com.nada.kasir.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.nada.kasir.core.data.local.entity.OutletEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OutletDao {
    @Query("SELECT * FROM outlets ORDER BY nama ASC")
    fun observeAll(): Flow<List<OutletEntity>>

    @Query("SELECT * FROM outlets WHERE aktif = 1 ORDER BY nama ASC")
    fun observeActive(): Flow<List<OutletEntity>>

    @Insert
    suspend fun insert(entity: OutletEntity): Long

    @Update
    suspend fun update(entity: OutletEntity)

    @Query("UPDATE outlets SET aktif = :aktif, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setAktif(id: Long, aktif: Boolean, updatedAt: Long)

    @Query("SELECT * FROM outlets WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): OutletEntity?
}
