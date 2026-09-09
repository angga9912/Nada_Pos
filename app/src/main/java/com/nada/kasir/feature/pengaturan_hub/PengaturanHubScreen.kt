package com.nada.kasir.feature.pengaturan_hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nada.kasir.core.paket.PaketAplikasi

private data class ItemPengaturan(
    val judul: String, val subjudul: String, val ikon: ImageVector, val onClick: () -> Unit
)

/**
 * Tab "Pengaturan" (hasil pengelompokan menu dari Dashboard - poin 5 & 6 brief redesign).
 * Untuk KASIR, hanya opsi non-administratif yang ditampilkan (poin 18: role guard tetap berlaku).
 */
@Composable
fun PengaturanHubScreen(
    isAdmin: Boolean,
    onBukaPengaturanPrinter: () -> Unit,
    onBukaPengaturanToko: () -> Unit,
    onBukaPengguna: () -> Unit,
    onBukaBackup: () -> Unit,
    onLogout: () -> Unit,
    viewModel: PengaturanHubViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val paketAktif by viewModel.paketAktif.collectAsState()

    val itemAdmin = buildList {
        add(ItemPengaturan("Pengaturan Printer", "Kelola printer thermal Bluetooth", Icons.Filled.Print, onBukaPengaturanPrinter))
        add(ItemPengaturan("Pengaturan Toko", "Identitas toko, struk, dan warna aplikasi", Icons.Filled.Storefront, onBukaPengaturanToko))
        if (paketAktif.mencakup(PaketAplikasi.PRO)) {
            add(ItemPengaturan("Manajemen Pengguna", "Kelola akun admin dan kasir", Icons.Filled.Group, onBukaPengguna))
        }
        if (paketAktif.mencakup(PaketAplikasi.CUSTOM)) {
            add(ItemPengaturan("Backup & Restore Data", "Cadangkan atau pulihkan seluruh data", Icons.Filled.CloudUpload, onBukaBackup))
        }
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
            } else {
                item {
                    Text(
                        "Fitur pengaturan hanya tersedia untuk Admin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
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
