package com.nada.kasir.feature.produk

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nada.kasir.core.data.local.entity.ProductEntity
import com.nada.kasir.core.data.repository.ProductRepository
import com.nada.kasir.core.excel.ExcelExporter
import com.nada.kasir.core.excel.ExcelImporter
import com.nada.kasir.core.excel.ImportProdukResult
import com.nada.kasir.core.paket.PaketAplikasi
import com.nada.kasir.core.paket.PaketRepository
import com.nada.kasir.core.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ProdukViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val paketRepository: PaketRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: Context
) : ViewModel() {

    val daftarProduk: StateFlow<List<ProductEntity>> = productRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val paketAktif: StateFlow<PaketAplikasi> = paketRepository.observePaketAktif()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PaketAplikasi.BASIC)

    private val _pesanImportExport = MutableStateFlow<String?>(null)
    val pesanImportExport: StateFlow<String?> = _pesanImportExport

    private val _fileExportTerakhir = MutableStateFlow<File?>(null)
    val fileExportTerakhir: StateFlow<File?> = _fileExportTerakhir

    fun simpan(product: ProductEntity, onError: (String) -> Unit) {
        viewModelScope.launch {
            when (val result = productRepository.simpan(product)) {
                is Result.Failure -> onError(result.error.pesan)
                is Result.Success -> Unit
            }
        }
    }

    fun hapus(id: Long) {
        viewModelScope.launch { productRepository.hapus(id) }
    }

    /** Export DATA_PRODUK ke .xlsx (poin 6 & 15). */
    fun exportExcel() {
        viewModelScope.launch {
            val produk = daftarProduk.value
            val file = withContext(Dispatchers.IO) {
                ExcelExporter(appContext).exportProduk(produk) { "" } // kategori disederhanakan di Phase 3
            }
            _fileExportTerakhir.value = file
            _pesanImportExport.value = "Export berhasil: ${file.name}"
        }
    }

    /** Import DATA_PRODUK dari .xlsx yang dipilih pengguna (poin 6 & 15). */
    fun importExcel(uri: Uri) {
        viewModelScope.launch {
            val hasil: ImportProdukResult = withContext(Dispatchers.IO) {
                ExcelImporter(appContext).bacaDanValidasiProduk(uri)
            }
            if (hasil.formatSalah) {
                _pesanImportExport.value = "File Excel tidak sesuai format."
                return@launch
            }
            val (jumlahBerhasil, dilewati) = productRepository.importBanyak(hasil.berhasil)
            val ringkasan = StringBuilder("Import selesai: $jumlahBerhasil produk berhasil ditambahkan.")
            if (hasil.gagal.isNotEmpty()) {
                ringkasan.append("\n${hasil.gagal.size} baris gagal (lihat baris: ${hasil.gagal.joinToString { "${it.first}" }}).")
            }
            if (dilewati.isNotEmpty()) {
                ringkasan.append("\n${dilewati.size} baris dilewati karena barcode duplikat.")
            }
            _pesanImportExport.value = ringkasan.toString()
        }
    }

    fun clearPesanImportExport() { _pesanImportExport.value = null }
}
