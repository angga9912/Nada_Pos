package com.nada.kasir.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TransactionStatus { COMPLETED, CANCELLED }

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noTransaksi: String, // format INV-YYYYMMDD-XXXX, unik (poin 20)
    val nomorAntrian: Int = 0, // reset otomatis tiap hari, dipakai untuk memanggil pembeli
    val namaPembeli: String? = null, // opsional, ditampilkan di struk jika diisi
    val tanggalWaktu: Long, // epoch millis
    val userId: Long,
    val subtotal: Double,
    val diskon: Double,
    val total: Double,
    val status: TransactionStatus = TransactionStatus.COMPLETED
)
