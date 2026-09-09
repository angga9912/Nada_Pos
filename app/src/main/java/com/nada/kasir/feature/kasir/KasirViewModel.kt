package com.nada.kasir.feature.kasir

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nada.kasir.core.data.local.entity.MetodePembayaran
import com.nada.kasir.core.data.local.entity.ProductEntity
import com.nada.kasir.core.data.repository.PrinterRepository
import com.nada.kasir.core.data.repository.ProductRepository
import com.nada.kasir.core.data.repository.StoreRepository
import com.nada.kasir.core.data.repository.TransactionRepository
import com.nada.kasir.core.domain.model.KeranjangItem
import com.nada.kasir.core.printer.BluetoothPrinterManager
import com.nada.kasir.core.printer.StrukFormatter
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
    val sedangMencetak: Boolean = false
) {
    val subtotal: Double get() = keranjang.sumOf { it.harga * it.qty }
    val total: Double get() = subtotal - diskonTotal
}

@HiltViewModel
class KasirViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val transactionRepository: TransactionRepository,
    private val storeRepository: StoreRepository,
    private val printerRepository: PrinterRepository,
    private val bluetoothPrinterManager: BluetoothPrinterManager
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

    val uiState: StateFlow<KasirUiState> = combine(
        queryFlow.flatMapLatest { q -> if (q.isBlank()) productRepository.observeActive() else productRepository.search(q) },
        keranjangFlow,
        diskonFlow,
        errorFlow,
        transaksiBerhasilFlow,
        prosesBayarFlow,
        previewStrukFlow,
        sedangMencetakFlow,
        nomorAntrianBerhasilFlow
    ) { flows ->
        @Suppress("UNCHECKED_CAST")
        KasirUiState(
            produk = flows[0] as List<ProductEntity>,
            keranjang = flows[1] as List<KeranjangItem>,
            diskonTotal = flows[2] as Double,
            errorPesan = flows[3] as String?,
            transaksiBerhasilId = flows[4] as Long?,
            isProsesBayar = flows[5] as Boolean,
            previewStruk = flows[6] as String?,
            sedangMencetak = flows[7] as Boolean,
            nomorAntrianBerhasil = flows[8] as Int?
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), KasirUiState())

    fun onQueryChange(q: String) { queryFlow.value = q }

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
                errorFlow.value = "Produk belum terdaftar."
            } else {
                tambahKeKeranjang(product)
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

    fun bayar(userId: Long, metode: MetodePembayaran, jumlahDiterima: Double, namaPembeli: String? = null) {
        viewModelScope.launch {
            prosesBayarFlow.value = true
            val result = transactionRepository.simpanTransaksiKasir(
                userId = userId,
                items = keranjangFlow.value,
                diskonTotal = diskonFlow.value,
                metode = metode,
                jumlahDiterima = jumlahDiterima,
                namaPembeli = namaPembeli
            )
            prosesBayarFlow.value = false
            when (result) {
                is Result.Success -> {
                    transaksiBerhasilFlow.value = result.data
                    val (transaksi, _, _) = transactionRepository.getDetail(result.data)
                    nomorAntrianBerhasilFlow.value = transaksi?.nomorAntrian
                    keranjangFlow.value = emptyList()
                    diskonFlow.value = 0.0
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
    }

    /**
     * Tampilkan PREVIEW struk dulu sebelum benar-benar mencetak. Tidak menyentuh
     * printer sama sekali di langkah ini - murni menyusun teks dari data toko & transaksi.
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
