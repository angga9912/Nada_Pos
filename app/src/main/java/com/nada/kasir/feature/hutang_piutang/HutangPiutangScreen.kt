package com.nada.kasir.feature.hutang_piutang

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nada.kasir.core.data.local.entity.HutangPiutangEntity
import com.nada.kasir.core.data.local.entity.HutangPiutangPembayaranEntity
import com.nada.kasir.core.domain.logic.HutangPiutangLogic
import com.nada.kasir.core.domain.logic.HutangPiutangLogic.KondisiJatuhTempo
import com.nada.kasir.core.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val WarnaMerah = Color(0xFFDC2626)
private val WarnaHijau = Color(0xFF16A34A)
private val WarnaOranye = Color(0xFFD97706)

private fun tanggalPendek(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale("in", "ID")).format(Date(millis))

/** Opsi jatuh tempo di formulir: null = tanpa jatuh tempo. */
private val OPSI_JATUH_TEMPO: List<Pair<Int?, String>> =
    listOf(null to "Tanpa jatuh tempo", 7 to "7 hari lagi", 14 to "14 hari lagi", 30 to "30 hari lagi")

@Composable
fun HutangPiutangScreen(viewModel: HutangPiutangViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var tampilFormBaru by remember { mutableStateOf(false) }
    var tampilBayar by remember { mutableStateOf(false) }
    var tampilEdit by remember { mutableStateOf(false) }
    var tampilKonfirmasiBatal by remember { mutableStateOf(false) }

    LaunchedEffect(state.pesan) {
        state.pesan?.let {
            snackbar.showSnackbar(it)
            viewModel.pesanDibaca()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { tampilFormBaru = true }) {
                Icon(Icons.Default.Add, contentDescription = "Tambah hutang/piutang")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Text("Hutang / Piutang", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            RingkasanKartu(state)
            Spacer(Modifier.height(8.dp))

            val tab = if (state.jenisTerpilih == HutangPiutangLogic.JENIS_PIUTANG) 0 else 1
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { viewModel.pilihJenis(HutangPiutangLogic.JENIS_PIUTANG) }, text = { Text("Piutang") })
                Tab(selected = tab == 1, onClick = { viewModel.pilihJenis(HutangPiutangLogic.JENIS_HUTANG) }, text = { Text("Hutang") })
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Switch(checked = state.tampilkanSelesai, onCheckedChange = viewModel::setTampilkanSelesai)
                Spacer(Modifier.width(8.dp))
                Text("Tampilkan yang lunas / dibatalkan", style = MaterialTheme.typography.bodySmall)
            }

            if (state.daftar.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.jenisTerpilih == HutangPiutangLogic.JENIS_PIUTANG)
                            "Belum ada piutang. Piutang dari penjualan \"bayar nanti\" di Kasir juga muncul di sini."
                        else "Belum ada hutang tercatat.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(state.daftar, key = { it.id }) { item ->
                        BarisCatatan(item, onClick = { viewModel.bukaDetail(item.id) })
                    }
                }
            }
        }
    }

    if (tampilFormBaru) {
        FormCatatanDialog(
            judul = "Catat Hutang/Piutang",
            jenisAwal = state.jenisTerpilih,
            onDismiss = { tampilFormBaru = false },
            onSimpan = { jenis, pelaku, jumlah, hari, ket ->
                viewModel.catatBaru(jenis, pelaku, jumlah, hari, ket)
                tampilFormBaru = false
            }
        )
    }

    val detail = state.detail
    if (detail != null) {
        DetailDialog(
            item = detail,
            riwayat = state.riwayatPembayaran,
            onTutup = viewModel::tutupDetail,
            onBayar = { tampilBayar = true },
            onEdit = { tampilEdit = true },
            onBatalkan = { tampilKonfirmasiBatal = true }
        )
        if (tampilBayar) {
            BayarDialog(
                sisa = detail.sisa,
                onDismiss = { tampilBayar = false },
                onSimpan = { jumlah, catatan ->
                    viewModel.catatPembayaran(detail.id, jumlah, catatan)
                    tampilBayar = false
                }
            )
        }
        if (tampilEdit) {
            EditDialog(
                item = detail,
                bolehUbahJumlah = detail.nomorReferensi == null && detail.status == HutangPiutangLogic.STATUS_AKTIF &&
                    state.riwayatPembayaran.isEmpty(),
                onDismiss = { tampilEdit = false },
                onSimpan = { pelaku, jatuhTempo, ket, jumlahBaru ->
                    viewModel.ubah(detail.id, pelaku, jatuhTempo, ket, jumlahBaru)
                    tampilEdit = false
                }
            )
        }
        if (tampilKonfirmasiBatal) {
            AlertDialog(
                onDismissRequest = { tampilKonfirmasiBatal = false },
                title = { Text("Batalkan catatan?") },
                text = { Text("Catatan ${detail.pelaku} akan ditandai dibatalkan dan tidak dihitung lagi. Baris tidak dihapus.") },
                confirmButton = {
                    TextButton(onClick = { viewModel.batalkan(detail.id); tampilKonfirmasiBatal = false }) { Text("Ya, batalkan") }
                },
                dismissButton = { TextButton(onClick = { tampilKonfirmasiBatal = false }) { Text("Tidak") } }
            )
        }
    }
}

@Composable
private fun RingkasanKartu(state: HutangPiutangUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Total piutang (belum dibayar)", style = MaterialTheme.typography.labelSmall)
                    Text(CurrencyFormatter.format(state.totalSisaAktifPiutang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Total hutang (harus dibayar)", style = MaterialTheme.typography.labelSmall)
                    Text(CurrencyFormatter.format(state.totalSisaAktifHutang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            if (state.jumlahTerlambat > 0) {
                Spacer(Modifier.height(4.dp))
                Text("${state.jumlahTerlambat} catatan melewati jatuh tempo", style = MaterialTheme.typography.bodySmall, color = WarnaMerah)
            }
        }
    }
}

@Composable
private fun BarisCatatan(item: HutangPiutangEntity, onClick: () -> Unit) {
    val sekarang = System.currentTimeMillis()
    val kondisi = HutangPiutangLogic.kondisiJatuhTempo(item.status, item.tanggalJatuhTempo, sekarang)
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.pelaku, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(CurrencyFormatter.format(item.sisa), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text(
                "Dari ${CurrencyFormatter.format(item.jumlah)} • ${tanggalPendek(item.tanggalTransaksi)}" +
                    (item.nomorReferensi?.let { " • $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val (label, warna) = labelDanWarna(item, kondisi, sekarang)
            if (label != null) Text(label, style = MaterialTheme.typography.labelMedium, color = warna)
        }
    }
}

private fun labelDanWarna(item: HutangPiutangEntity, kondisi: KondisiJatuhTempo, sekarang: Long): Pair<String?, Color> = when {
    item.status == HutangPiutangLogic.STATUS_LUNAS -> "LUNAS" to WarnaHijau
    item.status == HutangPiutangLogic.STATUS_BATAL -> "DIBATALKAN" to Color.Gray
    kondisi == KondisiJatuhTempo.TERLAMBAT -> {
        val hari = -HutangPiutangLogic.selisihHari(item.tanggalJatuhTempo ?: sekarang, sekarang)
        "Terlambat $hari hari" to WarnaMerah
    }
    kondisi == KondisiJatuhTempo.HARI_INI -> "Jatuh tempo hari ini" to WarnaOranye
    kondisi == KondisiJatuhTempo.BELUM -> "Jatuh tempo ${tanggalPendek(item.tanggalJatuhTempo ?: sekarang)}" to Color.Unspecified
    else -> null to Color.Unspecified
}

@Composable
private fun PilihanJatuhTempo(terpilih: Int?, onPilih: (Int?) -> Unit) {
    OPSI_JATUH_TEMPO.forEach { (hari, label) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = terpilih == hari, onClick = { onPilih(hari) })
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FormCatatanDialog(
    judul: String,
    jenisAwal: String,
    onDismiss: () -> Unit,
    onSimpan: (jenis: String, pelaku: String, jumlah: Double, jatuhTempoHari: Int?, keterangan: String?) -> Unit
) {
    var jenis by remember { mutableStateOf(jenisAwal) }
    var pelaku by remember { mutableStateOf("") }
    var jumlahText by remember { mutableStateOf("") }
    var keterangan by remember { mutableStateOf("") }
    var hari by remember { mutableStateOf<Int?>(null) }
    val jumlah = jumlahText.toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(judul) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = jenis == HutangPiutangLogic.JENIS_PIUTANG, onClick = { jenis = HutangPiutangLogic.JENIS_PIUTANG })
                    Text("Piutang (orang berutang ke toko)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = jenis == HutangPiutangLogic.JENIS_HUTANG, onClick = { jenis = HutangPiutangLogic.JENIS_HUTANG })
                    Text("Hutang (toko berutang)")
                }
                OutlinedTextField(value = pelaku, onValueChange = { pelaku = it }, label = { Text("Nama pihak") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = jumlahText, onValueChange = { jumlahText = it.filter { c -> c.isDigit() } },
                    label = { Text("Jumlah (Rp)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = keterangan, onValueChange = { keterangan = it }, label = { Text("Keterangan (opsional)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Jatuh tempo", style = MaterialTheme.typography.labelMedium)
                PilihanJatuhTempo(hari) { hari = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pelaku.isNotBlank() && jumlah > 0.0,
                onClick = { onSimpan(jenis, pelaku, jumlah, hari, keterangan.ifBlank { null }) }
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun DetailDialog(
    item: HutangPiutangEntity,
    riwayat: List<HutangPiutangPembayaranEntity>,
    onTutup: () -> Unit,
    onBayar: () -> Unit,
    onEdit: () -> Unit,
    onBatalkan: () -> Unit
) {
    val aktif = item.status == HutangPiutangLogic.STATUS_AKTIF
    AlertDialog(
        onDismissRequest = onTutup,
        title = { Text("${HutangPiutangLogic.labelJenis(item.jenis)}: ${item.pelaku}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Jumlah awal: ${CurrencyFormatter.format(item.jumlah)}")
                Text("Sisa: ${CurrencyFormatter.format(item.sisa)}", fontWeight = FontWeight.Bold)
                Text("Status: ${item.status}")
                Text("Tanggal: ${tanggalPendek(item.tanggalTransaksi)}")
                item.tanggalJatuhTempo?.let { Text("Jatuh tempo: ${tanggalPendek(it)}") }
                item.nomorReferensi?.let {
                    Text(if (item.jenis == HutangPiutangLogic.JENIS_HUTANG) "Dari pembelian: $it" else "Dari transaksi: $it")
                }
                item.keterangan?.let { Text("Keterangan: $it") }
                Spacer(Modifier.height(8.dp))
                Text("Riwayat pembayaran", style = MaterialTheme.typography.titleSmall)
                if (riwayat.isEmpty()) {
                    Text("Belum ada pembayaran.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    riwayat.forEach { p ->
                        Text(
                            "${tanggalPendek(p.tanggal)} • ${CurrencyFormatter.format(p.jumlah)}" + (p.catatan?.let { " • $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (aktif) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onBayar) { Text("Catat Pembayaran") }
                        OutlinedButton(onClick = onEdit) { Text("Edit") }
                    }
                    if (item.nomorReferensi == null && riwayat.isEmpty()) {
                        TextButton(onClick = onBatalkan) { Text("Batalkan catatan", color = WarnaMerah) }
                    }
                } else if (item.status == HutangPiutangLogic.STATUS_LUNAS) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onEdit) { Text("Edit") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onTutup) { Text("Tutup") } }
    )
}

@Composable
private fun BayarDialog(sisa: Double, onDismiss: () -> Unit, onSimpan: (Double, String?) -> Unit) {
    var jumlahText by remember { mutableStateOf("") }
    var catatan by remember { mutableStateOf("") }
    val jumlah = jumlahText.toDoubleOrNull() ?: 0.0
    val kelebihan = jumlah > sisa + HutangPiutangLogic.TOLERANSI_RUPIAH
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Catat Pembayaran") },
        text = {
            Column {
                Text("Sisa saat ini: ${CurrencyFormatter.format(sisa)}")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = jumlahText, onValueChange = { jumlahText = it.filter { c -> c.isDigit() } },
                    label = { Text("Jumlah dibayar (Rp)") }, singleLine = true, isError = kelebihan,
                    supportingText = { if (kelebihan) Text("Melebihi sisa") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { jumlahText = sisa.toLong().toString() }) { Text("Lunasi semua") }
                OutlinedTextField(value = catatan, onValueChange = { catatan = it }, label = { Text("Catatan (opsional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = jumlah > 0.0 && !kelebihan, onClick = { onSimpan(jumlah, catatan.ifBlank { null }) }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun EditDialog(
    item: HutangPiutangEntity,
    bolehUbahJumlah: Boolean,
    onDismiss: () -> Unit,
    onSimpan: (pelaku: String, jatuhTempo: Long?, keterangan: String?, jumlahBaru: Double?) -> Unit
) {
    var pelaku by remember { mutableStateOf(item.pelaku) }
    var jumlahText by remember { mutableStateOf(item.jumlah.toLong().toString()) }
    var keterangan by remember { mutableStateOf(item.keterangan.orEmpty()) }
    // -1 = jatuh tempo tidak diubah, 0 = hapus jatuh tempo, selain itu = berapa hari dari sekarang.
    var pilihanHari by remember { mutableStateOf(-1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Catatan") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = pelaku, onValueChange = { pelaku = it }, label = { Text("Nama pihak") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = jumlahText, onValueChange = { jumlahText = it.filter { c -> c.isDigit() } },
                    label = { Text("Jumlah (Rp)") }, singleLine = true, enabled = bolehUbahJumlah,
                    supportingText = { if (!bolehUbahJumlah) Text("Tidak bisa diubah: dari penjualan, sudah ada pembayaran, atau sudah selesai") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = keterangan, onValueChange = { keterangan = it }, label = { Text("Keterangan") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(
                    "Jatuh tempo" + (item.tanggalJatuhTempo?.let { " (sekarang ${tanggalPendek(it)})" } ?: " (belum diatur)"),
                    style = MaterialTheme.typography.labelMedium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = pilihanHari == -1, onClick = { pilihanHari = -1 })
                    Text("Jangan diubah")
                }
                OPSI_JATUH_TEMPO.forEach { (hari, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = pilihanHari == (hari ?: 0), onClick = { pilihanHari = hari ?: 0 })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pelaku.isNotBlank() && (!bolehUbahJumlah || (jumlahText.toDoubleOrNull() ?: 0.0) > 0.0),
                onClick = {
                    val jatuhTempo = when (pilihanHari) {
                        -1 -> item.tanggalJatuhTempo
                        0 -> null
                        else -> HutangPiutangLogic.jatuhTempoDariHari(pilihanHari, System.currentTimeMillis())
                    }
                    onSimpan(pelaku, jatuhTempo, keterangan.ifBlank { null }, if (bolehUbahJumlah) jumlahText.toDoubleOrNull() else null)
                }
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
