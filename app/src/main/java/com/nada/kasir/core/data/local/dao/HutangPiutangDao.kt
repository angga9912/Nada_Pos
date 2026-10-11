package com.nada.kasir.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.nada.kasir.core.data.local.entity.HutangPiutangEntity
import com.nada.kasir.core.data.local.entity.HutangPiutangPembayaranEntity
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

    /** Catatan yang terhubung ke transaksi/pembelian lewat nomor referensinya (mis. nomor pembelian PUR-...). */
    @Query("SELECT * FROM hutang_piutang WHERE nomorReferensi = :nomor AND jenis = :jenis LIMIT 1")
    suspend fun findByReferensi(nomor: String, jenis: String): HutangPiutangEntity?

    // --- Riwayat pembayaran/cicilan (Phase 5 tahap 1) ---
    @Insert
    suspend fun insertPembayaran(pembayaran: HutangPiutangPembayaranEntity): Long

    @Query("SELECT * FROM hutang_piutang_pembayaran WHERE hutangPiutangId = :hutangPiutangId ORDER BY tanggal DESC, id DESC")
    fun observePembayaran(hutangPiutangId: Long): Flow<List<HutangPiutangPembayaranEntity>>

    @Query("SELECT COUNT(*) FROM hutang_piutang_pembayaran WHERE hutangPiutangId = :hutangPiutangId")
    suspend fun countPembayaran(hutangPiutangId: Long): Int

    // Backup & Restore (F-02)
    @Query("SELECT * FROM hutang_piutang_pembayaran")
    suspend fun getAllPembayaranForBackup(): List<HutangPiutangPembayaranEntity>
    @Insert
    suspend fun insertAllPembayaran(entities: List<HutangPiutangPembayaranEntity>)
    @Query("DELETE FROM hutang_piutang_pembayaran")
    suspend fun clearPembayaran()

    @Query("SELECT * FROM hutang_piutang")
    suspend fun getAllForBackup(): List<HutangPiutangEntity>
    @Insert
    suspend fun insertAll(entities: List<HutangPiutangEntity>)
    @Query("DELETE FROM hutang_piutang")
    suspend fun clearAll()
}
