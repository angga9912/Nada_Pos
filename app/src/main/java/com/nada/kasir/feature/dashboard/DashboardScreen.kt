package com.nada.kasir.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nada.kasir.core.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.*

// Warna status semantik (poin 9) - tetap konsisten terlepas dari warna branding toko,
// supaya makna "aman/menipis/habis" tidak berubah-ubah antar pelanggan.
private val HijauAman = Color(0xFF2E7D32)
private val OranyeWarning = Color(0xFFF57C00)
private val MerahBahaya = Color(0xFFD32F2F)

/**
 * Dashboard (Home) - redesign "Modern Minimalist POS Dashboard".
 * Struktur: Header -> Ringkasan -> Primary Action -> Menu Utama -> Perhatian Stok -> Transaksi Terbaru.
 * Bottom navigation ada di level Scaffold luar (NadaNavGraph), bukan di sini.
 */
@Composable
fun DashboardScreen(
    isAdmin: Boolean,
    namaPengguna: String,
    onBukaKasir: () -> Unit,
    onBukaProduk: () -> Unit,
    onBukaRiwayat: () -> Unit,
    onBukaPengaturanPrinter: () -> Unit,
    onBukaBackup: () -> Unit,
    onBukaLaporan: () -> Unit,
    onBukaPengaturanToko: () -> Unit,
    onBukaPengguna: () -> Unit,
    onLogout: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.muatTransaksiTerbaru() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { HeaderDashboard(namaToko = state.store?.nama ?: "NADA POS", logoPath = state.store?.logoPath, namaPengguna = namaPengguna, onLogout = onLogout) }
        item { Spacer(Modifier.height(4.dp)) }
        item { RingkasanSection(state) }
        item { Spacer(Modifier.height(20.dp)) }
        item { PrimaryActionCard(onClick = onBukaKasir) }
        item { Spacer(Modifier.height(24.dp)) }
        item { MenuUtamaSection(isAdmin, state.paketAktif, onBukaProduk, onBukaRiwayat, onBukaLaporan) }
        item { Spacer(Modifier.height(24.dp)) }
        item { PerhatianStokSection(state.stokMenipis, state.stokHabis, onLihatProduk = onBukaProduk) }
        item { Spacer(Modifier.height(24.dp)) }
        item { TransaksiTerbaruSection(state, onLihatSemua = onBukaRiwayat, onMulaiTransaksi = onBukaKasir) }
    }
}

@Composable
private fun HeaderDashboard(namaToko: String, logoPath: String?, namaPengguna: String, onLogout: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    val sdfTanggal = remember { SimpleDateFormat("EEEE, d MMMM yyyy", Locale("id", "ID")) }

    Column(modifier = Modifier.fillMaxWidth().padding(20.dp, 20.dp, 20.dp, 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Logo toko asli jika sudah diunggah (Pengaturan Toko), fallback ke ikon netral
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (!logoPath.isNullOrBlank() && java.io.File(logoPath).exists()) {
                    coil.compose.AsyncImage(
                        model = java.io.File(logoPath),
                        contentDescription = "Logo toko",
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Icon(Icons.Filled.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(namaToko, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                Text(
                    "Selamat datang kembali, $namaPengguna",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { /* Notifikasi belum tersedia di phase ini */ }) {
                Icon(Icons.Outlined.Notifications, contentDescription = "Notifikasi", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Menu lainnya", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Keluar") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                        onClick = { showMenu = false; onLogout() }
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            sdfTanggal.format(Date()).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun RingkasanSection(state: DashboardUiState) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KartuRingkasan(
                modifier = Modifier.weight(1f), label = "Penjualan Hari Ini",
                nilai = CurrencyFormatter.format(state.penjualanHariIni),
                ikon = Icons.Filled.Payments, warna = MaterialTheme.colorScheme.primary
            )
            KartuRingkasan(
                modifier = Modifier.weight(1f), label = "Transaksi",
                nilai = "${state.jumlahTransaksi}",
                ikon = Icons.Filled.Receipt, warna = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KartuRingkasan(
                modifier = Modifier.weight(1f), label = "Stok Menipis",
                nilai = "${state.stokMenipis}",
                ikon = Icons.Filled.WarningAmber, warna = OranyeWarning
            )
            KartuRingkasan(
                modifier = Modifier.weight(1f), label = "Stok Habis",
                nilai = "${state.stokHabis}",
                ikon = Icons.Filled.RemoveShoppingCart, warna = MerahBahaya
            )
        }
    }
}

@Composable
private fun KartuRingkasan(modifier: Modifier = Modifier, label: String, nilai: String, ikon: ImageVector, warna: Color) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier.size(32.dp).background(warna.copy(alpha = 0.12f), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(ikon, contentDescription = null, tint = warna, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(nilai, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }
}

@Composable
private fun PrimaryActionCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).heightIn(min = 76.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 2.dp
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PointOfSale, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Transaksi Baru", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                Text("Mulai transaksi penjualan", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.85f)))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White)
        }
    }
}

@Composable
private fun MenuUtamaSection(
    isAdmin: Boolean,
    paketAktif: com.nada.kasir.core.paket.PaketAplikasi,
    onBukaProduk: () -> Unit, onBukaRiwayat: () -> Unit, onBukaLaporan: () -> Unit
) {
    val bisaLaporan = isAdmin && paketAktif.mencakup(com.nada.kasir.core.paket.PaketAplikasi.CUSTOM)
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text("Menu Utama", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ItemMenuUtama(Modifier.weight(1f), Icons.Filled.Inventory2, if (isAdmin) "Produk" else "Lihat Produk", onBukaProduk)
            ItemMenuUtama(Modifier.weight(1f), Icons.Filled.ReceiptLong, "Riwayat", onBukaRiwayat)
        }
        if (bisaLaporan) {
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ItemMenuUtama(Modifier.weight(1f), Icons.Filled.Assessment, "Laporan", onBukaLaporan)
                Spacer(Modifier.weight(1f)) // slot kosong menjaga grid tetap 2 kolom rapi
            }
        }
    }
}

@Composable
private fun ItemMenuUtama(modifier: Modifier, ikon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier.heightIn(min = 88.dp),
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.Center) {
            Box(
                modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(ikon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
        }
    }
}

@Composable
private fun PerhatianStokSection(stokMenipis: Int, stokHabis: Int, onLihatProduk: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text("Perhatian Stok", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(12.dp))
        when {
            stokHabis > 0 -> BarisPerhatian(
                ikon = Icons.Filled.RemoveShoppingCart, warna = MerahBahaya,
                judul = "Stok Habis", deskripsi = "$stokHabis produk kehabisan stok, segera lakukan stok masuk.",
                onClick = onLihatProduk
            )
            stokMenipis > 0 -> BarisPerhatian(
                ikon = Icons.Filled.WarningAmber, warna = OranyeWarning,
                judul = "Stok Menipis", deskripsi = "$stokMenipis produk membutuhkan restock.",
                onClick = onLihatProduk
            )
            else -> BarisPerhatian(
                ikon = Icons.Filled.CheckCircle, warna = HijauAman,
                judul = "Semua stok dalam kondisi aman", deskripsi = null, onClick = null
            )
        }
    }
}

@Composable
private fun BarisPerhatian(ikon: ImageVector, warna: Color, judul: String, deskripsi: String?, onClick: (() -> Unit)?) {
    Surface(
        onClick = onClick ?: {}, enabled = onClick != null,
        shape = RoundedCornerShape(16.dp), color = warna.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(ikon, contentDescription = null, tint = warna, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(judul, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                deskripsi?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (onClick != null) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = warna)
            }
        }
    }
}

@Composable
private fun TransaksiTerbaruSection(state: DashboardUiState, onLihatSemua: () -> Unit, onMulaiTransaksi: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Transaksi Terbaru", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            if (state.transaksiTerbaru.isNotEmpty()) {
                TextButton(onClick = onLihatSemua) { Text("Lihat Semua →", style = MaterialTheme.typography.bodySmall) }
            }
        }
        Spacer(Modifier.height(8.dp))

        when {
            state.sedangMemuatTransaksiTerbaru -> {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
            state.transaksiTerbaru.isEmpty() -> {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Belum ada transaksi hari ini.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(onClick = onMulaiTransaksi) { Text("Mulai Transaksi") }
                    }
                }
            }
            else -> {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        state.transaksiTerbaru.forEachIndexed { idx, trx ->
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(trx.noTransaksi, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                    Text(
                                        trx.previewProduk.ifBlank { "-" }, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(CurrencyFormatter.format(trx.total), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                    Text(trx.jam, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (idx != state.transaksiTerbaru.lastIndex) {
                                Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }
    }
}
