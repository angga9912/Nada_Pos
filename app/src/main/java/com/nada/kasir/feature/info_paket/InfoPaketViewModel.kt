package com.nada.kasir.feature.info_paket

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nada.kasir.core.paket.PaketAplikasi
import com.nada.kasir.core.paket.PaketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class InfoPaketViewModel @Inject constructor(
    paketRepository: PaketRepository
) : ViewModel() {
    val paketAktif: StateFlow<PaketAplikasi> = paketRepository.observePaketAktif()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PaketAplikasi.BASIC)
}
