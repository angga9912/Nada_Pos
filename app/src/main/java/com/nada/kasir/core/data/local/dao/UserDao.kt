package com.nada.kasir.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.nada.kasir.core.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE username = :username AND aktif = 1 LIMIT 1")
    suspend fun findByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users")
    fun observeAll(): Flow<List<UserEntity>>

    @Insert
    suspend fun insert(user: UserEntity): Long

    @Insert
    suspend fun insertAll(users: List<UserEntity>): List<Long>

    @Query("SELECT * FROM users")
    suspend fun getAllForBackup(): List<UserEntity>

    @Query("DELETE FROM users")
    suspend fun clearAll()

}
