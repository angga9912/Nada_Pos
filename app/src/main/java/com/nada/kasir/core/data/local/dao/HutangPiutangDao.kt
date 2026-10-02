package com.nada.kasir.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.nada.kasir.core.data.local.entity.HutangPiutangEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HutangPiutangDao {
    @Query("SELECT * FROM hutang_piutang ORDER BY tanggalTransaksi DESC")
    fun observeAll(): Flow<List<HutangPiutangEntity>>

    @Query("SELECT * FROM hutang_piutang WHERE jenis = :jenis ORDER BY tanggalTransaksi DESC")
    fun observeByJenis(jenis: String): Flow<List<HutangPiutangEntity>>

    @Insert
    suspend fun insert(entity: HutangPiutangEntity): Long

    @Update
    suspend fun update(entity: HutangPiutangEntity)

    @Query("UPDATE hutang_piutang SET sisa = :sisa, status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatusAndSisa(id: Long, sisa: Double, status: String, updatedAt: Long)

    @Query("SELECT * FROM hutang_piutang WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): HutangPiutangEntity?
}
