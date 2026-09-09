package com.nada.kasir.feature.produk

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nada.kasir.core.data.local.entity.ProductEntity
import com.nada.kasir.core.util.CurrencyFormatter
import com.nada.kasir.core.util.FileShareHelper

/** Halaman DATA PRODUK (poin 6), dengan Import/Export Excel (poin 15, Phase 3). */
@Composable
fun ProdukScreen(isAdmin: Boolean = true, viewModel: ProdukViewModel = hiltViewModel()) {
    val produkList by viewModel.daftarProduk.collectAsState()
    val paketAktif by viewModel.paketAktif.collectAsState()
    val pesanImportExport by viewModel.pesanImportExport.collectAsState()
    val fileExportTerakhir by viewModel.fileExportTerakhir.collectAsState()
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ProductEntity?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val pilihFileImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importExcel(uri)
    }

    Scaffold(
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(onClick = { editing = null; showForm = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Tambah Produk")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isAdmin && paketAktif.mencakup(com.nada.kasir.core.paket.PaketAplikasi.CUSTOM)) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            pilihFileImport.launch(arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "*/*"
                            ))
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Import Excel") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { viewModel.exportExcel() }, modifier = Modifier.weight(1f)) {
                        Text("Export Excel")
                    }
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(produkList) { produk ->
                    ListItem(
                        headlineContent = { Text(produk.nama) },
                        supportingContent = {
                            Text("${produk.kodeProduk} • Stok: ${produk.stok} • ${CurrencyFormatter.format(produk.hargaJual)}")
                        },
                        trailingContent = {
                            if (isAdmin) {
                                Row {
                                    TextButton(onClick = { editing = produk; showForm = true }) { Text("Edit") }
                                    TextButton(onClick = { viewModel.hapus(produk.id) }) { Text("Hapus") }
                                }
                            }
                        }
                    )
                    Divider()
                }
            }
        }
    }

    if (showForm) {
        ProdukFormDialog(
            initial = editing,
            onDismiss = { showForm = false },
            onSimpan = { produk ->
                viewModel.simpan(produk) { pesan -> errorMsg = pesan }
                showForm = false
            }
        )
    }

    pesanImportExport?.let { pesan ->
        AlertDialog(
            onDismissRequest = { viewModel.clearPesanImportExport() },
            title = { Text("Import / Export Excel") },
            text = { Text(pesan) },
            confirmButton = { TextButton(onClick = { viewModel.clearPesanImportExport() }) { Text("OK") } },
            dismissButton = {
                fileExportTerakhir?.let { file ->
                    TextButton(onClick = {
                        FileShareHelper.bagikanFile(context, file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        viewModel.clearPesanImportExport()
                    }) { Text("Bagikan File") }
                }
            }
        )
    }

    errorMsg?.let { pesan ->
        AlertDialog(
            onDismissRequest = { errorMsg = null },
            confirmButton = { TextButton(onClick = { errorMsg = null }) { Text("OK") } },
            title = { Text("Perhatian") },
            text = { Text(pesan) }
        )
    }
}

@Composable
private fun ProdukFormDialog(
    initial: ProductEntity?,
    onDismiss: () -> Unit,
    onSimpan: (ProductEntity) -> Unit
) {
    var kode by remember { mutableStateOf(initial?.kodeProduk ?: "") }
    var barcode by remember { mutableStateOf(initial?.barcode ?: "") }
    var nama by remember { mutableStateOf(initial?.nama ?: "") }
    var hargaBeli by remember { mutableStateOf(initial?.hargaBeli?.toString() ?: "") }
    var hargaJual by remember { mutableStateOf(initial?.hargaJual?.toString() ?: "") }
    var stok by remember { mutableStateOf(initial?.stok?.toString() ?: "0") }
    var stokMin by remember { mutableStateOf(initial?.stokMinimum?.toString() ?: "5") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Tambah Produk" else "Edit Produk") },
        text = {
            Column {
                OutlinedTextField(kode, { kode = it }, label = { Text("Kode Produk") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(barcode, { barcode = it }, label = { Text("Barcode") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(nama, { nama = it }, label = { Text("Nama Produk") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(hargaBeli, { hargaBeli = it }, label = { Text("Harga Beli") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(hargaJual, { hargaJual = it }, label = { Text("Harga Jual") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(stok, { stok = it }, label = { Text("Stok") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(stokMin, { stokMin = it }, label = { Text("Stok Minimum") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSimpan(
                    ProductEntity(
                        id = initial?.id ?: 0,
                        kodeProduk = kode,
                        barcode = barcode.ifBlank { null },
                        nama = nama,
                        categoryId = initial?.categoryId,
                        hargaBeli = hargaBeli.toDoubleOrNull() ?: 0.0,
                        hargaJual = hargaJual.toDoubleOrNull() ?: 0.0,
                        stok = stok.toIntOrNull() ?: 0,
                        stokMinimum = stokMin.toIntOrNull() ?: 5
                    )
                )
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
