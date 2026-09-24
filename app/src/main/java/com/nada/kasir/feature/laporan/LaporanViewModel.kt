package com.nada.kasir.feature.laporan

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nada.kasir.core.data.local.dao.ProdukTerlaris
import com.nada.kasir.core.data.local.entity.StoreEntity
import com.nada.kasir.core.data.repository.LaporanPeriode
import com.nada.kasir.core.data.repository.ProductRepository
import com.nada.kasir.core.data.repository.ReportRepository
import com.nada.kasir.core.data.repository.StoreRepository
import com.nada.kasir.core.data.repository.TransactionRepository
import com.nada.kasir.core.excel.ExcelExporter
import com.nada.kasir.core.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*
import javax.inject.Inject

enum class TabLaporan { HARIAN, TUJUH_HARI, BULANAN, STOK }

data class LaporanUiState(
    val tabAktif: TabLaporan = TabLaporan.HARIAN,
    val store: StoreEntity? = null,
    val totalOmzetSemuaWaktu: Double = 0.0,
    val penjualanHariIni: Double = 0.0,
    val jumlahTransaksiHariIni: Int = 0,
    val itemTerjualHariIni: Int = 0,
    val stokMenipis: Int = 0,
    val stokHabis: Int = 0,
    val totalProduk: Int = 0,
    val produkPopuler: List<ProdukTerlaris> = emptyList(),
    val laporanHarian: LaporanPeriode? = null,
    val laporan7Hari: LaporanPeriode? = null,
    val laporanBulanan: LaporanPeriode? = null,
    val fileExportTerakhir: File? = null,
    val sedangMemuat: Boolean = false,
    val currentUserName: String = "Admin"
)

@HiltViewModel
class LaporanViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val productRepository: ProductRepository,
    private val transactionRepository: TransactionRepository,
    private val storeRepository: StoreRepository,
    private val sessionManager: SessionManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(LaporanUiState())
    val uiState: StateFlow<LaporanUiState> = _uiState

    init {
        muatSemuaLaporan()
    }

    fun pilihTab(tab: TabLaporan) {
        _uiState.value = _uiState.value.copy(tabAktif = tab)
    }

    fun muatSemuaLaporan() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(sedangMemuat = true)

            val calHariIni = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val startHari = calHariIni.timeInMillis
            val endHari = startHari + 24 * 60 * 60 * 1000L - 1L

            val cal7Hari = Calendar.getInstance().apply {
                timeInMillis = startHari
                add(Calendar.DAY_OF_YEAR, -6)
            }
            val start7Hari = cal7Hari.timeInMillis

            val calBulan = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val startBulan = calBulan.timeInMillis
            val endBulan = System.currentTimeMillis()

            val laporanHarian = reportRepository.laporanPeriode(startHari, endHari)
            val laporan7Hari = reportRepository.laporanPeriode(start7Hari, endBulan, sertakanProdukTerlaris = true)
            val laporanBulanan = reportRepository.laporanPeriode(startBulan, endBulan, sertakanProdukTerlaris = true)
            val menipis = productRepository.observeStokMenipis().first()
            val habis = productRepository.observeStokHabis().first()
            val semuaAktif = productRepository.observeActive().first()
            val totalOmzet = transactionRepository.observeTotalOmzetSemuaWaktu().first()
            val populer = transactionRepository.getProdukPopuler(start7Hari, endBulan, 10)
            val store = storeRepository.getOrCreateDefault()
            val userName = sessionManager.currentUser.value?.nama ?: "Admin"

            _uiState.value = _uiState.value.copy(
                store = store,
                totalOmzetSemuaWaktu = totalOmzet,
                penjualanHariIni = laporanHarian.totalPenjualan,
                jumlahTransaksiHariIni = laporanHarian.jumlahTransaksi,
                itemTerjualHariIni = laporanHarian.produkTerjual,
                stokMenipis = menipis.size,
                stokHabis = habis.size,
                totalProduk = semuaAktif.size,
                produkPopuler = populer,
                laporanHarian = laporanHarian,
                laporan7Hari = laporan7Hari,
                laporanBulanan = laporanBulanan,
                currentUserName = userName,
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
                    TabLaporan.TUJUH_HARI -> {
                        val laporan = _uiState.value.laporan7Hari ?: return@withContext null
                        ExcelExporter(appContext).exportLaporan(
                            "LAPORAN_7_HARI", laporan.totalPenjualan, laporan.jumlahTransaksi,
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
