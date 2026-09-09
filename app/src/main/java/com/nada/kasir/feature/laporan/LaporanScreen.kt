package com.nada.kasir.feature.laporan

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nada.kasir.core.data.repository.LaporanPeriode
import com.nada.kasir.core.util.CurrencyFormatter
import com.nada.kasir.core.util.FileShareHelper

/** LAPORAN (poin 14): Hari Ini, Bulanan, Stok - dengan export Excel. */
@Composable
fun LaporanScreen(viewModel: LaporanViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Laporan", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        TabRow(selectedTabIndex = state.tabAktif.ordinal) {
            Tab(selected = state.tabAktif == TabLaporan.HARIAN, onClick = { viewModel.pilihTab(TabLaporan.HARIAN) }, text = { Text("Hari Ini") })
            Tab(selected = state.tabAktif == TabLaporan.BULANAN, onClick = { viewModel.pilihTab(TabLaporan.BULANAN) }, text = { Text("Bulanan") })
            Tab(selected = state.tabAktif == TabLaporan.STOK, onClick = { viewModel.pilihTab(TabLaporan.STOK) }, text = { Text("Stok") })
        }
        Spacer(Modifier.height(16.dp))

        if (state.sedangMemuat) {
            CircularProgressIndicator()
        } else {
            when (state.tabAktif) {
                TabLaporan.HARIAN -> LaporanRingkasan(state.laporanHarian)
                TabLaporan.BULANAN -> LaporanRingkasan(state.laporanBulanan, tampilkanTerlaris = true)
                TabLaporan.STOK -> LaporanStok(state.totalProduk, state.stokMenipis, state.stokHabis)
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = { viewModel.exportLaporanAktif() }, modifier = Modifier.fillMaxWidth()) {
            Text("Export ke Excel")
        }
        state.fileExportTerakhir?.let { file ->
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { FileShareHelper.bagikanFile(context, file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Bagikan: ${file.name}") }
        }
    }
}

@Composable
private fun LaporanRingkasan(laporan: LaporanPeriode?, tampilkanTerlaris: Boolean = false) {
    if (laporan == null) { Text("Belum ada data."); return }
    Column {
        BarisLaporan("Total Penjualan", CurrencyFormatter.format(laporan.totalPenjualan))
        BarisLaporan("Jumlah Transaksi", "${laporan.jumlahTransaksi}")
        BarisLaporan("Produk Terjual", "${laporan.produkTerjual}")
        BarisLaporan("Total Diskon", CurrencyFormatter.format(laporan.totalDiskon))
        BarisLaporan("Estimasi Keuntungan", CurrencyFormatter.format(laporan.estimasiKeuntungan))

        if (laporan.ringkasanMetodePembayaran.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Berdasarkan Metode Pembayaran", style = MaterialTheme.typography.titleSmall)
            laporan.ringkasanMetodePembayaran.forEach {
                BarisLaporan(it.metode, "${it.jumlahTransaksi}x • ${CurrencyFormatter.format(it.total)}")
            }
        }

        if (tampilkanTerlaris && laporan.produkTerlaris.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Produk Terlaris", style = MaterialTheme.typography.titleSmall)
            laporan.produkTerlaris.forEachIndexed { idx, p ->
                BarisLaporan("${idx + 1}. ${p.nama}", "${p.totalQty} terjual")
            }
        }
    }
}

@Composable
private fun LaporanStok(totalProduk: Int, stokMenipis: Int, stokHabis: Int) {
    Column {
        BarisLaporan("Total Produk Aktif", "$totalProduk")
        BarisLaporan("Stok Menipis", "$stokMenipis")
        BarisLaporan("Stok Habis", "$stokHabis")
    }
}

@Composable
private fun BarisLaporan(label: String, nilai: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(nilai, style = MaterialTheme.typography.bodyMedium)
    }
}
