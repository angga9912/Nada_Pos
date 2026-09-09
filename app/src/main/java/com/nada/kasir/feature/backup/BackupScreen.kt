package com.nada.kasir.feature.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nada.kasir.core.util.FileShareHelper

/** BACKUP DATA / RESTORE DATA (poin 17). */
@Composable
fun BackupScreen(viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showKonfirmasiRestore by remember { mutableStateOf<android.net.Uri?>(null) }

    val pilihFileRestore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) showKonfirmasiRestore = uri
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Backup & Restore Data", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "Mencakup: Produk, Stok, Transaksi, Pengaturan Toko, Pengguna, Printer.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { viewModel.buatBackup() },
            enabled = !state.sedangProses,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text(if (state.sedangProses) "Memproses..." else "Backup Data") }

        state.fileBackupTerakhir?.let { file ->
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { FileShareHelper.bagikanFile(context, file, "application/json") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Bagikan File Backup") }
        }

        Spacer(Modifier.height(24.dp))
        Divider()
        Spacer(Modifier.height(24.dp))

        Text("Restore Data", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Data saat ini akan DIGANTIKAN oleh isi file backup yang dipilih. " +
            "Proses aman: jika file backup rusak/gagal dibaca, data Anda saat ini TIDAK akan berubah.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { pilihFileRestore.launch(arrayOf("application/json", "*/*")) },
            enabled = !state.sedangProses,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("Pilih File & Restore Data") }
    }

    showKonfirmasiRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { showKonfirmasiRestore = null },
            title = { Text("Konfirmasi Restore") },
            text = { Text("Semua data saat ini akan digantikan oleh isi file backup. Lanjutkan?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.restoreDariUri(context, uri)
                    showKonfirmasiRestore = null
                }) { Text("Ya, Restore") }
            },
            dismissButton = { TextButton(onClick = { showKonfirmasiRestore = null }) { Text("Batal") } }
        )
    }

    state.pesan?.let { pesan ->
        AlertDialog(
            onDismissRequest = { viewModel.clearPesan() },
            confirmButton = { TextButton(onClick = { viewModel.clearPesan() }) { Text("OK") } },
            title = { Text("Backup & Restore") },
            text = { Text(pesan) }
        )
    }
}
