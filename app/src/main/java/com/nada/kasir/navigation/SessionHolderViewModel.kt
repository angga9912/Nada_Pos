package com.nada.kasir.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nada.kasir.core.lisensi.LicenseRepository
import com.nada.kasir.core.paket.PaketAplikasi
import com.nada.kasir.core.paket.PaketRepository
import com.nada.kasir.core.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Wrapper tipis supaya SessionManager (singleton biasa) bisa diambil lewat
 * hiltViewModel() dari Composable manapun di NavGraph, memakai instance
 * SessionManager yang sama (karena SessionManager sendiri @Singleton).
 *
 * Juga membawa paket aktif supaya NavGraph bisa membatasi fitur per paket
 * (mis. Laporan = CUSTOM ke atas) di satu tempat yang sama dengan pengecekan role.
 */
@HiltViewModel
class SessionHolderViewModel @Inject constructor(
    val sessionManager: SessionManager,
    paketRepository: PaketRepository,
    private val licenseRepository: LicenseRepository
) : ViewModel() {
    val paketAktif: StateFlow<PaketAplikasi> = paketRepository.observePaketAktif()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PaketAplikasi.BASIC)

    init {
        // F-09: sebelumnya kadaluarsa lisensi hanya dicek saat LoginViewModel dibuat (aplikasi dibuka).
        // Toko yang membiarkan aplikasi tetap terbuka berhari-hari tidak pernah diturunkan ke Basic.
        // Sekarang dicek berkala selama aplikasi hidup; paketAktif di atas otomatis ikut berubah.
        viewModelScope.launch {
            while (isActive) {
                runCatching { licenseRepository.cekDanTurunkanJikaKadaluarsa() }
                delay(INTERVAL_CEK_LISENSI_MS)
            }
        }
    }

    private companion object {
        const val INTERVAL_CEK_LISENSI_MS = 5L * 60 * 1000 // 5 menit
    }
}
