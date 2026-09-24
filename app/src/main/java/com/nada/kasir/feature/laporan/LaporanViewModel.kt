package com.nada.kasir.feature.laporan

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nada.kasir.core.data.repository.LaporanPeriode
import com.nada.kasir.core.data.repository.ProductRepository
import com.nada.kasir.core.data.repository.ReportRepository
import com.nada.kasir.core.excel.ExcelExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*
import javax.inject.Inject

enum class TabLaporan { HARIAN, BULANAN, STOK }

data class LaporanUiState(
    val tabAktif: TabLaporan = TabLaporan.HARIAN,
    val laporanHarian: LaporanPeriode? = null,
    val laporanBulanan: LaporanPeriode? = null,
    val stokMenipis: Int = 0,
    val stokHabis: Int = 0,
    val totalProduk: Int = 0,
    val fileExportTerakhir: File? = null,
    val sedangMemuat: Boolean = false
)

@HiltViewModel
class LaporanViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val productRepository: ProductRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(LaporanUiState())
    val uiState: StateFlow<LaporanUiState> = _uiState

    init {
        muatSemuaLaporan()
    }

    fun pilihTab(tab: TabLaporan) { _uiState.value = _uiState.value.copy(tabAktif = tab) }

    fun muatSemuaLaporan() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(sedangMemuat = true)

            val calHariIni = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }
            val startHari = calHariIni.timeInMillis
            val endHari = startHari + 24 * 60 * 60 * 1000L

            val calBulan = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }
            val startBulan = calBulan.timeInMillis
            val endBulan = System.currentTimeMillis()

            val laporanHarian = reportRepository.laporanPeriode(startHari, endHari)
            val laporanBulanan = reportRepository.laporanPeriode(startBulan, endBulan, sertakanProdukTerlaris = true)
            val menipis = productRepository.observeStokMenipis().first()
            val habis = productRepository.observeStokHabis().first()
            val semuaAktif = productRepository.observeActive().first()

            _uiState.value = _uiState.value.copy(
                laporanHarian = laporanHarian,
                laporanBulanan = laporanBulanan,
                stokMenipis = menipis.size,
                stokHabis = habis.size,
                totalProduk = semuaAktif.size,
                sedangMemuat = false
            )
        }
    }

    fun exportLaporanAktif() {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                when (_uiState.value.tabAktif) {
                    TabLaporan.HARIAN -> {
                        val laporan = _uiState.value.laporanHarian ?: return@withContext null
                        ExcelExporter(appContext).exportLaporan(
                            "LAPORAN_HARIAN", laporan.totalPenjualan, laporan.jumlahTransaksi,
                            laporan.produkTerjual, laporan.totalDiskon, laporan.estimasiKeuntungan
                        )
                    }
                    TabLaporan.BULANAN -> {
                        val laporan = _uiState.value.laporanBulanan ?: return@withContext null
                        ExcelExporter(appContext).exportLaporan(
                            "LAPORAN_BULANAN", laporan.totalPenjualan, laporan.jumlahTransaksi,
                            laporan.produkTerjual, laporan.totalDiskon, laporan.estimasiKeuntungan
                        )
                    }
                    TabLaporan.STOK -> {
                        val produkList = productRepository.observeActive().first()
                        ExcelExporter(appContext).exportProduk(produkList) { "-" }
                    }
                }
            }
            if (file != null) {
                _uiState.value = _uiState.value.copy(fileExportTerakhir = file)
            }
        }
    }
}
