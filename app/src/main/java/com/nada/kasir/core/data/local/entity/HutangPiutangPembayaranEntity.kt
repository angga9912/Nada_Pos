package com.nada.kasir.core.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Riwayat pembayaran/cicilan sebuah catatan hutang atau piutang (Phase 5, tahap 1).
 * Satu catatan [HutangPiutangEntity] bisa dibayar bertahap; tiap pembayaran satu baris di sini.
 */
@Entity(
    tableName = "hutang_piutang_pembayaran",
    indices = [Index(value = ["hutangPiutangId"])]
)
data class HutangPiutangPembayaranEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hutangPiutangId: Long,
    val jumlah: Double,
    val tanggal: Long,
    val catatan: String? = null,
    val userId: Long? = null,
    val createdAt: Long
)
