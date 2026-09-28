package com.nada.kasir.feature.pengaturan_hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nada.kasir.core.paket.PaketAplikasi
import com.nada.kasir.core.util.Result

private data class ItemPengaturan(
    val judul: String, val subjudul: String, val ikon: ImageVector, val onClick: () -> Unit
)

/**
 * Tab "Pengaturan" (hasil pengelompokan menu dari Dashboard).
 * Setiap pengguna (Admin maupun Kasir) memiliki akses ke Ganti Password Akun Saya dan Keluar.
 * Menu administratif hanya aktif untuk Admin.
 */
@Composable
fun PengaturanHubScreen(
    isAdmin: Boolean,
    onBukaPengaturanPrinter: () -> Unit,
    onBukaPengaturanToko: () -> Unit,
    onBukaPengguna: () -> Unit,
    onBukaBackup: () -> Unit,
    onBukaInfoPaket: () -> Unit,
    onLogout: () -> Unit,
    viewModel: PengaturanHubViewModel = hiltViewModel()
) {
    val paketAktif by viewModel.paketAktif.collectAsState()
    var showDialogGantiPassword by remember { mutableStateOf(false) }
    var pesanNotifikasi by remember { mutableStateOf<String?>(null) }

    val itemAdmin = buildList {
        add(ItemPengaturan("Pengaturan Printer", "Kelola printer thermal Bluetooth", Icons.Filled.Print, onBukaPengaturanPrinter))
        add(ItemPengaturan("Pengaturan Toko", "Identitas toko, struk, dan warna aplikasi", Icons.Filled.Storefront, onBukaPengaturanToko))
        if (paketAktif.mencakup(PaketAplikasi.PRO)) {
            add(ItemPengaturan("Manajemen Pengguna", "Kelola akun admin dan kasir", Icons.Filled.Group, onBukaPengguna))
        }
        if (paketAktif.mencakup(PaketAplikasi.CUSTOM)) {
            add(ItemPengaturan("Backup & Restore Data", "Cadangkan atau pulihkan seluruh data", Icons.Filled.CloudUpload, onBukaBackup))
        }
        add(ItemPengaturan("Info Paket & Upgrade", "Bandingkan Basic, Custom, dan Pro", Icons.Filled.WorkspacePremium, onBukaInfoPaket))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Pengaturan",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(20.dp, 20.dp, 20.dp, 8.dp)
        )

        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
            if (isAdmin) {
                items(itemAdmin) { item -> BarisPengaturan(item) }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Keamanan Akun",
                    style = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                BarisPengaturan(
                    ItemPengaturan(
                        judul = "Ganti Password Saya",
                        subjudul = "Ubah kata sandi akun ${viewModel.currentUserName}",
                        ikon = Icons.Filled.Lock,
                        onClick = { showDialogGantiPassword = true }
                    )
                )
            }

            item { Spacer(Modifier.height(16.dp)) }
            item {
                BarisPengaturan(
                    ItemPengaturan("Keluar", "Akhiri sesi dan kembali ke halaman login", Icons.AutoMirrored.Filled.Logout, onLogout),
                    warna = MaterialTheme.colorScheme.error
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showDialogGantiPassword) {
        DialogGantiPasswordHub(
            onDismiss = { showDialogGantiPassword = false },
            onSimpan = { passLama, passBaru ->
                viewModel.gantiPassword(passLama, passBaru) { res ->
                    when (res) {
                        is Result.Success -> pesanNotifikasi = "Password berhasil diperbarui. Silakan gunakan password baru ini pada login berikutnya."
                        is Result.Failure -> pesanNotifikasi = res.error.pesan
                    }
                }
                showDialogGantiPassword = false
            }
        )
    }

    pesanNotifikasi?.let { pesan ->
        AlertDialog(
            onDismissRequest = { pesanNotifikasi = null },
            confirmButton = { TextButton(onClick = { pesanNotifikasi = null }) { Text("OK") } },
            title = { Text("Pemberitahuan") },
            text = { Text(pesan) }
        )
    }
}

@Composable
private fun DialogGantiPasswordHub(
    onDismiss: () -> Unit,
    onSimpan: (String, String) -> Unit
) {
    var passwordLama by remember { mutableStateOf("") }
    var passwordBaru by remember { mutableStateOf("") }
    var konfirmasiPassword by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ganti Password Akun") },
        text = {
            Column {
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
                errorText?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (passwordLama.isBlank()) {
                        errorText = "Password saat ini wajib diisi."
                        return@TextButton
                    }
                    if (passwordBaru.length < 6) {
                        errorText = "Password baru minimal 6 karakter."
                        return@TextButton
                    }
                    if (passwordBaru != konfirmasiPassword) {
                        errorText = "Konfirmasi password baru tidak cocok."
                        return@TextButton
                    }
                    onSimpan(passwordLama, passwordBaru)
                }
            ) { Text("Perbarui") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun BarisPengaturan(item: ItemPengaturan, warna: Color = MaterialTheme.colorScheme.primary) {
    Surface(
        onClick = item.onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(warna.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(item.ikon, contentDescription = null, tint = warna, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.judul, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium))
                Text(item.subjudul, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.Filled.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
