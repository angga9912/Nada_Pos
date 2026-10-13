package com.nada.kasir.core.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "purchase_items",
    indices = [
        Index(value = ["purchaseId"])
    ]
)
data class PurchaseItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseId: Long,
    val productId: Long,
    val namaProdukSnapshot: String, // snapshot saat pembelian
    val qty: Int,
    val hargaBeli: Double,
    val diskon: Double = 0.0,
    val subtotal: Double
)
