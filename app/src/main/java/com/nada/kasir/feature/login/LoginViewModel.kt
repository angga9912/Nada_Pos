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
    val infoAdminDefault: String? = null,
    val sisaDetikLockout: Int = 0
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    private val licenseRepository: LicenseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    // Proteksi brute-force login
    private var percobaanGagal = 0
    private var lockoutSampaiMillis = 0L

    companion object {
        private const val MAKS_PERCOBAAN_GAGAL = 5
        private const val DURASI_LOCKOUT_MILLIS = 30_000L // 30 detik
    }

    init {
        // Mode demo / first-run (poin 23): pastikan selalu ada 1 akun admin agar toko baru bisa login.
        viewModelScope.launch {
            val adminBaru = userRepository.pastikanAdaAdminDefault()
            if (adminBaru != null) {
                _uiState.value = _uiState.value.copy(
                    infoAdminDefault = "Akun pertama dibuat otomatis - Username: admin, Password: admin123. Segera ganti password setelah login demi keamanan toko Anda."
                )
            }
        }
        // Cek lisensi langganan setiap aplikasi dibuka - otomatis turun ke Basic kalau sudah kadaluarsa.
        viewModelScope.launch { licenseRepository.cekDanTurunkanJikaKadaluarsa() }
    }

    fun login(username: String, password: String) {
        val sekarang = System.currentTimeMillis()
        if (sekarang < lockoutSampaiMillis) {
            val sisaDetik = (((lockoutSampaiMillis - sekarang) / 1000) + 1).toInt()
            _uiState.value = _uiState.value.copy(
                errorPesan = "Terlalu banyak percobaan login gagal. Silakan tunggu $sisaDetik detik lagi.",
                sisaDetikLockout = sisaDetik
            )
            return
        }

        if (username.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(errorPesan = "Username dan password wajib diisi.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(sedangProses = true, errorPesan = null)
            when (val result = userRepository.login(username.trim(), password)) {
                is Result.Success -> {
                    percobaanGagal = 0
                    lockoutSampaiMillis = 0L
                    sessionManager.login(result.data)
                    _uiState.value = _uiState.value.copy(
                        sedangProses = false,
                        loginBerhasil = result.data,
                        sisaDetikLockout = 0
                    )
                }
                is Result.Failure -> {
                    percobaanGagal++
                    val pesanError: String
                    var sisaDetik = 0
                    if (percobaanGagal >= MAKS_PERCOBAAN_GAGAL) {
                        lockoutSampaiMillis = System.currentTimeMillis() + DURASI_LOCKOUT_MILLIS
                        sisaDetik = (DURASI_LOCKOUT_MILLIS / 1000).toInt()
                        pesanError = "Terlalu banyak percobaan gagal ($percobaanGagal kali). Login dikunci sementara selama $sisaDetik detik."
                    } else {
                        val sisaPercobaan = MAKS_PERCOBAAN_GAGAL - percobaanGagal
                        pesanError = "${result.error.pesan} (Sisa kesempatan: $sisaPercobaan kali sebelum dikunci)"
                    }
                    _uiState.value = _uiState.value.copy(
                        sedangProses = false,
                        errorPesan = pesanError,
                        sisaDetikLockout = sisaDetik
                    )
                }
            }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(errorPesan = null) }
    fun clearInfoAdminDefault() { _uiState.value = _uiState.value.copy(infoAdminDefault = null) }
}
