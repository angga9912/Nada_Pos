package com.nada.kasir.feature.riwayat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nada.kasir.core.data.local.entity.TransactionEntity
import com.nada.kasir.core.data.repository.PrinterRepository
import com.nada.kasir.core.data.repository.StoreRepository
import com.nada.kasir.core.data.repository.TransactionRepository
import com.nada.kasir.core.printer.BluetoothPrinterManager
import com.nada.kasir.core.printer.StrukFormatter
import com.nada.kasir.core.session.SessionManager
import com.nada.kasir.core.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

enum class FilterRentangRiwayat(val label: String) {
    HARI_INI("Hari Ini"),
    TUJUH_HARI("7 Hari"),
    BULAN_INI("Bulan Ini"),
    SEMUA("Semua")
}

@HiltViewModel
class RiwayatViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val storeRepository: StoreRepository,
    private val printerRepository: PrinterRepository,
    private val bluetoothPrinterManager: BluetoothPrinterManager,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    val query: StateFlow<String> = queryFlow

    private val filterRentangFlow = MutableStateFlow(FilterRentangRiwayat.HARI_INI)
    val filterRentang: StateFlow<FilterRentangRiwayat> = filterRentangFlow

    private val previewStrukFlow = MutableStateFlow<String?>(null)
    private val transaksiIdPreviewFlow = MutableStateFlow<Long?>(null)
    private val sedangMencetakFlow = MutableStateFlow(false)

    val previewStruk: StateFlow<String?> = previewStrukFlow
    val sedangMencetak: StateFlow<Boolean> = sedangMencetakFlow

    val riwayat: StateFlow<List<TransactionEntity>> = combine(queryFlow, filterRentangFlow) { q, filter ->
        Pair(q, filter)
    }.flatMapLatest { (q, filter) ->
        val now = System.currentTimeMillis()
        val (start, end) = when (filter) {
            FilterRentangRiwayat.HARI_INI -> {
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                val s = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
                Pair(s, cal.timeInMillis)
            }
            FilterRentangRiwayat.TUJUH_HARI -> {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -7)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, now)
            }
            FilterRentangRiwayat.BULAN_INI -> {
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                val s = cal.timeInMillis
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
                Pair(s, cal.timeInMillis)
            }
            FilterRentangRiwayat.SEMUA -> {
                Pair(0L, Long.MAX_VALUE)
            }
        }
        transactionRepository.observeRiwayat(q.trim(), start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChange(q: String) { queryFlow.value = q }

    fun onFilterRentangChange(filter: FilterRentangRiwayat) { filterRentangFlow.value = filter }

    fun batalkanTransaksi(id: Long, onError: (String) -> Unit) {
        if (!sessionManager.isAdmin()) {
            onError("Hanya Administrator yang berhak membatalkan transaksi.")
            return
        }
        viewModelScope.launch {
            when (val r = transactionRepository.batalkanTransaksi(id)) {
                is Result.Failure -> onError(r.error.pesan)
                is Result.Success -> Unit
            }
        }
    }

    /** Tampilkan PREVIEW dulu sebelum Cetak Ulang benar-benar dikirim ke printer (poin 13). */
    fun tampilkanPreviewCetakUlang(transactionId: Long, onError: (String) -> Unit) {
        viewModelScope.launch {
            val store = storeRepository.getOrCreateDefault()
            val (transaksi, items, payment) = transactionRepository.getDetail(transactionId)
            if (transaksi == null) { onError("Transaksi tidak ditemukan."); return@launch }
            transaksiIdPreviewFlow.value = transactionId
            previewStrukFlow.value = StrukFormatter.buatStrukPreviewText(store, transaksi, items, payment)
        }
    }

    fun tutupPreviewStruk() {
        previewStrukFlow.value = null
        transaksiIdPreviewFlow.value = null
    }

    /** Dipanggil dari dialog preview saat pengguna menekan "Cetak Sekarang". */
    fun cetakDariPreview(onError: (String) -> Unit) {
        val transactionId = transaksiIdPreviewFlow.value ?: return
        viewModelScope.launch {
            sedangMencetakFlow.value = true
            val printerDefault = printerRepository.getDefault()
            if (printerDefault == null) {
                sedangMencetakFlow.value = false
                tutupPreviewStruk()
                onError("Belum ada printer default. Atur di menu Pengaturan Printer.")
                return@launch
            }
            val store = storeRepository.getOrCreateDefault()
            val (transaksi, items, payment) = transactionRepository.getDetail(transactionId)
            if (transaksi == null) {
                sedangMencetakFlow.value = false
                tutupPreviewStruk()
                onError("Transaksi tidak ditemukan.")
                return@launch
            }
            val struk = StrukFormatter.buatStruk(store, transaksi, items, payment)
            val result = bluetoothPrinterManager.cetak(printerDefault.macAddress, struk)
            sedangMencetakFlow.value = false
            tutupPreviewStruk()
            if (result is Result.Failure) onError(result.error.pesan)
        }
    }
}
