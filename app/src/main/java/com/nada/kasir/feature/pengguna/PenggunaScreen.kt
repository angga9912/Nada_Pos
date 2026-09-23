package com.nada.kasir.feature.pengguna

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nada.kasir.core.data.local.entity.UserEntity
import com.nada.kasir.core.data.local.entity.UserRole
import com.nada.kasir.core.util.Result

/** MANAJEMEN PENGGUNA - khusus ADMIN (poin 18: "Mengelola" akun kasir/admin). */
@Composable
fun PenggunaScreen(
    currentUserId: Long = 1L,
    viewModel: PenggunaViewModel = hiltViewModel()
) {
    val daftar by viewModel.daftarPengguna.collectAsState()
    var showFormTambah by remember { mutableStateOf(false) }
    var userUntukResetPassword by remember { mutableStateOf<UserEntity?>(null) }
    var userUntukHapus by remember { mutableStateOf<UserEntity?>(null) }
    var pesanInfo by remember { mutableStateOf<String?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showFormTambah = true }) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Pengguna")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                "Manajemen Pengguna",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )
            LazyColumn {
                items(daftar) { user ->
                    ListItem(
                        headlineContent = {
                            Text("${user.nama} ${if (user.id == currentUserId) "(Anda)" else ""}")
                        },
                        supportingContent = {
                            Text("${user.username} • ${user.role.name}${if (!user.aktif) " • NONAKTIF" else ""}")
                        },
                        trailingContent = {
                            Row {
                                IconButton(
                                    onClick = { userUntukResetPassword = user },
                                    contentDescription = "Ganti Password"
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = "Ganti Password", tint = MaterialTheme.colorScheme.primary)
                                }
                                if (user.id != currentUserId) {
                                    IconButton(
                                        onClick = { userUntukHapus = user },
                                        contentDescription = "Hapus Pengguna"
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    )
                    Divider()
                }
            }
        }
    }

    if (showFormTambah) {
        TambahPenggunaDialog(
            onDismiss = { showFormTambah = false },
            onSimpan = { nama, username, password, role ->
                viewModel.tambahPengguna(nama, username, password, role) { pesan ->
                    pesanInfo = pesan
                }
                showFormTambah = false
            }
        )
    }

    userUntukResetPassword?.let { targetUser ->
        ResetPasswordDialog(
            targetUser = targetUser,
            isAkunSendiri = targetUser.id == currentUserId,
            onDismiss = { userUntukResetPassword = null },
            onGantiSendiri = { passLama, passBaru ->
                viewModel.gantiPassword(targetUser.id, passLama, passBaru) { res ->
                    when (res) {
                        is Result.Success -> pesanInfo = "Password Anda berhasil diperbarui."
                        is Result.Failure -> pesanInfo = res.error.pesan
                    }
                }
                userUntukResetPassword = null
            },
            onResetOlehAdmin = { passBaru ->
                viewModel.resetPasswordOlehAdmin(targetUser.id, passBaru) { res ->
                    when (res) {
                        is Result.Success -> pesanInfo = "Password pengguna '${targetUser.username}' berhasil direset."
                        is Result.Failure -> pesanInfo = res.error.pesan
                    }
                }
                userUntukResetPassword = null
            }
        )
    }

    userUntukHapus?.let { targetUser ->
        AlertDialog(
            onDismissRequest = { userUntukHapus = null },
            title = { Text("Hapus Pengguna") },
            text = { Text("Apakah Anda yakin ingin menghapus pengguna '${targetUser.nama}' (${targetUser.username})?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.hapusPengguna(targetUser.id, currentUserId) { res ->
                        when (res) {
                            is Result.Success -> pesanInfo = "Pengguna berhasil dihapus."
                            is Result.Failure -> pesanInfo = res.error.pesan
                        }
                    }
                    userUntukHapus = null
                }) { Text("Ya, Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { userUntukHapus = null }) { Text("Batal") }
            }
        )
    }

    pesanInfo?.let { pesan ->
        AlertDialog(
            onDismissRequest = { pesanInfo = null },
            confirmButton = { TextButton(onClick = { pesanInfo = null }) { Text("OK") } },
            title = { Text("Pemberitahuan") },
            text = { Text(pesan) }
        )
    }
}

@Composable
private fun TambahPenggunaDialog(
    onDismiss: () -> Unit,
    onSimpan: (String, String, String, UserRole) -> Unit
) {
    var nama by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(UserRole.KASIR) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Pengguna") },
        text = {
            Column {
                OutlinedTextField(
                    value = nama,
                    onValueChange = { nama = it },
                    label = { Text("Nama") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (min 6 karakter)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text("Peran (Role):", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = role == UserRole.KASIR, onClick = { role = UserRole.KASIR })
                    Text("Kasir")
                    Spacer(Modifier.width(16.dp))
                    RadioButton(selected = role == UserRole.ADMIN, onClick = { role = UserRole.ADMIN })
                    Text("Admin")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSimpan(nama, username, password, role) },
                enabled = nama.isNotBlank() && username.isNotBlank() && password.length >= 6
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun ResetPasswordDialog(
    targetUser: UserEntity,
    isAkunSendiri: Boolean,
    onDismiss: () -> Unit,
    onGantiSendiri: (String, String) -> Unit,
    onResetOlehAdmin: (String) -> Unit
) {
    var passwordLama by remember { mutableStateOf("") }
    var passwordBaru by remember { mutableStateOf("") }
    var konfirmasiPassword by remember { mutableStateOf("") }
    var errorLocal by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isAkunSendiri) "Ganti Password Saya" else "Reset Password: ${targetUser.username}")
        },
        text = {
            Column {
                if (isAkunSendiri) {
                    OutlinedTextField(
                        value = passwordLama,
                        onValueChange = { passwordLama = it },
                        label = { Text("Password Saat Ini") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = passwordBaru,
                    onValueChange = { passwordBaru = it },
                    label = { Text("Password Baru (min 6 karakter)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = konfirmasiPassword,
                    onValueChange = { konfirmasiPassword = it },
                    label = { Text("Ulangi Password Baru") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                errorLocal?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (passwordBaru.length < 6) {
                        errorLocal = "Password baru minimal 6 karakter."
                        return@TextButton
                    }
                    if (passwordBaru != konfirmasiPassword) {
                        errorLocal = "Konfirmasi password baru tidak cocok."
                        return@TextButton
                    }
                    if (isAkunSendiri) {
                        if (passwordLama.isBlank()) {
                            errorLocal = "Password saat ini wajib diisi."
                            return@TextButton
                        }
                        onGantiSendiri(passwordLama, passwordBaru)
                    } else {
                        onResetOlehAdmin(passwordBaru)
                    }
                }
            ) { Text("Perbarui") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
