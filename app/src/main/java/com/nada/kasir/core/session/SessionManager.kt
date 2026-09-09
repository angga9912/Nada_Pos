package com.nada.kasir.core.session

import com.nada.kasir.core.data.local.entity.UserEntity
import com.nada.kasir.core.data.local.entity.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Menyimpan pengguna yang sedang login (poin 18). Sengaja di-memori saja
 * (bukan disimpan permanen) - setiap aplikasi dibuka ulang, kasir wajib
 * login lagi. Ini praktik umum untuk aplikasi kasir bersama (shared device).
 */
@Singleton
class SessionManager @Inject constructor() {
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser

    fun login(user: UserEntity) { _currentUser.value = user }
    fun logout() { _currentUser.value = null }

    fun isAdmin(): Boolean = _currentUser.value?.role == UserRole.ADMIN
    fun sudahLogin(): Boolean = _currentUser.value != null
}
