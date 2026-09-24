package com.nada.kasir.feature.laporan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.nada.kasir.core.data.local.dao.ProdukTerlaris
import com.nada.kasir.core.data.repository.LaporanPeriode
import com.nada.kasir.core.util.CurrencyFormatter
import com.nada.kasir.core.util.FileShareHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Halaman Laporan / Dashboard Finansial dengan desain "Wallet-Style Modern POS".
 * Merefleksikan tata letak high-fidelity: Header -> Wallet Card (Total Balance) ->
 * 3 Kartu Metrik (Sales today, Items sold, Low stock) -> Popular Products (Last 7 days).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaporanScreen(
    onBukaRiwayat: (() -> Unit)? = null,
    viewModel: LaporanViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var sembunyikanSaldo by rememberSaveable { mutableStateOf(false) }
    var showDetailSheet by rememberSaveable { mutableStateOf(false) }
    var showAllPopularSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.muatSemuaLaporan()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. Header (Tanggal + Judul "Dashboard" + Avatar)
            item {
                HeaderDashboardLaporan(
                    currentUserName = state.currentUserName,
                    storeName = state.store?.nama ?: "Warung Sederhana"
                )
            }

            item { Spacer(Modifier.height(14.dp)) }

            // 2. Kartu Dompet Biru (Wallet Card)
            item {
                WalletCardSection(
                    storeName = state.store?.nama ?: "Warung Sederhana",
                    totalBalance = state.totalOmzetSemuaWaktu,
                    salesToday = state.penjualanHariIni,
                    sembunyikanSaldo = sembunyikanSaldo,
                    onToggleSembunyikan = { sembunyikanSaldo = !sembunyikanSaldo },
                    onViewReports = { showDetailSheet = true },
                    onHistoryClick = {
                        if (onBukaRiwayat != null) onBukaRiwayat()
                        else showDetailSheet = true
                    }
                )
            }

            item { Spacer(Modifier.height(18.dp)) }

            // 3. Baris 3 Kartu Metrik (Sales today, Items sold, Low stock)
            item {
                MetricSummarySection(
                    salesToday = state.jumlahTransaksiHariIni,
                    itemsSold = state.itemTerjualHariIni,
                    lowStock = state.stokMenipis
                )
            }

            item { Spacer(Modifier.height(22.dp)) }

            // 4. Bagian "Popular Products" (Last 7 days)
            item {
                PopularProductsSection(
                    popularProducts = state.produkPopuler,
                    onViewAllClick = { showAllPopularSheet = true }
                )
            }
        }

        // Bottom Sheet: Detail Laporan Finansial & Export Excel
        if (showDetailSheet) {
            ModalBottomSheet(
                onDismissRequest = { showDetailSheet = false },
                containerColor = Color.White,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                DetailLaporanSheetContent(
                    state = state,
                    onTabSelected = { viewModel.pilihTab(it) },
                    onExportExcel = { viewModel.exportLaporanAktif() },
                    onBagikanFile = { file ->
                        FileShareHelper.bagikanFile(
                            context,
                            file,
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        )
                    },
                    onTutup = { showDetailSheet = false }
                )
            }
        }

        // Bottom Sheet: Lihat Semua Produk Populer
        if (showAllPopularSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAllPopularSheet = false },
                containerColor = Color.White,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                AllPopularProductsSheetContent(
                    products = state.produkPopuler,
                    onTutup = { showAllPopularSheet = false }
                )
            }
        }
    }
}

/** Header atas: Subtitle tanggal, Judul "Dashboard", dan avatar inisial bulat. */
@Composable
private fun HeaderDashboardLaporan(
    currentUserName: String,
    storeName: String
) {
    val sdfTanggal = remember {
        try {
            SimpleDateFormat("EEEE, d MMM", Locale.ENGLISH)
        } catch (e: Exception) {
            SimpleDateFormat("EEEE, d MMM", Locale.getDefault())
        }
    }
    val tanggalTeks = remember { sdfTanggal.format(Date()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = tanggalTeks,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Dashboard",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    fontSize = 28.sp
                )
            )
        }

        // Avatar bulat inisial pengguna / toko
        val inisial = currentUserName.trim().take(1).uppercase().ifBlank {
            storeName.trim().take(1).uppercase().ifBlank { "W" }
        }
        Box(
            modifier = Modifier
                .size(46.dp)
                .shadow(elevation = 4.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF2563EB), Color(0xFF1D4ED8))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = inisial,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 18.sp
                )
            )
        }
    }
}

/**
 * Kartu Dompet Biru (Wallet Card) dengan efek lipatan visual, saldo utama,
 * indikator tren penjualan hari ini, dan aksi cepat (View Reports, History, Sensor Saldo).
 */
@Composable
private fun WalletCardSection(
    storeName: String,
    totalBalance: Double,
    salesToday: Double,
    sembunyikanSaldo: Boolean,
    onToggleSembunyikan: () -> Unit,
    onViewReports: () -> Unit,
    onHistoryClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        // Lapisan kartu belakang yang menyembul sedikit (efek kartu di dalam saku dompet)
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .height(60.dp)
                .align(Alignment.TopCenter),
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            color = Color(0xFF60A5FA).copy(alpha = 0.65f)
        ) {}

        // Kartu saku dompet utama
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(26.dp),
                    ambientColor = Color(0x331D4ED8),
                    spotColor = Color(0x4D1D4ED8)
                ),
            shape = RoundedCornerShape(26.dp),
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF3B82F6), // Vibrant Sky/Royal Blue
                                Color(0xFF2563EB), // Rich Blue
                                Color(0xFF1E40AF)  // Deep Midnight Blue
                            )
                        )
                    )
            ) {
                // Pola highlight aksen melengkung transparan di saku dompet
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp)
                ) {
                    // Baris Nama Toko & Badge "CASH"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = storeName.ifBlank { "Warung Sederhana" },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                fontSize = 17.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.White.copy(alpha = 0.22f)
                        ) {
                            Text(
                                text = "CASH",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.2.sp
                               ),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Label "TOTAL BALANCE"
                    Text(
                        text = "TOTAL BALANCE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.75f),
                            letterSpacing = 1.3.sp,
                            fontSize = 11.sp
                        )
                    )
                    Spacer(Modifier.height(4.dp))

                    // Nilai Saldo & Info Hari Ini (↗ Rp X today)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        val saldoTeks = if (sembunyikanSaldo) {
                            "Rp ••••••••"
                        } else {
                            formatSaldoCompact(totalBalance)
                        }

                        Text(
                            text = saldoTeks,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 30.sp,
                                letterSpacing = (-0.5).sp
                            ),
                            maxLines = 1,
                            modifier = Modifier.clickable { onToggleSembunyikan() }
                        )

                        // Tren penjualan hari ini
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = Color(0xFF86EFAC),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${CurrencyFormatter.format(salesToday)} today",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.92f),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(22.dp))

                    // Baris Aksi: Pill "View Reports" + Circle Clock + Circle Eye
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Tombol Pill "View Reports"
                        Surface(
                            onClick = onViewReports,
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.22f),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Assessment,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "View Reports",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        // Tombol Bulat: Riwayat Transaksi (Clock)
                        Surface(
                            onClick = onHistoryClick,
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.22f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.History,
                                    contentDescription = "Riwayat Transaksi",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(10.dp))

                        // Tombol Bulat: Sembunyikan/Tampilkan Saldo (Eye)
                        Surface(
                            onClick = onToggleSembunyikan,
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.22f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (sembunyikanSaldo) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = "Toggle Sensor Saldo",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 3 Kartu Metrik Ringkas: Sales today | Items sold | Low stock
 * Dibungkus dalam satu kontainer putih membulat yang bersih.
 */
@Composable
private fun MetricSummarySection(
    salesToday: Int,
    itemsSold: Int,
    lowStock: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Kolom 1: Sales today
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "$salesToday",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        fontSize = 22.sp
                    )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Sales today",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                )
            }

            // Garis pembatas halus
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(34.dp)
                    .background(Color(0xFFE2E8F0))
            )

            // Kolom 2: Items sold
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "$itemsSold",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        fontSize = 22.sp
                    )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Items sold",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                )
            }

            // Garis pembatas halus
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(34.dp)
                    .background(Color(0xFFE2E8F0))
            )

            // Kolom 3: Low stock
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "$lowStock",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (lowStock > 0) Color(0xFFEF4444) else Color(0xFF0F172A),
                        fontSize = 22.sp
                    )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Low stock",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}

/**
 * Bagian "Popular Products" (Last 7 days) dengan nomor rank bulat hitam,
 * thumbnail foto produk, progress bar volume penjualan, dan nilai omzet.
 */
@Composable
private fun PopularProductsSection(
    popularProducts: List<ProdukTerlaris>,
    onViewAllClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        // Baris Header Seksi
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Popular Products",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        fontSize = 18.sp
                    )
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = "Last 7 days",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                )
            }
            TextButton(
                onClick = onViewAllClick,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = "View all",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Kontainer Kartu Produk Populer
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            // Jika belum ada penjualan dalam 7 hari terakhir, tampilkan item percontohan rapi
            val listTampil = if (popularProducts.isNotEmpty()) {
                popularProducts.take(4)
            } else {
                listOf(
                    ProdukTerlaris(productId = 1, nama = "Kopi Susu", totalQty = 32, totalOmzet = 576000.0),
                    ProdukTerlaris(productId = 2, nama = "Croissant", totalQty = 21, totalOmzet = 462000.0)
                )
            }

            val maxQty = maxOf(1, listTampil.maxOf { it.totalQty })

            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                listTampil.forEachIndexed { index, product ->
                    PopularProductItemRow(
                        rank = index + 1,
                        product = product,
                        progress = product.totalQty.toFloat() / maxQty
                    )
                    if (index < listTampil.size - 1) {
                        Divider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = Color(0xFFF1F5F9),
                            thickness = 1.dp
                        )
                    }
                }
            }
        }
    }
}

/** Satu baris item produk populer di dalam kartu daftar. */
@Composable
private fun PopularProductItemRow(
    rank: Int,
    product: ProdukTerlaris,
    progress: Float
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Badge bulat hitam penanda ranking (1, 2, ...)
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$rank",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 11.sp
                )
            )
        }

        Spacer(Modifier.width(12.dp))

        // Thumbnail gambar produk
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF1F5F9)),
            contentAlignment = Alignment.Center
        ) {
            if (!product.fotoPath.isNullOrBlank() && File(product.fotoPath).exists()) {
                AsyncImage(
                    model = File(product.fotoPath),
                    contentDescription = product.nama,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = when (rank) {
                        1 -> Icons.Filled.LocalCafe
                        2 -> Icons.Filled.BakeryDining
                        else -> Icons.Filled.Fastfood
                    },
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Nama produk & Progress Bar Biru
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = product.nama,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0F172A),
                    fontSize = 14.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0.08f, 1f) },
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Color(0xFF2563EB),
                trackColor = Color(0xFFE2E8F0)
            )
        }

        Spacer(Modifier.width(10.dp))

        // Omzet & Jumlah Terjual
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = CurrencyFormatter.format(product.totalOmzet),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    fontSize = 14.sp
                )
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${product.totalQty} sold",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                )
            )
        }
    }
}

/**
 * Konten Bottom Sheet "View Reports" yang menampilkan rincian laporan
 * berkala, tab Hari Ini / 7 Hari / Bulanan / Stok, dan opsi ekspor Excel.
 */
@Composable
private fun DetailLaporanSheetContent(
    state: LaporanUiState,
    onTabSelected: (TabLaporan) -> Unit,
    onExportExcel: () -> Unit,
    onBagikanFile: (File) -> Unit,
    onTutup: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Detailed Reports",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            )
            IconButton(onClick = onTutup) {
                Icon(Icons.Filled.Close, contentDescription = "Tutup")
            }
        }

        Spacer(Modifier.height(12.dp))

        // Tab Selector Periode
        TabRow(
            selectedTabIndex = state.tabAktif.ordinal,
            containerColor = Color.Transparent,
            contentColor = Color(0xFF2563EB)
        ) {
            Tab(
                selected = state.tabAktif == TabLaporan.HARIAN,
                onClick = { onTabSelected(TabLaporan.HARIAN) },
                text = { Text("Hari Ini", fontWeight = if (state.tabAktif == TabLaporan.HARIAN) FontWeight.Bold else FontWeight.Normal) }
            )
            Tab(
                selected = state.tabAktif == TabLaporan.TUJUH_HARI,
                onClick = { onTabSelected(TabLaporan.TUJUH_HARI) },
                text = { Text("7 Hari", fontWeight = if (state.tabAktif == TabLaporan.TUJUH_HARI) FontWeight.Bold else FontWeight.Normal) }
            )
            Tab(
                selected = state.tabAktif == TabLaporan.BULANAN,
                onClick = { onTabSelected(TabLaporan.BULANAN) },
                text = { Text("Bulanan", fontWeight = if (state.tabAktif == TabLaporan.BULANAN) FontWeight.Bold else FontWeight.Normal) }
            )
            Tab(
                selected = state.tabAktif == TabLaporan.STOK,
                onClick = { onTabSelected(TabLaporan.STOK) },
                text = { Text("Stok", fontWeight = if (state.tabAktif == TabLaporan.STOK) FontWeight.Bold else FontWeight.Normal) }
            )
        }

        Spacer(Modifier.height(16.dp))

        if (state.sedangMemuat) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF2563EB))
            }
        } else {
            when (state.tabAktif) {
                TabLaporan.HARIAN -> LaporanDetailView(state.laporanHarian)
                TabLaporan.TUJUH_HARI -> LaporanDetailView(state.laporan7Hari)
                TabLaporan.BULANAN -> LaporanDetailView(state.laporanBulanan)
                TabLaporan.STOK -> LaporanStokDetailView(state.totalProduk, state.stokMenipis, state.stokHabis)
            }
        }

        Spacer(Modifier.height(20.dp))

        // Tombol Export & Bagikan Excel
        Button(
            onClick = onExportExcel,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
        ) {
            Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Export Laporan ke Excel (.xlsx)", fontWeight = FontWeight.SemiBold)
        }

        state.fileExportTerakhir?.let { file ->
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onBagikanFile(file) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Bagikan File: ${file.name}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** Tampilan ringkasan metrik finansial pada sheet detail. */
@Composable
private fun LaporanDetailView(laporan: LaporanPeriode?) {
    if (laporan == null) {
        Text("Belum ada data transaksi.", color = Color(0xFF64748B), modifier = Modifier.padding(vertical = 12.dp))
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        BarisInfoLaporan("Total Penjualan", CurrencyFormatter.format(laporan.totalPenjualan), isBold = true)
        BarisInfoLaporan("Jumlah Transaksi", "${laporan.jumlahTransaksi} transaksi")
        BarisInfoLaporan("Produk Terjual", "${laporan.produkTerjual} item")
        BarisInfoLaporan("Total Diskon", CurrencyFormatter.format(laporan.totalDiskon))
        BarisInfoLaporan("Estimasi Keuntungan Bersih", CurrencyFormatter.format(laporan.estimasiKeuntungan), isBold = true, highlightColor = Color(0xFF16A34A))

        if (laporan.ringkasanMetodePembayaran.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Metode Pembayaran",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            )
            Spacer(Modifier.height(6.dp))
            laporan.ringkasanMetodePembayaran.forEach { item ->
                BarisInfoLaporan(
                    label = item.metode,
                    nilai = "${item.jumlahTransaksi}x • ${CurrencyFormatter.format(item.total)}"
                )
            }
        }
    }
}

/** Tampilan metrik status inventaris stok pada sheet detail. */
@Composable
private fun LaporanStokDetailView(totalProduk: Int, stokMenipis: Int, stokHabis: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BarisInfoLaporan("Total Produk Aktif", "$totalProduk produk")
        BarisInfoLaporan("Stok Menipis (<= 5)", "$stokMenipis produk", highlightColor = Color(0xFFEA580C))
        BarisInfoLaporan("Stok Habis (0)", "$stokHabis produk", highlightColor = Color(0xFFDC2626))
    }
}

@Composable
private fun BarisInfoLaporan(
    label: String,
    nilai: String,
    isBold: Boolean = false,
    highlightColor: Color? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color(0xFF475569),
                fontWeight = if (isBold) FontWeight.SemiBold else FontWeight.Normal
            )
        )
        Text(
            text = nilai,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = highlightColor ?: if (isBold) Color(0xFF0F172A) else Color(0xFF334155),
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium
            )
        )
    }
}

/** Sheet daftar lengkap seluruh produk populer. */
@Composable
private fun AllPopularProductsSheetContent(
    products: List<ProdukTerlaris>,
    onTutup: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "All Popular Products (Last 7 Days)",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            )
            IconButton(onClick = onTutup) {
                Icon(Icons.Filled.Close, contentDescription = "Tutup")
            }
        }

        Spacer(Modifier.height(12.dp))

        if (products.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Belum ada data penjualan 7 hari terakhir.", color = Color(0xFF94A3B8))
            }
        } else {
            val maxQty = maxOf(1, products.maxOf { it.totalQty })
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                items(products.size) { idx ->
                    val item = products[idx]
                    PopularProductItemRow(
                        rank = idx + 1,
                        product = item,
                        progress = item.totalQty.toFloat() / maxQty
                    )
                    if (idx < products.size - 1) {
                        Divider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = Color(0xFFF1F5F9)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Format saldo ke gaya ringkas seperti kartu dompet di referensi desain (mis. "Rp 189,9M").
 * Jika nilai di bawah 1 juta, tampilkan nominal rupiah standar.
 */
private fun formatSaldoCompact(amount: Double): String {
    return when {
        amount >= 1_000_000_000 -> {
            val nilaiMiliar = amount / 1_000_000_000.0
            String.format(Locale("id", "ID"), "Rp %,.1fB", nilaiMiliar)
        }
        amount >= 1_000_000 -> {
            val nilaiJuta = amount / 1_000_000.0
            String.format(Locale("id", "ID"), "Rp %,.1fM", nilaiJuta)
        }
        amount >= 100_000 -> {
            CurrencyFormatter.format(amount)
        }
        else -> {
            CurrencyFormatter.format(amount)
        }
    }
}
