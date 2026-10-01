package com.nada.kasir.feature.info_paket

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nada.kasir.core.data.repository.TransactionRepository
import com.nada.kasir.core.lisensi.DeviceIdProvider
import com.nada.kasir.core.paket.PaketAplikasi
import com.nada.kasir.core.paket.PaketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class InfoPaketViewModel @Inject constructor(
    paketRepository: PaketRepository,
    transactionRepository: TransactionRepository,
    deviceIdProvider: DeviceIdProvider
) : ViewModel() {
    /** ID Perangkat untuk disertakan di pesan WhatsApp upgrade/trial (kode lisensi terikat perangkat). */
    val idPerangkat: String = deviceIdProvider.idTampil

    val paketAktif: StateFlow<PaketAplikasi> = paketRepository.observePaketAktif()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PaketAplikasi.BASIC)

    // Dipakai sebagai konteks di layar ini: "hari ini sudah berapa transaksi" - membantu
    // pemilik toko Basic menimbang keputusan upgrade dengan angka pemakaian sendiri, bukan
    // cuma janji fitur. Query & cara hitung awal/akhir hari SAMA dengan yang dipakai banner
    // saran upgrade di KasirViewModel, supaya angkanya selalu konsisten di kedua tempat.
    val jumlahTransaksiHariIni: StateFlow<Int> = transactionRepository
        .observeJumlahTransaksiHariIni(awalHariIniMillis(), akhirHariIniMillis())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private fun awalHariIniMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun akhirHariIniMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}
