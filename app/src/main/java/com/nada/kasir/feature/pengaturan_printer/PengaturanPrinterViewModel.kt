package com.nada.kasir.feature.pengaturan_printer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nada.kasir.core.data.local.entity.PrinterEntity
import com.nada.kasir.core.data.repository.PrinterRepository
import com.nada.kasir.core.printer.BluetoothPrinterManager
import com.nada.kasir.core.printer.PairedPrinterInfo
import com.nada.kasir.core.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PengaturanPrinterUiState(
    val daftarPrinterTerpasang: List<PairedPrinterInfo> = emptyList(),
    val printerDefault: PrinterEntity? = null,
    val bluetoothAktif: Boolean = true,
    val ukuranKertasDipilih: String = "58mm",
    val statusPesan: String? = null,
    val sedangTestPrint: Boolean = false
)

@HiltViewModel
class PengaturanPrinterViewModel @Inject constructor(
    private val bluetoothPrinterManager: BluetoothPrinterManager,
    private val printerRepository: PrinterRepository
) : ViewModel() {

    private val statusFlow = MutableStateFlow<String?>(null)
    private val ukuranKertasFlow = MutableStateFlow("58mm")
    private val testPrintFlow = MutableStateFlow(false)
    private val daftarFlow = MutableStateFlow<List<PairedPrinterInfo>>(emptyList())

    val uiState: StateFlow<PengaturanPrinterUiState> = combine(
        daftarFlow, printerRepository.observeAll(), statusFlow, ukuranKertasFlow, testPrintFlow
    ) { daftar, semuaPrinter, status, ukuran, testing ->
        PengaturanPrinterUiState(
            daftarPrinterTerpasang = daftar,
            printerDefault = semuaPrinter.firstOrNull { it.isDefault },
            bluetoothAktif = bluetoothPrinterManager.bluetoothTersedia(),
            ukuranKertasDipilih = ukuran,
            statusPesan = status,
            sedangTestPrint = testing
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PengaturanPrinterUiState())

    /** Panggil setelah izin Bluetooth diberikan (poin 8: "Scan printer"). */
    fun muatDaftarPrinterTerpasang() {
        daftarFlow.value = bluetoothPrinterManager.daftarPrinterTerpasang()
    }

    fun pilihUkuranKertas(ukuran: String) { ukuranKertasFlow.value = ukuran }

    fun simpanSebagaiDefault(info: PairedPrinterInfo) {
        viewModelScope.launch {
            printerRepository.setSebagaiDefault(
                PrinterEntity(nama = info.nama, macAddress = info.macAddress, ukuranKertas = ukuranKertasFlow.value, isDefault = true)
            )
            statusFlow.value = "Printer '${info.nama}' disimpan sebagai default."
        }
    }

    fun testPrint(macAddress: String) {
        viewModelScope.launch {
            testPrintFlow.value = true
            val result = bluetoothPrinterManager.testPrint(macAddress, ukuranKertasFlow.value)
            testPrintFlow.value = false
            statusFlow.value = when (result) {
                is Result.Success -> "Test print berhasil dikirim."
                is Result.Failure -> result.error.pesan
            }
        }
    }

    fun clearStatus() { statusFlow.value = null }
}
