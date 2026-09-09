package com.nada.kasir.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "printers")
data class PrinterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nama: String,
    val macAddress: String,
    val ukuranKertas: String, // "58mm" | "80mm"
    val isDefault: Boolean = false
)
