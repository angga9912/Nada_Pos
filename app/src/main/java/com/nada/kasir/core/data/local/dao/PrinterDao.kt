package com.nada.kasir.core.data.local.dao

import androidx.room.*
import com.nada.kasir.core.data.local.entity.PrinterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PrinterDao {
    @Query("SELECT * FROM printers")
    fun observeAll(): Flow<List<PrinterEntity>>

    @Query("SELECT * FROM printers WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): PrinterEntity?

    @Insert
    suspend fun insert(printer: PrinterEntity): Long

    @Query("UPDATE printers SET isDefault = 0")
    suspend fun clearDefault()

    @Query("UPDATE printers SET isDefault = 1 WHERE id = :id")
    suspend fun setDefault(id: Long)

    @Delete
    suspend fun delete(printer: PrinterEntity)

    @Query("SELECT * FROM printers")
    suspend fun getAllForBackup(): List<PrinterEntity>

    @Insert
    suspend fun insertAll(printers: List<PrinterEntity>): List<Long>

    @Query("DELETE FROM printers")
    suspend fun clearAll()

}
