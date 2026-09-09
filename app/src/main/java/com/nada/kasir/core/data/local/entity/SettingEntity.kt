package com.nada.kasir.core.data.local.entity

import androidx.room.Entity

/**
 * Key-value store untuk feature flags per pelanggan (poin 22), contoh:
 * key="feature_barcode" value="true"
 * key="feature_hutang"  value="false"
 * Dibaca oleh FeatureConfig.kt agar fitur bisa di-toggle tanpa mengubah kode.
 */
@Entity(tableName = "settings", primaryKeys = ["key"])
data class SettingEntity(
    val key: String,
    val value: String
)
