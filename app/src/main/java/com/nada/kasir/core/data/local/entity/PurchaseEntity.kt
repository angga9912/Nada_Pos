package com.nada.kasir.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class PurchaseStatus { PENDING, RECEIVED, CANCELLED }

@Entity(tableName = "purchases")
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noPembelian: String, // format: PUR-YYYYMMDD-XXXX
    val supplierId: Long,
    val outletId: Long? = null, // Phase 5: outlet penerima barang
    val tanggalPembelian: Long,
    val tanggalTerima: Long? = null, // null jika belum diterima
    val subtotal: Double,
    val diskonSupplier: Double = 0.0, // diskon dari supplier
    val totalBayar: Double,
    val hutangSisa: Double, // Phase 5: sisa hutang ke supplier
    val status: PurchaseStatus = PurchaseStatus.PENDING,
    val keterangan: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
