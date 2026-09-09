package com.nada.kasir.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kodeProduk: String,
    val barcode: String?, // unik divalidasi di Repository, nullable karena tidak semua produk punya barcode
    val nama: String,
    val categoryId: Long?,
    val satuan: String = "pcs",
    val hargaBeli: Double,
    val hargaJual: Double,
    val stok: Int,
    val stokMinimum: Int = 5,
    val fotoPath: String? = null,
    val isActive: Boolean = true,
    val deletedAt: Long? = null // soft delete (poin 26)
)
