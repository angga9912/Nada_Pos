package com.nada.kasir.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "suppliers")
data class SupplierEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nama: String,
    val kontak: String = "",
    val telepon: String = "",
    val alamat: String = "",
    val npwp: String = "",
    val catatan: String = "",
    val aktif: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
