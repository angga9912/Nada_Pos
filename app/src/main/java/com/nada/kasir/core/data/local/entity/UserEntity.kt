package com.nada.kasir.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class UserRole { ADMIN, KASIR }

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nama: String,
    val username: String,
    val passwordHash: String, // hashing wajib (poin 26), lihat PasswordHasher.kt
    val role: UserRole,
    val aktif: Boolean = true
)
