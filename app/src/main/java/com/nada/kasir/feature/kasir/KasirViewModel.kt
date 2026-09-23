package com.nada.kasir.feature.kasir

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nada.kasir.core.data.local.dao.CategoryDao
import com.nada.kasir.core.data.local.entity.CategoryEntity
import com.nada.kasir.core.data.local.entity.MetodePembayaran
import com.nada.kasir.core.data.local.entity.ProductEntity
import com.nada.kasir.core.data.local.entity.StoreEntity
import com.nada.kasir.core.data.local.entity.UserEntity
import com.nada.kasir.core.data.repository.PrinterRepository
import com.nada.kasir.core.data.repository.ProductRepository
import com.nada.kasir.core.data.repository.StoreRepository
import com.nada.kasir.core.data.repository.TransactionRepository
import com.nada.kasir.core.domain.model.KeranjangItem
import com.nada.kasir.core.printer.BluetoothPrinterManager
import com.nada.kasir.core.printer.StrukFormatter
import com.nada.kasir.core.session.SessionManager
import com.nada.kasir.core.util.AppError
import com.nada.kasir.core.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KasirUiState(
    val query: String = "",
    val produk: List<ProductEntity> = emptyList(),
    val keranjang: List<KeranjangItem> = emptyList(),
    val diskonTotal: Double = 0.0,
    val errorPesan: String? = null,
    val transaksiBerhasilId: Long? = null,
    val nomorAntrianBerhasil: Int? = null,
    val isProsesBayar: Boolean = false,
    val previewStruk: String? = null,
    val sedangMencetak: Boolean = false,
    val barcodeBelumTerdaftar: String? = null,
    val kategoriList: List<CategoryEntity> = emptyList(),
    val kategoriTerpilihNama: String = "Semua",
    val kategoriTerpilihId: Long? = null,
    val store: StoreEntity? = null,
    val namaPelanggan: String = "",
    val catatanTransaksi: String = "",
    val printerTersedia: Boolean = false,
    val cashierName: String = "Kasir",
    val statusOnline: Boolean = true
) {
    val subtotal: Double get() = keranjang.sumOf { it.harga * it.qty }
    val total: Double get() = (subtotal - diskonTotal).coerceAtLeast(0.0)
    val totalItemCount: Int get() = keranjang.sumOf { it.qty }
}

@HiltViewModel
class KasirViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val transactionRepository: TransactionRepository,
    private val storeRepository: StoreRepository,
    private val printerRepository: PrinterRepository,
    private val bluetoothPrinterManager: BluetoothPrinterManager,
    private val categoryDao: CategoryDao,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val keranjangFlow = MutableStateFlow<List<KeranjangItem>>(emptyList())
    private val diskonFlow = MutableStateFlow(0.0)
    private val errorFlow = MutableStateFlow<String?>(null)
    private val transaksiBerhasilFlow = MutableStateFlow<Long?>(null)
    private val nomorAntrianBerhasilFlow = MutableStateFlow<Int?>(null)
    private val prosesBayarFlow = MutableStateFlow(false)
    private val previewStrukFlow = MutableStateFlow<String?>(null)
    private val sedangMencetakFlow = MutableStateFlow(false)
    private val barcodeBelumTerdaftarFlow = MutableStateFlow<String?>(null)
    private val kategoriTerpilihFlow = MutableStateFlow<Pair<Long?, String>>(null to "Semua")
    private val namaPelangganFlow = MutableStateFlow("")
    private val catatanTransaksiFlow = MutableStateFlow("")

    private val categoriesFlow: Flow<List<CategoryEntity>> = categoryDao.observeAll()
    private val storeFlow: Flow<StoreEntity?> = storeRepository.observeStore()
    private val printerAdaFlow: Flow<Boolean> = printerRepository.observeAll().map { it.isNotEmpty() }

    val uiState: StateFlow<KasirUiState> = combine(
        listOf(
            queryFlow.flatMapLatest { q -> if (q.isBlank()) productRepository.observeActive() else productRepository.search(q) },
            keranjangFlow,
            diskonFlow,
            errorFlow,
            transaksiBerhasilFlow,
            prosesBayarFlow,
            previewStrukFlow,
            sedangMencetakFlow,
            nomorAntrianBerhasilFlow,
            barcodeBelumTerdaftarFlow,
            kategoriTerpilihFlow,
            categoriesFlow,
            storeFlow,
            namaPelangganFlow,
            catatanTransaksiFlow,
            printerAdaFlow,
            sessionManager.currentUser
        )
    ) { flows ->
        @Suppress("UNCHECKED_CAST")
        val rawProduk = flows[0] as List<ProductEntity>
        @Suppress("UNCHECKED_CAST")
        val keranjang = flows[1] as List<KeranjangItem>
        val diskonTotal = flows[2] as Double
        val errorPesan = flows[3] as String?
        val transaksiBerhasilId = flows[4] as Long?
        val isProsesBayar = flows[5] as Boolean
        val previewStruk = flows[6] as String?
        val sedangMencetak = flows[7] as Boolean
        val nomorAntrianBerhasil = flows[8] as Int?
        val barcodeBelumTerdaftar = flows[9] as String?
        @Suppress("UNCHECKED_CAST")
        val (kategoriId, kategoriNama) = flows[10] as Pair<Long?, String>
        @Suppress("UNCHECKED_CAST")
        val kategoriList = flows[11] as List<CategoryEntity>
        val storeData = flows[12] as StoreEntity?
        val namaPelanggan = flows[13] as String
        val catatanTransaksi = flows[14] as String
        val printerTersedia = flows[15] as Boolean
        val user = flows[16] as UserEntity?

        val filteredProduk = if (kategoriNama == "Semua" || kategoriNama.isBlank()) {
            rawProduk
        } else if (kategoriId != null) {
            rawProduk.filter { it.categoryId == kategoriId }
        } else {
            val matchingCat = kategoriList.firstOrNull { it.nama.equals(kategoriNama, ignoreCase = true) }
            if (matchingCat != null) {
                rawProduk.filter { it.categoryId == matchingCat.id }
            } else {
                rawProduk.filter { it.nama.contains(kategoriNama, ignoreCase = true) }
            }
        }

        KasirUiState(
            query = queryFlow.value,
            produk = filteredProduk,
            keranjang = keranjang,
            diskonTotal = diskonTotal,
            errorPesan = errorPesan,
            transaksiBerhasilId = transaksiBerhasilId,
            nomorAntrianBerhasil = nomorAntrianBerhasil,
            isProsesBayar = isProsesBayar,
            previewStruk = previewStruk,
            sedangMencetak = sedangMencetak,
            barcodeBelumTerdaftar = barcodeBelumTerdaftar,
            kategoriList = kategoriList,
            kategoriTerpilihNama = kategoriNama,
            kategoriTerpilihId = kategoriId,
            store = storeData,
            namaPelanggan = namaPelanggan,
            catatanTransaksi = catatanTransaksi,
            printerTersedia = printerTersedia,
            cashierName = user?.nama ?: "Kasir"
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), KasirUiState())

    fun onQueryChange(q: String) { queryFlow.value = q }

    fun pilihKategori(id: Long?, nama: String) {
        kategoriTerpilihFlow.value = id to nama
    }

    fun setNamaPelanggan(nama: String) {
        namaPelangganFlow.value = nama
    }

    fun setCatatan(catatan: String) {
        catatanTransaksiFlow.value = catatan
    }

    fun hapusItemKeranjang(productId: Long) {
        val current = keranjangFlow.value.toMutableList()
        current.removeAll { it.productId == productId }
        keranjangFlow.value = current
    }

    fun kosongkanKeranjang() {
        keranjangFlow.value = emptyList()
        diskonFlow.value = 0.0
        namaPelangganFlow.value = ""
        catatanTransaksiFlow.value = ""
    }

    fun tambahKeKeranjang(product: ProductEntity) {
        if (product.stok <= 0) {
            errorFlow.value = AppError.StokTidakCukup.pesan
            return
        }
        val current = keranjangFlow.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.productId == product.id }
        if (existingIndex >= 0) {
            val existing = current[existingIndex]
            if (existing.qty + 1 > product.stok) {
                errorFlow.value = AppError.StokTidakCukup.pesan
                return
            }
            current[existingIndex] = existing.copy(qty = existing.qty + 1)
        } else {
            current.add(
                KeranjangItem(
                    productId = product.id,
                    nama = product.nama,
                    harga = product.hargaJual,
                    qty = 1,
                    stokTersedia = product.stok
                )
            )
        }
        keranjangFlow.value = current
    }

    /** Dipanggil setelah scan barcode berhasil menemukan produk (Phase 2). */
    fun tambahDariBarcode(barcode: String) {
        viewModelScope.launch {
            val product = productRepository.cariByBarcode(barcode)
            if (product == null) {
                // Barcode belum ada di data produk -> layar Kasir menawarkan "Tambah Produk Baru"
                barcodeBelumTerdaftarFlow.value = barcode
            } else {
                tambahKeKeranjang(product)
            }
        }
    }

    fun tutupBarcodeBelumTerdaftar() { barcodeBelumTerdaftarFlow.value = null }

    /** Simpan produk baru dari dialog "Produk belum terdaftar" (barcode hasil scan sudah terisi). */
    fun simpanProdukBaru(product: ProductEntity) {
        viewModelScope.launch {
            barcodeBelumTerdaftarFlow.value = null
            when (val result = productRepository.simpan(product)) {
                is Result.Failure -> errorFlow.value = result.error.pesan
                is Result.Success -> Unit
            }
        }
    }

    fun ubahQty(productId: Long, qtyBaru: Int) {
        val current = keranjangFlow.value.toMutableList()
        val idx = current.indexOfFirst { it.productId == productId }
        if (idx < 0) return
        val item = current[idx]
        if (qtyBaru <= 0) {
            current.removeAt(idx)
        } else if (qtyBaru > item.stokTersedia) {
            errorFlow.value = AppError.StokTidakCukup.pesan
            return
        } else {
            current[idx] = item.copy(qty = qtyBaru)
        }
        keranjangFlow.value = current
    }

    fun setDiskonTotal(nilai: Double) { diskonFlow.value = nilai }

    fun bayar(userId: Long, metode: MetodePembayaran, jumlahDiterima: Double, namaPembeli: String? = null, catatanMetode: String? = null) {
        viewModelScope.launch {
            prosesBayarFlow.value = true
            val finalNamaPembeli = namaPembeli?.ifBlank { null } ?: namaPelangganFlow.value.ifBlank { null }
            val finalCatatan = catatanMetode?.ifBlank { null } ?: catatanTransaksiFlow.value.ifBlank { null }

            val result = transactionRepository.simpanTransaksiKasir(
                userId = userId,
                items = keranjangFlow.value,
                diskonTotal = diskonFlow.value,
                metode = metode,
                jumlahDiterima = jumlahDiterima,
                namaPembeli = finalNamaPembeli,
                catatanMetode = finalCatatan
            )
            prosesBayarFlow.value = false
            when (result) {
                is Result.Success -> {
                    transaksiBerhasilFlow.value = result.data
                    val (transaksi, _, _) = transactionRepository.getDetail(result.data)
                    nomorAntrianBerhasilFlow.value = transaksi?.nomorAntrian
                    keranjangFlow.value = emptyList()
                    diskonFlow.value = 0.0
                    namaPelangganFlow.value = ""
                    catatanTransaksiFlow.value = ""
                }
                is Result.Failure -> {
                    errorFlow.value = result.error.pesan
                }
            }
        }
    }

    fun mulaiTransaksiBaru() {
        transaksiBerhasilFlow.value = null
        nomorAntrianBerhasilFlow.value = null
        keranjangFlow.value = emptyList()
        diskonFlow.value = 0.0
        namaPelangganFlow.value = ""
        catatanTransaksiFlow.value = ""
    }

    /**
     * Tampilkan PREVIEW struk dulu sebelum benar-benar mencetak.
     */
    fun tampilkanPreviewStruk(transactionId: Long) {
        viewModelScope.launch {
            val store = storeRepository.getOrCreateDefault()
            val (transaksi, items, payment) = transactionRepository.getDetail(transactionId)
            if (transaksi == null) {
                errorFlow.value = AppError.TransaksiGagalDisimpan.pesan
                return@launch
            }
            previewStrukFlow.value = StrukFormatter.buatStrukPreviewText(store, transaksi, items, payment)
        }
    }

    fun tutupPreviewStruk() { previewStrukFlow.value = null }

    /** Dipanggil dari dialog preview saat pengguna menekan "Cetak Sekarang" (poin 8 & 9). */
    fun cetakDariPreview(transactionId: Long) {
        viewModelScope.launch {
            sedangMencetakFlow.value = true
            val printerDefault = printerRepository.getDefault()
            if (printerDefault == null) {
                sedangMencetakFlow.value = false
                previewStrukFlow.value = null
                errorFlow.value = "Belum ada printer default. Atur di menu Pengaturan Printer."
                return@launch
            }
            val store = storeRepository.getOrCreateDefault()
            val (transaksi, items, payment) = transactionRepository.getDetail(transactionId)
            if (transaksi == null) {
                sedangMencetakFlow.value = false
                previewStrukFlow.value = null
                errorFlow.value = AppError.TransaksiGagalDisimpan.pesan
                return@launch
            }
            val strukBytes = StrukFormatter.buatStruk(store, transaksi, items, payment)
            val result = bluetoothPrinterManager.cetak(printerDefault.macAddress, strukBytes)
            sedangMencetakFlow.value = false
            previewStrukFlow.value = null
            if (result is Result.Failure) errorFlow.value = result.error.pesan
        }
    }

    fun clearError() { errorFlow.value = null }
}
