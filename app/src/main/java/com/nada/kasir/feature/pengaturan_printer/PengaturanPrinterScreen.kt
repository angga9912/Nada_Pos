package com.nada.kasir.feature.pengaturan_printer

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/** PENGATURAN PRINTER (poin 8): scan/pilih printer terpasang, ukuran kertas, test print, simpan default. */
@Composable
fun PengaturanPrinterScreen(viewModel: PengaturanPrinterViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { hasil ->
        if (hasil.values.all { it }) viewModel.muatDaftarPrinterTerpasang()
    }

    fun mintaIzinDanScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN))
        } else {
            viewModel.muatDaftarPrinterTerpasang()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Pengaturan Printer", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        if (!state.bluetoothAktif) {
            Text("Bluetooth tidak aktif. Aktifkan Bluetooth di pengaturan HP terlebih dahulu.", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        Text("Ukuran Kertas", style = MaterialTheme.typography.titleMedium)
        Row {
            listOf("58mm", "80mm").forEach { ukuran ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 16.dp)) {
                    RadioButton(selected = state.ukuranKertasDipilih == ukuran, onClick = { viewModel.pilihUkuranKertas(ukuran) })
                    Text(ukuran)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = { mintaIzinDanScan() }, modifier = Modifier.fillMaxWidth()) {
            Text("Scan Printer Terpasang")
        }
        Text(
            "Pastikan printer sudah dipasangkan (paired) lewat Pengaturan Bluetooth HP terlebih dahulu.",
            style = MaterialTheme.typography.labelSmall
        )

        Spacer(Modifier.height(16.dp))
        state.printerDefault?.let {
            Text("Printer default saat ini: ${it.nama} (${it.ukuranKertas})", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn {
            items(state.daftarPrinterTerpasang) { printer ->
                ListItem(
                    headlineContent = { Text(printer.nama) },
                    supportingContent = { Text(printer.macAddress) },
                    trailingContent = {
                        Row {
                            TextButton(
                                onClick = { viewModel.testPrint(printer.macAddress) },
                                enabled = !state.sedangTestPrint
                            ) { Text(if (state.sedangTestPrint) "..." else "Test Print") }
                            TextButton(onClick = { viewModel.simpanSebagaiDefault(printer) }) { Text("Jadikan Default") }
                        }
                    }
                )
                Divider()
            }
        }
    }

    state.statusPesan?.let { pesan ->
        AlertDialog(
            onDismissRequest = { viewModel.clearStatus() },
            confirmButton = { TextButton(onClick = { viewModel.clearStatus() }) { Text("OK") } },
            title = { Text("Printer") },
            text = { Text(pesan) }
        )
    }
}
