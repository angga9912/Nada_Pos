package com.nada.kasir.feature.outlet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nada.kasir.core.data.local.entity.OutletEntity
import com.nada.kasir.core.data.repository.OutletRepository
import com.nada.kasir.core.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FormOutlet(
    val asli: OutletEntity? = null,
    val nama: String = "",
    val kode: String = "",
    val alamat: String = "",
    val telepon: String = ""
)

data class OutletUiState(
    val daftar: List<OutletEntity> = emptyList(),
    /** Outlet yang benar-benar dipakai memberi label transaksi baru; null = tanpa label. */
    val idAktif: Long? = null,
    val form: FormOutlet? = null,
    val pesan: String? = null
)

@HiltViewModel
class OutletViewModel @Inject constructor(
    private val repository: OutletRepository
) : ViewModel() {

    private val formFlow = MutableStateFlow<FormOutlet?>(null)
    private val pesanFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<OutletUiState> = combine(
        repository.observeAll(), repository.observeOutletAktifEfektif(), formFlow, pesanFlow
    ) { daftar, aktif, form, pesan ->
        OutletUiState(daftar = daftar, idAktif = aktif?.id, form = form, pesan = pesan)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OutletUiState())

    fun bukaTambah() { formFlow.value = FormOutlet() }
    fun bukaEdit(o: OutletEntity) { formFlow.value = FormOutlet(o, o.nama, o.kode, o.alamat, o.telepon) }
    fun tutupForm() { formFlow.value = null }
    fun ubahForm(ubah: (FormOutlet) -> FormOutlet) { formFlow.value = formFlow.value?.let(ubah) }
    fun pesanDibaca() { pesanFlow.value = null }

    fun simpan() {
        val form = formFlow.value ?: return
        viewModelScope.launch {
            val dasar = form.asli ?: OutletEntity(nama = "", kode = "")
            val entity = dasar.copy(nama = form.nama, kode = form.kode, alamat = form.alamat.trim(), telepon = form.telepon.trim())
            when (val hasil = repository.simpan(entity)) {
                is Result.Success -> { formFlow.value = null; pesanFlow.value = "Outlet disimpan." }
                is Result.Failure -> pesanFlow.value = hasil.error.pesan
            }
        }
    }

    /** null = tanpa cabang: transaksi, pembelian, dan mutasi stok baru tidak diberi label. */
    fun pilihAktif(id: Long?) {
        viewModelScope.launch {
            repository.setIdAktif(id)
            pesanFlow.value = if (id == null) "Transaksi baru tidak diberi label cabang." else "Cabang aktif diganti."
        }
    }

    fun ubahStatus(outlet: OutletEntity, aktif: Boolean) {
        viewModelScope.launch {
            repository.setAktif(outlet.id, aktif)
            if (!aktif && outlet.id == uiState.value.idAktif) {
                pesanFlow.value = "Outlet dinonaktifkan. Transaksi baru sekarang tanpa label cabang."
            }
        }
    }
}

@Composable
fun OutletScreen(viewModel: OutletViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.pesan) {
        state.pesan?.let {
            snackbar.showSnackbar(it)
            viewModel.pesanDibaca()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::bukaTambah) {
                Icon(Icons.Default.Add, contentDescription = "Tambah outlet")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Outlet / Cabang", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Pilih cabang aktif di HP/kasir ini. Transaksi, pembelian, dan perubahan stok manual yang dibuat " +
                    "sesudahnya diberi label cabang itu, dan Laporan bisa difilter per cabang. Stok produk tetap satu " +
                    "pool untuk semua cabang.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                item {
                    ListItem(
                        leadingContent = { RadioButton(selected = state.idAktif == null, onClick = { viewModel.pilihAktif(null) }) },
                        headlineContent = { Text("Tanpa cabang") },
                        supportingContent = { Text("Transaksi baru tidak diberi label cabang") },
                        modifier = Modifier.clickable { viewModel.pilihAktif(null) }
                    )
                    Divider()
                }
                items(state.daftar, key = { it.id }) { outlet ->
                    ListItem(
                        leadingContent = {
                            RadioButton(
                                selected = state.idAktif == outlet.id,
                                enabled = outlet.aktif,
                                onClick = { viewModel.pilihAktif(outlet.id) }
                            )
                        },
                        headlineContent = { Text(outlet.nama, fontWeight = if (state.idAktif == outlet.id) FontWeight.Bold else FontWeight.Normal) },
                        supportingContent = {
                            Column {
                                Text("${outlet.kode} • ${if (outlet.aktif) "Aktif" else "Nonaktif"}")
                                if (outlet.alamat.isNotBlank()) Text(outlet.alamat, style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { viewModel.bukaEdit(outlet) }) { Text("Edit") }
                                Switch(checked = outlet.aktif, onCheckedChange = { viewModel.ubahStatus(outlet, it) })
                            }
                        },
                        modifier = Modifier.clickable(enabled = outlet.aktif) { viewModel.pilihAktif(outlet.id) }
                    )
                    Divider()
                }
            }
        }
    }

    state.form?.let { form ->
        AlertDialog(
            onDismissRequest = viewModel::tutupForm,
            title = { Text(if (form.asli == null) "Tambah Outlet" else "Edit Outlet") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = form.nama, onValueChange = { v -> viewModel.ubahForm { it.copy(nama = v) } },
                        label = { Text("Nama outlet") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = form.kode, onValueChange = { v -> viewModel.ubahForm { it.copy(kode = v) } },
                        label = { Text("Kode outlet (mis. CBG1)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = form.alamat, onValueChange = { v -> viewModel.ubahForm { it.copy(alamat = v) } },
                        label = { Text("Alamat (opsional)") }, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = form.telepon, onValueChange = { v -> viewModel.ubahForm { it.copy(telepon = v) } },
                        label = { Text("Telepon (opsional)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = { TextButton(onClick = viewModel::simpan) { Text("Simpan") } },
            dismissButton = { TextButton(onClick = viewModel::tutupForm) { Text("Batal") } }
        )
    }
}
