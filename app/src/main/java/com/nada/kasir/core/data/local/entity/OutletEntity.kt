package com.nada.kasir.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outlets")
data class OutletEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nama: String,
    val kode: String,
    val alamat: String = "",
    val telepon: String = "",
    val aktif: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
