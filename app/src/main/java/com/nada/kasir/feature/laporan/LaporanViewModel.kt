package com.nada.kasir.feature.laporan

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nada.kasir.core.data.local.dao.ProdukTerlaris
import com.nada.kasir.core.data.local.entity.OutletEntity
import com.nada.kasir.core.data.local.entity.StoreEntity
import com.nada.kasir.core.data.repository.OutletRepository
import com.nada.kasir.core.domain.logic.OutletAktifLogic
import com.nada.kasir.core.paket.FiturPaket
import com.nada.kasir.core.paket.PaketRepository
import com.nada.kasir.core.paket.mengizinkan
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
    val currentUserName: String = "Admin",
    /** Daftar cabang untuk filter; kosong bila paket tidak mencakup Outlet atau belum ada outlet. */
    val daftarOutlet: List<OutletEntity> = emptyList(),
    /** null = semua cabang. */
    val outletFilterId: Long? = null
) {
    val labelFilterCabang: String get() = OutletAktifLogic.labelFilter(outletFilterId, daftarOutlet)
}

@HiltViewModel
class LaporanViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val productRepository: ProductRepository,
    private val transactionRepository: TransactionRepository,
    private val storeRepository: StoreRepository,
    private val sessionManager: SessionManager,
    private val outletRepository: OutletRepository,
    private val paketRepository: PaketRepository,
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

    /** null = semua cabang. Memuat ulang seluruh angka laporan untuk cabang itu. */
    fun pilihCabang(id: Long?) {
        _uiState.value = _uiState.value.copy(outletFilterId = id)
        muatSemuaLaporan()
    }

    fun muatSemuaLaporan() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(sedangMemuat = true)

            // Filter cabang hanya ada bila paket mencakup Outlet; pilihan yang sudah tidak ada dianggap "semua cabang".
            val outletTersedia = paketRepository.observePaketAktif().first().mengizinkan(FiturPaket.OUTLET)
            val daftarOutlet = if (outletTersedia) outletRepository.observeAll().first() else emptyList()
            val filterId = _uiState.value.outletFilterId?.takeIf { id -> daftarOutlet.any { it.id == id } }

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

            val laporanHarian = reportRepository.laporanPeriode(startHari, endHari, outletId = filterId)
            val laporan7Hari = reportRepository.laporanPeriode(start7Hari, endBulan, sertakanProdukTerlaris = true, outletId = filterId)
            val laporanBulanan = reportRepository.laporanPeriode(startBulan, endBulan, sertakanProdukTerlaris = true, outletId = filterId)
            val menipis = productRepository.observeStokMenipis().first()
            val habis = productRepository.observeStokHabis().first()
            val semuaAktif = productRepository.observeActive().first()
            val totalOmzet = if (filterId == null) transactionRepository.observeTotalOmzetSemuaWaktu().first()
                else transactionRepository.getTotalOmzetSemuaWaktu(filterId)
            val populer = transactionRepository.getProdukPopuler(start7Hari, endBulan, 10, filterId)
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
                daftarOutlet = daftarOutlet,
                outletFilterId = filterId,
                sedangMemuat = false
            )
        }
    }

    fun exportLaporanAktif() {
        viewModelScope.launch {
            // Ekspor mengikuti filter cabang yang sedang aktif; kode cabang ditambahkan ke nama file.
            val sufiksCabang = _uiState.value.let { st ->
                st.daftarOutlet.firstOrNull { it.id == st.outletFilterId }
                    ?.kode?.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                    ?.takeIf { it.isNotEmpty() }?.let { "_$it" } ?: ""
            }
            val file = withContext(Dispatchers.IO) {
                when (_uiState.value.tabAktif) {
                    TabLaporan.HARIAN -> {
                        val laporan = _uiState.value.laporanHarian ?: return@withContext null
                        ExcelExporter(appContext).exportLaporan(
                            "LAPORAN_HARIAN$sufiksCabang", laporan.totalPenjualan, laporan.jumlahTransaksi,
                            laporan.produkTerjual, laporan.totalDiskon, laporan.estimasiKeuntungan
                        )
                    }
                    TabLaporan.TUJUH_HARI -> {
                        val laporan = _uiState.value.laporan7Hari ?: return@withContext null
                        ExcelExporter(appContext).exportLaporan(
                            "LAPORAN_7_HARI$sufiksCabang", laporan.totalPenjualan, laporan.jumlahTransaksi,
                            laporan.produkTerjual, laporan.totalDiskon, laporan.estimasiKeuntungan
                        )
                    }
                    TabLaporan.BULANAN -> {
                        val laporan = _uiState.value.laporanBulanan ?: return@withContext null
                        ExcelExporter(appContext).exportLaporan(
                            "LAPORAN_BULANAN$sufiksCabang", laporan.totalPenjualan, laporan.jumlahTransaksi,
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
