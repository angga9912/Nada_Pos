package com.nada.kasir.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MetodePembayaran { TUNAI, QRIS, TRANSFER, DEBIT, KREDIT, LAINNYA }

@Entity(tableName = "payments")
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val metode: MetodePembayaran,
    val jumlahDiterima: Double,
    val kembalian: Double
)
