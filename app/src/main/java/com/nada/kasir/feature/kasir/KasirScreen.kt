package com.nada.kasir.feature.kasir

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.nada.kasir.core.data.local.entity.MetodePembayaran
import com.nada.kasir.core.data.local.entity.ProductEntity
import com.nada.kasir.core.domain.model.KeranjangItem
import com.nada.kasir.core.util.BeepPlayer
import com.nada.kasir.core.util.CurrencyFormatter
import com.nada.kasir.core.util.HandheldScannerDetector
import com.nada.kasir.feature.kasir.barcode.BarcodeScannerScreen
import com.nada.kasir.feature.produk.ProdukFormDialog
import com.nada.kasir.feature.struk.StrukPreviewDialog

// Brand Color Palette NADA POS
private val NadaBlue = Color(0xFF1976D2)
private val NadaBlueDark = Color(0xFF0D47A1)
private val NadaBlueLight = Color(0xFFE3F2FD)
private val NadaBackground = Color(0xFFF8FAFC)
private val NadaSurface = Color.White
private val NadaTextDark = Color(0xFF0F172A)
private val NadaTextMuted = Color(0xFF64748B)
private val NadaBorder = Color(0xFFE2E8F0)
private val NadaSuccess = Color(0xFF16A34A)
private val NadaSuccessLight = Color(0xFFDCFCE7)
private val NadaWarning = Color(0xFFD97706)
private val NadaWarningLight = Color(0xFFFEF3C7)

/**
 * Halaman Utama Kasir NADA POS.
 * High-fidelity, touch-friendly, mobile-first retail cashier interface.
 * Alur interaksi utama: SCAN -> ADD -> REVIEW -> PAY.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KasirScreen(
    currentUserId: Long,
    isAdmin: Boolean = false,
    viewModel: KasirViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showPembayaranDialog by remember { mutableStateOf(false) }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var showDiskonDialog by remember { mutableStateOf(false) }
    var showPelangganDialog by remember { mutableStateOf(false) }
    var showCatatanDialog by remember { mutableStateOf(false) }
    var showMenuLainnya by remember { mutableStateOf(false) }
    var tampilFormProdukBaru by remember { mutableStateOf(false) }

    // Buffer deteksi barcode scanner fisik (handheld)
    val handheldDetector = remember {
        HandheldScannerDetector(onBarcodeTerdeteksi = { kode ->
            BeepPlayer.beep()
            viewModel.tambahDariBarcode(kode)
            viewModel.onQueryChange("")
        })
    }

    if (showBarcodeScanner) {
        BarcodeScannerScreen(
            onDetected = { kode ->
                BeepPlayer.beep()
                showBarcodeScanner = false
                viewModel.tambahDariBarcode(kode)
            },
            onClose = { showBarcodeScanner = false }
        )
        return
    }

    if (state.transaksiBerhasilId != null) {
        TransaksiBerhasilDialog(
            nomorAntrian = state.nomorAntrianBerhasil,
            onTransaksiBaru = { viewModel.mulaiTransaksiBaru() },
            onCetak = { viewModel.tampilkanPreviewStruk(state.transaksiBerhasilId!!) },
            onBagikan = { /* Share struk */ }
        )
        state.previewStruk?.let { teks ->
            StrukPreviewDialog(
                teksStruk = teks,
                sedangMencetak = state.sedangMencetak,
                onCetak = { viewModel.cetakDariPreview(state.transaksiBerhasilId!!) },
                onTutup = { viewModel.tutupPreviewStruk() }
            )
        }
        return
    }

    // Barcode baru belum terdaftar
    state.barcodeBelumTerdaftar?.let { kode ->
        if (isAdmin && tampilFormProdukBaru) {
            ProdukFormDialog(
                initial = null,
                initialBarcode = kode,
                onDismiss = {
                    tampilFormProdukBaru = false
                    viewModel.tutupBarcodeBelumTerdaftar()
                },
                onSimpan = { produk ->
                    tampilFormProdukBaru = false
                    viewModel.simpanProdukBaru(produk)
                }
            )
        } else if (isAdmin) {
            AlertDialog(
                onDismissRequest = { viewModel.tutupBarcodeBelumTerdaftar() },
                title = { Text("Produk belum terdaftar", fontWeight = FontWeight.Bold) },
                text = { Text("Barcode $kode belum ada di data produk. Tambahkan sebagai produk baru?") },
                confirmButton = {
                    Button(
                        onClick = { tampilFormProdukBaru = true },
                        colors = ButtonDefaults.buttonColors(containerColor = NadaBlue)
                    ) { Text("Tambah Produk Baru") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.tutupBarcodeBelumTerdaftar() }) { Text("Batal") }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { viewModel.tutupBarcodeBelumTerdaftar() },
                title = { Text("Produk belum terdaftar", fontWeight = FontWeight.Bold) },
                text = { Text("Barcode $kode belum ada di data produk. Minta admin untuk menambahkannya.") },
                confirmButton = {
                    Button(
                        onClick = { viewModel.tutupBarcodeBelumTerdaftar() },
                        colors = ButtonDefaults.buttonColors(containerColor = NadaBlue)
                    ) { Text("OK") }
                }
            )
        }
    }

    state.errorPesan?.let { pesan ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearError() },
                    colors = ButtonDefaults.buttonColors(containerColor = NadaBlue)
                ) { Text("OK") }
            },
            title = { Text("Perhatian", fontWeight = FontWeight.Bold) },
            text = { Text(pesan) }
        )
    }

    val jumlahDiKeranjang: (Long) -> Int = { productId ->
        state.keranjang.firstOrNull { it.productId == productId }?.qty ?: 0
    }

    val defaultKategori = listOf("Semua", "Minuman", "Makanan", "Snack", "Sembako", "Lainnya")
    val daftarKategoriNama = remember(state.kategoriList) {
        val list = mutableListOf("Semua")
        if (state.kategoriList.isNotEmpty()) {
            list.addAll(state.kategoriList.map { it.nama })
        } else {
            list.addAll(listOf("Minuman", "Makanan", "Snack", "Sembako", "Lainnya"))
        }
        list.distinct()
    }

    Scaffold(
        containerColor = NadaBackground,
        topBar = {
            KasirTopBar(
                namaToko = state.store?.nama ?: "Toko Kita",
                kasirNama = state.cashierName,
                isPrinterReady = state.printerTersedia
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 1. SEARCH + CAMERA SCAN BAR
            SearchScanSection(
                query = state.query,
                onQueryChange = { teksBaru ->
                    if (teksBaru.length > state.query.length) {
                        val karakterBaru = teksBaru.last()
                        if (karakterBaru == '\n') {
                            handheldDetector.onEnterOrNewline()
                            return@SearchScanSection
                        } else {
                            handheldDetector.onCharTyped(karakterBaru)
                        }
                    }
                    viewModel.onQueryChange(teksBaru)
                },
                onScanClick = { showBarcodeScanner = true }
            )

            // 2. HORIZONTAL CATEGORY CHIPS
            CategoryChipsSection(
                categories = daftarKategoriNama,
                selectedCategory = state.kategoriTerpilihNama,
                onCategorySelect = { kategori ->
                    val matching = state.kategoriList.firstOrNull { it.nama.equals(kategori, ignoreCase = true) }
                    viewModel.pilihKategori(matching?.id, kategori)
                }
            )

            // 3. PRODUCT GRID (2 COLUMNS)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (state.produk.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                Icons.Filled.Inventory2,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = NadaTextMuted.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (state.query.isNotBlank()) "Produk tidak ditemukan" else "Belum ada produk",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NadaTextDark
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Scan barcode atau cari nama produk lain",
                                style = MaterialTheme.typography.bodySmall,
                                color = NadaTextMuted
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.produk, key = { it.id }) { produk ->
                            ProductCard(
                                produk = produk,
                                qtyDiKeranjang = jumlahDiKeranjang(produk.id),
                                onTambah = {
                                    BeepPlayer.beep()
                                    viewModel.tambahKeKeranjang(produk)
                                }
                            )
                        }
                    }
                }
            }

            // 4. CART & CHECKOUT PANEL (STICKY BOTTOM)
            if (state.keranjang.isNotEmpty()) {
                CartBottomPanel(
                    keranjang = state.keranjang,
                    subtotal = state.subtotal,
                    diskonTotal = state.diskonTotal,
                    total = state.total,
                    namaPelanggan = state.namaPelanggan,
                    catatan = state.catatanTransaksi,
                    onUbahQty = viewModel::ubahQty,
                    onHapusItem = viewModel::hapusItemKeranjang,
                    onBukaDiskon = { showDiskonDialog = true },
                    onBukaPelanggan = { showPelangganDialog = true },
                    onBukaCatatan = { showCatatanDialog = true },
                    onMenuLainnya = { showMenuLainnya = true },
                    onBayar = { showPembayaranDialog = true }
                )
            }
        }
    }

    // Dialog Tambah Diskon
    if (showDiskonDialog) {
        DiskonDialog(
            diskonAwal = state.diskonTotal,
            subtotal = state.subtotal,
            onDismiss = { showDiskonDialog = false },
            onTerapkan = { nominal ->
                viewModel.setDiskonTotal(nominal)
                showDiskonDialog = false
            }
        )
    }

    // Dialog Nama Pelanggan
    if (showPelangganDialog) {
        PelangganDialog(
            namaAwal = state.namaPelanggan,
            onDismiss = { showPelangganDialog = false },
            onSimpan = { nama ->
                viewModel.setNamaPelanggan(nama)
                showPelangganDialog = false
            }
        )
    }

    // Dialog Catatan Transaksi
    if (showCatatanDialog) {
        CatatanDialog(
            catatanAwal = state.catatanTransaksi,
            onDismiss = { showCatatanDialog = false },
            onSimpan = { catatan ->
                viewModel.setCatatan(catatan)
                showCatatanDialog = false
            }
        )
    }

    // Menu Aksi Lainnya
    if (showMenuLainnya) {
        AlertDialog(
            onDismissRequest = { showMenuLainnya = false },
            title = { Text("Aksi Keranjang", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            viewModel.kosongkanKeranjang()
                            showMenuLainnya = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(12.dp))
                            Text("Kosongkan Keranjang", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMenuLainnya = false }) { Text("Tutup") }
            }
        )
    }

    // Dialog Pembayaran
    if (showPembayaranDialog) {
        PembayaranDialog(
            total = state.total,
            namaPelangganDefault = state.namaPelanggan,
            onDismiss = { showPembayaranDialog = false },
            onKonfirmasi = { metode, jumlahDiterima, namaPembeli, catatanMetode ->
                showPembayaranDialog = false
                viewModel.bayar(currentUserId, metode, jumlahDiterima, namaPembeli, catatanMetode)
            }
        )
    }
}

/**
 * 1. TOP BAR
 * Ringkas, modern, menyajikan info penting toko & status online.
 */
@Composable
private fun KasirTopBar(
    namaToko: String,
    kasirNama: String,
    isPrinterReady: Boolean
) {
    Surface(
        color = NadaSurface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Kiri: Brand + Toko + Status Online
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = NadaBlue,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.PointOfSale,
                            contentDescription = "NADA POS",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "NADA POS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = NadaTextDark,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            namaToko,
                            style = MaterialTheme.typography.bodySmall,
                            color = NadaTextMuted,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("•", color = NadaTextMuted, fontSize = 10.sp)
                        Spacer(Modifier.width(6.dp))
                        // Status pill "Online"
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = NadaSuccessLight,
                            contentColor = NadaSuccess
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(NadaSuccess, CircleShape)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Online",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Kanan: Ikon Printer, Notifikasi, Avatar Kasir
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { /* Status printer */ }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Print,
                        contentDescription = "Printer",
                        tint = if (isPrinterReady) NadaBlue else NadaTextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = { /* Notifikasi */ }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.Notifications,
                        contentDescription = "Notifikasi",
                        tint = NadaTextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                // Avatar Kasir
                Surface(
                    shape = CircleShape,
                    color = NadaBlueLight,
                    border = BorderStroke(1.5.dp, NadaBlue.copy(alpha = 0.3f)),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            kasirNama.take(1).uppercase(),
                            color = NadaBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 2. SEARCH + SCAN BARCODE
 * Search bar besar dengan tombol aksi kamera ponsel yang mencolok.
 */
@Composable
private fun SearchScanSection(
    query: String,
    onQueryChange: (String) -> Unit,
    onScanClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    "Cari produk atau scan barcode",
                    fontSize = 13.sp,
                    color = NadaTextMuted
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Cari",
                    tint = NadaTextMuted,
                    modifier = Modifier.size(22.dp)
                )
            },
            trailingIcon = {
                Surface(
                    onClick = onScanClick,
                    shape = RoundedCornerShape(12.dp),
                    color = NadaBlue,
                    contentColor = Color.White,
                    modifier = Modifier
                        .height(36.dp)
                        .padding(end = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "Scan",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "Scan",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            },
            shape = RoundedCornerShape(18.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Microcopy panduan scan
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 6.dp, top = 4.dp)
        ) {
            Text(
                "📷 Scan barcode menggunakan kamera HP",
                style = MaterialTheme.typography.bodySmall,
                color = NadaTextMuted,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * 3. KATEGORI PRODUK
 * Horizontal category chips dengan highlight NADA Blue.
 */
@Composable
private fun CategoryChipsSection(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { kategori ->
            val isAktif = kategori.equals(selectedCategory, ignoreCase = true)
            Surface(
                onClick = { onCategorySelect(kategori) },
                shape = RoundedCornerShape(12.dp),
                color = if (isAktif) NadaBlue else NadaSurface,
                border = if (isAktif) null else BorderStroke(1.dp, NadaBorder),
                shadowElevation = if (isAktif) 2.dp else 0.dp,
                modifier = Modifier.height(38.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 14.dp)
                ) {
                    Text(
                        kategori,
                        fontSize = 13.sp,
                        fontWeight = if (isAktif) FontWeight.Bold else FontWeight.Medium,
                        color = if (isAktif) Color.White else NadaTextDark
                    )
                }
            }
        }
    }
}

/**
 * 4. PRODUCT CARD (GRID 2 KOLOM)
 * Kartu produk minimalis, harga kontras tinggi, foto, stok, dan tombol (+)
 */
@Composable
private fun ProductCard(
    produk: ProductEntity,
    qtyDiKeranjang: Int,
    onTambah: () -> Unit
) {
    Surface(
        onClick = onTambah,
        shape = RoundedCornerShape(16.dp),
        color = NadaSurface,
        border = BorderStroke(1.dp, if (qtyDiKeranjang > 0) NadaBlue.copy(alpha = 0.5f) else NadaBorder),
        shadowElevation = if (qtyDiKeranjang > 0) 2.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Gambar Produk + Badge Qty Keranjang
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(NadaBackground),
                contentAlignment = Alignment.Center
            ) {
                if (!produk.fotoPath.isNullOrBlank()) {
                    AsyncImage(
                        model = produk.fotoPath,
                        contentDescription = produk.nama,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Filled.ShoppingBag,
                        contentDescription = null,
                        tint = NadaBlue.copy(alpha = 0.35f),
                        modifier = Modifier.size(44.dp)
                    )
                }

                // Badge kuantitas jika sudah ada di keranjang
                if (qtyDiKeranjang > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NadaBlue,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    ) {
                        Text(
                            "${qtyDiKeranjang}x",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Info Produk
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    produk.nama,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = NadaTextDark,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.height(38.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    CurrencyFormatter.format(produk.hargaJual),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = NadaBlue
                )
                Spacer(Modifier.height(6.dp))

                // Baris Bawah: Stok & Tombol Tambah
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val stokRendah = produk.stok <= 5
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (stokRendah) NadaWarningLight else NadaBackground
                    ) {
                        Text(
                            "Stok ${produk.stok}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (stokRendah) NadaWarning else NadaTextMuted,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Tombol Tambah (+)
                    Surface(
                        shape = CircleShape,
                        color = NadaBlueLight,
                        border = BorderStroke(1.dp, NadaBlue.copy(alpha = 0.3f)),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Tambah",
                                tint = NadaBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 5, 6, 7 & 8. CART & CHECKOUT PANEL
 * Menggabungkan Keranjang, Quick Actions, Ringkasan Transaksi, dan Tombol BAYAR.
 */
@Composable
private fun CartBottomPanel(
    keranjang: List<KeranjangItem>,
    subtotal: Double,
    diskonTotal: Double,
    total: Double,
    namaPelanggan: String,
    catatan: String,
    onUbahQty: (Long, Int) -> Unit,
    onHapusItem: (Long) -> Unit,
    onBukaDiskon: () -> Unit,
    onBukaPelanggan: () -> Unit,
    onBukaCatatan: () -> Unit,
    onMenuLainnya: () -> Unit,
    onBayar: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        color = NadaSurface,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, NadaBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Header Keranjang & Toggle Expand
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.ShoppingBag,
                        contentDescription = null,
                        tint = NadaBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Keranjang · ${keranjang.sumOf { it.qty }} item",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NadaTextDark
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (expanded) "Sembunyikan" else "Rincian",
                        style = MaterialTheme.typography.labelMedium,
                        color = NadaBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        if (expanded) Icons.Filled.Close else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = NadaBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Daftar Item Keranjang (tampil 1 item ringkas saat collapsed, full list saat expanded)
            val itemTampil = if (expanded) keranjang else keranjang.take(1)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                itemTampil.forEach { item ->
                    CartItemRow(
                        item = item,
                        onUbahQty = { qtyBaru -> onUbahQty(item.productId, qtyBaru) },
                        onHapus = { onHapusItem(item.productId) }
                    )
                }
                if (!expanded && keranjang.size > 1) {
                    Text(
                        "+ ${keranjang.size - 1} item lainnya...",
                        fontSize = 11.sp,
                        color = NadaTextMuted,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }
            }

            // Quick Actions: + Diskon, + Pelanggan, + Catatan, ⋮ Lainnya
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QuickActionChip(
                    label = if (diskonTotal > 0) "-${CurrencyFormatter.format(diskonTotal)}" else "+ Diskon",
                    isHighlight = diskonTotal > 0,
                    onClick = onBukaDiskon,
                    modifier = Modifier.weight(1f)
                )
                QuickActionChip(
                    label = if (namaPelanggan.isNotBlank()) namaPelanggan else "+ Pelanggan",
                    isHighlight = namaPelanggan.isNotBlank(),
                    onClick = onBukaPelanggan,
                    modifier = Modifier.weight(1f)
                )
                QuickActionChip(
                    label = if (catatan.isNotBlank()) "Ada Catatan" else "+ Catatan",
                    isHighlight = catatan.isNotBlank(),
                    onClick = onBukaCatatan,
                    modifier = Modifier.weight(1f)
                )
                QuickActionChip(
                    label = "⋮ Lainnya",
                    isHighlight = false,
                    onClick = onMenuLainnya,
                    modifier = Modifier.width(72.dp)
                )
            }

            Divider(color = NadaBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 6.dp))

            // Transaction Summary: Subtotal, Diskon, TOTAL
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row {
                        Text("Subtotal", fontSize = 12.sp, color = NadaTextMuted)
                        Spacer(Modifier.width(8.dp))
                        Text(CurrencyFormatter.format(subtotal), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = NadaTextDark)
                    }
                    if (diskonTotal > 0) {
                        Row {
                            Text("Diskon", fontSize = 12.sp, color = NadaWarning)
                            Spacer(Modifier.width(8.dp))
                            Text("-${CurrencyFormatter.format(diskonTotal)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NadaWarning)
                        }
                    }
                }

                // TOTAL (Largest typography, highest contrast)
                Column(horizontalAlignment = Alignment.End) {
                    Text("TOTAL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NadaTextMuted)
                    Text(
                        CurrencyFormatter.format(total),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = NadaBlue
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Primary Payment Button (Main CTA)
            Button(
                onClick = onBayar,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NadaBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "BAYAR",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Text(
                            "F4 — Bayar",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            CurrencyFormatter.format(total),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Baris item di dalam keranjang belanja dengan stepper [- Qty +]
 */
@Composable
private fun CartItemRow(
    item: KeranjangItem,
    onUbahQty: (Int) -> Unit,
    onHapus: () -> Unit
) {
    Surface(
        color = NadaBackground,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.nama,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = NadaTextDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${CurrencyFormatter.format(item.harga)} × ${item.qty} = ${CurrencyFormatter.format(item.harga * item.qty)}",
                    fontSize = 12.sp,
                    color = NadaTextMuted
                )
            }

            // Stepper [-] qty [+]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    onClick = { onUbahQty(item.qty - 1) },
                    shape = CircleShape,
                    color = NadaSurface,
                    border = BorderStroke(1.dp, NadaBorder),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("-", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NadaTextDark)
                    }
                }

                Text(
                    "${item.qty}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = NadaTextDark,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )

                Surface(
                    onClick = { onUbahQty(item.qty + 1) },
                    shape = CircleShape,
                    color = NadaBlueLight,
                    border = BorderStroke(1.dp, NadaBlue.copy(alpha = 0.3f)),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Tambah",
                            tint = NadaBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Quick Action Chip Button (+ Diskon, + Pelanggan, dsb.)
 */
@Composable
private fun QuickActionChip(
    label: String,
    isHighlight: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isHighlight) NadaBlueLight else NadaSurface,
        border = BorderStroke(1.dp, if (isHighlight) NadaBlue else NadaBorder),
        modifier = modifier.height(34.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Medium,
                color = if (isHighlight) NadaBlue else NadaTextDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Dialog Pemberian Diskon
 */
@Composable
private fun DiskonDialog(
    diskonAwal: Double,
    subtotal: Double,
    onDismiss: () -> Unit,
    onTerapkan: (Double) -> Unit
) {
    var nominalText by remember { mutableStateOf(if (diskonAwal > 0) diskonAwal.toLong().toString() else "") }
    var modePersen by remember { mutableStateOf(false) }
    var persenText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Terapkan Diskon", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = !modePersen,
                        onClick = { modePersen = false },
                        label = { Text("Nominal (Rp)") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = modePersen,
                        onClick = { modePersen = true },
                        label = { Text("Persentase (%)") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))

                if (!modePersen) {
                    OutlinedTextField(
                        value = nominalText,
                        onValueChange = { nominalText = it.filter { c -> c.isDigit() } },
                        label = { Text("Nominal Diskon (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = persenText,
                        onValueChange = { persenText = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Persen Diskon (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val nominal = if (!modePersen) {
                        nominalText.toDoubleOrNull() ?: 0.0
                    } else {
                        val persen = persenText.toDoubleOrNull() ?: 0.0
                        (subtotal * (persen / 100.0)).coerceAtMost(subtotal)
                    }
                    onTerapkan(nominal)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NadaBlue)
            ) { Text("Terapkan") }
        },
        dismissButton = {
            TextButton(onClick = { onTerapkan(0.0) }) { Text("Hapus Diskon") }
        }
    )
}

/**
 * Dialog Input Nama Pelanggan
 */
@Composable
private fun PelangganDialog(
    namaAwal: String,
    onDismiss: () -> Unit,
    onSimpan: (String) -> Unit
) {
    var nama by remember { mutableStateOf(namaAwal) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nama Pelanggan / Meja", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = nama,
                onValueChange = { nama = it },
                label = { Text("Nama atau Nomor Meja") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onSimpan(nama.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = NadaBlue)
            ) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

/**
 * Dialog Input Catatan Transaksi
 */
@Composable
private fun CatatanDialog(
    catatanAwal: String,
    onDismiss: () -> Unit,
    onSimpan: (String) -> Unit
) {
    var catatan by remember { mutableStateOf(catatanAwal) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Catatan Transaksi", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = catatan,
                onValueChange = { catatan = it },
                label = { Text("Tulis catatan transaksi...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        },
        confirmButton = {
            Button(
                onClick = { onSimpan(catatan.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = NadaBlue)
            ) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

/**
 * Dialog Pembayaran (Tunai, QRIS, Lainnya)
 */
@Composable
private fun PembayaranDialog(
    total: Double,
    namaPelangganDefault: String = "",
    onDismiss: () -> Unit,
    onKonfirmasi: (MetodePembayaran, Double, String?, String?) -> Unit
) {
    var metode by remember { mutableStateOf(MetodePembayaran.TUNAI) }
    var uangDiterimaText by remember { mutableStateOf("") }
    var namaPembeli by remember { mutableStateOf(namaPelangganDefault) }
    var catatanMetodeLainnya by remember { mutableStateOf("") }
    val uangDiterima = uangDiterimaText.toDoubleOrNull() ?: 0.0
    val kembalian = uangDiterima - total

    val metodeDitampilkan = listOf(MetodePembayaran.TUNAI, MetodePembayaran.QRIS, MetodePembayaran.LAINNYA)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pembayaran", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Surface(
                    color = NadaBlueLight,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total Tagihan", fontWeight = FontWeight.Medium, color = NadaTextDark)
                        Text(
                            CurrencyFormatter.format(total),
                            fontWeight = FontWeight.ExtraBold,
                            color = NadaBlue,
                            fontSize = 18.sp
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = namaPembeli,
                    onValueChange = { namaPembeli = it },
                    label = { Text("Nama Pembeli (opsional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                Text("Metode Pembayaran", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    metodeDitampilkan.forEach { m ->
                        val isPilih = metode == m
                        Surface(
                            onClick = { metode = m },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isPilih) NadaBlue else NadaSurface,
                            border = BorderStroke(1.dp, if (isPilih) NadaBlue else NadaBorder),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    if (m == MetodePembayaran.LAINNYA) "Lainnya" else m.name,
                                    color = if (isPilih) Color.White else NadaTextDark,
                                    fontWeight = if (isPilih) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                if (metode == MetodePembayaran.LAINNYA) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = catatanMetodeLainnya,
                        onValueChange = { catatanMetodeLainnya = it },
                        label = { Text("Nama metode (mis. Transfer BCA)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (metode == MetodePembayaran.TUNAI) {
                    Spacer(Modifier.height(10.dp))
                    PembayaranTunaiInput(
                        uangDiterimaText = uangDiterimaText,
                        onUangDiterimaTextChange = { uangDiterimaText = it },
                        total = total
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Kembalian", fontWeight = FontWeight.SemiBold)
                        Text(
                            CurrencyFormatter.format(if (kembalian > 0) kembalian else 0.0),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (kembalian >= 0) NadaSuccess else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val jumlah = if (metode == MetodePembayaran.TUNAI) uangDiterima else total
                    val catatan = if (metode == MetodePembayaran.LAINNYA) catatanMetodeLainnya.ifBlank { null } else null
                    onKonfirmasi(metode, jumlah, namaPembeli.ifBlank { null }, catatan)
                },
                enabled = (metode != MetodePembayaran.TUNAI || uangDiterima >= total) &&
                        (metode != MetodePembayaran.LAINNYA || catatanMetodeLainnya.isNotBlank()),
                colors = ButtonDefaults.buttonColors(containerColor = NadaBlue)
            ) { Text("Konfirmasi Bayar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

private val PECAHAN_UANG_UMUM = listOf(10_000.0, 20_000.0, 50_000.0, 100_000.0)

@Composable
private fun PembayaranTunaiInput(
    uangDiterimaText: String,
    onUangDiterimaTextChange: (String) -> Unit,
    total: Double
) {
    var modeManual by remember { mutableStateOf(false) }
    val uangDiterima = uangDiterimaText.toDoubleOrNull() ?: 0.0

    Column {
        Text("Uang Diterima", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))

        Surface(
            color = NadaBackground,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, NadaBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = CurrencyFormatter.format(uangDiterima),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = NadaBlue,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            )
        }
        Spacer(Modifier.height(8.dp))

        // Pilihan cepat pecahan uang umum
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            PECAHAN_UANG_UMUM.chunked(2).forEach { baris ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    baris.forEach { nominal ->
                        FilterChip(
                            selected = !modeManual && uangDiterima == nominal,
                            onClick = {
                                modeManual = false
                                onUangDiterimaTextChange(nominal.toLong().toString())
                            },
                            label = {
                                Text(
                                    CurrencyFormatter.format(nominal),
                                    maxLines = 1,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (baris.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = {
                modeManual = false
                onUangDiterimaTextChange(total.toLong().toString())
            }) {
                Text("Uang Pas (${CurrencyFormatter.format(total)})", fontWeight = FontWeight.Bold, color = NadaBlue)
            }

            TextButton(onClick = { modeManual = !modeManual }) {
                Text(if (modeManual) "Pecahan Umum" else "Ketik Manual", color = NadaTextMuted)
            }
        }

        if (modeManual) {
            Spacer(Modifier.height(4.dp))
            KeypadKalkulator(
                onAngka = { digit ->
                    val gabungan = (uangDiterimaText + digit).trimStart('0')
                    onUangDiterimaTextChange(gabungan)
                },
                onHapus = { onUangDiterimaTextChange(uangDiterimaText.dropLast(1)) },
                onBersihkan = { onUangDiterimaTextChange("") }
            )
        }
    }
}

@Composable
private fun KeypadKalkulator(onAngka: (String) -> Unit, onHapus: () -> Unit, onBersihkan: () -> Unit) {
    val barisTombol = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("C", "0", "⌫")
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        barisTombol.forEach { baris ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                baris.forEach { label ->
                    OutlinedButton(
                        onClick = {
                            when (label) {
                                "C" -> onBersihkan()
                                "⌫" -> onHapus()
                                else -> onAngka(label)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransaksiBerhasilDialog(
    nomorAntrian: Int?,
    onTransaksiBaru: () -> Unit,
    onCetak: () -> Unit,
    onBagikan: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = NadaSuccess)
                Spacer(Modifier.width(8.dp))
                Text("TRANSAKSI BERHASIL", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text("Transaksi telah berhasil disimpan.")
                nomorAntrian?.let {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = NadaBlueLight,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text("Nomor Antrian", style = MaterialTheme.typography.labelMedium, color = NadaTextMuted)
                            Text("$it", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, color = NadaBlue)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onTransaksiBaru,
                colors = ButtonDefaults.buttonColors(containerColor = NadaBlue)
            ) { Text("Transaksi Baru") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCetak) { Text("Cetak Struk") }
                TextButton(onClick = onBagikan) { Text("Bagikan") }
            }
        }
    )
}
