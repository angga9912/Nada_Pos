package com.nada.kasir.feature.stok

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nada.kasir.core.domain.logic.StokLogic
import com.nada.kasir.core.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val WarnaMerah = Color(0xFFDC2626)
private val WarnaHijau = Color(0xFF16A34A)

private fun tanggalWaktu(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy HH:mm", Locale("in", "ID")).format(Date(millis))

@Composable
fun StokScreen(viewModel: StokViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.pesan) {
        state.pesan?.let {
            snackbar.showSnackbar(it)
            viewModel.pesanDibaca()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Text("Stok", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                JenisAksi.values().forEach { jenis ->
                    FilledTonalButton(onClick = { viewModel.bukaForm(jenis) }, modifier = Modifier.weight(1f)) {
                        Text(jenis.judul.removePrefix("Stok ").let { if (jenis == JenisAksi.OPNAME) "Opname" else it }, maxLines = 1)
                    }
                }
            }
            Text(
                "Belanja dari supplier dicatat di menu Pembelian (Pro) agar hutangnya ikut tercatat.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
            val urutan = FilterMutasi.values()
            TabRow(selectedTabIndex = urutan.indexOf(state.filter)) {
                urutan.forEach { f ->
                    Tab(selected = state.filter == f, onClick = { viewModel.pilihFilter(f) }, text = { Text(f.label) })
                }
            }
            if (state.riwayat.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.filter == FilterMutasi.MANUAL) "Belum ada perubahan stok manual. Gunakan tombol di atas."
                        else "Belum ada riwayat stok.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(state.riwayat, key = { it.mutasi.id }) { baris ->
                        val m = baris.mutasi
                        val naik = StokLogic.menambah(m.tipe)
                        ListItem(
                            headlineContent = { Text(baris.namaProduk, fontWeight = FontWeight.Bold) },
                            supportingContent = {
                                Column {
                                    Text(tanggalWaktu(m.tanggalWaktu), style = MaterialTheme.typography.bodySmall)
                                    m.keterangan?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                }
                            },
                            trailingContent = {
                                Text(
                                    StokLogic.teksQty(m.tipe, m.qty),
                                    color = if (naik) WarnaHijau else WarnaMerah,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        )
                        Divider()
                    }
                    if (state.terpotong) {
                        item {
                            Text(
                                "Menampilkan 200 terbaru. Persempit dengan filter di atas.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    state.form?.let { FormStokDialog(state, it, viewModel) }
}

@Composable
private fun FormStokDialog(state: StokUiState, form: FormStok, viewModel: StokViewModel) {
    var pilihProduk by remember { mutableStateOf(false) }
    val angka = KeyboardOptions(keyboardType = KeyboardType.Number)
    val kesalahan = form.pesanKesalahan()
    val produk = form.produk

    AlertDialog(
        onDismissRequest = viewModel::tutupForm,
        title = { Text(form.jenis.judul) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedButton(onClick = { pilihProduk = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(produk?.nama ?: "Pilih produk")
                }
                if (produk != null) {
                    Text(
                        "Stok sistem sekarang: ${produk.stok}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = form.qtyText,
                    onValueChange = { v -> viewModel.ubahForm { it.copy(qtyText = v.filter(Char::isDigit).take(7)) } },
                    label = { Text(if (form.jenis == JenisAksi.OPNAME) "Stok hasil hitung fisik" else "Jumlah") },
                    singleLine = true, keyboardOptions = angka, modifier = Modifier.fillMaxWidth()
                )

                when (form.jenis) {
                    JenisAksi.MASUK -> {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = form.hargaBeliText,
                            onValueChange = { v -> viewModel.ubahForm { it.copy(hargaBeliText = v.filter(Char::isDigit)) } },
                            label = { Text("Harga beli per unit (opsional)") }, singleLine = true, keyboardOptions = angka,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (form.hargaBeliText.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Switch(
                                    checked = form.perbaruiHargaBeli,
                                    onCheckedChange = { c -> viewModel.ubahForm { it.copy(perbaruiHargaBeli = c) } }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Perbarui harga beli produk")
                                    Text(
                                        produk?.let { "Sekarang ${CurrencyFormatter.format(it.hargaBeli)}" } ?: "Mengganti harga beli di data produk.",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    JenisAksi.KELUAR -> {
                        Spacer(Modifier.height(8.dp))
                        Text("Alasan", style = MaterialTheme.typography.labelMedium)
                        StokLogic.AlasanKeluar.values().forEach { a ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = form.alasan == a, onClick = { viewModel.ubahForm { it.copy(alasan = a) } })
                                Text(a.label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    JenisAksi.OPNAME -> {
                        if (produk != null && form.qtyText.isNotBlank()) {
                            val selisih = StokLogic.selisihOpname(produk.stok, form.qty)
                            val teks = when {
                                selisih > 0 -> "Selisih +$selisih (stok bertambah)"
                                selisih < 0 -> "Selisih −${-selisih} (stok berkurang)"
                                else -> "Sama dengan stok sistem"
                            }
                            Text(
                                teks, fontWeight = FontWeight.Bold,
                                color = when { selisih > 0 -> WarnaHijau; selisih < 0 -> WarnaMerah; else -> Color.Unspecified },
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = form.catatan, onValueChange = { v -> viewModel.ubahForm { it.copy(catatan = v) } },
                    label = {
                        Text(
                            if (form.jenis == JenisAksi.KELUAR && form.alasan == StokLogic.AlasanKeluar.LAINNYA) "Catatan (wajib)"
                            else "Catatan (opsional)"
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (kesalahan != null && (produk != null || form.qtyText.isNotBlank())) {
                    Text(kesalahan, color = WarnaMerah, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = viewModel::simpan, enabled = kesalahan == null) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = viewModel::tutupForm) { Text("Batal") } }
    )

    if (pilihProduk) {
        AlertDialog(
            onDismissRequest = { pilihProduk = false },
            title = { Text("Pilih produk") },
            text = {
                Column {
                    OutlinedTextField(
                        value = state.kataCari, onValueChange = viewModel::cariProduk,
                        label = { Text("Cari nama / kode") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(state.hasilCariProduk, key = { it.id }) { p ->
                            ListItem(
                                headlineContent = { Text(p.nama) },
                                supportingContent = { Text("Stok ${p.stok}") },
                                modifier = Modifier.clickable { viewModel.pilihProduk(p); pilihProduk = false }
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pilihProduk = false }) { Text("Tutup") } }
        )
    }
}
