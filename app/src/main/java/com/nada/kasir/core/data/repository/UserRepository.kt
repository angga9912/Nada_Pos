package com.nada.kasir.core.data.repository

import com.nada.kasir.core.data.local.dao.UserDao
import com.nada.kasir.core.data.local.entity.UserEntity
import com.nada.kasir.core.data.local.entity.UserRole
import com.nada.kasir.core.util.AppError
import com.nada.kasir.core.util.PasswordHasher
import com.nada.kasir.core.util.Result
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao
) {
    fun observeAll(): Flow<List<UserEntity>> = userDao.observeAll()

    /** ADMIN: Mengelola pengguna (poin 18). Password selalu di-hash, tidak pernah plain text (poin 26). */
    suspend fun buatPengguna(nama: String, username: String, passwordPlain: String, role: UserRole): Result<Long> {
        if (userDao.findByUsername(username) != null) {
            return Result.Failure(AppError.Lainnya("Username sudah dipakai."))
        }
        if (passwordPlain.length < 4) {
            return Result.Failure(AppError.Lainnya("Password minimal 4 karakter."))
        }
        val id = userDao.insert(
            UserEntity(nama = nama, username = username, passwordHash = PasswordHasher.hash(passwordPlain), role = role)
        )
        return Result.Success(id)
    }

    /** Login kasir/admin. Pesan error sengaja umum (tidak bilang "username salah" vs "password salah") untuk keamanan dasar. */
    suspend fun login(username: String, passwordPlain: String): Result<UserEntity> {
        val user = userDao.findByUsername(username)
            ?: return Result.Failure(AppError.Lainnya("Username atau password salah."))
        return if (PasswordHasher.verify(passwordPlain, user.passwordHash)) {
            Result.Success(user)
        } else {
            Result.Failure(AppError.Lainnya("Username atau password salah."))
        }
    }

    /** Dipanggil sekali saat aplikasi pertama kali dijalankan (poin 23: mode demo / first-run). */
    suspend fun pastikanAdaAdminDefault(): UserEntity? {
        val sudahAdaUser = userDao.findByUsername("admin") != null
        if (!sudahAdaUser) {
            userDao.insert(
                UserEntity(
                    nama = "Administrator", username = "admin",
                    passwordHash = PasswordHasher.hash("admin123"), role = UserRole.ADMIN
                )
            )
            return userDao.findByUsername("admin")
        }
        return null
    }
}
