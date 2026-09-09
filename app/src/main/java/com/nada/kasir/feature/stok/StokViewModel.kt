package com.nada.kasir.feature.stok

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nada.kasir.core.data.local.entity.StockMovementEntity
import com.nada.kasir.core.data.repository.StockRepository
import com.nada.kasir.core.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StokViewModel @Inject constructor(
    private val stockRepository: StockRepository
) : ViewModel() {

    val riwayatMutasi: StateFlow<List<StockMovementEntity>> = stockRepository.observeRiwayat()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun stokMasuk(productId: Long, qty: Int, hargaBeli: Double?, supplier: String?, keterangan: String?, onError: (String) -> Unit) {
        viewModelScope.launch {
            when (val r = stockRepository.stokMasuk(productId, qty, hargaBeli, supplier, keterangan)) {
                is Result.Failure -> onError(r.error.pesan)
                is Result.Success -> Unit
            }
        }
    }

    fun stokKeluar(productId: Long, qty: Int, keterangan: String?, onError: (String) -> Unit) {
        viewModelScope.launch {
            when (val r = stockRepository.stokKeluar(productId, qty, keterangan)) {
                is Result.Failure -> onError(r.error.pesan)
                is Result.Success -> Unit
            }
        }
    }
}
