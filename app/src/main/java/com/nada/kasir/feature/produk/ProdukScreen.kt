package com.nada.kasir.feature.produk

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.nada.kasir.core.data.local.entity.ProductEntity
import com.nada.kasir.core.util.CurrencyFormatter
import com.nada.kasir.core.util.FileShareHelper
import com.nada.kasir.core.util.ProductPhotoStorageHelper
import java.io.File
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
    var pesanFoto by remember { mutableStateOf<String?>(null) }
    var tampilPilihanFoto by remember { mutableStateOf(false) }
    // File sementara tempat aplikasi kamera menulis foto; null bila kamera tidak sedang dibuka.
    var fotoKameraSementara by remember { mutableStateOf<File?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Foto baru menggantikan foto sebelumnya. Foto yang dibuat di form ini tetapi belum tersimpan ke produk
    // dibuang supaya tidak menumpuk saat pengguna memotret atau memilih ulang.
    fun pakaiFoto(pathBaru: String) {
        val lama = fotoPath
        if (lama != null && lama != initial?.fotoPath) ProductPhotoStorageHelper.hapusFoto(lama)
        fotoPath = pathBaru
        pesanFoto = null
    }

    val pilihFoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) { ProductPhotoStorageHelper.simpanFotoDariUri(context, uri) }
                if (path != null) {
                    pakaiFoto(path)
                } else {
                    pesanFoto = "Foto tidak bisa dibaca. Coba pilih foto lain."
                }
            }
        }
    }

    val ambilFoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { berhasil ->
        val sementara = fotoKameraSementara
        fotoKameraSementara = null
        if (sementara != null) {
            if (berhasil) {
                scope.launch {
                    val path = withContext(Dispatchers.IO) { ProductPhotoStorageHelper.simpanFotoDariKamera(context, sementara) }
                    if (path != null) {
                        pakaiFoto(path)
                    } else {
                        pesanFoto = "Foto dari kamera gagal disimpan. Coba ambil lagi."
                    }
                }
            } else {
                sementara.delete() // dibatalkan di aplikasi kamera
            }
        }
    }

    fun bukaKamera() {
        val siap = ProductPhotoStorageHelper.siapkanFotoKamera(context)
        if (siap == null) {
            pesanFoto = "Kamera tidak bisa dibuka. Coba lagi."
            return
        }
        val (file, uri) = siap
        fotoKameraSementara = file
        try {
            ambilFoto.launch(uri)
        } catch (e: ActivityNotFoundException) {
            fotoKameraSementara = null
            file.delete()
            pesanFoto = "Aplikasi kamera tidak ditemukan di HP ini. Gunakan pilihan Galeri."
        }
    }

    val izinKamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { diizinkan ->
        if (diizinkan) {
            bukaKamera()
        } else {
            pesanFoto = "Izin kamera ditolak. Aktifkan di Pengaturan HP, atau pilih foto dari Galeri."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Tambah Produk" else "Edit Produk") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                FotoProdukPicker(fotoPath = fotoPath, onPilihFoto = { tampilPilihanFoto = true })
                pesanFoto?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
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

                if (!initial?.fotoPath.isNullOrBlank() && initial?.fotoPath != fotoPath) {
                    com.nada.kasir.core.util.ProductPhotoStorageHelper.hapusFoto(initial?.fotoPath)
                }

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

    if (tampilPilihanFoto) {
        AlertDialog(
            onDismissRequest = { tampilPilihanFoto = false },
            title = { Text("Foto Produk") },
            text = {
                Column {
                    PilihanFotoItem(Icons.Filled.PhotoCamera, "Ambil dari Kamera") {
                        tampilPilihanFoto = false
                        val sudahDiizinkan = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED
                        if (sudahDiizinkan) bukaKamera() else izinKamera.launch(Manifest.permission.CAMERA)
                    }
                    PilihanFotoItem(Icons.Filled.PhotoLibrary, "Pilih dari Galeri") {
                        tampilPilihanFoto = false
                        pilihFoto.launch("image/*")
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { tampilPilihanFoto = false }) { Text("Batal") } }
        )
    }
}

/** Satu baris pilihan (ikon + teks) di dialog pilihan sumber foto produk. */
@Composable
private fun PilihanFotoItem(ikon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(ikon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Kotak persegi di form Produk untuk menambah/mengganti foto (kamera atau galeri). */
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
        // Tombol kamera kecil di pojok, di atas foto - membuka pilihan Kamera atau Galeri
        Surface(
            onClick = onPilihFoto,
            color = MaterialTheme.colorScheme.primary,
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
        ) {
            Icon(
                Icons.Filled.AddAPhoto,
                contentDescription = "Tambah atau ganti foto produk",
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
