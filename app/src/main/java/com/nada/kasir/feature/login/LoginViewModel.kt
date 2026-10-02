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
    val sisaDetikLockout: Int = 0,
    /** Login dengan password bawaan berhasil, tapi sesi BELUM dibuka sampai password diganti (F-09). */
    val perluGantiPassword: UserEntity? = null,
    val errorGantiPassword: String? = null,
    val sedangGantiPassword: Boolean = false
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
                    infoAdminDefault = "Akun pertama dibuat otomatis - Username: ${adminBaru.username}, " +
                        "Password: ${UserRepository.PASSWORD_ADMIN_DEFAULT}. Setelah login pertama Anda akan " +
                        "diminta membuat password baru demi keamanan toko Anda."
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
                    if (UserRepository.passwordTergolongDefault(password)) {
                        // Password bawaan aplikasi bersifat publik: akun TIDAK boleh dipakai sebelum
                        // pemiliknya membuat password sendiri. Sesi sengaja belum dibuka di sini.
                        _uiState.value = _uiState.value.copy(
                            sedangProses = false,
                            perluGantiPassword = result.data,
                            errorGantiPassword = null,
                            sisaDetikLockout = 0
                        )
                    } else {
                        sessionManager.login(result.data)
                        _uiState.value = _uiState.value.copy(
                            sedangProses = false,
                            loginBerhasil = result.data,
                            sisaDetikLockout = 0
                        )
                    }
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

    /** Menyelesaikan login pertama: mengganti password bawaan, lalu membuka sesi. */
    fun gantiPasswordAwal(passwordBaru: String, konfirmasi: String) {
        val user = _uiState.value.perluGantiPassword ?: return
        if (passwordBaru != konfirmasi) {
            _uiState.value = _uiState.value.copy(errorGantiPassword = "Konfirmasi password tidak sama.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(sedangGantiPassword = true, errorGantiPassword = null)
            // Password lama = password bawaan: login barusan membuktikan itulah password akun ini.
            when (val hasil = userRepository.gantiPassword(user.id, UserRepository.PASSWORD_ADMIN_DEFAULT, passwordBaru)) {
                is Result.Success -> {
                    sessionManager.login(user)
                    _uiState.value = _uiState.value.copy(
                        sedangGantiPassword = false,
                        perluGantiPassword = null,
                        loginBerhasil = user
                    )
                }
                is Result.Failure -> _uiState.value = _uiState.value.copy(
                    sedangGantiPassword = false,
                    errorGantiPassword = hasil.error.pesan
                )
            }
        }
    }

    /** Batal ganti password: kembali ke form login tanpa membuka sesi. */
    fun batalGantiPassword() {
        _uiState.value = _uiState.value.copy(perluGantiPassword = null, errorGantiPassword = null)
    }

    fun clearError() { _uiState.value = _uiState.value.copy(errorPesan = null) }
    fun clearInfoAdminDefault() { _uiState.value = _uiState.value.copy(infoAdminDefault = null) }
}
