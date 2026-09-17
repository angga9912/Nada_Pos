package com.nada.kasir.feature.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private data class HalamanOnboarding(
    val emoji: String,
    val judul: String,
    val deskripsi: String
)

private val daftarHalaman = listOf(
    HalamanOnboarding(
        emoji = "🧾",
        judul = "Selamat Datang di NADA POS",
        deskripsi = "Kasir digital yang bantu kamu kelola usaha lebih gampang."
    ),
    HalamanOnboarding(
        emoji = "🛒",
        judul = "Transaksi Secepat Kilat",
        deskripsi = "Catat penjualan & scan barcode langsung pakai kamera HP."
    ),
    HalamanOnboarding(
        emoji = "📦",
        judul = "Stok & Laporan Otomatis",
        deskripsi = "Stok berkurang sendiri tiap transaksi, laporan penjualan langsung tersedia."
    ),
    HalamanOnboarding(
        emoji = "☁\uFE0F",
        judul = "Aman Walau Offline",
        deskripsi = "Tetap jalan tanpa internet - data bisa dibackup ke Google Drive kapan saja."
    ),
    HalamanOnboarding(
        emoji = "🚀",
        judul = "Yuk, Mulai!",
        deskripsi = "Semua sudah siap. Ayo kelola usahamu sekarang."
    )
)

/**
 * Layar onboarding animasi yang tampil SEKALI SAJA saat aplikasi pertama kali dibuka
 * (dicek lewat [OnboardingPreference] di NadaNavGraph). Dirancang buat pembeli baru
 * yang belum pernah lihat aplikasinya sama sekali.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun OnboardingScreen(onSelesai: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { daftarHalaman.size })
    val scope = rememberCoroutineScope()
    val halamanTerakhir = pagerState.currentPage == daftarHalaman.lastIndex

    Scaffold(
        bottomBar = {
            Column(modifier = Modifier.padding(24.dp)) {
                // Indikator titik halaman - titik aktif memanjang & berubah warna (animasi implisit
                // lewat recomposition tiap pagerState.currentPage berubah).
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    daftarHalaman.indices.forEach { index ->
                        val aktif = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .height(8.dp)
                                .width(if (aktif) 24.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (aktif) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (halamanTerakhir) {
                        Spacer(Modifier.width(1.dp))
                    } else {
                        TextButton(onClick = onSelesai) { Text("Lewati") }
                    }

                    Button(onClick = {
                        if (halamanTerakhir) {
                            onSelesai()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    }) {
                        Text(if (halamanTerakhir) "Mulai Sekarang" else "Lanjut")
                    }
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) { page ->
            val halaman = daftarHalaman[page]

            // Efek "membesar & memudar saat digeser" - dihitung dari jarak halaman ini
            // terhadap posisi scroll pager saat ini (animasi mengikuti gestur swipe pengguna).
            val jarakDariFokus = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
            val scale = 1f - (jarakDariFokus * 0.25f).coerceIn(0f, 0.25f)
            val alpha = 1f - (jarakDariFokus * 0.7f).coerceIn(0f, 0.7f)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(halaman.emoji, fontSize = 64.sp)
                }
                Spacer(Modifier.height(32.dp))
                Text(
                    halaman.judul,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer { this.alpha = alpha }
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    halaman.deskripsi,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer { this.alpha = alpha }
                )
            }
        }
    }
}
