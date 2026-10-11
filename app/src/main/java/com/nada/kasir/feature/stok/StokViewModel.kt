package com.nada.kasir.feature.stok

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nada.kasir.core.data.local.entity.ProductEntity
import com.nada.kasir.core.data.local.entity.StockMovementEntity
import com.nada.kasir.core.data.repository.ProductRepository
import com.nada.kasir.core.data.repository.StockRepository
import com.nada.kasir.core.domain.logic.StokLogic
import com.nada.kasir.core.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FilterMutasi(val label: String) {
    MANUAL("Manual"), SEMUA("Semua"), MASUK("Masuk"), KELUAR("Keluar");

    fun cocok(m: StockMovementEntity): Boolean = when (this) {
        MANUAL -> StokLogic.mutasiManual(m)
        SEMUA -> true
        MASUK -> StokLogic.menambah(m.tipe)
        KELUAR -> !StokLogic.menambah(m.tipe)
    }
}

enum class JenisAksi(val judul: String) {
    MASUK("Stok Masuk"), KELUAR("Stok Keluar"), OPNAME("Opname Stok")
}

/** Formulir satu aksi stok. Angka disimpan sebagai teks supaya pengetikan tidak terpotong. */
data class FormStok(
    val jenis: JenisAksi,
    val produk: ProductEntity? = null,
    val qtyText: String = "",
    val hargaBeliText: String = "",
    val perbaruiHargaBeli: Boolean = false,
    val alasan: StokLogic.AlasanKeluar = StokLogic.AlasanKeluar.RUSAK,
    val catatan: String = ""
) {
    val qty: Int get() = qtyText.toIntOrNull() ?: 0
    val hargaBeli: Double? get() = hargaBeliText.toDoubleOrNull()

    /** Pesan error untuk ditampilkan; null bila formulir siap disimpan. */
    fun pesanKesalahan(): String? {
        val p = produk ?: return "Pilih produk dulu."
        return when (jenis) {
            JenisAksi.MASUK -> StokLogic.validasiQty(qty)
                ?: if (hargaBeliText.isNotBlank() && hargaBeli == null) "Harga beli tidak valid."
                else StokLogic.validasiHargaBeli(hargaBeli)
            JenisAksi.KELUAR -> StokLogic.validasiStokKeluar(p.stok, qty, alasan, catatan)
            JenisAksi.OPNAME ->
                if (qtyText.isBlank()) "Isi stok hasil hitung fisik."
                else StokLogic.validasiOpname(p.stok, qty)
        }
    }
}

data class BarisMutasi(val mutasi: StockMovementEntity, val namaProduk: String)

data class StokUiState(
    val filter: FilterMutasi = FilterMutasi.MANUAL,
    val riwayat: List<BarisMutasi> = emptyList(),
    val terpotong: Boolean = false,
    val form: FormStok? = null,
    val kataCari: String = "",
    val hasilCariProduk: List<ProductEntity> = emptyList(),
    val pesan: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StokViewModel @Inject constructor(
    private val stockRepository: StockRepository,
    private val productRepository: ProductRepository
) : ViewModel() {

    private val filterFlow = MutableStateFlow(FilterMutasi.MANUAL)
    private val formFlow = MutableStateFlow<FormStok?>(null)
    private val kataCariFlow = MutableStateFlow("")
    private val pesanFlow = MutableStateFlow<String?>(null)

    private val produkFlow = kataCariFlow.flatMapLatest { kata ->
        if (kata.isBlank()) productRepository.observeActive() else productRepository.search(kata.trim())
    }

    val uiState: StateFlow<StokUiState> = combine(
        stockRepository.observeRiwayat(), productRepository.observeNamaSemua(), filterFlow, formFlow,
        kataCariFlow, produkFlow, pesanFlow
    ) { v ->
        @Suppress("UNCHECKED_CAST")
        val mutasi = v[0] as List<StockMovementEntity>
        @Suppress("UNCHECKED_CAST")
        val nama = v[1] as List<com.nada.kasir.core.data.local.dao.NamaProduk>
        val filter = v[2] as FilterMutasi
        val form = v[3] as FormStok?
        val kata = v[4] as String
        @Suppress("UNCHECKED_CAST")
        val produk = v[5] as List<ProductEntity>
        val pesan = v[6] as String?

        val namaPerId = nama.associate { it.id to it.nama }
        val cocok = mutasi.filter { filter.cocok(it) }
        StokUiState(
            filter = filter,
            riwayat = cocok.take(BATAS_BARIS).map { BarisMutasi(it, namaPerId[it.productId] ?: "Produk dihapus") },
            terpotong = cocok.size > BATAS_BARIS,
            form = form,
            kataCari = kata,
            hasilCariProduk = produk,
            pesan = pesan
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StokUiState())

    fun pilihFilter(filter: FilterMutasi) { filterFlow.value = filter }
    fun pesanDibaca() { pesanFlow.value = null }
    fun cariProduk(kata: String) { kataCariFlow.value = kata }

    fun bukaForm(jenis: JenisAksi) { formFlow.value = FormStok(jenis); kataCariFlow.value = "" }
    fun tutupForm() { formFlow.value = null }
    fun ubahForm(ubah: (FormStok) -> FormStok) { formFlow.value = formFlow.value?.let(ubah) }
    fun pilihProduk(produk: ProductEntity) = ubahForm { it.copy(produk = produk) }

    fun simpan() {
        val form = formFlow.value ?: return
        form.pesanKesalahan()?.let { pesanFlow.value = it; return }
        val produk = form.produk ?: return
        viewModelScope.launch {
            val catatan = form.catatan.trim().ifBlank { null }
            val berhasil: String? = when (form.jenis) {
                JenisAksi.MASUK -> stockRepository.stokMasuk(
                    productId = produk.id, qty = form.qty, hargaBeli = form.hargaBeli, supplierId = null,
                    keterangan = StokLogic.keteranganMasuk(catatan), perbaruiHargaBeli = form.perbaruiHargaBeli
                ).hasil("Stok masuk dicatat: ${produk.nama} +${form.qty}.")
                JenisAksi.KELUAR -> stockRepository.stokKeluar(
                    productId = produk.id, qty = form.qty,
                    keterangan = StokLogic.keteranganKeluar(form.alasan, catatan)
                ).hasil("Stok keluar dicatat: ${produk.nama} −${form.qty}.")
                JenisAksi.OPNAME -> when (val r = stockRepository.opname(
                    productId = produk.id, stokFisik = form.qty, keterangan = StokLogic.keteranganOpname(catatan)
                )) {
                    is Result.Success -> {
                        val teks = if (r.data > 0) "+${r.data}" else "−${-r.data}"
                        pesanFlow.value = "Stok ${produk.nama} disesuaikan ke ${form.qty} ($teks)."
                        "ok"
                    }
                    is Result.Failure -> { pesanFlow.value = r.error.pesan; null }
                }
            }
            if (berhasil != null) formFlow.value = null
        }
    }

    /** Menyimpan pesan sukses/gagal ke [pesanFlow]; mengembalikan non-null bila berhasil. */
    private fun Result<Unit>.hasil(pesanSukses: String): String? = when (this) {
        is Result.Success -> { pesanFlow.value = pesanSukses; "ok" }
        is Result.Failure -> { pesanFlow.value = this.error.pesan; null }
    }

    private companion object {
        const val BATAS_BARIS = 200
    }
}
