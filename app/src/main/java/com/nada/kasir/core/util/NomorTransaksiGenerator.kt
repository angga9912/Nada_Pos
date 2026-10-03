package com.nada.kasir.core.util

import com.nada.kasir.core.data.local.dao.TransactionDao
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * Menghasilkan nomor transaksi format INV-YYYYMMDD-XXXX (poin 20).
 * Dijamin tidak duplikat karena dihitung dari jumlah transaksi hari ini + 1,
 * dan insert dilakukan di dalam DB transaction yang sama (lihat TransactionRepository).
 */
class NomorTransaksiGenerator @Inject constructor(
    private val transactionDao: TransactionDao
) {
    suspend fun generate(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale("id", "ID"))
        val today = dateFormat.format(Date())
        val prefix = "INV-$today-"
        val countToday = transactionDao.countTodayTransactions(prefix)
        val nextNumber = (countToday + 1).toString().padStart(4, '0')
        return "$prefix$nextNumber"
    }
}
