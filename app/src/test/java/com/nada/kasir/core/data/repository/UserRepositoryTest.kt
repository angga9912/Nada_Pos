package com.nada.kasir.core.data.repository

import com.nada.kasir.core.data.local.dao.UserDao
import com.nada.kasir.core.data.local.entity.UserEntity
import com.nada.kasir.core.data.local.entity.UserRole
import com.nada.kasir.core.util.PasswordHasher
import com.nada.kasir.core.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DAO palsu di memori - cukup untuk menguji aturan di UserRepository tanpa Room/Android. */
private class FakeUserDao : UserDao {
    val data = mutableListOf<UserEntity>()
    private var nextId = 1L

    override suspend fun findByUsername(username: String) =
        data.firstOrNull { it.username == username && it.aktif }
    override suspend fun findByUsernameAnyStatus(username: String) =
        data.firstOrNull { it.username == username }
    override suspend fun findById(id: Long) = data.firstOrNull { it.id == id }
    override suspend fun count() = data.size
    override fun observeAll(): Flow<List<UserEntity>> = flowOf(data.toList())
    override suspend fun insert(user: UserEntity): Long {
        val id = if (user.id != 0L) user.id else nextId++
        data.add(user.copy(id = id))
        return id
    }
    override suspend fun insertAll(users: List<UserEntity>) = users.map { insert(it) }
    override suspend fun updatePassword(userId: Long, passwordHash: String) {
        val i = data.indexOfFirst { it.id == userId }
        if (i >= 0) data[i] = data[i].copy(passwordHash = passwordHash)
    }
    override suspend fun deleteById(userId: Long) { data.removeAll { it.id == userId } }
    override suspend fun updateStatusAktif(userId: Long, aktif: Boolean) {
        val i = data.indexOfFirst { it.id == userId }
        if (i >= 0) data[i] = data[i].copy(aktif = aktif)
    }
    override suspend fun getAllForBackup() = data.toList()
    override suspend fun clearAll() { data.clear() }
}

class UserRepositoryTest {

    private val dao = FakeUserDao()
    private val repo = UserRepository(dao)
    private val bawaan = UserRepository.PASSWORD_ADMIN_DEFAULT

    // ---- Seed akun pertama (F-09) ----

    @Test
    fun `aplikasi baru - akun admin bawaan dibuat dengan password bawaan`() = runBlocking {
        val admin = repo.pastikanAdaAdminDefault()
        assertNotNull(admin)
        assertEquals("admin", admin!!.username)
        assertEquals(UserRole.ADMIN, admin.role)
        assertTrue(PasswordHasher.verify(bawaan, admin.passwordHash))
        assertEquals(1, dao.data.size)
    }

    @Test
    fun `akun bawaan tidak dibuat dua kali`() = runBlocking {
        repo.pastikanAdaAdminDefault()
        assertNull(repo.pastikanAdaAdminDefault())
        assertEquals(1, dao.data.size)
    }

    @Test
    fun `admin yang dinonaktifkan TIDAK memicu pembuatan admin baru dengan password bawaan`() = runBlocking {
        val admin = repo.pastikanAdaAdminDefault()!!
        dao.updateStatusAktif(admin.id, false)

        assertNull(repo.pastikanAdaAdminDefault())
        assertEquals(1, dao.data.size)
        assertFalse(dao.data.single().aktif)
    }

    @Test
    fun `sudah ada pengguna dengan username lain - akun bawaan tidak dibuat`() = runBlocking {
        repo.buatPengguna("Budi", "budi", "rahasia123", UserRole.ADMIN)
        assertNull(repo.pastikanAdaAdminDefault())
        assertEquals(1, dao.data.size)
    }

    // ---- Deteksi password bawaan ----

    @Test
    fun `passwordTergolongDefault hanya benar untuk password bawaan persis`() {
        assertTrue(UserRepository.passwordTergolongDefault(bawaan))
        assertFalse(UserRepository.passwordTergolongDefault(bawaan + "1"))
        assertFalse(UserRepository.passwordTergolongDefault(bawaan.uppercase()))
        assertFalse(UserRepository.passwordTergolongDefault(""))
    }

    @Test
    fun `login dengan password bawaan berhasil di level repository dan bisa dikenali`() = runBlocking {
        repo.pastikanAdaAdminDefault()
        val hasil = repo.login("admin", bawaan)
        assertTrue(hasil is Result.Success)
        // LoginViewModel memakai pengecekan ini untuk menahan sesi sampai password diganti.
        assertTrue(UserRepository.passwordTergolongDefault(bawaan))
    }

    // ---- Password baru tidak boleh sama dengan password bawaan ----

    @Test
    fun `gantiPassword menolak password baru yang sama dengan password bawaan`() = runBlocking {
        val admin = repo.pastikanAdaAdminDefault()!!
        val hasil = repo.gantiPassword(admin.id, bawaan, bawaan)
        assertTrue(hasil is Result.Failure)
        // password tetap yang lama
        assertTrue(PasswordHasher.verify(bawaan, dao.data.single().passwordHash))
    }

    @Test
    fun `gantiPassword menolak password baru terlalu pendek`() = runBlocking {
        val admin = repo.pastikanAdaAdminDefault()!!
        assertTrue(repo.gantiPassword(admin.id, bawaan, "abc") is Result.Failure)
    }

    @Test
    fun `gantiPassword menolak password lama yang salah`() = runBlocking {
        val admin = repo.pastikanAdaAdminDefault()!!
        assertTrue(repo.gantiPassword(admin.id, "salah-total", "passwordBaru1") is Result.Failure)
    }

    @Test
    fun `gantiPassword sukses - password baru berlaku dan password bawaan tidak lagi`() = runBlocking {
        val admin = repo.pastikanAdaAdminDefault()!!
        assertTrue(repo.gantiPassword(admin.id, bawaan, "passwordBaru1") is Result.Success)

        assertTrue(repo.login("admin", "passwordBaru1") is Result.Success)
        assertTrue(repo.login("admin", bawaan) is Result.Failure)
    }

    @Test
    fun `resetPasswordOlehAdmin menolak password bawaan`() = runBlocking {
        val admin = repo.pastikanAdaAdminDefault()!!
        assertTrue(repo.resetPasswordOlehAdmin(admin.id, bawaan) is Result.Failure)
        assertTrue(repo.resetPasswordOlehAdmin(admin.id, "passwordBaru1") is Result.Success)
    }

    @Test
    fun `buatPengguna menolak password bawaan`() = runBlocking {
        assertTrue(repo.buatPengguna("Siti", "siti", bawaan, UserRole.KASIR) is Result.Failure)
        assertEquals(0, dao.data.size)
        assertTrue(repo.buatPengguna("Siti", "siti", "passwordBaru1", UserRole.KASIR) is Result.Success)
    }
}
