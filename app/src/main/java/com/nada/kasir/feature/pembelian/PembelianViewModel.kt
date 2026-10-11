package com.nada.kasir.feature.pembelian

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nada.kasir.core.data.local.entity.ProductEntity
import com.nada.kasir.core.data.local.entity.PurchaseEntity
import com.nada.kasir.core.data.local.entity.PurchaseItemEntity
import com.nada.kasir.core.data.local.entity.PurchaseStatus
import com.nada.kasir.core.data.local.entity.SupplierEntity
import com.nada.kasir.core.data.repository.PembelianInput
import com.nada.kasir.core.data.repository.ProductRepository
import com.nada.kasir.core.data.repository.PurchaseRepository
import com.nada.kasir.core.data.repository.SupplierRepository
import com.nada.kasir.core.domain.logic.HutangPiutangLogic
import com.nada.kasir.core.domain.logic.PembelianLogic
import com.nada.kasir.core.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FilterPembelian(val label: String) {
    SEMUA("Semua"), MENUNGGU("Menunggu"), DITERIMA("Diterima"), BATAL("Batal");

    fun cocok(status: PurchaseStatus): Boolean = when (this) {
        SEMUA -> true
        MENUNGGU -> status == PurchaseStatus.PENDING
        DITERIMA -> status == PurchaseStatus.RECEIVED
        BATAL -> status == PurchaseStatus.CANCELLED
    }
}

/** Satu baris barang di formulir. Angka disimpan sebagai teks supaya pengetikan tidak terpotong. */
data class ItemDraft(
    val productId: Long,
    val nama: String,
    val stokSaatIni: Int,
    val qtyText: String = "1",
    val hargaText: String = "",
    val diskonText: String = ""
) {
    fun keInput() = PembelianLogic.ItemInput(
        productId = productId,
        qty = qtyText.toIntOrNull() ?: 0,
        hargaBeli = hargaText.toDoubleOrNull() ?: 0.0,
        diskon = diskonText.toDoubleOrNull() ?: 0.0
    )
}

data class FormPembelian(
    val supplierId: Long? = null,
    val items: List<ItemDraft> = emptyList(),
    val diskonSupplierText: String = "",
    val bayarText: String = "",
    val jatuhTempoHari: Int? = null,
    val terimaSekarang: Boolean = true,
    val perbaruiHargaBeli: Boolean = true,
    val keterangan: String = ""
) {
    val diskonSupplier: Double get() = diskonSupplierText.toDoubleOrNull() ?: 0.0
    val bayarSekarang: Double get() = bayarText.toDoubleOrNull() ?: 0.0
    fun itemInputs() = items.map { it.keInput() }
    fun ringkasan() = PembelianLogic.hitungRingkasan(itemInputs(), diskonSupplier)
    fun sisaHutang() = PembelianLogic.sisaHutang(ringkasan().total, bayarSekarang)
    /** Pesan error untuk ditampilkan; null bila formulir siap disimpan. */
    fun pesanKesalahan(): String? = when {
        supplierId == null -> "Pilih supplier dulu."
        else -> PembelianLogic.validasiPembelian(itemInputs(), diskonSupplier, bayarSekarang)
    }
}

data class BarisPembelian(val pembelian: PurchaseEntity, val namaSupplier: String)

data class PembelianUiState(
    val filter: FilterPembelian = FilterPembelian.SEMUA,
    val daftar: List<BarisPembelian> = emptyList(),
    val supplierAktif: List<SupplierEntity> = emptyList(),
    val form: FormPembelian? = null,
    val kataCari: String = "",
    val hasilCariProduk: List<ProductEntity> = emptyList(),
    val detail: BarisPembelian? = null,
    val itemDetail: List<PurchaseItemEntity> = emptyList(),
    val pesan: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PembelianViewModel @Inject constructor(
    private val repository: PurchaseRepository,
    private val supplierRepository: SupplierRepository,
    private val productRepository: ProductRepository
) : ViewModel() {

    private val filterFlow = MutableStateFlow(FilterPembelian.SEMUA)
    private val formFlow = MutableStateFlow<FormPembelian?>(null)
    private val kataCariFlow = MutableStateFlow("")
    private val idDetailFlow = MutableStateFlow<Long?>(null)
    private val pesanFlow = MutableStateFlow<String?>(null)

    private val produkFlow = kataCariFlow.flatMapLatest { kata ->
        if (kata.isBlank()) productRepository.observeActive() else productRepository.search(kata.trim())
    }
    private val itemDetailFlow = idDetailFlow.flatMapLatest { id ->
        if (id == null) flowOf(emptyList<PurchaseItemEntity>()) else repository.observeItems(id)
    }

    val uiState: StateFlow<PembelianUiState> = combine(
        repository.observeAll(), supplierRepository.observeAll(), filterFlow, formFlow, kataCariFlow,
        produkFlow, idDetailFlow, itemDetailFlow, pesanFlow
    ) { v ->
        @Suppress("UNCHECKED_CAST")
        val pembelian = v[0] as List<PurchaseEntity>
        @Suppress("UNCHECKED_CAST")
        val supplier = v[1] as List<SupplierEntity>
        val filter = v[2] as FilterPembelian
        val form = v[3] as FormPembelian?
        val kata = v[4] as String
        @Suppress("UNCHECKED_CAST")
        val produk = v[5] as List<ProductEntity>
        val idDetail = v[6] as Long?
        @Suppress("UNCHECKED_CAST")
        val itemDetail = v[7] as List<PurchaseItemEntity>
        val pesan = v[8] as String?

        val namaSupplier = supplier.associate { it.id to it.nama }
        val baris = pembelian.map { BarisPembelian(it, namaSupplier[it.supplierId] ?: "Supplier dihapus") }
        PembelianUiState(
            filter = filter,
            daftar = baris.filter { filter.cocok(it.pembelian.status) },
            supplierAktif = supplier.filter { it.aktif },
            form = form,
            kataCari = kata,
            hasilCariProduk = produk,
            detail = idDetail?.let { id -> baris.firstOrNull { it.pembelian.id == id } },
            itemDetail = itemDetail,
            pesan = pesan
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PembelianUiState())

    fun pilihFilter(filter: FilterPembelian) { filterFlow.value = filter }
    fun pesanDibaca() { pesanFlow.value = null }
    fun cariProduk(kata: String) { kataCariFlow.value = kata }
    fun bukaDetail(id: Long) { idDetailFlow.value = id }
    fun tutupDetail() { idDetailFlow.value = null }

    // ---- Formulir ----

    fun bukaForm() { formFlow.value = FormPembelian(); kataCariFlow.value = "" }
    fun tutupForm() { formFlow.value = null }
    fun ubahForm(ubah: (FormPembelian) -> FormPembelian) { formFlow.value = formFlow.value?.let(ubah) }

    fun tambahProduk(produk: ProductEntity) = ubahForm { form ->
        val ada = form.items.any { it.productId == produk.id }
        if (ada) {
            form.copy(items = form.items.map {
                if (it.productId == produk.id) it.copy(qtyText = ((it.qtyText.toIntOrNull() ?: 0) + 1).toString()) else it
            })
        } else {
            form.copy(items = form.items + ItemDraft(
                productId = produk.id, nama = produk.nama, stokSaatIni = produk.stok,
                hargaText = produk.hargaBeli.toLong().toString()
            ))
        }
    }

    fun ubahItem(productId: Long, ubah: (ItemDraft) -> ItemDraft) = ubahForm { form ->
        form.copy(items = form.items.map { if (it.productId == productId) ubah(it) else it })
    }

    fun hapusItem(productId: Long) = ubahForm { form -> form.copy(items = form.items.filterNot { it.productId == productId }) }

    fun simpan() {
        val form = formFlow.value ?: return
        form.pesanKesalahan()?.let { pesanFlow.value = it; return }
        val supplierId = form.supplierId ?: return
        viewModelScope.launch {
            val jatuhTempo = HutangPiutangLogic.jatuhTempoDariHari(form.jatuhTempoHari, System.currentTimeMillis())
            val input = PembelianInput(
                supplierId = supplierId,
                items = form.itemInputs(),
                diskonSupplier = form.diskonSupplier,
                bayarSekarang = form.bayarSekarang,
                jatuhTempo = if (form.sisaHutang() > 0.0) jatuhTempo else null,
                terimaSekarang = form.terimaSekarang,
                perbaruiHargaBeli = form.perbaruiHargaBeli,
                keterangan = form.keterangan.ifBlank { null }
            )
            when (val hasil = repository.simpanPembelian(input)) {
                is Result.Success -> {
                    formFlow.value = null
                    idDetailFlow.value = hasil.data
                    pesanFlow.value = "Pembelian disimpan."
                }
                is Result.Failure -> pesanFlow.value = hasil.error.pesan
            }
        }
    }

    // ---- Detail ----

    fun terimaBarang(id: Long) {
        viewModelScope.launch {
            when (val hasil = repository.terimaBarang(id, perbaruiHargaBeli = true)) {
                is Result.Success -> pesanFlow.value = "Barang diterima, stok bertambah."
                is Result.Failure -> pesanFlow.value = hasil.error.pesan
            }
        }
    }

    fun batalkan(id: Long) {
        viewModelScope.launch {
            when (val hasil = repository.batalkan(id)) {
                is Result.Success -> pesanFlow.value = "Pembelian dibatalkan."
                is Result.Failure -> pesanFlow.value = hasil.error.pesan
            }
        }
    }
}
