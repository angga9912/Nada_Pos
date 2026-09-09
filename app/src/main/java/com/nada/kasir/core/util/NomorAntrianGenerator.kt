package com.nada.kasir.core.util

import com.nada.kasir.core.data.local.dao.TransactionDao
import java.util.Calendar
import javax.inject.Inject

/**
 * Nomor antrian untuk memanggil pembeli - TERPISAH dari nomor transaksi/invoice.
 * Format angka sederhana (1, 2, 3, ...) dan otomatis reset ke 1 setiap
 * pergantian hari, dihitung dari SEMUA transaksi hari ini (termasuk yang nanti
 * dibatalkan), supaya nomor yang sudah dipanggil tidak pernah dipakai ulang
 * pada hari yang sama.
 */
class NomorAntrianGenerator @Inject constructor(
    private val transactionDao: TransactionDao
) {
    suspend fun generate(): Int {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val startHariIni = cal.timeInMillis
        val endHariIni = startHariIni + 24 * 60 * 60 * 1000L
        val jumlahHariIni = transactionDao.countSemuaTransaksiHariIni(startHariIni, endHariIni)
        return jumlahHariIni + 1
    }
}
