package com.nada.kasir.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nada.kasir.core.data.local.entity.StoreEntity
import com.nada.kasir.core.data.local.entity.TransactionEntity
import com.nada.kasir.core.data.local.entity.TransactionItemEntity
import com.nada.kasir.core.data.repository.ProductRepository
import com.nada.kasir.core.data.repository.StoreRepository
import com.nada.kasir.core.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class TransaksiTerbaruTampilan(
    val id: Long,
    val noTransaksi: String,
    val previewProduk: String,
    val total: Double,
    val jam: String
)

data class DashboardUiState(
    val penjualanHariIni: Double = 0.0,
    val jumlahTransaksi: Int = 0,
    val stokMenipis: Int = 0,
    val stokHabis: Int = 0,
    val store: StoreEntity? = null,
    val transaksiTerbaru: List<TransaksiTerbaruTampilan> = emptyList(),
    val sedangMemuatTransaksiTerbaru: Boolean = true,
    val paketAktif: com.nada.kasir.core.paket.PaketAplikasi = com.nada.kasir.core.paket.PaketAplikasi.BASIC
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val productRepository: ProductRepository,
    private val storeRepository: StoreRepository,
    private val paketRepository: com.nada.kasir.core.paket.PaketRepository
) : ViewModel() {

    private val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
    }
    private val startMillis = cal.timeInMillis
    private val endMillis = startMillis + 24 * 60 * 60 * 1000L

    private val transaksiTerbaruFlow = MutableStateFlow<List<TransaksiTerbaruTampilan>>(emptyList())
    private val sedangMemuatFlow = MutableStateFlow(true)

    val uiState: StateFlow<DashboardUiState> = combine(
        transactionRepository.observeTotalPenjualanHariIni(startMillis, endMillis),
        transactionRepository.observeJumlahTransaksiHariIni(startMillis, endMillis),
        productRepository.observeStokMenipis(),
        productRepository.observeStokHabis(),
        storeRepository.observeStore(),
        transaksiTerbaruFlow,
        sedangMemuatFlow,
        paketRepository.observePaketAktif()
    ) { flows ->
        DashboardUiState(
            penjualanHariIni = flows[0] as Double,
            jumlahTransaksi = flows[1] as Int,
            stokMenipis = (flows[2] as List<*>).size,
            stokHabis = (flows[3] as List<*>).size,
            store = flows[4] as StoreEntity?,
            transaksiTerbaru = flows[5] as List<TransaksiTerbaruTampilan>,
            sedangMemuatTransaksiTerbaru = flows[6] as Boolean,
            paketAktif = flows[7] as com.nada.kasir.core.paket.PaketAplikasi
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    init {
        muatTransaksiTerbaru()
    }

    /** Dipanggil ulang setiap Dashboard dibuka (mis. setelah kasir menyelesaikan transaksi baru). */
    fun muatTransaksiTerbaru() {
        viewModelScope.launch {
            sedangMemuatFlow.value = true
            val sdfJam = java.text.SimpleDateFormat("HH:mm", Locale("id", "ID"))
            val hasil = transactionRepository.getTransaksiTerbaruDenganItem(5).map { (trx, items) ->
                TransaksiTerbaruTampilan(
                    id = trx.id,
                    noTransaksi = trx.noTransaksi,
                    previewProduk = items.joinToString(", ") { it.namaProdukSnapshot },
                    total = trx.total,
                    jam = sdfJam.format(Date(trx.tanggalWaktu))
                )
            }
            transaksiTerbaruFlow.value = hasil
            sedangMemuatFlow.value = false
        }
    }
}
