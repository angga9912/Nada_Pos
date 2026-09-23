package com.nada.kasir.feature.pengaturan_hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nada.kasir.core.data.repository.UserRepository
import com.nada.kasir.core.paket.PaketAplikasi
import com.nada.kasir.core.paket.PaketRepository
import com.nada.kasir.core.session.SessionManager
import com.nada.kasir.core.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PengaturanHubViewModel @Inject constructor(
    paketRepository: PaketRepository,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository
) : ViewModel() {

    val paketAktif: StateFlow<PaketAplikasi> = paketRepository.observePaketAktif()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PaketAplikasi.BASIC)

    val currentUserId: Long get() = sessionManager.currentUser.value?.id ?: 1L
    val currentUserName: String get() = sessionManager.currentUser.value?.nama ?: "Pengguna"

    fun gantiPassword(passwordLama: String, passwordBaru: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = userRepository.gantiPassword(currentUserId, passwordLama, passwordBaru)
            onResult(res)
        }
    }
}
