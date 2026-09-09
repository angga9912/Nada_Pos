package com.nada.kasir.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transaction_items")
data class TransactionItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val productId: Long,
    val namaProdukSnapshot: String, // disimpan agar riwayat tetap benar walau nama produk diedit
    val qty: Int,
    val harga: Double,
    val diskon: Double = 0.0,
    val subtotal: Double
)
