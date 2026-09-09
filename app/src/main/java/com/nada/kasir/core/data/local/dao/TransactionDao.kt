package com.nada.kasir.core.data.local.dao

import androidx.room.*
import com.nada.kasir.core.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert
    suspend fun insertItems(items: List<TransactionItemEntity>)

    @Insert
    suspend fun insertPayment(payment: PaymentEntity)

    @Query("SELECT COUNT(*) FROM transactions WHERE noTransaksi = :noTransaksi")
    suspend fun countByNoTransaksi(noTransaksi: String): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE noTransaksi LIKE :prefix || '%'")
    suspend fun countTodayTransactions(prefix: String): Int

    @Query("""
        SELECT * FROM transactions 
        WHERE (:query = '' OR noTransaksi LIKE '%' || :query || '%')
        AND tanggalWaktu BETWEEN :startMillis AND :endMillis
        ORDER BY tanggalWaktu DESC
    """)
    fun observeRiwayat(query: String, startMillis: Long, endMillis: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun findById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transaction_items WHERE transactionId = :transactionId")
    suspend fun getItems(transactionId: Long): List<TransactionItemEntity>

    @Query("SELECT * FROM payments WHERE transactionId = :transactionId LIMIT 1")
    suspend fun getPayment(transactionId: Long): PaymentEntity?

    @Query("UPDATE transactions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: TransactionStatus)

    @Query("SELECT COALESCE(SUM(total),0) FROM transactions WHERE status = 'COMPLETED' AND tanggalWaktu BETWEEN :startMillis AND :endMillis")
    fun observeTotalPenjualan(startMillis: Long, endMillis: Long): Flow<Double>

    @Query("SELECT COUNT(*) FROM transactions WHERE status = 'COMPLETED' AND tanggalWaktu BETWEEN :startMillis AND :endMillis")
    fun observeJumlahTransaksi(startMillis: Long, endMillis: Long): Flow<Int>

    // === Untuk Backup/Restore & Export Excel (Phase 3) ===
    @Query("SELECT * FROM transactions")
    suspend fun getAllTransactionsForBackup(): List<TransactionEntity>

    @Query("SELECT * FROM transaction_items")
    suspend fun getAllItemsForBackup(): List<TransactionItemEntity>

    @Query("SELECT * FROM payments")
    suspend fun getAllPaymentsForBackup(): List<PaymentEntity>

    @Insert
    suspend fun insertAllTransactions(transactions: List<TransactionEntity>): List<Long>

    @Insert
    suspend fun insertAllItems(items: List<TransactionItemEntity>)

    @Insert
    suspend fun insertAllPayments(payments: List<PaymentEntity>)

    @Query("DELETE FROM transactions")
    suspend fun clearTransactions()

    @Query("DELETE FROM transaction_items")
    suspend fun clearItems()

    @Query("DELETE FROM payments")
    suspend fun clearPayments()


    // === LAPORAN (Phase 4, poin 14) ===
    @Query("""
        SELECT ti.* FROM transaction_items ti
        INNER JOIN transactions t ON t.id = ti.transactionId
        WHERE t.status = 'COMPLETED' AND t.tanggalWaktu BETWEEN :start AND :end
    """)
    suspend fun getItemsSelesaiDalamRentang(start: Long, end: Long): List<TransactionItemEntity>

    @Query("""
        SELECT SUM(diskon) FROM transactions
        WHERE status = 'COMPLETED' AND tanggalWaktu BETWEEN :start AND :end
    """)
    suspend fun getTotalDiskonDalamRentang(start: Long, end: Long): Double?

    @Query("""
        SELECT p.metode as metode, SUM(t.total) as total, COUNT(*) as jumlahTransaksi
        FROM payments p
        INNER JOIN transactions t ON t.id = p.transactionId
        WHERE t.status = 'COMPLETED' AND t.tanggalWaktu BETWEEN :start AND :end
        GROUP BY p.metode
    """)
    suspend fun getRingkasanMetodePembayaran(start: Long, end: Long): List<RingkasanMetodePembayaran>

    @Query("""
        SELECT COALESCE(SUM(total), 0) FROM transactions
        WHERE status = 'COMPLETED' AND tanggalWaktu BETWEEN :start AND :end
    """)
    suspend fun getTotalPenjualanDalamRentang(start: Long, end: Long): Double

    @Query("""
        SELECT COUNT(*) FROM transactions
        WHERE status = 'COMPLETED' AND tanggalWaktu BETWEEN :start AND :end
    """)
    suspend fun getJumlahTransaksiDalamRentang(start: Long, end: Long): Int

    @Query("""
        SELECT ti.productId as productId, ti.namaProdukSnapshot as nama, SUM(ti.qty) as totalQty
        FROM transaction_items ti
        INNER JOIN transactions t ON t.id = ti.transactionId
        WHERE t.status = 'COMPLETED' AND t.tanggalWaktu BETWEEN :start AND :end
        GROUP BY ti.productId
        ORDER BY totalQty DESC
        LIMIT :limit
    """)
    suspend fun getProdukTerlaris(start: Long, end: Long, limit: Int): List<ProdukTerlaris>


    @Query("SELECT * FROM transactions WHERE status = 'COMPLETED' ORDER BY tanggalWaktu DESC LIMIT :limit")
    suspend fun getTransaksiTerbaru(limit: Int): List<TransactionEntity>


    // === Nomor Antrian (reset otomatis tiap hari) ===
    @Query("SELECT COUNT(*) FROM transactions WHERE tanggalWaktu BETWEEN :start AND :end")
    suspend fun countSemuaTransaksiHariIni(start: Long, end: Long): Int

}

data class RingkasanMetodePembayaran(val metode: String, val total: Double, val jumlahTransaksi: Int)
data class ProdukTerlaris(val productId: Long, val nama: String, val totalQty: Int)
