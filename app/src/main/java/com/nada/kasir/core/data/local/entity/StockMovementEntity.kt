package com.nada.kasir.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TipeMutasiStok { MASUK, KELUAR, PENYESUAIAN, PENJUALAN, PEMBATALAN }

@Entity(tableName = "stock_movements")
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val tipe: TipeMutasiStok,
    val qty: Int, // selalu positif; arah ditentukan oleh 'tipe'
    val referensiTransaksiId: Long? = null,
    val supplier: String? = null,
    val keterangan: String? = null,
    val tanggalWaktu: Long
)
