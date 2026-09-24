package com.nada.kasir.feature.produk

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.nada.kasir.core.data.local.entity.ProductEntity
import com.nada.kasir.core.util.CurrencyFormatter
import com.nada.kasir.core.util.FileShareHelper
import com.nada.kasir.core.util.ProductPhotoStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Halaman DATA PRODUK (poin 6), dengan Import/Export Excel (poin 15, Phase 3). */
@Composable
fun ProdukScreen(isAdmin: Boolean = true, viewModel: ProdukViewModel = hiltViewModel()) {
    val produkList by viewModel.daftarProduk.collectAsState()
    val paketAktif by viewModel.paketAktif.collectAsState()
    val pesanImportExport by viewModel.pesanImportExport.collectAsState()
    val fileExportTerakhir by viewModel.fileExportTerakhir.collectAsState()
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ProductEntity?>(null) }
    var produkAkanDihapus by remember { mutableStateOf<ProductEntity?>(null) }
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
                        leadingContent = { ThumbnailProduk(produk.fotoPath) },
                        headlineContent = { Text(produk.nama) },
                        supportingContent = {
                            Text("${produk.kodeProduk} • Stok: ${produk.stok} • ${CurrencyFormatter.format(produk.hargaJual)}")
                        },
                        trailingContent = {
                            if (isAdmin) {
                                Row {
                                    TextButton(onClick = { editing = produk; showForm = true }) { Text("Edit") }
                                    TextButton(onClick = { produkAkanDihapus = produk }) { Text("Hapus") }
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

    produkAkanDihapus?.let { produk ->
        AlertDialog(
            onDismissRequest = { produkAkanDihapus = null },
            title = { Text("Hapus Produk") },
            text = { Text("Apakah Anda yakin ingin menghapus '${produk.nama}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.hapus(produk.id)
                        produkAkanDihapus = null
                    }
                ) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { produkAkanDihapus = null }) { Text("Batal") }
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
internal fun ProdukFormDialog(
    initial: ProductEntity?,
    onDismiss: () -> Unit,
    onSimpan: (ProductEntity) -> Unit,
    initialBarcode: String? = null // diisi dari hasil scan di layar Kasir
) {
    var kode by remember { mutableStateOf(initial?.kodeProduk ?: "") }
    var barcode by remember { mutableStateOf(initial?.barcode ?: initialBarcode ?: "") }
    var nama by remember { mutableStateOf(initial?.nama ?: "") }
    var hargaBeli by remember { mutableStateOf(initial?.hargaBeli?.toString() ?: "") }
    var hargaJual by remember { mutableStateOf(initial?.hargaJual?.toString() ?: "") }
    var stok by remember { mutableStateOf(initial?.stok?.toString() ?: "0") }
    var stokMin by remember { mutableStateOf(initial?.stokMinimum?.toString() ?: "5") }
    var fotoPath by remember { mutableStateOf(initial?.fotoPath) }
    var errorValidasi by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pilihFoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) { ProductPhotoStorageHelper.simpanFotoDariUri(context, uri) }
                if (path != null) fotoPath = path
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Tambah Produk" else "Edit Produk") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                FotoProdukPicker(fotoPath = fotoPath, onPilihFoto = { pilihFoto.launch("image/*") })
                Spacer(Modifier.height(8.dp))
                errorValidasi?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                }
                OutlinedTextField(kode, { kode = it; errorValidasi = null }, label = { Text("Kode Produk *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(barcode, { barcode = it }, label = { Text("Barcode") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(nama, { nama = it; errorValidasi = null }, label = { Text("Nama Produk *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    hargaBeli, { hargaBeli = it },
                    label = { Text("Harga Beli") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    hargaJual, { hargaJual = it },
                    label = { Text("Harga Jual") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    stok, { stok = it },
                    label = { Text("Stok") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    stokMin, { stokMin = it },
                    label = { Text("Stok Minimum") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (kode.trim().isBlank()) {
                    errorValidasi = "Kode produk tidak boleh kosong."
                    return@TextButton
                }
                if (nama.trim().isBlank()) {
                    errorValidasi = "Nama produk tidak boleh kosong."
                    return@TextButton
                }
                val hBeli = maxOf(0.0, hargaBeli.toDoubleOrNull() ?: 0.0)
                val hJual = maxOf(0.0, hargaJual.toDoubleOrNull() ?: 0.0)
                val s = maxOf(0, stok.toIntOrNull() ?: 0)
                val sMin = maxOf(0, stokMin.toIntOrNull() ?: 5)
                onSimpan(
                    ProductEntity(
                        id = initial?.id ?: 0,
                        kodeProduk = kode.trim(),
                        barcode = barcode.trim().ifBlank { null },
                        nama = nama.trim(),
                        categoryId = initial?.categoryId,
                        hargaBeli = hBeli,
                        hargaJual = hJual,
                        stok = s,
                        stokMinimum = sMin,
                        fotoPath = fotoPath
                    )
                )
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

/** Kotak persegi di form Produk untuk memilih/ganti foto dari galeri HP. */
@Composable
private fun FotoProdukPicker(fotoPath: String?, onPilihFoto: () -> Unit) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (!fotoPath.isNullOrBlank() && java.io.File(fotoPath).exists()) {
            AsyncImage(
                model = java.io.File(fotoPath),
                contentDescription = "Foto produk",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Filled.Inventory2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(28.dp)
            )
        }
        // Tombol kamera kecil di pojok, di atas foto - tap di mana saja pada kotak ini membuka galeri
        Surface(
            onClick = onPilihFoto,
            color = MaterialTheme.colorScheme.primary,
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
        ) {
            Icon(
                Icons.Filled.AddAPhoto,
                contentDescription = "Pilih foto produk",
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.padding(6.dp).size(16.dp)
            )
        }
    }
}

/** Foto kecil bulat di daftar Produk; ikon netral kalau produk belum punya foto. */
@Composable
private fun ThumbnailProduk(fotoPath: String?) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (!fotoPath.isNullOrBlank() && java.io.File(fotoPath).exists()) {
            AsyncImage(
                model = java.io.File(fotoPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Filled.Inventory2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
