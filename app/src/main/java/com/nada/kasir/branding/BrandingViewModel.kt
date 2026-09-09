package com.nada.kasir.branding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nada.kasir.core.data.local.entity.StoreEntity
import com.nada.kasir.core.data.repository.StoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Menyediakan data toko untuk MainActivity supaya warna aplikasi mengikuti StoreEntity.warnaUtama (poin 2 & 21). */
@HiltViewModel
class BrandingViewModel @Inject constructor(
    storeRepository: StoreRepository
) : ViewModel() {
    val store: StateFlow<StoreEntity?> = storeRepository.observeStore()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
