package com.nada.kasir.feature.kasir

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nada.kasir.core.data.local.entity.CategoryEntity
import com.nada.kasir.core.data.local.entity.MetodePembayaran
import com.nada.kasir.core.data.local.entity.ProductEntity
import com.nada.kasir.core.data.repository.CategoryRepository
import com.nada.kasir.core.data.repository.PrinterRepository
import com.nada.kasir.core.data.repository.ProductRepository
import com.nada.kasir.core.data.repository.StoreRepository
import com.nada.kasir.core.data.repository.TransactionRepository
import com.nada.kasir.core.domain.model.KeranjangItem
import com.nada.kasir.core.paket.PaketAplikasi
import com.nada.kasir.core.paket.PaketRepository
import com.nada.kasir.core.printer.BluetoothPrinterManager
import com.nada.kasir.core.printer.StrukFormatter
import com.nada.kasir.core.util.AppError
import com.nada.kasir.core.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Ambang jumlah transaksi SELESAI dalam 1 hari yang memicu banner saran upgrade (bukan blokir -
 * lihat diskusi: hard limit transaksi harian di paket Basic sengaja TIDAK dipakai karena
 * berisiko menghentikan toko yang justru lagi ramai, dan gampang diakali cuma dengan memundurkan
 * tanggal HP karena aplikasi ini offline total tanpa server). Banner ini murni ajakan lembut,
 * kasir tetap bisa lanjut transaksi seperti biasa walau banner tidak ditutup/diabaikan.
 */
private const val AMBANG_TRANSAKSI_HARIAN_UNTUK_SARAN = 50

data class KasirUiState(
    val query: String = "",
    val produk: List<ProductEntity> = emptyList(),
    val kategoriList: List<CategoryEntity> = emptyList(),
    val kategoriTerpilihId: Long? = null,
    val store: com.nada.kasir.core.data.local.entity.StoreEntity? = null,
    val keranjang: List<KeranjangItem> = emptyList(),
    val diskonTotal: Double = 0.0,
    val errorPesan: String? = null,
    val transaksiBerhasilId: Long? = null,
    val nomorAntrianBerhasil: Int? = null,
    val isProsesBayar: Boolean = false,
    val previewStruk: String? = null,
    val sedangMencetak: Boolean = false,
    val barcodeBelumTerdaftar: String? = null,
    val jumlahTransaksiHariIni: Int = 0,
    val tampilkanSaranUpgrade: Boolean = false
) {
    val subtotal: Double get() = keranjang.sumOf { it.harga * it.qty }
    val total: Double get() = subtotal - diskonTotal
}

@HiltViewModel
class KasirViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val paketRepository: PaketRepository,
    private val storeRepository: StoreRepository,
    private val printerRepository: PrinterRepository,
    private val bluetoothPrinterManager: BluetoothPrinterManager
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val kategoriTerpilihFlow = MutableStateFlow<Long?>(null)
    private val keranjangFlow = MutableStateFlow<List<KeranjangItem>>(emptyList())
    private val diskonFlow = MutableStateFlow(0.0)
    private val errorFlow = MutableStateFlow<String?>(null)
    private val transaksiBerhasilFlow = MutableStateFlow<Long?>(null)
    private val nomorAntrianBerhasilFlow = MutableStateFlow<Int?>(null)
    private val prosesBayarFlow = MutableStateFlow(false)
    private val previewStrukFlow = MutableStateFlow<String?>(null)
    private val sedangMencetakFlow = MutableStateFlow(false)
    private val barcodeBelumTerdaftarFlow = MutableStateFlow<String?>(null)

    // Query teks & kategori terpilih digabung dulu (poin desain mockup: chip kategori & search
    // bar aktif bersamaan) baru di-flatMapLatest ke satu query DAO gabungan - supaya ganti
    // kategori/ketik cari sama-sama langsung refresh grid produk, tidak saling menimpa.
    private val produkTerfilterFlow: Flow<List<ProductEntity>> =
        combine(queryFlow, kategoriTerpilihFlow) { query, categoryId -> query.trim() to categoryId }
            .flatMapLatest { (query, categoryId) -> productRepository.observeFiltered(categoryId, query) }

    val uiState: StateFlow<KasirUiState> = combine(
        produkTerfilterFlow,
        keranjangFlow,
        diskonFlow,
        errorFlow,
        transaksiBerhasilFlow,
        prosesBayarFlow,
        previewStrukFlow,
        sedangMencetakFlow,
        nomorAntrianBerhasilFlow,
        barcodeBelumTerdaftarFlow,
        categoryRepository.observeAll(),
        kategoriTerpilihFlow,
        storeRepository.observeStore(),
        transactionRepository.observeJumlahTransaksiHariIni(awalHariIniMillis(), akhirHariIniMillis()),
        paketRepository.observePaketAktif(),
        paketRepository.observeSaranUpgradeDismissedTanggal()
    ) { flows ->
        @Suppress("UNCHECKED_CAST")
        val jumlahTransaksiHariIni = flows[13] as Int
        val paketAktif = flows[14] as PaketAplikasi
        val dismissedTanggal = flows[15] as String?
        KasirUiState(
            produk = flows[0] as List<ProductEntity>,
            keranjang = flows[1] as List<KeranjangItem>,
            diskonTotal = flows[2] as Double,
            errorPesan = flows[3] as String?,
            transaksiBerhasilId = flows[4] as Long?,
            isProsesBayar = flows[5] as Boolean,
            previewStruk = flows[6] as String?,
            sedangMencetak = flows[7] as Boolean,
            nomorAntrianBerhasil = flows[8] as Int?,
            barcodeBelumTerdaftar = flows[9] as String?,
            kategoriList = flows[10] as List<CategoryEntity>,
            kategoriTerpilihId = flows[11] as Long?,
            store = flows[12] as com.nada.kasir.core.data.local.entity.StoreEntity?,
            jumlahTransaksiHariIni = jumlahTransaksiHariIni,
            // Cuma tampil kalau: masih Basic (Custom/Pro tidak perlu diajak upgrade lagi),
            // sudah lewat ambang harian, DAN belum ditutup kasir HARI ini juga.
            tampilkanSaranUpgrade = paketAktif == PaketAplikasi.BASIC &&
                jumlahTransaksiHariIni >= AMBANG_TRANSAKSI_HARIAN_UNTUK_SARAN &&
                dismissedTanggal != tanggalHariIniString()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), KasirUiState())

    private fun tanggalHariIniString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())

    private fun awalHariIniMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun akhirHariIniMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    /** Dipanggil saat kasir menutup (×) banner saran upgrade - tidak muncul lagi HARI ini saja. */
    fun dismissSaranUpgrade() {
        viewModelScope.launch { paketRepository.dismissSaranUpgradeHariIni(tanggalHariIniString()) }
    }

    fun onQueryChange(q: String) { queryFlow.value = q }

    /**
     * Dipanggil saat kasir menyentuh chip kategori. categoryId null = chip "Semua".
     * categoryName ikut dikirim UI (buat label tampilan di sisi UI) - tidak dipakai
     * di ViewModel karena nama kategori sudah tersedia dari [kategoriList] via id-nya.
     */
    fun pilihKategori(categoryId: Long?, categoryName: String) { kategoriTerpilihFlow.value = categoryId }

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
            val result = transactionRepository.simpanTransaksiKasir(
                userId = userId,
                items = keranjangFlow.value,
                diskonTotal = diskonFlow.value,
                metode = metode,
                jumlahDiterima = jumlahDiterima,
                namaPembeli = namaPembeli,
                catatanMetode = catatanMetode
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

    /**
     * Dipakai tombol "Bagikan" di TransaksiBerhasilDialog - bangun teks struk yang sama
     * persis dengan preview cetak (StrukFormatter.buatStrukPreviewText), lalu dikembalikan
     * lewat callback supaya UI bisa langsung oper ke FileShareHelper.bagikanTeks tanpa
     * perlu menampilkan dialog preview dulu.
     */
    fun buatTeksStruk(transactionId: Long, onSelesai: (String) -> Unit) {
        viewModelScope.launch {
            val store = storeRepository.getOrCreateDefault()
            val (transaksi, items, payment) = transactionRepository.getDetail(transactionId)
            if (transaksi == null) {
                errorFlow.value = AppError.TransaksiGagalDisimpan.pesan
                return@launch
            }
            onSelesai(StrukFormatter.buatStrukPreviewText(store, transaksi, items, payment))
        }
    }

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
