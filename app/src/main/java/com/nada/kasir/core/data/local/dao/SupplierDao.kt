package com.nada.kasir.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.nada.kasir.core.data.local.entity.SupplierEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplierDao {
    @Query("SELECT * FROM suppliers ORDER BY nama ASC")
    fun observeAll(): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE aktif = 1 ORDER BY nama ASC")
    fun observeActive(): Flow<List<SupplierEntity>>

    @Insert
    suspend fun insert(entity: SupplierEntity): Long

    @Update
    suspend fun update(entity: SupplierEntity)

    @Query("UPDATE suppliers SET aktif = :aktif, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setAktif(id: Long, aktif: Boolean, updatedAt: Long)

    @Query("SELECT * FROM suppliers WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): SupplierEntity?
}
