package com.nada.kasir.feature.pantau_cabang

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nada.kasir.core.data.repository.OutletRepository
import com.nada.kasir.core.pantau.PantauCabangPengaturan
import com.nada.kasir.core.pantau.PantauCabangPengirim
import com.nada.kasir.core.pantau.PantauCabangRepository
import com.nada.kasir.core.pantau.PantauCabangScheduler
import com.nada.kasir.core.pantau.RekapHarianLogic
import com.nada.kasir.core.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class PantauCabangUiState(
    val konfigurasi: PantauCabangPengaturan.Konfigurasi = PantauCabangPengaturan.Konfigurasi(),
    val firebaseSiap: Boolean = true,
    val emailMasuk: String? = null,
    /** Identitas cabang HP ini (outlet aktif); null bila belum ada outlet aktif. */
    val namaCabang: String? = null,
    val kodeCabang: String? = null,
    val sibuk: Boolean = false,
    val pesan: String? = null
)

@HiltViewModel
class PantauCabangViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pengaturan: PantauCabangPengaturan,
    private val repository: PantauCabangRepository,
    private val pengirim: PantauCabangPengirim,
    outletRepository: OutletRepository
) : ViewModel() {

    private val emailFlow = MutableStateFlow(repository.emailSaatIni())
    private val sibukFlow = MutableStateFlow(false)
    private val pesanFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PantauCabangUiState> = combine(
        pengaturan.observe(),
        outletRepository.observeOutletAktifEfektif(),
        emailFlow,
        sibukFlow,
        pesanFlow
    ) { konfig, outlet, email, sibuk, pesan ->
        PantauCabangUiState(
            konfigurasi = konfig,
            firebaseSiap = repository.firebaseSiap(),
            emailMasuk = email,
            namaCabang = outlet?.nama,
            kodeCabang = outlet?.kode,
            sibuk = sibuk,
            pesan = pesan
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PantauCabangUiState())

    fun pesanDibaca() { pesanFlow.value = null }

    fun masuk(email: String, password: String) {
        viewModelScope.launch {
            sibukFlow.value = true
            when (val hasil = repository.masuk(email, password)) {
                is Result.Success -> {
                    emailFlow.value = repository.emailSaatIni()
                    pesanFlow.value = "Berhasil masuk."
                }
                is Result.Failure -> pesanFlow.value = hasil.error.pesan
            }
            sibukFlow.value = false
        }
    }

    fun keluar() {
        repository.keluar()
        emailFlow.value = null
        pesanFlow.value = "Anda sudah keluar dari akun Firebase."
    }

    /** Saklar "kirim otomatis": butuh kode toko valid saat dinyalakan; menyesuaikan jadwal WorkManager. */
    fun ubahSaklar(tokoId: String, aktif: Boolean) {
        val galat = RekapHarianLogic.validasiTokoId(tokoId)
        if (aktif && galat != null) {
            pesanFlow.value = galat
            return
        }
        viewModelScope.launch {
            pengaturan.simpan(aktif, tokoId)
            if (aktif) {
                PantauCabangScheduler.jadwalkan(context)
                pesanFlow.value = "Pengiriman otomatis dinyalakan. Rekap dikirim tiap beberapa jam saat ada internet."
            } else {
                PantauCabangScheduler.batalkan(context)
                pesanFlow.value = "Pengiriman otomatis dimatikan."
            }
        }
    }

    /** Simpan kode toko saja; status saklar otomatis tidak berubah. */
    fun simpanKode(tokoId: String) {
        RekapHarianLogic.validasiTokoId(tokoId)?.let {
            pesanFlow.value = it
            return
        }
        viewModelScope.launch {
            pengaturan.simpan(pengaturan.baca().aktif, tokoId)
            pesanFlow.value = "Kode toko tersimpan."
        }
    }

    fun kirimSekarang() {
        viewModelScope.launch {
            sibukFlow.value = true
            pesanFlow.value = pengirim.kirimSekarang().pesan
            sibukFlow.value = false
        }
    }
}

private fun formatWaktu(ms: Long): String =
    SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID")).format(Date(ms))

@Composable
fun PantauCabangScreen(viewModel: PantauCabangViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.pesan) {
        state.pesan?.let {
            snackbar.showSnackbar(it)
            viewModel.pesanDibaca()
        }
    }

    // Isian lokal; disegarkan dari setelan tersimpan setiap kali berubah.
    var tokoId by remember(state.konfigurasi.tokoId) { mutableStateOf(state.konfigurasi.tokoId) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Pantau Cabang", style = MaterialTheme.typography.titleLarge)
            Text(
                "Kirim rekap penjualan harian HP ini ke cloud supaya admin bisa memantau dari jauh lewat web " +
                    "dashboard. Kasir tetap bisa berjualan tanpa internet; rekap dikirim saat HP online.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!state.firebaseSiap) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        "Firebase belum dikonfigurasi pada aplikasi ini (google-services.json belum dipasang saat " +
                            "build). Fitur ini belum bisa dipakai.",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // 1. Identitas cabang
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Cabang HP ini", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.height(4.dp))
                    if (state.namaCabang != null) {
                        Text("${state.namaCabang} (${RekapHarianLogic.bersihkanKode(state.kodeCabang)})")
                    } else {
                        Text(
                            "Belum ada cabang aktif, rekap dicatat sebagai \"Pusat\". Pilih cabang aktif di menu " +
                                "Outlet / Cabang sebelum mengaktifkan pengiriman.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 2. Akun Firebase
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Akun Firebase", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    if (state.emailMasuk != null) {
                        Text("Masuk sebagai ${state.emailMasuk}")
                        OutlinedButton(onClick = viewModel::keluar, enabled = !state.sibuk) { Text("Keluar") }
                    } else {
                        Text(
                            "Gunakan akun cabang yang dibuat admin di Firebase.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = email, onValueChange = { email = it },
                            label = { Text("Email") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = password, onValueChange = { password = it },
                            label = { Text("Password") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { viewModel.masuk(email, password); password = "" },
                            enabled = state.firebaseSiap && !state.sibuk
                        ) { Text("Masuk") }
                    }
                }
            }

            // 3. Kode toko + saklar otomatis
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Toko & pengiriman otomatis", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    OutlinedTextField(
                        value = tokoId, onValueChange = { tokoId = it },
                        label = { Text("Kode Toko") }, singleLine = true,
                        supportingText = { Text("Tanyakan ke admin (UID akun admin di Firebase).") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Kirim rekap otomatis")
                            Text(
                                "Tiap beberapa jam saat ada internet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.konfigurasi.aktif,
                            onCheckedChange = { viewModel.ubahSaklar(tokoId, it) },
                            enabled = state.firebaseSiap && !state.sibuk
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.simpanKode(tokoId) },
                        enabled = state.firebaseSiap && !state.sibuk
                    ) { Text("Simpan Kode Toko") }
                }
            }

            // 4. Status & kirim manual
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Status", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        state.konfigurasi.terakhirKirim?.let { "Terakhir terkirim: ${formatWaktu(it)}" }
                            ?: "Belum pernah terkirim."
                    )
                    state.konfigurasi.pesanTerakhir?.let {
                        Text("Gagal terakhir: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = viewModel::kirimSekarang,
                        enabled = state.firebaseSiap && state.emailMasuk != null &&
                            RekapHarianLogic.validasiTokoId(state.konfigurasi.tokoId) == null && !state.sibuk
                    ) { Text(if (state.sibuk) "Mengirim..." else "Kirim rekap sekarang") }
                    Text(
                        "Yang dikirim: ringkasan penjualan hari ini dan 2 hari sebelumnya (jumlah transaksi, omzet, " +
                            "metode bayar, produk terlaris). Tanpa data pelanggan atau password.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
