package com.nada.kasir.feature.kasir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Inventory2
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
 * Layout mobile-first: grid produk full-width di atas, keranjang sebagai
 * bottom bar ringkas (selalu terlihat) yang bisa di-expand jadi bottom sheet
 * penuh saat disentuh - supaya nama produk & qty selalu jelas terbaca saat
 * kasir/pembeli merevisi pesanan, tidak terpotong seperti layout kolom sempit.
 */
@Composable
fun KasirScreen(
    currentUserId: Long,
    isAdmin: Boolean = false,
    viewModel: KasirViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showPembayaranDialog by remember { mutableStateOf(false) }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var showKeranjangSheet by remember { mutableStateOf(false) }
    var tampilFormProdukBaru by remember { mutableStateOf(false) }

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
            onTransaksiBaru = { viewModel.mulaiTransaksiBaru() },
            onCetak = { viewModel.tampilkanPreviewStruk(state.transaksiBerhasilId!!) },
            onBagikan = { /* TODO Phase 3: share struk via FileProvider */ }
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
        // Area produk - full width, tidak lagi berbagi lebar dengan panel keranjang
        Column(modifier = Modifier.weight(1f).padding(12.dp)) {
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
                label = { Text("Cari produk / kode, atau scan dengan alat scanner") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { showBarcodeScanner = true }) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Scan barcode dengan kamera")
                    }
                }
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

        // Bar keranjang ringkas - SELALU terlihat di bawah, tidak pernah membuat nama produk
        // di keranjang tersembunyi/terpotong. Disentuh untuk lihat & revisi detail pesanan.
        if (state.keranjang.isNotEmpty()) {
            KeranjangBarRingkas(
                jumlahItem = state.keranjang.sumOf { it.qty },
                total = state.total,
                onClick = { showKeranjangSheet = true }
            )
        }
    }

    if (showKeranjangSheet) {
        KeranjangBottomSheet(
            keranjang = state.keranjang,
            subtotal = state.subtotal,
            diskonTotal = state.diskonTotal,
            total = state.total,
            isProsesBayar = state.isProsesBayar,
            onUbahQty = viewModel::ubahQty,
            onTutup = { showKeranjangSheet = false },
            onBayar = {
                showKeranjangSheet = false
                showPembayaranDialog = true
            }
        )
    }

    if (showPembayaranDialog) {
        PembayaranDialog(
            total = state.total,
            onDismiss = { showPembayaranDialog = false },
            onKonfirmasi = { metode, jumlahDiterima, namaPembeli, catatanMetode ->
                showPembayaranDialog = false
                viewModel.bayar(currentUserId, metode, jumlahDiterima, namaPembeli, catatanMetode)
            }
        )
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
                    .background(MaterialTheme.colorScheme.surfaceVariant),
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(28.dp)
                    )
                }
                if (jumlahDiKeranjang > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(bottomStart = 8.dp),
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Text(
                            "$jumlahDiKeranjang",
                            color = androidx.compose.ui.graphics.Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
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
            }
        }
    }
}

/** Bar ringkas selalu terlihat di bagian bawah layar Kasir - tap untuk buka detail keranjang. */
@Composable
private fun KeranjangBarRingkas(jumlahItem: Int, total: Double, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "$jumlahItem item di keranjang",
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    CurrencyFormatter.format(total),
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Lihat Keranjang", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.bodyMedium)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White
                )
            }
        }
    }
}

/**
 * Bottom sheet keranjang - full width, jadi nama produk & kontrol qty selalu
 * jelas terbaca. Di sinilah pembeli/kasir merevisi pesanan (ubah qty, hapus item).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun KeranjangBottomSheet(
    keranjang: List<com.nada.kasir.core.domain.model.KeranjangItem>,
    subtotal: Double,
    diskonTotal: Double,
    total: Double,
    isProsesBayar: Boolean,
    onUbahQty: (Long, Int) -> Unit,
    onTutup: () -> Unit,
    onBayar: () -> Unit
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onTutup, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("Keranjang (${keranjang.sumOf { it.qty }} item)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Column(modifier = Modifier.heightIn(max = 360.dp)) {
                LazyColumn {
                    items(keranjang, key = { it.productId }) { item ->
                        KeranjangRow(
                            nama = item.nama,
                            qty = item.qty,
                            harga = item.harga,
                            subtotal = item.subtotal,
                            onQtyChange = { qtyBaru -> onUbahQty(item.productId, qtyBaru) }
                        )
                        Divider()
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            RingkasanBaris("Subtotal", subtotal)
            RingkasanBaris("Diskon", diskonTotal)
            Divider(modifier = Modifier.padding(vertical = 6.dp))
            RingkasanBaris("Total", total, tebal = true)

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onBayar,
                enabled = keranjang.isNotEmpty() && !isProsesBayar,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(if (isProsesBayar) "Memproses..." else "BAYAR", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun KeranjangRow(nama: String, qty: Int, harga: Double, subtotal: Double, onQtyChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(nama, style = MaterialTheme.typography.bodyMedium)
            Text(CurrencyFormatter.format(harga), style = MaterialTheme.typography.labelSmall)
        }
        Row {
            IconButtonQty(label = "-", onClick = { onQtyChange(qty - 1) })
            Text("$qty", modifier = Modifier.padding(horizontal = 8.dp))
            IconButtonQty(label = "+", onClick = { onQtyChange(qty + 1) })
        }
        Text(CurrencyFormatter.format(subtotal), modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun IconButtonQty(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.size(36.dp), contentPadding = PaddingValues(0.dp)) {
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
private fun PembayaranDialog(total: Double, onDismiss: () -> Unit, onKonfirmasi: (MetodePembayaran, Double, String?, String?) -> Unit) {
    var metode by remember { mutableStateOf(MetodePembayaran.TUNAI) }
    var uangDiterimaText by remember { mutableStateOf("") }
    var namaPembeli by remember { mutableStateOf("") }
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
