package com.nada.kasir.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nada.kasir.core.data.local.entity.UserEntity
import com.nada.kasir.core.data.repository.UserRepository
import com.nada.kasir.core.lisensi.LicenseRepository
import com.nada.kasir.core.session.SessionManager
import com.nada.kasir.core.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val sedangProses: Boolean = false,
    val errorPesan: String? = null,
    val loginBerhasil: UserEntity? = null,
    val infoAdminDefault: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    private val licenseRepository: LicenseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    init {
        // Mode demo / first-run (poin 23): pastikan selalu ada 1 akun admin agar toko baru bisa login.
        viewModelScope.launch {
            val adminBaru = userRepository.pastikanAdaAdminDefault()
            if (adminBaru != null) {
                _uiState.value = _uiState.value.copy(
                    infoAdminDefault = "Akun pertama dibuat otomatis - Username: admin, Password: admin123. Segera ganti password setelah login."
                )
            }
        }
        // Cek lisensi langganan setiap aplikasi dibuka - otomatis turun ke Basic kalau sudah kadaluarsa.
        viewModelScope.launch { licenseRepository.cekDanTurunkanJikaKadaluarsa() }
    }

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(errorPesan = "Username dan password wajib diisi.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(sedangProses = true, errorPesan = null)
            when (val result = userRepository.login(username.trim(), password)) {
                is Result.Success -> {
                    sessionManager.login(result.data)
                    _uiState.value = _uiState.value.copy(sedangProses = false, loginBerhasil = result.data)
                }
                is Result.Failure -> {
                    _uiState.value = _uiState.value.copy(sedangProses = false, errorPesan = result.error.pesan)
                }
            }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(errorPesan = null) }
    fun clearInfoAdminDefault() { _uiState.value = _uiState.value.copy(infoAdminDefault = null) }
}
