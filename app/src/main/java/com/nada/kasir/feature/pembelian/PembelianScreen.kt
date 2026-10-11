package com.nada.kasir.feature.pembelian

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nada.kasir.core.data.local.entity.PurchaseItemEntity
import com.nada.kasir.core.data.local.entity.PurchaseStatus
import com.nada.kasir.core.domain.logic.PembelianLogic
import com.nada.kasir.core.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val WarnaMerah = Color(0xFFDC2626)
private val WarnaHijau = Color(0xFF16A34A)
private val WarnaOranye = Color(0xFFD97706)

private fun tanggalPendek(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale("in", "ID")).format(Date(millis))

private fun warnaStatus(status: PurchaseStatus): Color = when (status) {
    PurchaseStatus.RECEIVED -> WarnaHijau
    PurchaseStatus.PENDING -> WarnaOranye
    PurchaseStatus.CANCELLED -> Color.Gray
}

/** Opsi jatuh tempo hutang di formulir: null = tanpa jatuh tempo. */
private val OPSI_JATUH_TEMPO: List<Pair<Int?, String>> =
    listOf(null to "Tanpa jatuh tempo", 7 to "7 hari lagi", 14 to "14 hari lagi", 30 to "30 hari lagi")

@Composable
fun PembelianScreen(viewModel: PembelianViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var konfirmasiBatal by remember { mutableStateOf(false) }
    val form = state.form

    LaunchedEffect(state.pesan) {
        state.pesan?.let {
            snackbar.showSnackbar(it)
            viewModel.pesanDibaca()
        }
    }
    if (form != null) BackHandler { viewModel.tutupForm() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (form == null) {
                FloatingActionButton(onClick = viewModel::bukaForm) {
                    Icon(Icons.Default.Add, contentDescription = "Pembelian baru")
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (form != null) {
                FormLayar(state, form, viewModel)
            } else {
                DaftarLayar(state, viewModel)
            }
        }
    }

    val detail = state.detail
    if (detail != null && form == null) {
        DetailDialog(
            baris = detail,
            items = state.itemDetail,
            onTutup = viewModel::tutupDetail,
            onTerima = { viewModel.terimaBarang(detail.pembelian.id) },
            onBatalkan = { konfirmasiBatal = true }
        )
        if (konfirmasiBatal) {
            AlertDialog(
                onDismissRequest = { konfirmasiBatal = false },
                title = { Text("Batalkan pembelian?") },
                text = {
                    Text(
                        "Pembelian ${detail.pembelian.noPembelian} ditandai dibatalkan." +
                            if (detail.pembelian.status == PurchaseStatus.RECEIVED) " Stok yang sudah diterima akan dikurangi lagi." else ""
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.batalkan(detail.pembelian.id); konfirmasiBatal = false }) { Text("Ya, batalkan") }
                },
                dismissButton = { TextButton(onClick = { konfirmasiBatal = false }) { Text("Tidak") } }
            )
        }
    }
}

@Composable
private fun DaftarLayar(state: PembelianUiState, viewModel: PembelianViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Pembelian", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        val urutan = FilterPembelian.values()
        TabRow(selectedTabIndex = urutan.indexOf(state.filter)) {
            urutan.forEach { f ->
                Tab(selected = state.filter == f, onClick = { viewModel.pilihFilter(f) }, text = { Text(f.label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        if (state.daftar.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Belum ada pembelian. Tekan + untuk mencatat belanja stok dari supplier.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 88.dp)) {
                items(state.daftar, key = { it.pembelian.id }) { baris ->
                    val p = baris.pembelian
                    Card(modifier = Modifier.fillMaxWidth().clickable { viewModel.bukaDetail(p.id) }) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(baris.namaSupplier, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(CurrencyFormatter.format(p.totalBayar), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                "${p.noPembelian} • ${tanggalPendek(p.tanggalPembelian)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(PembelianLogic.labelStatus(p.status), style = MaterialTheme.typography.labelMedium, color = warnaStatus(p.status))
                                if (p.status != PurchaseStatus.CANCELLED && p.hutangSisa > 0.0) {
                                    Text("Hutang ${CurrencyFormatter.format(p.hutangSisa)}", style = MaterialTheme.typography.labelMedium, color = WarnaMerah)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormLayar(state: PembelianUiState, form: FormPembelian, viewModel: PembelianViewModel) {
    var pilihSupplier by remember { mutableStateOf(false) }
    var pilihProduk by remember { mutableStateOf(false) }
    val supplier = state.supplierAktif.firstOrNull { it.id == form.supplierId }
    val ringkasan = form.ringkasan()
    val sisa = form.sisaHutang()
    val kesalahan = form.pesanKesalahan()
    val angka = KeyboardOptions(keyboardType = KeyboardType.Number)

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Pembelian Baru", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = viewModel::tutupForm) { Text("Batal") }
        }
        Spacer(Modifier.height(8.dp))

        OutlinedButton(onClick = { pilihSupplier = true }, modifier = Modifier.fillMaxWidth()) {
            Text(supplier?.nama ?: "Pilih supplier")
        }
        if (state.supplierAktif.isEmpty()) {
            Text(
                "Belum ada supplier aktif. Tambahkan dulu di menu Supplier.",
                style = MaterialTheme.typography.bodySmall, color = WarnaMerah
            )
        }

        Spacer(Modifier.height(12.dp))
        Text("Barang", style = MaterialTheme.typography.titleMedium)
        form.items.forEach { item ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.nama, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Stok sekarang ${item.stokSaatIni}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { viewModel.hapusItem(item.productId) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Hapus ${item.nama}")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = item.qtyText, onValueChange = { v -> viewModel.ubahItem(item.productId) { it.copy(qtyText = v.filter(Char::isDigit)) } },
                            label = { Text("Jumlah") }, singleLine = true, keyboardOptions = angka, modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = item.hargaText, onValueChange = { v -> viewModel.ubahItem(item.productId) { it.copy(hargaText = v.filter(Char::isDigit)) } },
                            label = { Text("Harga beli") }, singleLine = true, keyboardOptions = angka, modifier = Modifier.weight(1.4f)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = item.diskonText, onValueChange = { v -> viewModel.ubahItem(item.productId) { it.copy(diskonText = v.filter(Char::isDigit)) } },
                        label = { Text("Diskon baris (Rp, opsional)") }, singleLine = true, keyboardOptions = angka, modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Subtotal ${CurrencyFormatter.format(PembelianLogic.subtotalItem(item.keInput()))}",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        OutlinedButton(onClick = { pilihProduk = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Tambah barang")
        }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtotal")
                    Text(CurrencyFormatter.format(ringkasan.subtotal))
                }
                OutlinedTextField(
                    value = form.diskonSupplierText, onValueChange = { v -> viewModel.ubahForm { it.copy(diskonSupplierText = v.filter(Char::isDigit)) } },
                    label = { Text("Diskon dari supplier (Rp, opsional)") }, singleLine = true, keyboardOptions = angka,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total", fontWeight = FontWeight.Bold)
                    Text(CurrencyFormatter.format(ringkasan.total), fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = form.bayarText, onValueChange = { v -> viewModel.ubahForm { it.copy(bayarText = v.filter(Char::isDigit)) } },
            label = { Text("Dibayar sekarang (Rp)") }, singleLine = true, keyboardOptions = angka,
            supportingText = { Text("Kosongkan bila belum dibayar sama sekali. Sisanya menjadi hutang ke supplier.") },
            modifier = Modifier.fillMaxWidth()
        )
        if (sisa > 0.0) {
            Text("Sisa hutang ke supplier: ${CurrencyFormatter.format(sisa)}", color = WarnaMerah, fontWeight = FontWeight.Bold)
            Text("Jatuh tempo", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
            OPSI_JATUH_TEMPO.forEach { (hari, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = form.jatuhTempoHari == hari, onClick = { viewModel.ubahForm { it.copy(jatuhTempoHari = hari) } })
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = form.terimaSekarang, onCheckedChange = { c -> viewModel.ubahForm { it.copy(terimaSekarang = c) } })
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Barang sudah diterima")
                Text(
                    if (form.terimaSekarang) "Stok produk langsung bertambah." else "Pesan dulu. Stok bertambah setelah Anda menekan Terima Barang.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Switch(checked = form.perbaruiHargaBeli, onCheckedChange = { c -> viewModel.ubahForm { it.copy(perbaruiHargaBeli = c) } })
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Perbarui harga beli produk")
                Text("Harga beli di data produk diganti dengan harga pembelian ini (setelah diskon baris).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedTextField(
            value = form.keterangan, onValueChange = { v -> viewModel.ubahForm { it.copy(keterangan = v) } },
            label = { Text("Keterangan (opsional)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        if (kesalahan != null && (form.items.isNotEmpty() || form.supplierId != null)) {
            Text(kesalahan, color = WarnaMerah, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
        Button(
            onClick = viewModel::simpan, enabled = kesalahan == null,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp)
        ) { Text("Simpan Pembelian") }
    }

    if (pilihSupplier) {
        AlertDialog(
            onDismissRequest = { pilihSupplier = false },
            title = { Text("Pilih supplier") },
            text = {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(state.supplierAktif, key = { it.id }) { s ->
                        ListItem(
                            headlineContent = { Text(s.nama) },
                            supportingContent = { if (s.telepon.isNotBlank()) Text(s.telepon) },
                            modifier = Modifier.clickable {
                                viewModel.ubahForm { it.copy(supplierId = s.id) }
                                pilihSupplier = false
                            }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pilihSupplier = false }) { Text("Tutup") } }
        )
    }

    if (pilihProduk) {
        AlertDialog(
            onDismissRequest = { pilihProduk = false },
            title = { Text("Tambah barang") },
            text = {
                Column {
                    OutlinedTextField(
                        value = state.kataCari, onValueChange = viewModel::cariProduk,
                        label = { Text("Cari nama / barcode") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(state.hasilCariProduk, key = { it.id }) { p ->
                            val sudah = form.items.any { it.productId == p.id }
                            ListItem(
                                headlineContent = { Text(p.nama) },
                                supportingContent = {
                                    Text("Stok ${p.stok} • Harga beli ${CurrencyFormatter.format(p.hargaBeli)}" + if (sudah) " • sudah ditambahkan" else "")
                                },
                                modifier = Modifier.clickable { viewModel.tambahProduk(p) }
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pilihProduk = false }) { Text("Selesai") } }
        )
    }
}

@Composable
private fun DetailDialog(
    baris: BarisPembelian,
    items: List<PurchaseItemEntity>,
    onTutup: () -> Unit,
    onTerima: () -> Unit,
    onBatalkan: () -> Unit
) {
    val p = baris.pembelian
    AlertDialog(
        onDismissRequest = onTutup,
        title = { Text(p.noPembelian) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Supplier: ${baris.namaSupplier}")
                Text("Tanggal: ${tanggalPendek(p.tanggalPembelian)}")
                Text("Status: ${PembelianLogic.labelStatus(p.status)}", color = warnaStatus(p.status), fontWeight = FontWeight.Bold)
                p.tanggalTerima?.let { Text("Diterima: ${tanggalPendek(it)}") }
                Spacer(Modifier.height(8.dp))
                Text("Barang", style = MaterialTheme.typography.titleSmall)
                items.forEach {
                    Text(
                        "${it.namaProdukSnapshot} • ${it.qty} x ${CurrencyFormatter.format(it.hargaBeli)}" +
                            (if (it.diskon > 0.0) " − ${CurrencyFormatter.format(it.diskon)}" else "") +
                            " = ${CurrencyFormatter.format(it.subtotal)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Subtotal: ${CurrencyFormatter.format(p.subtotal)}")
                if (p.diskonSupplier > 0.0) Text("Diskon supplier: ${CurrencyFormatter.format(p.diskonSupplier)}")
                Text("Total: ${CurrencyFormatter.format(p.totalBayar)}", fontWeight = FontWeight.Bold)
                if (p.status != PurchaseStatus.CANCELLED && p.hutangSisa > 0.0) {
                    Text("Sisa hutang: ${CurrencyFormatter.format(p.hutangSisa)}", color = WarnaMerah, fontWeight = FontWeight.Bold)
                    Text("Cicilan dicatat di menu Hutang / Piutang (tab Hutang).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                p.keterangan?.let { Text("Keterangan: $it") }
                if (p.status == PurchaseStatus.PENDING) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onTerima) { Text("Terima Barang") }
                    Text("Stok bertambah dan harga beli produk diperbarui.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (p.status != PurchaseStatus.CANCELLED) {
                    TextButton(onClick = onBatalkan) { Text("Batalkan pembelian", color = WarnaMerah) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onTutup) { Text("Tutup") } }
    )
}
