package com.nada.kasir.feature.pengguna

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nada.kasir.core.data.local.entity.UserEntity
import com.nada.kasir.core.data.local.entity.UserRole
import com.nada.kasir.core.data.repository.UserRepository
import com.nada.kasir.core.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PenggunaViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    val daftarPengguna: StateFlow<List<UserEntity>> = userRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun tambahPengguna(nama: String, username: String, password: String, role: UserRole, onError: (String) -> Unit) {
        viewModelScope.launch {
            when (val result = userRepository.buatPengguna(nama, username, password, role)) {
                is Result.Failure -> onError(result.error.pesan)
                is Result.Success -> Unit
            }
        }
    }
}
