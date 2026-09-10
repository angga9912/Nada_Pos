package com.nada.kasir.feature.kasir

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nada.kasir.core.data.local.entity.MetodePembayaran
import com.nada.kasir.core.util.CurrencyFormatter
import com.nada.kasir.core.util.HandheldScannerDetector
import com.nada.kasir.feature.kasir.barcode.BarcodeScannerScreen
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
    viewModel: KasirViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showPembayaranDialog by remember { mutableStateOf(false) }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var showKeranjangSheet by remember { mutableStateOf(false) }

    // Buffer untuk membedakan ketikan scanner fisik (handheld) vs ketikan manual kasir (poin 5, Phase 2)
    val handheldDetector = remember {
        HandheldScannerDetector(onBarcodeTerdeteksi = { kode ->
            viewModel.tambahDariBarcode(kode)
            viewModel.onQueryChange("")
        })
    }

    if (showBarcodeScanner) {
        BarcodeScannerScreen(
            onDetected = { kode ->
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
                columns = GridCells.Adaptive(minSize = 150.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(state.produk) { produk ->
                    ProdukKasirCard(
                        nama = produk.nama,
                        harga = produk.hargaJual,
                        stok = produk.stok,
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
            onKonfirmasi = { metode, jumlahDiterima, namaPembeli ->
                showPembayaranDialog = false
                viewModel.bayar(currentUserId, metode, jumlahDiterima, namaPembeli)
            }
        )
    }
}

@Composable
private fun ProdukKasirCard(nama: String, harga: Double, stok: Int, jumlahDiKeranjang: Int, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(nama, style = MaterialTheme.typography.bodyMedium, maxLines = 2, minLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(CurrencyFormatter.format(harga), style = MaterialTheme.typography.bodyLarge)
            Text("Stok: $stok", style = MaterialTheme.typography.labelSmall)
            if (jumlahDiKeranjang > 0) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "Di keranjang: $jumlahDiKeranjang",
                        color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
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
private fun PembayaranDialog(total: Double, onDismiss: () -> Unit, onKonfirmasi: (MetodePembayaran, Double, String?) -> Unit) {
    var metode by remember { mutableStateOf(MetodePembayaran.TUNAI) }
    var uangDiterimaText by remember { mutableStateOf("") }
    var namaPembeli by remember { mutableStateOf("") }
    val uangDiterima = uangDiterimaText.toDoubleOrNull() ?: 0.0
    val kembalian = uangDiterima - total

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
                MetodePembayaran.values().forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = metode == m, onClick = { metode = m })
                        Text(m.name)
                    }
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
                    onKonfirmasi(metode, jumlah, namaPembeli.ifBlank { null })
                },
                enabled = metode != MetodePembayaran.TUNAI || uangDiterima > 0.0
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

        // Pilihan cepat pecahan uang umum.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            PECAHAN_UANG_UMUM.forEach { nominal ->
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
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
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
