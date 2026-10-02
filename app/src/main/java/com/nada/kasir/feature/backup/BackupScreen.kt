package com.nada.kasir.feature.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nada.kasir.core.backup.BackupEncryption
import com.nada.kasir.core.util.FileShareHelper

/** BACKUP DATA / RESTORE DATA (poin 17). Backup selalu terenkripsi dengan password (F-08). */
@Composable
fun BackupScreen(viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showDialogPasswordBackup by remember { mutableStateOf(false) }

    val pilihFileRestore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.periksaFileRestore(uri)
    }

    // Membuka file picker sistem Android (Storage Access Framework). "Google Drive" otomatis
    // muncul sebagai salah satu lokasi penyimpanan di picker ini (kalau aplikasi Google Drive
    // terpasang & pemilik toko sudah login) - tanpa aplikasi ini perlu integrasi Google Drive
    // API/OAuth apa pun secara langsung. Pengguna tinggal pilih akun & folder Drive-nya sendiri.
    val simpanKeDrive = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) viewModel.simpanBackupKeUri(context, uri)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Backup & Restore Data", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "Mencakup: Produk (termasuk foto), Stok, Transaksi, Pengaturan Toko (termasuk logo), Pengguna, Printer. " +
                "File backup dienkripsi dengan password yang Anda tentukan, jadi aman dikirim atau disimpan di Google Drive.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { showDialogPasswordBackup = true },
            enabled = !state.sedangProses,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text(if (state.sedangProses) "Memproses..." else "Backup Data") }

        state.fileBackupTerakhir?.let { file ->
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { FileShareHelper.bagikanFile(context, file, "application/octet-stream") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Bagikan File Backup") }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { simpanKeDrive.launch(file.name) },
                enabled = !state.sedangProses,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Simpan ke Google Drive") }
            Text(
                "Pilih akun & folder Google Drive toko Anda di jendela yang muncul, lalu backup " +
                "akan tersimpan otomatis di sana sebagai cadangan online.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(24.dp))
        Divider()
        Spacer(Modifier.height(24.dp))

        Text("Restore Data", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Data saat ini akan DIGANTIKAN oleh isi file backup yang dipilih. " +
            "Proses aman: jika file backup rusak, passwordnya salah, atau gagal dibaca, data Anda saat ini TIDAK akan berubah.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { pilihFileRestore.launch(arrayOf("*/*")) },
            enabled = !state.sedangProses,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("Pilih File & Restore Data") }
    }

    if (showDialogPasswordBackup) {
        var password by remember { mutableStateOf("") }
        var konfirmasi by remember { mutableStateOf("") }
        val pesanError = when {
            password.isEmpty() -> null
            else -> BackupEncryption.pesanPasswordTidakValid(password)
                ?: if (konfirmasi.isNotEmpty() && konfirmasi != password) "Konfirmasi password tidak sama." else null
        }
        val siap = password.isNotEmpty() && pesanError == null && konfirmasi == password
        AlertDialog(
            onDismissRequest = { showDialogPasswordBackup = false },
            title = { Text("Password Backup") },
            text = {
                Column {
                    Text(
                        "Backup akan dienkripsi dengan password ini. CATAT password-nya: jika lupa, " +
                            "backup tidak bisa dibuka lagi oleh siapa pun, termasuk pembuat aplikasi.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password backup (min. ${BackupEncryption.PASSWORD_MIN} karakter)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = konfirmasi,
                        onValueChange = { konfirmasi = it },
                        label = { Text("Ulangi password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    pesanError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = siap,
                    onClick = {
                        viewModel.buatBackup(password)
                        showDialogPasswordBackup = false
                    }
                ) { Text("Buat Backup") }
            },
            dismissButton = { TextButton(onClick = { showDialogPasswordBackup = false }) { Text("Batal") } }
        )
    }

    state.restoreMenunggu?.let { menunggu ->
        var passwordRestore by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!state.sedangProses) viewModel.batalRestore() },
            title = { Text("Konfirmasi Restore") },
            text = {
                Column {
                    Text("Semua data saat ini akan digantikan oleh isi file backup. Lanjutkan?")
                    if (menunggu.terenkripsi) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = passwordRestore,
                            onValueChange = { passwordRestore = it },
                            label = { Text("Password backup") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "File ini adalah backup lama yang tidak terenkripsi. Backup baru yang Anda buat akan terenkripsi.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !state.sedangProses && (!menunggu.terenkripsi || passwordRestore.isNotEmpty()),
                    onClick = {
                        viewModel.restoreDariUri(menunggu.uri, if (menunggu.terenkripsi) passwordRestore else null)
                    }
                ) { Text(if (state.sedangProses) "Memproses..." else "Ya, Restore") }
            },
            dismissButton = {
                TextButton(enabled = !state.sedangProses, onClick = { viewModel.batalRestore() }) { Text("Batal") }
            }
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
