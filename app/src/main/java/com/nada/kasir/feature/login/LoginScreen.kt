package com.nada.kasir.feature.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nada.kasir.core.data.local.entity.UserEntity

/** Login (poin 18). Wajib login sebelum bisa memakai halaman lain. */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onLoginBerhasil: (UserEntity) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(state.loginBerhasil) {
        state.loginBerhasil?.let { onLoginBerhasil(it) }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("NADA POS", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { viewModel.login(username, password) },
            enabled = !state.sedangProses,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text(if (state.sedangProses) "Memproses..." else "Masuk") }
    }

    state.infoAdminDefault?.let { pesan ->
        AlertDialog(
            onDismissRequest = { viewModel.clearInfoAdminDefault() },
            confirmButton = { TextButton(onClick = { viewModel.clearInfoAdminDefault() }) { Text("Mengerti") } },
            title = { Text("Akun Pertama Dibuat") },
            text = { Text(pesan) }
        )
    }

    // F-09: login dengan password bawaan wajib diikuti pembuatan password baru. Dialog ini tidak bisa
    // ditutup dengan tap di luar atau tombol back; satu-satunya jalan keluar adalah ganti password
    // atau Batal (kembali ke form login tanpa masuk).
    state.perluGantiPassword?.let {
        var passwordBaru by remember { mutableStateOf("") }
        var konfirmasi by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Buat Password Baru") },
            text = {
                Column {
                    Text("Password bawaan aplikasi tidak boleh dipakai lagi. Buat password baru (minimal 6 karakter) untuk melanjutkan.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = passwordBaru,
                        onValueChange = { passwordBaru = it },
                        label = { Text("Password baru") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = konfirmasi,
                        onValueChange = { konfirmasi = it },
                        label = { Text("Ulangi password baru") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.errorGantiPassword?.let { pesan ->
                        Spacer(Modifier.height(8.dp))
                        Text(pesan, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !state.sedangGantiPassword,
                    onClick = { viewModel.gantiPasswordAwal(passwordBaru, konfirmasi) }
                ) { Text(if (state.sedangGantiPassword) "Menyimpan..." else "Simpan & Masuk") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.batalGantiPassword() }) { Text("Batal") }
            }
        )
    }

    state.errorPesan?.let { pesan ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            confirmButton = { TextButton(onClick = { viewModel.clearError() }) { Text("OK") } },
            title = { Text("Login Gagal") },
            text = { Text(pesan) }
        )
    }
}
