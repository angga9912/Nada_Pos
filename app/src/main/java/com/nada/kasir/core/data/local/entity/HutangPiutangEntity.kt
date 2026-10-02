package com.nada.kasir.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hutang_piutang")
data class HutangPiutangEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jenis: String, // PIUTANG atau HUTANG
    val pelaku: String,
    val nomorReferensi: String? = null,
    val jumlah: Double,
    val sisa: Double,
    val status: String = "AKTIF",
    val tanggalTransaksi: Long = System.currentTimeMillis(),
    val tanggalJatuhTempo: Long? = null,
    val keterangan: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
