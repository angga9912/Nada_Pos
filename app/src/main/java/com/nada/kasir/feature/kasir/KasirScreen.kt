package com.nada.kasir.feature.kasir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import com.nada.kasir.core.data.local.entity.MetodePembayaran
import com.nada.kasir.core.util.BeepPlayer
import com.nada.kasir.core.util.CurrencyFormatter
import com.nada.kasir.core.util.HandheldScannerDetector
import com.nada.kasir.feature.kasir.barcode.BarcodeScannerScreen
import com.nada.kasir.feature.produk.ProdukFormDialog
import com.nada.kasir.feature.struk.StrukPreviewDialog

/**
 * Halaman Kasir - fitur utama aplikasi (poin 4).
 * Layout mobile-first: grid produk full-width di atas, keranjang + ringkasan +
 * tombol BAYAR SELALU terbuka langsung di bagian bawah layar begitu ada item
 * (bukan lagi bar ringkas yang perlu disentuh dulu untuk dibuka) - sesuai revisi
 * desain mockup, supaya kasir bisa langsung lihat & revisi pesanan tanpa ekstra
 * ketukan. Nama produk & qty tetap jelas terbaca karena daftar keranjang punya
 * baris lebarnya sendiri (bukan kolom sempit berbagi dengan grid).
 */
@Composable
fun KasirScreen(
    currentUserId: Long,
    isAdmin: Boolean = false,
    namaPengguna: String = "Kasir",
    viewModel: KasirViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showPembayaranDialog by remember { mutableStateOf(false) }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var tampilFormProdukBaru by remember { mutableStateOf(false) }
    // State quick action "+ Diskon" & "+ Pelanggan" di keranjang (poin desain mockup).
    // Nama pembeli disimpan di layar ini (bukan ViewModel) supaya bisa langsung mengisi
    // dialog Pembayaran tanpa kasir mengetik ulang - baru benar-benar disimpan ke transaksi
    // saat viewModel.bayar() dipanggil, alur data pembayaran tidak berubah sama sekali.
    var showDiskonDialog by remember { mutableStateOf(false) }
    var showPelangganDialog by remember { mutableStateOf(false) }
    var namaPembeliDicatat by remember { mutableStateOf("") }

    // Buffer untuk membedakan ketikan scanner fisik (handheld) vs ketikan manual kasir (poin 5, Phase 2)
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
            onTransaksiBaru = {
                viewModel.mulaiTransaksiBaru()
                namaPembeliDicatat = ""
            },
            onCetak = { viewModel.tampilkanPreviewStruk(state.transaksiBerhasilId!!) },
            onBagikan = {
                viewModel.buatTeksStruk(state.transaksiBerhasilId!!) { teks ->
                    com.nada.kasir.core.util.FileShareHelper.bagikanTeks(context, teks, "Bagikan Struk Transaksi")
                }
            }
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

    // Barcode terbaca tapi belum ada di data produk -> tawarkan tambah produk baru
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
                title = { Text("Produk belum terdaftar") },
                text = { Text("Barcode $kode belum ada di data produk. Tambahkan sebagai produk baru?") },
                confirmButton = {
                    TextButton(onClick = { tampilFormProdukBaru = true }) { Text("Tambah Produk Baru") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.tutupBarcodeBelumTerdaftar() }) { Text("Batal") }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { viewModel.tutupBarcodeBelumTerdaftar() },
                title = { Text("Produk belum terdaftar") },
                text = { Text("Barcode $kode belum ada di data produk. Minta admin untuk menambahkannya.") },
                confirmButton = {
                    TextButton(onClick = { viewModel.tutupBarcodeBelumTerdaftar() }) { Text("OK") }
                }
            )
        }
    }

    state.errorPesan?.let { pesan ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            confirmButton = { TextButton(onClick = { viewModel.clearError() }) { Text("OK") } },
            title = { Text("Perhatian") },
            text = { Text(pesan) }
        )
    }

    val jumlahDiKeranjang: (Long) -> Int = { productId ->
        state.keranjang.firstOrNull { it.productId == productId }?.qty ?: 0
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HeaderKasir(
            namaToko = state.store?.nama ?: "NADA POS",
            logoPath = state.store?.logoPath,
            namaPengguna = namaPengguna
        )
        // Area produk - full width, tidak lagi berbagi lebar dengan panel keranjang
        Column(modifier = Modifier.weight(1f).padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { teksBaru ->
                        // Deteksi karakter baru yang masuk untuk mengenali pola ketikan scanner fisik.
                        if (teksBaru.length > state.query.length) {
                            val karakterBaru = teksBaru.last()
                            if (karakterBaru == '\n') {
                                handheldDetector.onEnterOrNewline()
                                return@OutlinedTextField
                            } else {
                                handheldDetector.onCharTyped(karakterBaru)
                            }
                        }
                        viewModel.onQueryChange(teksBaru)
                    },
                    placeholder = { Text("Cari produk atau scan barcode") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { showBarcodeScanner = true },
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier.height(56.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Scan barcode dengan kamera", modifier = Modifier.size(18.dp))
                        Text("Scan", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text(
                "Scan barcode menggunakan kamera HP",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
            Spacer(Modifier.height(10.dp))
            CategoryChipsRow(
                kategori = state.kategoriList,
                kategoriTerpilih = state.kategoriTerpilihId,
                onPilih = { id, nama -> viewModel.pilihKategori(id, nama) }
            )
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                // Adaptive = jumlah kolom menyesuaikan sendiri lebar layar (HP kecil, HP besar,
                // atau tablet) - kartu diusahakan sekitar 108dp, minimal 96dp di layar tersempit.
                columns = GridCells.Adaptive(minSize = 108.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 4.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(state.produk, key = { it.id }) { produk ->
                    ProdukKasirCard(
                        nama = produk.nama,
                        harga = produk.hargaJual,
                        stok = produk.stok,
                        fotoPath = produk.fotoPath,
                        jumlahDiKeranjang = jumlahDiKeranjang(produk.id),
                        onClick = { viewModel.tambahKeKeranjang(produk) }
                    )
                }
            }
        }

        // Panel keranjang - SELALU terbuka & menempel di bagian bawah layar utama, tidak lagi
        // modal bottom sheet yang perlu disentuh dulu untuk dibuka (revisi sesuai desain mockup
        // terbaru: keranjang + ringkasan + tombol BAYAR langsung terlihat di layar Kasir).
        // Tinggi mengecil otomatis saat kosong ("empty cart state yang informatif" di desain
        // mockup) supaya grid produk di atas tetap dapat ruang paling luas.
        KeranjangPanel(
            keranjang = state.keranjang,
            subtotal = state.subtotal,
            diskonTotal = state.diskonTotal,
            total = state.total,
            namaPembeli = namaPembeliDicatat,
            isProsesBayar = state.isProsesBayar,
            onUbahQty = viewModel::ubahQty,
            onHapusItem = { productId -> viewModel.ubahQty(productId, 0) },
            onDiskonClick = { showDiskonDialog = true },
            onPelangganClick = { showPelangganDialog = true },
            onBayar = { showPembayaranDialog = true }
        )
    }

    if (showDiskonDialog) {
        DiskonDialog(
            diskonAwal = state.diskonTotal,
            onDismiss = { showDiskonDialog = false },
            onSimpan = { nilai ->
                viewModel.setDiskonTotal(nilai)
                showDiskonDialog = false
            }
        )
    }

    if (showPelangganDialog) {
        PelangganDialog(
            namaAwal = namaPembeliDicatat,
            onDismiss = { showPelangganDialog = false },
            onSimpan = { nama ->
                namaPembeliDicatat = nama
                showPelangganDialog = false
            }
        )
    }

    if (showPembayaranDialog) {
        PembayaranDialog(
            total = state.total,
            namaPembeliAwal = namaPembeliDicatat,
            onDismiss = { showPembayaranDialog = false },
            onKonfirmasi = { metode, jumlahDiterima, namaPembeli, catatanMetode ->
                showPembayaranDialog = false
                viewModel.bayar(currentUserId, metode, jumlahDiterima, namaPembeli, catatanMetode)
            }
        )
    }
}

/**
 * Top bar Kasir - identitas toko (logo + nama, pola sama seperti HeaderDashboard supaya
 * konsisten) plus status "Online", notifikasi & printer (placeholder, belum ada sistem
 * notifikasi/status printer real-time - sama seperti bel di Dashboard), dan avatar inisial
 * kasir yang sedang login. Warna tetap ikut branding dinamis toko (ThemeConfig), bukan hardcode.
 */
@Composable
private fun HeaderKasir(namaToko: String, logoPath: String?, namaPengguna: String) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                if (!logoPath.isNullOrBlank() && java.io.File(logoPath).exists()) {
                    AsyncImage(
                        model = java.io.File(logoPath),
                        contentDescription = "Logo toko",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Icon(
                        Icons.Filled.Storefront,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    namaToko,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(WarnaOnline)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Online", style = MaterialTheme.typography.labelSmall, color = WarnaOnline)
                }
            }
            IconButton(onClick = { /* Notifikasi belum tersedia di phase ini, sama seperti Dashboard */ }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.Notifications,
                    contentDescription = "Notifikasi",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = { /* Status printer real-time belum ada - atur printer di tab Pengaturan */ }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Print,
                    contentDescription = "Printer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    inisialNama(namaPengguna),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Divider()
    }
}

/** Warna status "Online" - tetap konsisten terlepas dari warna branding toko (sama seperti warna status stok di Dashboard). */
private val WarnaOnline = androidx.compose.ui.graphics.Color(0xFF2E7D32)

/** Inisial 1-2 huruf dari nama pengguna untuk avatar bulat di header (mis. "Rina Kasir" -> "RK"). */
private fun inisialNama(nama: String): String {
    val kata = nama.trim().split(" ").filter { it.isNotBlank() }
    return when {
        kata.isEmpty() -> "?"
        kata.size == 1 -> kata[0].take(2).uppercase()
        else -> (kata[0].take(1) + kata[1].take(1)).uppercase()
    }
}

/**
 * Baris chip kategori produk (horizontal scroll) untuk filter cepat di grid Kasir.
 * "Semua" merepresentasikan kategoriTerpilih == null. Data kategori & filter sudah ada
 * di KasirViewModel sebelumnya (pilihKategori) - baris ini murni UI yang sebelumnya belum ada.
 */
@Composable
private fun CategoryChipsRow(
    kategori: List<com.nada.kasir.core.data.local.entity.CategoryEntity>,
    kategoriTerpilih: Long?,
    onPilih: (Long?, String) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = kategoriTerpilih == null,
                onClick = { onPilih(null, "Semua") },
                label = { Text("Semua") }
            )
        }
        items(kategori, key = { it.id }) { kat ->
            FilterChip(
                selected = kategoriTerpilih == kat.id,
                onClick = { onPilih(kat.id, kat.nama) },
                label = { Text(kat.nama) }
            )
        }
    }
}

/**
 * Kartu produk di grid Kasir - dibuat RINGKAS (padding & teks kecil, maks 1 baris nama)
 * supaya makin banyak produk terlihat sekaligus tanpa scroll berlebihan, tapi tetap
 * NYAMAN disentuh (kartu persegi utuh yang bisa ditekan, bukan cuma teks kecil).
 * Foto produk mengisi bagian atas kartu; kalau belum ada foto, tampil ikon netral
 * supaya grid tetap rapi (bukan lubang kosong).
 */
@Composable
private fun ProdukKasirCard(
    nama: String,
    harga: Double,
    stok: Int,
    fotoPath: String?,
    jumlahDiKeranjang: Int,
    onClick: () -> Unit
) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f) // foto selalu persegi -> grid rapi walau kartu ikut melebar/menyempit
                    // Tile warna branding toko (bukan abu-abu generik) - konsisten dengan tile
                    // ikon lain di aplikasi (logo header, kartu konfirmasi scan barcode).
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (!fotoPath.isNullOrBlank() && java.io.File(fotoPath).exists()) {
                    AsyncImage(
                        model = java.io.File(fotoPath),
                        contentDescription = nama,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Filled.Inventory2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                if (jumlahDiKeranjang > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                    ) {
                        Text(
                            "$jumlahDiKeranjang",
                            color = androidx.compose.ui.graphics.Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
                if (stok <= 0) {
                    Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    ) {
                        Text(
                            "Habis",
                            color = androidx.compose.ui.graphics.Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    nama,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    CurrencyFormatter.format(harga),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1
                )
                // "Stok N" (poin desain mockup) - hanya saat stok masih ada, karena stok 0
                // sudah ditandai lewat overlay "Habis" di atas, jadi tidak perlu diulang.
                if (stok > 0) {
                    Text(
                        "Stok $stok",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Panel keranjang - SELALU terlihat menempel di bagian bawah layar Kasir (bukan modal
 * bottom sheet lagi), sesuai revisi desain mockup: kasir langsung lihat & ubah pesanan
 * tanpa harus menyentuh dulu untuk membukanya. Saat kosong, panel otomatis mengecil dan
 * hanya menampilkan pesan ("empty cart state yang informatif" sesuai prinsip UX di desain
 * mockup) supaya grid produk di atas tetap dapat ruang paling luas.
 */
@Composable
private fun KeranjangPanel(
    keranjang: List<com.nada.kasir.core.domain.model.KeranjangItem>,
    subtotal: Double,
    diskonTotal: Double,
    total: Double,
    namaPembeli: String,
    isProsesBayar: Boolean,
    onUbahQty: (Long, Int) -> Unit,
    onHapusItem: (Long) -> Unit,
    onDiskonClick: () -> Unit,
    onPelangganClick: () -> Unit,
    onBayar: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            if (keranjang.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.ShoppingCart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Keranjang masih kosong", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium))
                        Text(
                            "Pilih produk di atas untuk mulai transaksi",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                return@Column
            }

            Text(
                "Keranjang · ${keranjang.sumOf { it.qty }} item",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(Modifier.height(8.dp))

            // Tinggi maksimum item list dikecilkan dari versi sheet (320dp -> 150dp) karena
            // panel ini sekarang berbagi layar dengan grid produk di atasnya, bukan mengambil
            // alih seluruh layar seperti modal. Kalau item lebih banyak dari itu, tetap bisa
            // di-scroll di dalam area kecil ini tanpa mendorong tombol BAYAR keluar layar.
            Column(modifier = Modifier.heightIn(max = 150.dp)) {
                LazyColumn {
                    items(keranjang, key = { it.productId }) { item ->
                        KeranjangRow(
                            nama = item.nama,
                            qty = item.qty,
                            harga = item.harga,
                            subtotal = item.subtotal,
                            onQtyChange = { qtyBaru -> onUbahQty(item.productId, qtyBaru) },
                            onHapus = { onHapusItem(item.productId) }
                        )
                        Divider()
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            RingkasanBaris("Subtotal", subtotal)
            // Baris Diskon hanya muncul kalau ada nilainya, dan pakai tanda "−" manual
            // (bukan angka negatif ke CurrencyFormatter) supaya format tetap rapi "− Rp2.000".
            if (diskonTotal > 0.0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Diskon", style = MaterialTheme.typography.bodyMedium)
                    Text("− ${CurrencyFormatter.format(diskonTotal)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Divider(modifier = Modifier.padding(vertical = 6.dp))
            // Total pakai typography terbesar di panel ini (poin desain mockup) supaya
            // nominal akhir paling menonjol & gampang dibaca kasir sebelum konfirmasi bayar.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text("Total", style = MaterialTheme.typography.titleMedium)
                Text(
                    CurrencyFormatter.format(total),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(Modifier.height(10.dp))
            // Quick actions (poin desain mockup): Diskon & Pelanggan aktif sungguhan
            // (Diskon -> viewModel.setDiskonTotal, Pelanggan -> ikut mengisi dialog
            // Pembayaran). Catatan transaksi belum tersedia di fase ini - belum ada kolom
            // untuk itu di data transaksi - ditampilkan tetap sesuai desain, sama seperti
            // ikon Notifikasi/Printer di header yang juga masih placeholder.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    AksiCepatChip(
                        label = if (diskonTotal > 0.0) "Diskon ${CurrencyFormatter.format(diskonTotal)}" else "+ Diskon",
                        aktif = diskonTotal > 0.0,
                        onClick = onDiskonClick
                    )
                }
                item {
                    AksiCepatChip(
                        label = namaPembeli.ifBlank { "+ Pelanggan" },
                        aktif = namaPembeli.isNotBlank(),
                        onClick = onPelangganClick
                    )
                }
                item {
                    AksiCepatChip(label = "+ Catatan", aktif = false, onClick = {})
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onBayar,
                enabled = keranjang.isNotEmpty() && !isProsesBayar,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (isProsesBayar) {
                    Text("Memproses...", style = MaterialTheme.typography.titleMedium)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("BAYAR", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(CurrencyFormatter.format(total), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AksiCepatChip(label: String, aktif: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        colors = if (aktif) {
            AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                labelColor = MaterialTheme.colorScheme.primary
            )
        } else {
            AssistChipDefaults.assistChipColors()
        }
    )
}

@Composable
private fun KeranjangRow(nama: String, qty: Int, harga: Double, subtotal: Double, onQtyChange: (Int) -> Unit, onHapus: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(nama, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(
                "${CurrencyFormatter.format(harga)} × $qty · ${CurrencyFormatter.format(subtotal)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(4.dp))
        IconButtonQty(label = "−", onClick = { onQtyChange(qty - 1) })
        Text("$qty", modifier = Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodyMedium)
        IconButtonQty(label = "+", onClick = { onQtyChange(qty + 1) })
        // Hapus langsung dari keranjang - versi ringkas dari "swipe/delete gesture" di
        // desain mockup (swipe asli butuh dependency drag tambahan, tombol ini lebih
        // pasti disentuh jarinya di HP kecil & tetap satu langkah).
        IconButton(onClick = onHapus, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Hapus dari keranjang",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** Tombol stepper qty bulat (poin desain mockup, sebelumnya kotak). */
@Composable
private fun IconButtonQty(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp),
        contentPadding = PaddingValues(0.dp),
        shape = CircleShape
    ) {
        Text(label)
    }
}

@Composable
private fun RingkasanBaris(label: String, nilai: Double, tebal: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = if (tebal) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
        Text(
            CurrencyFormatter.format(nilai),
            style = if (tebal) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun DiskonDialog(
    diskonAwal: Double,
    onDismiss: () -> Unit,
    onSimpan: (Double) -> Unit
) {
    var nominalText by remember { mutableStateOf(if (diskonAwal > 0) diskonAwal.toLong().toString() else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Terapkan Diskon") },
        text = {
            OutlinedTextField(
                value = nominalText,
                onValueChange = { nominalText = it.filter { c -> c.isDigit() } },
                label = { Text("Nominal Diskon (Rp)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val nominal = nominalText.toDoubleOrNull() ?: 0.0
                    onSimpan(nominal)
                }
            ) { Text("Terapkan") }
        },
        dismissButton = {
            TextButton(onClick = { onSimpan(0.0) }) { Text("Hapus Diskon") }
        }
    )
}

@Composable
private fun PelangganDialog(
    namaAwal: String,
    onDismiss: () -> Unit,
    onSimpan: (String) -> Unit
) {
    var nama by remember { mutableStateOf(namaAwal) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nama Pelanggan") },
        text = {
            OutlinedTextField(
                value = nama,
                onValueChange = { nama = it },
                label = { Text("Nama Pelanggan / Catatan Meja") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSimpan(nama.trim()) }) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
private fun PembayaranDialog(
    total: Double,
    namaPembeliAwal: String = "",
    onDismiss: () -> Unit,
    onKonfirmasi: (MetodePembayaran, Double, String?, String?) -> Unit
) {
    var metode by remember { mutableStateOf(MetodePembayaran.TUNAI) }
    var uangDiterimaText by remember { mutableStateOf("") }
    // Sudah diisi lebih dulu kalau kasir pakai quick action "+ Pelanggan" di keranjang -
    // tetap bisa diubah manual di sini seperti sebelumnya.
    var namaPembeli by remember { mutableStateOf(namaPembeliAwal) }
    var catatanMetodeLainnya by remember { mutableStateOf("") }
    val uangDiterima = uangDiterimaText.toDoubleOrNull() ?: 0.0
    val kembalian = uangDiterima - total

    // Hanya metode yang benar-benar sering dipakai yang ditampilkan (Transfer/Debit/Kartu
    // Kredit disembunyikan dari kasir, tapi enum-nya tetap utuh supaya transaksi lama dengan
    // metode itu tetap bisa dibaca). Kalau pembeli bayar dengan cara lain, kasir pilih
    // "Lainnya" dan tulis manual nama metodenya (mis. "Transfer BCA").
    val metodeDitampilkan = listOf(MetodePembayaran.TUNAI, MetodePembayaran.QRIS, MetodePembayaran.LAINNYA)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pembayaran") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Total: ${CurrencyFormatter.format(total)}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = namaPembeli,
                    onValueChange = { namaPembeli = it },
                    label = { Text("Nama Pembeli (opsional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                metodeDitampilkan.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = metode == m, onClick = { metode = m })
                        Text(if (m == MetodePembayaran.LAINNYA) "Lainnya" else m.name)
                    }
                }
                if (metode == MetodePembayaran.LAINNYA) {
                    OutlinedTextField(
                        value = catatanMetodeLainnya,
                        onValueChange = { catatanMetodeLainnya = it },
                        label = { Text("Nama metode (mis. Transfer BCA)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (metode == MetodePembayaran.TUNAI) {
                    Spacer(Modifier.height(4.dp))
                    PembayaranTunaiInput(
                        uangDiterimaText = uangDiterimaText,
                        onUangDiterimaTextChange = { uangDiterimaText = it },
                        total = total
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Kembalian: ${CurrencyFormatter.format(if (kembalian > 0) kembalian else 0.0)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val jumlah = if (metode == MetodePembayaran.TUNAI) uangDiterima else total
                    val catatan = if (metode == MetodePembayaran.LAINNYA) catatanMetodeLainnya.ifBlank { null } else null
                    onKonfirmasi(metode, jumlah, namaPembeli.ifBlank { null }, catatan)
                },
                enabled = (metode != MetodePembayaran.TUNAI || uangDiterima > 0.0) &&
                    (metode != MetodePembayaran.LAINNYA || catatanMetodeLainnya.isNotBlank())
            ) { Text("Konfirmasi") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

/** Pecahan uang tunai yang paling sering dipakai pembeli untuk transaksi kasir. */
private val PECAHAN_UANG_UMUM = listOf(10_000.0, 20_000.0, 50_000.0, 100_000.0)

/**
 * Input "uang diterima" saat bayar tunai. Defaultnya kasir cukup sentuh salah satu
 * pecahan umum (10rb/20rb/50rb/100rb) atau "Uang Pas". Kalau nominal dari pembeli
 * tidak ada di pilihan itu, kasir bisa buka keypad angka bergaya kalkulator untuk
 * mengetik nominal manual - tanpa perlu keyboard sistem Android yang penuh.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PembayaranTunaiInput(
    uangDiterimaText: String,
    onUangDiterimaTextChange: (String) -> Unit,
    total: Double
) {
    var modeManual by remember { mutableStateOf(false) }
    val uangDiterima = uangDiterimaText.toDoubleOrNull() ?: 0.0

    Column {
        Text("Uang diterima", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))

        // Layar penampil nominal, mirip kalkulator, biar kasir yakin sebelum konfirmasi.
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = CurrencyFormatter.format(uangDiterima),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )
        }
        Spacer(Modifier.height(8.dp))

        // Pilihan cepat pecahan uang umum, disusun grid 2x2 supaya tiap kotak
        // cukup lebar dan nominalnya tidak terpotong (sebelumnya 1 baris isi 4).
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
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Kalau jumlah pecahan ganjil, isi slot kosong biar kotak terakhir tidak melebar sendiri.
                    if (baris.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))

        TextButton(onClick = {
            modeManual = false
            onUangDiterimaTextChange(total.toLong().toString())
        }) {
            Text("Uang Pas (${CurrencyFormatter.format(total)})")
        }

        if (!modeManual) {
            TextButton(onClick = { modeManual = true }) {
                Text("Nominal lain? Ketik manual")
            }
        } else {
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

/** Keypad angka gaya kalkulator (0-9, hapus satu digit, bersihkan semua). */
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
                        modifier = Modifier.weight(1f).height(48.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(label, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransaksiBerhasilDialog(nomorAntrian: Int?, onTransaksiBaru: () -> Unit, onCetak: () -> Unit, onBagikan: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("TRANSAKSI BERHASIL") },
        text = {
            Column {
                Text("Transaksi telah tersimpan.")
                nomorAntrian?.let {
                    Spacer(Modifier.height(12.dp))
                    Text("Nomor Antrian", style = MaterialTheme.typography.labelMedium)
                    Text("$it", style = MaterialTheme.typography.displaySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onTransaksiBaru) { Text("Transaksi Baru") } },
        dismissButton = {
            Row {
                TextButton(onClick = onCetak) { Text("Cetak Struk") }
                TextButton(onClick = onBagikan) { Text("Bagikan") }
            }
        }
    )
}
