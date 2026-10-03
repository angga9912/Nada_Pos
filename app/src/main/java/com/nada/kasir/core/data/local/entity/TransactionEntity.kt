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
    val outletId: Long? = null, // Phase 5: lokasi/cabang tempat transaksi terjadi (optional untuk single store)
    val tanggalWaktu: Long, // epoch millis
    val userId: Long,
    val subtotal: Double,
    val diskon: Double,
    val total: Double,
    val hutangSisa: Double = 0.0, // Phase 5: jika pembeli berhutang (sistem hutang/piutang)
    val status: TransactionStatus = TransactionStatus.COMPLETED
)
