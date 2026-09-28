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

    /** ADMIN: Mengelola pengguna (poin 18). Password selalu di-hash dengan PBKDF2. */
    suspend fun buatPengguna(nama: String, username: String, passwordPlain: String, role: UserRole): Result<Long> {
        val bersihUsername = username.trim().lowercase()
        if (userDao.findByUsernameAnyStatus(bersihUsername) != null) {
            return Result.Failure(AppError.Lainnya("Username sudah dipakai."))
        }
        if (passwordPlain.length < 6) {
            return Result.Failure(AppError.Lainnya("Password minimal 6 karakter."))
        }
        val id = userDao.insert(
            UserEntity(
                nama = nama.trim(),
                username = bersihUsername,
                passwordHash = PasswordHasher.hash(passwordPlain),
                role = role
            )
        )
        return Result.Success(id)
    }

    /**
     * Login kasir/admin.
     * Menggunakan pesan error generik untuk mencegah enumeration.
     * Secara otomatis meng-upgrade hash lama ke PBKDF2 saat login berhasil.
     */
    suspend fun login(username: String, passwordPlain: String): Result<UserEntity> {
        val bersihUsername = username.trim().lowercase()
        val user = userDao.findByUsernameAnyStatus(bersihUsername)
            ?: return Result.Failure(AppError.Lainnya("Username atau password salah."))

        if (!user.aktif) {
            return Result.Failure(AppError.Lainnya("Akun ini dinonaktifkan. Hubungi Administrator."))
        }

        return if (PasswordHasher.verify(passwordPlain, user.passwordHash)) {
            // Auto-upgrade hash lama ke PBKDF2 tanpa mengganggu pengguna
            if (PasswordHasher.butuhUpgrade(user.passwordHash)) {
                val upgradedHash = PasswordHasher.hash(passwordPlain)
                userDao.updatePassword(user.id, upgradedHash)
            }
            Result.Success(user)
        } else {
            Result.Failure(AppError.Lainnya("Username atau password salah."))
        }
    }

    /** Ganti password mandiri (pengguna harus tahu password lama). */
    suspend fun gantiPassword(userId: Long, passwordLama: String, passwordBaru: String): Result<Unit> {
        if (passwordBaru.length < 6) {
            return Result.Failure(AppError.Lainnya("Password baru minimal 6 karakter."))
        }
        val user = userDao.findById(userId)
            ?: return Result.Failure(AppError.Lainnya("Pengguna tidak ditemukan."))

        if (!PasswordHasher.verify(passwordLama, user.passwordHash)) {
            return Result.Failure(AppError.Lainnya("Password lama salah."))
        }

        userDao.updatePassword(userId, PasswordHasher.hash(passwordBaru))
        return Result.Success(Unit)
    }

    /** Reset password oleh Admin (tidak butuh password lama). */
    suspend fun resetPasswordOlehAdmin(targetUserId: Long, passwordBaru: String): Result<Unit> {
        if (passwordBaru.length < 6) {
            return Result.Failure(AppError.Lainnya("Password baru minimal 6 karakter."))
        }
        val target = userDao.findById(targetUserId)
            ?: return Result.Failure(AppError.Lainnya("Pengguna tidak ditemukan."))

        userDao.updatePassword(target.id, PasswordHasher.hash(passwordBaru))
        return Result.Success(Unit)
    }

    /** Nonaktifkan pengguna (Admin tidak boleh menonaktifkan akunnya sendiri yang sedang aktif). */
    suspend fun hapusPengguna(targetUserId: Long, currentUserId: Long): Result<Unit> {
        if (targetUserId == currentUserId) {
            return Result.Failure(AppError.Lainnya("Tidak dapat menghapus akun Anda sendiri yang sedang aktif."))
        }
        userDao.updateStatusAktif(targetUserId, false)
        return Result.Success(Unit)
    }

    /** Ubah status aktif/nonaktif akun pengguna. */
    suspend fun setStatusAktif(userId: Long, aktif: Boolean, currentUserId: Long): Result<Unit> {
        if (userId == currentUserId && !aktif) {
            return Result.Failure(AppError.Lainnya("Tidak dapat menonaktifkan akun Anda sendiri yang sedang aktif."))
        }
        userDao.updateStatusAktif(userId, aktif)
        return Result.Success(Unit)
    }

    /** Dipanggil sekali saat aplikasi pertama kali dijalankan (poin 23: mode demo / first-run). */
    suspend fun pastikanAdaAdminDefault(): UserEntity? {
        val sudahAdaUser = userDao.findByUsername("admin") != null
        if (!sudahAdaUser) {
            userDao.insert(
                UserEntity(
                    nama = "Administrator",
                    username = "admin",
                    passwordHash = PasswordHasher.hash("admin123"),
                    role = UserRole.ADMIN
                )
            )
            return userDao.findByUsername("admin")
        }
        return null
    }
}
