package com.nada.kasir.feature.pengaturan_toko

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nada.kasir.core.data.local.entity.StoreEntity
import com.nada.kasir.core.data.repository.StoreRepository
import com.nada.kasir.core.lisensi.LicenseRepository
import com.nada.kasir.core.lisensi.StatusLisensi
import com.nada.kasir.core.paket.PaketAplikasi
import com.nada.kasir.core.util.LogoStorageHelper
import com.nada.kasir.core.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PengaturanTokoViewModel @Inject constructor(
    private val storeRepository: StoreRepository,
    private val licenseRepository: LicenseRepository
) : ViewModel() {

    val store: StateFlow<StoreEntity?> = storeRepository.observeStore()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val statusLisensi: StateFlow<StatusLisensi> = licenseRepository.observeStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatusLisensi(PaketAplikasi.BASIC, null, false))

    private val _pesanAktivasi = MutableStateFlow<String?>(null)
    val pesanAktivasi: StateFlow<String?> = _pesanAktivasi

    fun simpan(store: StoreEntity, onSelesai: () -> Unit) {
        viewModelScope.launch {
            storeRepository.simpan(store)
            onSelesai()
        }
    }

    /** Ganti logo toko (poin 1 & 2). Logo disalin ke storage internal agar path-nya stabil. */
    fun gantiLogo(context: Context, uri: Uri, storeSaatIni: StoreEntity) {
        viewModelScope.launch {
            val path = LogoStorageHelper.simpanLogoDariUri(context, uri)
            if (path != null) {
                storeRepository.simpan(storeSaatIni.copy(logoPath = path))
            }
        }
    }

    /** Aktivasi kode lisensi Custom/Pro (model bisnis freemium). */
    fun aktivasiLisensi(kode: String) {
        viewModelScope.launch {
            when (val hasil = licenseRepository.aktivasi(kode)) {
                is Result.Success -> _pesanAktivasi.value = "Aktivasi berhasil! Paket ${hasil.data.label} sekarang aktif."
                is Result.Failure -> _pesanAktivasi.value = hasil.error.pesan
            }
        }
    }

    fun clearPesanAktivasi() { _pesanAktivasi.value = null }
}
