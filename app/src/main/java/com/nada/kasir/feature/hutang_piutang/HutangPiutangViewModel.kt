package com.nada.kasir.feature.hutang_piutang

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nada.kasir.core.data.local.entity.HutangPiutangEntity
import com.nada.kasir.core.data.local.entity.HutangPiutangPembayaranEntity
import com.nada.kasir.core.data.repository.HutangPiutangRepository
import com.nada.kasir.core.domain.logic.HutangPiutangLogic
import com.nada.kasir.core.session.SessionManager
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

data class HutangPiutangUiState(
    val jenisTerpilih: String = HutangPiutangLogic.JENIS_PIUTANG,
    val tampilkanSelesai: Boolean = false,
    /** Daftar untuk tab [jenisTerpilih], sudah difilter status. */
    val daftar: List<HutangPiutangEntity> = emptyList(),
    val totalSisaAktifPiutang: Double = 0.0,
    val totalSisaAktifHutang: Double = 0.0,
    val jumlahTerlambat: Int = 0,
    val idDetail: Long? = null,
    val detail: HutangPiutangEntity? = null,
    val riwayatPembayaran: List<HutangPiutangPembayaranEntity> = emptyList(),
    val pesan: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HutangPiutangViewModel @Inject constructor(
    private val repository: HutangPiutangRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val jenisFlow = MutableStateFlow(HutangPiutangLogic.JENIS_PIUTANG)
    private val tampilkanSelesaiFlow = MutableStateFlow(false)
    private val idDetailFlow = MutableStateFlow<Long?>(null)
    private val pesanFlow = MutableStateFlow<String?>(null)

    private val riwayatFlow = idDetailFlow.flatMapLatest { id ->
        if (id == null) flowOf(emptyList<HutangPiutangPembayaranEntity>()) else repository.observePembayaran(id)
    }

    val uiState: StateFlow<HutangPiutangUiState> = combine(
        repository.observeAll(), jenisFlow, tampilkanSelesaiFlow, idDetailFlow, riwayatFlow, pesanFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val semua = values[0] as List<HutangPiutangEntity>
        val jenis = values[1] as String
        val selesai = values[2] as Boolean
        val idDetail = values[3] as Long?
        @Suppress("UNCHECKED_CAST")
        val riwayat = values[4] as List<HutangPiutangPembayaranEntity>
        val pesan = values[5] as String?
        val aktif = semua.filter { it.status == HutangPiutangLogic.STATUS_AKTIF }
        val sekarang = System.currentTimeMillis()
        HutangPiutangUiState(
            jenisTerpilih = jenis,
            tampilkanSelesai = selesai,
            daftar = semua.filter { it.jenis == jenis && (selesai || it.status == HutangPiutangLogic.STATUS_AKTIF) },
            totalSisaAktifPiutang = aktif.filter { it.jenis == HutangPiutangLogic.JENIS_PIUTANG }.sumOf { it.sisa },
            totalSisaAktifHutang = aktif.filter { it.jenis == HutangPiutangLogic.JENIS_HUTANG }.sumOf { it.sisa },
            jumlahTerlambat = aktif.count {
                HutangPiutangLogic.kondisiJatuhTempo(it.status, it.tanggalJatuhTempo, sekarang) ==
                    HutangPiutangLogic.KondisiJatuhTempo.TERLAMBAT
            },
            idDetail = idDetail,
            detail = idDetail?.let { id -> semua.firstOrNull { it.id == id } },
            riwayatPembayaran = riwayat,
            pesan = pesan
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HutangPiutangUiState())

    fun pilihJenis(jenis: String) { jenisFlow.value = jenis }
    fun setTampilkanSelesai(nilai: Boolean) { tampilkanSelesaiFlow.value = nilai }
    fun bukaDetail(id: Long) { idDetailFlow.value = id }
    fun tutupDetail() { idDetailFlow.value = null }
    fun pesanDibaca() { pesanFlow.value = null }

    fun catatBaru(jenis: String, pelaku: String, jumlah: Double, jatuhTempoHari: Int?, keterangan: String?) {
        viewModelScope.launch {
            val jatuhTempo = HutangPiutangLogic.jatuhTempoDariHari(jatuhTempoHari, System.currentTimeMillis())
            when (val hasil = repository.catatBaru(jenis, pelaku, jumlah, jatuhTempo, keterangan)) {
                is Result.Success -> { jenisFlow.value = jenis; pesanFlow.value = "Catatan disimpan." }
                is Result.Failure -> pesanFlow.value = hasil.error.pesan
            }
        }
    }

    fun ubah(id: Long, pelaku: String, jatuhTempo: Long?, keterangan: String?, jumlahBaru: Double?) {
        viewModelScope.launch {
            when (val hasil = repository.ubah(id, pelaku, jatuhTempo, keterangan, jumlahBaru)) {
                is Result.Success -> pesanFlow.value = "Perubahan disimpan."
                is Result.Failure -> pesanFlow.value = hasil.error.pesan
            }
        }
    }

    fun catatPembayaran(id: Long, jumlah: Double, catatan: String?) {
        viewModelScope.launch {
            val userId = sessionManager.currentUser.value?.id
            when (val hasil = repository.catatPembayaran(id, jumlah, catatan, userId)) {
                is Result.Success -> pesanFlow.value = "Pembayaran dicatat."
                is Result.Failure -> pesanFlow.value = hasil.error.pesan
            }
        }
    }

    fun batalkan(id: Long) {
        viewModelScope.launch {
            when (val hasil = repository.batalkan(id)) {
                is Result.Success -> { idDetailFlow.value = null; pesanFlow.value = "Catatan dibatalkan." }
                is Result.Failure -> pesanFlow.value = hasil.error.pesan
            }
        }
    }
}
