package com.nada.kasir.feature.info_paket

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nada.kasir.core.paket.PaketAplikasi
import com.nada.kasir.core.util.KontakSupport

private data class BarisFitur(val nama: String, val basic: Boolean, val custom: Boolean, val pro: Boolean)

private val daftarFitur = listOf(
    BarisFitur("Kasir & Transaksi", basic = true, custom = true, pro = true),
    BarisFitur("Produk & Stok", basic = true, custom = true, pro = true),
    BarisFitur("Cetak Struk Bluetooth", basic = true, custom = true, pro = true),
    BarisFitur("Custom Branding Toko", basic = false, custom = true, pro = true),
    BarisFitur("Laporan Harian & Bulanan", basic = false, custom = true, pro = true),
    BarisFitur("Export ke Excel", basic = false, custom = true, pro = true),
    BarisFitur("Backup & Restore Data", basic = false, custom = true, pro = true),
    BarisFitur("Multi User (Admin & Kasir)", basic = false, custom = false, pro = true),
)

/**
 * Layar informasi & perbandingan paket (Basic/Custom/Pro), diakses dari
 * Pengaturan. Menampilkan paket yang sedang aktif dan menyediakan tombol
 * langsung ke WhatsApp penjual untuk permintaan upgrade.
 */
@Composable
fun InfoPaketScreen(
    onKembali: () -> Unit,
    viewModel: InfoPaketViewModel = hiltViewModel()
) {
    val paketAktif by viewModel.paketAktif.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Info Paket & Upgrade") },
                navigationIcon = {
                    IconButton(onClick = onKembali) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                "Paket kamu saat ini: ${paketAktif.label}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                paketAktif.deskripsi,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))

            TabelPerbandingan(paketAktif = paketAktif)

            Spacer(Modifier.height(24.dp))

            Text(
                "Mau naik ke paket yang lebih lengkap?",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Hubungi kami langsung lewat WhatsApp untuk upgrade paket kamu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            PaketAplikasi.values()
                .filter { it.ordinal > paketAktif.ordinal }
                .forEach { paketTujuan ->
                    OutlinedButton(
                        onClick = { KontakSupport.bukaWhatsAppUpgrade(context, paketTujuan) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text("Upgrade ke ${paketTujuan.label} via WhatsApp")
                    }
                }

            if (paketAktif == PaketAplikasi.PRO) {
                Button(
                    onClick = {
                        KontakSupport.bukaWhatsApp(
                            context,
                            "Halo, saya sudah pakai Nada POS Pro dan ingin tanya fitur tambahan (Hutang/Piutang, Supplier, Multi Cabang)."
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text("Tanya Fitur Tambahan via WhatsApp")
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Nomor WhatsApp: 0813-1766-720",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TabelPerbandingan(paketAktif: PaketAplikasi) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(1.4f))
                HeaderPaket(PaketAplikasi.BASIC, paketAktif, Modifier.weight(1f))
                HeaderPaket(PaketAplikasi.CUSTOM, paketAktif, Modifier.weight(1f))
                HeaderPaket(PaketAplikasi.PRO, paketAktif, Modifier.weight(1f))
            }
            HorizontalDivider()

            daftarFitur.forEachIndexed { index, fitur ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        fitur.nama,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1.4f)
                    )
                    TandaFitur(fitur.basic, Modifier.weight(1f))
                    TandaFitur(fitur.custom, Modifier.weight(1f))
                    TandaFitur(fitur.pro, Modifier.weight(1f))
                }
                if (index != daftarFitur.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 12.dp))
            }
        }
    }
}

@Composable
private fun HeaderPaket(paket: PaketAplikasi, paketAktif: PaketAplikasi, modifier: Modifier = Modifier) {
    val aktif = paket == paketAktif
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            paket.label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = if (aktif) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        if (aktif) {
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(50))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    "Aktif",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun TandaFitur(tersedia: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (tersedia) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Tersedia",
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(20.dp)
            )
        } else {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Tidak tersedia",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
