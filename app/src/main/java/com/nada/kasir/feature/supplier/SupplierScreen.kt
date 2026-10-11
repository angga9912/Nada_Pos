package com.nada.kasir.feature.supplier

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nada.kasir.core.data.local.entity.PurchaseEntity
import com.nada.kasir.core.data.local.entity.PurchaseStatus
import com.nada.kasir.core.data.local.entity.SupplierEntity
import com.nada.kasir.core.data.repository.PurchaseRepository
import com.nada.kasir.core.data.repository.SupplierRepository
import com.nada.kasir.core.domain.logic.PembelianLogic
import com.nada.kasir.core.util.CurrencyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

private val WarnaMerah = Color(0xFFDC2626)

private fun tanggalPendek(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale("in", "ID")).format(Date(millis))

/** Supplier beserta ringkasan pembeliannya (pembelian yang dibatalkan tidak dihitung). */
data class BarisSupplier(val supplier: SupplierEntity, val jumlahPembelian: Int, val hutangAktif: Double)

data class SupplierUiState(
    val daftar: List<BarisSupplier> = emptyList(),
    val kataCari: String = "",
    val tampilkanNonaktif: Boolean = false,
    val detail: BarisSupplier? = null,
    val riwayat: List<PurchaseEntity> = emptyList(),
    val pesan: String? = null
)

@HiltViewModel
class SupplierViewModel @Inject constructor(
    private val repository: SupplierRepository,
    private val purchaseRepository: PurchaseRepository
) : ViewModel() {

    private val kataCariFlow = MutableStateFlow("")
    private val nonaktifFlow = MutableStateFlow(false)
    private val idDetailFlow = MutableStateFlow<Long?>(null)
    private val pesanFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SupplierUiState> = combine(
        repository.observeAll(), purchaseRepository.observeAll(), kataCariFlow, nonaktifFlow, idDetailFlow, pesanFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val supplier = values[0] as List<SupplierEntity>
        @Suppress("UNCHECKED_CAST")
        val pembelian = values[1] as List<PurchaseEntity>
        val kata = values[2] as String
        val nonaktif = values[3] as Boolean
        val idDetail = values[4] as Long?
        val pesan = values[5] as String?

        val aktifPerSupplier = pembelian.filter { it.status != PurchaseStatus.CANCELLED }.groupBy { it.supplierId }
        val semua = supplier.map { s ->
            val daftarBeli = aktifPerSupplier[s.id].orEmpty()
            BarisSupplier(s, daftarBeli.size, daftarBeli.sumOf { it.hutangSisa })
        }
        val kataBersih = kata.trim()
        SupplierUiState(
            daftar = semua.filter { baris ->
                (nonaktif || baris.supplier.aktif) &&
                    (kataBersih.isEmpty() ||
                        baris.supplier.nama.contains(kataBersih, ignoreCase = true) ||
                        baris.supplier.telepon.contains(kataBersih) ||
                        baris.supplier.kontak.contains(kataBersih, ignoreCase = true))
            },
            kataCari = kata,
            tampilkanNonaktif = nonaktif,
            detail = idDetail?.let { id -> semua.firstOrNull { it.supplier.id == id } },
            riwayat = idDetail?.let { id -> pembelian.filter { it.supplierId == id }.take(10) }.orEmpty(),
            pesan = pesan
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SupplierUiState())

    fun cari(kata: String) { kataCariFlow.value = kata }
    fun setTampilkanNonaktif(nilai: Boolean) { nonaktifFlow.value = nilai }
    fun bukaDetail(id: Long) { idDetailFlow.value = id }
    fun tutupDetail() { idDetailFlow.value = null }
    fun pesanDibaca() { pesanFlow.value = null }

    fun simpan(entity: SupplierEntity) {
        viewModelScope.launch {
            val nama = entity.nama.trim()
            if (nama.isEmpty()) { pesanFlow.value = "Nama supplier wajib diisi."; return@launch }
            val kembar = repository.observeAll().first().any { it.id != entity.id && it.nama.trim().equals(nama, ignoreCase = true) }
            if (kembar) { pesanFlow.value = "Supplier dengan nama \"$nama\" sudah ada."; return@launch }
            try {
                repository.save(
                    entity.copy(
                        nama = nama, kontak = entity.kontak.trim(), telepon = entity.telepon.trim(),
                        alamat = entity.alamat.trim(), catatan = entity.catatan.trim(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
                pesanFlow.value = if (entity.id == 0L) "Supplier ditambahkan." else "Perubahan disimpan."
            } catch (e: Exception) {
                pesanFlow.value = "Supplier gagal disimpan. Silakan coba lagi."
            }
        }
    }

    fun setAktif(id: Long, aktif: Boolean) {
        viewModelScope.launch {
            repository.setAktif(id, aktif)
            pesanFlow.value = if (aktif) "Supplier diaktifkan." else "Supplier dinonaktifkan."
        }
    }
}

@Composable
fun SupplierScreen(viewModel: SupplierViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var formUntuk by remember { mutableStateOf<SupplierEntity?>(null) } // id 0 = tambah baru
    var tampilForm by remember { mutableStateOf(false) }

    LaunchedEffect(state.pesan) {
        state.pesan?.let {
            snackbar.showSnackbar(it)
            viewModel.pesanDibaca()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { formUntuk = SupplierEntity(nama = ""); tampilForm = true }) {
                Icon(Icons.Default.Add, contentDescription = "Tambah supplier")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Text("Supplier", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.kataCari, onValueChange = viewModel::cari, singleLine = true,
                label = { Text("Cari nama / kontak / telepon") }, modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Switch(checked = state.tampilkanNonaktif, onCheckedChange = viewModel::setTampilkanNonaktif)
                Spacer(Modifier.width(8.dp))
                Text("Tampilkan yang nonaktif", style = MaterialTheme.typography.bodySmall)
            }
            if (state.daftar.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.kataCari.isBlank()) "Belum ada supplier. Tekan + untuk menambahkan." else "Tidak ada supplier yang cocok.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(state.daftar, key = { it.supplier.id }) { baris ->
                        ListItem(
                            headlineContent = {
                                Text(baris.supplier.nama + if (baris.supplier.aktif) "" else " (nonaktif)")
                            },
                            supportingContent = {
                                Column {
                                    val kontak = listOf(baris.supplier.kontak, baris.supplier.telepon).filter { it.isNotBlank() }.joinToString(" • ")
                                    if (kontak.isNotBlank()) Text(kontak)
                                    Text("${baris.jumlahPembelian} pembelian")
                                    if (baris.hutangAktif > 0.0) {
                                        Text("Hutang ${CurrencyFormatter.format(baris.hutangAktif)}", color = WarnaMerah)
                                    }
                                }
                            },
                            modifier = Modifier.clickable { viewModel.bukaDetail(baris.supplier.id) }
                        )
                        Divider()
                    }
                }
            }
        }
    }

    val detail = state.detail
    if (detail != null) {
        DetailSupplierDialog(
            baris = detail,
            riwayat = state.riwayat,
            onTutup = viewModel::tutupDetail,
            onEdit = { formUntuk = detail.supplier; tampilForm = true },
            onUbahAktif = { viewModel.setAktif(detail.supplier.id, !detail.supplier.aktif) }
        )
    }

    val awal = formUntuk
    if (tampilForm && awal != null) {
        FormSupplierDialog(
            awal = awal,
            onDismiss = { tampilForm = false },
            onSimpan = { viewModel.simpan(it); tampilForm = false }
        )
    }
}

@Composable
private fun FormSupplierDialog(awal: SupplierEntity, onDismiss: () -> Unit, onSimpan: (SupplierEntity) -> Unit) {
    var nama by remember(awal.id) { mutableStateOf(awal.nama) }
    var kontak by remember(awal.id) { mutableStateOf(awal.kontak) }
    var telepon by remember(awal.id) { mutableStateOf(awal.telepon) }
    var alamat by remember(awal.id) { mutableStateOf(awal.alamat) }
    var catatan by remember(awal.id) { mutableStateOf(awal.catatan) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (awal.id == 0L) "Tambah Supplier" else "Edit Supplier") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = nama, onValueChange = { nama = it }, label = { Text("Nama supplier") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = kontak, onValueChange = { kontak = it }, label = { Text("Nama kontak (opsional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = telepon, onValueChange = { telepon = it }, label = { Text("Telepon / WhatsApp (opsional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = alamat, onValueChange = { alamat = it }, label = { Text("Alamat (opsional)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = catatan, onValueChange = { catatan = it }, label = { Text("Catatan (opsional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = nama.isNotBlank(),
                onClick = { onSimpan(awal.copy(nama = nama, kontak = kontak, telepon = telepon, alamat = alamat, catatan = catatan)) }
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun DetailSupplierDialog(
    baris: BarisSupplier,
    riwayat: List<PurchaseEntity>,
    onTutup: () -> Unit,
    onEdit: () -> Unit,
    onUbahAktif: () -> Unit
) {
    val s = baris.supplier
    AlertDialog(
        onDismissRequest = onTutup,
        title = { Text(s.nama + if (s.aktif) "" else " (nonaktif)") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (s.kontak.isNotBlank()) Text("Kontak: ${s.kontak}")
                if (s.telepon.isNotBlank()) Text("Telepon: ${s.telepon}")
                if (s.alamat.isNotBlank()) Text("Alamat: ${s.alamat}")
                if (s.catatan.isNotBlank()) Text("Catatan: ${s.catatan}")
                Spacer(Modifier.height(8.dp))
                Text("${baris.jumlahPembelian} pembelian", fontWeight = FontWeight.Bold)
                Text(
                    "Hutang berjalan: ${CurrencyFormatter.format(baris.hutangAktif)}",
                    fontWeight = FontWeight.Bold, color = if (baris.hutangAktif > 0.0) WarnaMerah else Color.Unspecified
                )
                Spacer(Modifier.height(8.dp))
                Text("Pembelian terakhir", style = MaterialTheme.typography.titleSmall)
                if (riwayat.isEmpty()) {
                    Text("Belum ada pembelian dari supplier ini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    riwayat.forEach { p ->
                        Text(
                            "${tanggalPendek(p.tanggalPembelian)} • ${p.noPembelian} • ${CurrencyFormatter.format(p.totalBayar)} • ${PembelianLogic.labelStatus(p.status)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onEdit) { Text("Edit") }
                    OutlinedButton(onClick = onUbahAktif) { Text(if (s.aktif) "Nonaktifkan" else "Aktifkan") }
                }
                if (s.aktif) {
                    Text(
                        "Supplier nonaktif tidak muncul saat membuat pembelian baru. Riwayatnya tetap tersimpan.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onTutup) { Text("Tutup") } }
    )
}
