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
    companion object {
        /** Password akun pertama yang dibuat otomatis. Pengguna WAJIB menggantinya di login pertama. */
        const val PASSWORD_ADMIN_DEFAULT = "admin123"

        /** true jika [passwordPlain] adalah password bawaan aplikasi (yang publik di README/APK). */
        fun passwordTergolongDefault(passwordPlain: String): Boolean = passwordPlain == PASSWORD_ADMIN_DEFAULT

        /** Aturan password baru yang berlaku di semua jalur (buat, ganti, reset). Null = valid. */
        internal fun pesanPasswordTidakValid(passwordBaru: String, awalan: String = "Password"): String? = when {
            passwordBaru.length < 6 -> "$awalan minimal 6 karakter."
            passwordTergolongDefault(passwordBaru) -> "$awalan tidak boleh sama dengan password bawaan aplikasi."
            else -> null
        }
    }

    fun observeAll(): Flow<List<UserEntity>> = userDao.observeAll()

    /** ADMIN: Mengelola pengguna (poin 18). Password selalu di-hash dengan PBKDF2. */
    suspend fun buatPengguna(nama: String, username: String, passwordPlain: String, role: UserRole): Result<Long> {
        val bersihUsername = username.trim().lowercase()
        if (userDao.findByUsernameAnyStatus(bersihUsername) != null) {
            return Result.Failure(AppError.Lainnya("Username sudah dipakai."))
        }
        pesanPasswordTidakValid(passwordPlain)?.let { return Result.Failure(AppError.Lainnya(it)) }
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
        pesanPasswordTidakValid(passwordBaru, awalan = "Password baru")
            ?.let { return Result.Failure(AppError.Lainnya(it)) }
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
        pesanPasswordTidakValid(passwordBaru, awalan = "Password baru")
            ?.let { return Result.Failure(AppError.Lainnya(it)) }
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

    /**
     * Dipanggil saat layar login dibuka (poin 23: mode demo / first-run).
     *
     * Akun bawaan HANYA dibuat kalau tabel pengguna benar-benar kosong (aplikasi baru). Sebelumnya
     * yang dicek adalah akun "admin" yang AKTIF, sehingga begitu akun itu dinonaktifkan, akun
     * "admin" baru dengan password bawaan yang publik otomatis muncul lagi (F-09).
     *
     * @return akun yang baru dibuat, atau null jika tidak ada yang dibuat.
     */
    suspend fun pastikanAdaAdminDefault(): UserEntity? {
        if (userDao.count() > 0) return null
        val id = userDao.insert(
            UserEntity(
                nama = "Administrator",
                username = "admin",
                passwordHash = PasswordHasher.hash(PASSWORD_ADMIN_DEFAULT),
                role = UserRole.ADMIN
            )
        )
        return userDao.findById(id)
    }
}
