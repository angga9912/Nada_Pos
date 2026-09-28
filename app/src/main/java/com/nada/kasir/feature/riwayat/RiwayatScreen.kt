package com.nada.kasir.feature.riwayat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nada.kasir.core.data.local.entity.TransactionStatus
import com.nada.kasir.core.util.CurrencyFormatter
import com.nada.kasir.feature.struk.StrukPreviewDialog
import java.text.SimpleDateFormat
import java.util.*

/** RIWAYAT PENJUALAN (poin 13). */
@Composable
fun RiwayatScreen(isAdmin: Boolean = true, viewModel: RiwayatViewModel = hiltViewModel()) {
    val riwayat by viewModel.riwayat.collectAsState()
    val query by viewModel.query.collectAsState()
    val filterRentang by viewModel.filterRentang.collectAsState()
    val previewStruk by viewModel.previewStruk.collectAsState()
    val sedangMencetak by viewModel.sedangMencetak.collectAsState()
    var konfirmasiBatalId by remember { mutableStateOf<Long?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID")) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("Riwayat Penjualan", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text("Cari nomor transaksi / pembeli...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Hapus pencarian")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterRentangRiwayat.values().forEach { filter ->
                FilterChip(
                    selected = filterRentang == filter,
                    onClick = { viewModel.onFilterRentangChange(filter) },
                    label = { Text(filter.label) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        if (riwayat.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (query.isNotBlank()) "Tidak ada transaksi yang sesuai." else "Belum ada transaksi pada periode ini.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().weight(1f)) {
                items(riwayat, key = { it.id }) { trx ->
                    ListItem(
                        headlineContent = { Text("${trx.noTransaksi}${if (trx.nomorAntrian > 0) "  •  Antrian #${trx.nomorAntrian}" else ""}") },
                        supportingContent = {
                            val infoPembeli = if (!trx.namaPembeli.isNullOrBlank()) " • ${trx.namaPembeli}" else ""
                            Text("${sdf.format(Date(trx.tanggalWaktu))}$infoPembeli")
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(CurrencyFormatter.format(trx.total))
                                if (trx.status == TransactionStatus.CANCELLED) {
                                    Text("DIBATALKAN", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                } else {
                                    Row {
                                        TextButton(onClick = {
                                            viewModel.tampilkanPreviewCetakUlang(trx.id) { pesan -> errorMsg = pesan }
                                        }) { Text("Cetak Ulang") }
                                        if (isAdmin) {
                                            TextButton(onClick = { konfirmasiBatalId = trx.id }) { Text("Batalkan") }
                                        }
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

    previewStruk?.let { teks ->
        StrukPreviewDialog(
            teksStruk = teks,
            sedangMencetak = sedangMencetak,
            onCetak = { viewModel.cetakDariPreview { pesan -> errorMsg = pesan } },
            onTutup = { viewModel.tutupPreviewStruk() }
        )
    }

    // Pembatalan transaksi WAJIB konfirmasi (poin 13)
    konfirmasiBatalId?.let { id ->
        AlertDialog(
            onDismissRequest = { konfirmasiBatalId = null },
            title = { Text("Batalkan Transaksi?") },
            text = { Text("Stok akan dikembalikan. Transaksi tidak akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.batalkanTransaksi(id) { pesan -> errorMsg = pesan }
                    konfirmasiBatalId = null
                }) { Text("Ya, Batalkan") }
            },
            dismissButton = { TextButton(onClick = { konfirmasiBatalId = null }) { Text("Tidak") } }
        )
    }

    errorMsg?.let { pesan ->
        AlertDialog(
            onDismissRequest = { errorMsg = null },
            confirmButton = { TextButton(onClick = { errorMsg = null }) { Text("OK") } },
            title = { Text("Perhatian") },
            text = { Text(pesan) }
        )
    }
}
