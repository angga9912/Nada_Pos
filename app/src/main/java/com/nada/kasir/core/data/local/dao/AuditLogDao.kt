package com.nada.kasir.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.nada.kasir.core.data.local.entity.AuditLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {
    @Insert
    suspend fun insert(log: AuditLogEntity): Long

    @Query("SELECT * FROM audit_logs ORDER BY tanggalWaktu DESC")
    fun observeAll(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE tanggalWaktu BETWEEN :start AND :end ORDER BY tanggalWaktu DESC")
    suspend fun getLogsInRange(start: Long, end: Long): List<AuditLogEntity>

    @Query("SELECT * FROM audit_logs")
    suspend fun getAllForBackup(): List<AuditLogEntity>

    @Insert
    suspend fun insertAll(logs: List<AuditLogEntity>): List<Long>

    @Query("DELETE FROM audit_logs")
    suspend fun clearAll()
}
