package com.nada.kasir.feature.pengaturan_toko

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.nada.kasir.branding.ThemeConfig
import com.nada.kasir.core.data.local.entity.StoreEntity
import com.nada.kasir.core.paket.PaketAplikasi
import java.io.File

/** PENGATURAN TOKO (poin 1) + branding warna (poin 2) + custom struk (poin 10). */
@Composable
fun PengaturanTokoScreen(viewModel: PengaturanTokoViewModel = hiltViewModel()) {
    val storeDb by viewModel.store.collectAsState()
    val statusLisensi by viewModel.statusLisensi.collectAsState()
    val pesanAktivasi by viewModel.pesanAktivasi.collectAsState()
    val context = LocalContext.current
    var tersimpanPesan by remember { mutableStateOf(false) }
    var kodeAktivasi by remember { mutableStateOf("") }

    // State form lokal, diisi dari data toko begitu tersedia
    var nama by remember { mutableStateOf("") }
    var alamat by remember { mutableStateOf("") }
    var whatsapp by remember { mutableStateOf("") }
    var telepon by remember { mutableStateOf("") }
    var pemilik by remember { mutableStateOf("") }
    var slogan by remember { mutableStateOf("") }
    var footer by remember { mutableStateOf("") }
    var mataUang by remember { mutableStateOf("Rp") }
    var ukuranKertas by remember { mutableStateOf("58mm") }
    var warnaUtama by remember { mutableStateOf("#2E7D32") }
    var tampilkanLogo by remember { mutableStateOf(true) }
    var tampilkanAlamat by remember { mutableStateOf(true) }
    var tampilkanWa by remember { mutableStateOf(true) }
    var tampilkanDiskon by remember { mutableStateOf(true) }
    var sudahDiisi by remember { mutableStateOf(false) }

    LaunchedEffect(storeDb) {
        val s = storeDb ?: return@LaunchedEffect
        if (!sudahDiisi) {
            nama = s.nama; alamat = s.alamat; whatsapp = s.whatsapp; telepon = s.telepon
            pemilik = s.pemilik; slogan = s.slogan; footer = s.footerStruk; mataUang = s.mataUang
            ukuranKertas = s.ukuranKertas; warnaUtama = s.warnaUtama
            tampilkanLogo = s.tampilkanLogoStruk; tampilkanAlamat = s.tampilkanAlamatStruk
            tampilkanWa = s.tampilkanWaStruk; tampilkanDiskon = s.tampilkanDiskonStruk
            sudahDiisi = true
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScrollFallback().padding(16.dp)) {
        Text("Pengaturan Toko", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        // Logo Toko (poin 1 & 2) - dipakai di header Dashboard dan struk.
        LogoTokoSection(
            logoPath = storeDb?.logoPath,
            onLogoDipilih = { uri ->
                val current = storeDb ?: StoreEntity()
                viewModel.gantiLogo(context, uri, current)
            }
        )
        Spacer(Modifier.height(20.dp))
        Divider()
        Spacer(Modifier.height(16.dp))

        // Status lisensi & aktivasi kode (model bisnis freemium: Basic gratis, Custom/Pro berbayar)
        Text("Status Lisensi", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Paket aktif: ${statusLisensi.paket.label}", style = MaterialTheme.typography.bodyMedium)
                val kadaluarsaMillis = statusLisensi.kadaluarsaMillis
                when {
                    statusLisensi.paket == PaketAplikasi.BASIC -> Text(
                        "Upgrade ke Custom/Pro untuk membuka lebih banyak fitur.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    statusLisensi.isLifetime -> Text(
                        "Lisensi permanen - tidak pernah kadaluarsa.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    kadaluarsaMillis != null -> Text(
                        "Aktif sampai: ${java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale("id","ID")).format(java.util.Date(kadaluarsaMillis))}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = kodeAktivasi,
            onValueChange = { kodeAktivasi = it },
            label = { Text("Kode Aktivasi") },
            placeholder = { Text("NADA-PRO-20271231-XXXXXXXX") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { viewModel.aktivasiLisensi(kodeAktivasi); kodeAktivasi = "" },
            enabled = kodeAktivasi.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Aktivasi") }
        Text(
            "Belum punya kode? Hubungi penjual aplikasi ini untuk upgrade paket.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(20.dp))
        Divider()
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(nama, { nama = it }, label = { Text("Nama Toko") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(alamat, { alamat = it }, label = { Text("Alamat") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(whatsapp, { whatsapp = it }, label = { Text("Nomor WhatsApp") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(telepon, { telepon = it }, label = { Text("Nomor Telepon") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(pemilik, { pemilik = it }, label = { Text("Nama Pemilik") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(slogan, { slogan = it }, label = { Text("Slogan") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(footer, { footer = it }, label = { Text("Footer Struk") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(mataUang, { mataUang = it }, label = { Text("Simbol Mata Uang") }, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(16.dp))
        Text("Ukuran Kertas Struk", style = MaterialTheme.typography.titleMedium)
        Row {
            listOf("58mm", "80mm").forEach { ukuran ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 16.dp)) {
                    RadioButton(selected = ukuranKertas == ukuran, onClick = { ukuranKertas = ukuran })
                    Text(ukuran)
                }
            }
        }

        if (statusLisensi.paket.mencakup(PaketAplikasi.CUSTOM)) {
            Spacer(Modifier.height(16.dp))
            Text("Warna Utama Aplikasi", style = MaterialTheme.typography.titleMedium)
            Row {
                ThemeConfig.PRESET_WARNA.forEach { (hex, _) ->
                    val warna = try { Color(("FF" + hex.removePrefix("#")).toLong(16)) } catch (e: Exception) { Color.Gray }
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(warna)
                            .border(width = if (warnaUtama == hex) 3.dp else 0.dp, color = Color.Black, shape = CircleShape)
                            .clickable { warnaUtama = hex }
                    )
                }
            }
        } else {
            Spacer(Modifier.height(16.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Custom warna aplikasi tersedia di paket Custom & Pro.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Pengaturan Struk", style = MaterialTheme.typography.titleMedium)
        BarisToggle("Tampilkan Logo di Struk", tampilkanLogo) { tampilkanLogo = it }
        BarisToggle("Tampilkan Alamat di Struk", tampilkanAlamat) { tampilkanAlamat = it }
        BarisToggle("Tampilkan WhatsApp di Struk", tampilkanWa) { tampilkanWa = it }
        BarisToggle("Tampilkan Diskon di Struk", tampilkanDiskon) { tampilkanDiskon = it }
        Text(
            "Catatan: Harga Beli TIDAK PERNAH ditampilkan di struk pelanggan.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                val existing = storeDb ?: StoreEntity()
                viewModel.simpan(
                    existing.copy(
                        nama = nama, alamat = alamat, whatsapp = whatsapp, telepon = telepon,
                        pemilik = pemilik, slogan = slogan, footerStruk = footer, mataUang = mataUang,
                        ukuranKertas = ukuranKertas, warnaUtama = warnaUtama,
                        tampilkanLogoStruk = tampilkanLogo, tampilkanAlamatStruk = tampilkanAlamat,
                        tampilkanWaStruk = tampilkanWa, tampilkanDiskonStruk = tampilkanDiskon,
                        tampilkanHargaBeliStruk = false // dipaksa false, tidak bisa diubah dari UI (poin 10)
                    )
                ) { tersimpanPesan = true }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("Simpan Pengaturan") }
        Spacer(Modifier.height(24.dp))
    }

    if (tersimpanPesan) {
        AlertDialog(
            onDismissRequest = { tersimpanPesan = false },
            confirmButton = { TextButton(onClick = { tersimpanPesan = false }) { Text("OK") } },
            title = { Text("Tersimpan") },
            text = { Text("Pengaturan toko berhasil disimpan. Warna aplikasi akan langsung berubah.") }
        )
    }

    pesanAktivasi?.let { pesan ->
        AlertDialog(
            onDismissRequest = { viewModel.clearPesanAktivasi() },
            confirmButton = { TextButton(onClick = { viewModel.clearPesanAktivasi() }) { Text("OK") } },
            title = { Text("Aktivasi Lisensi") },
            text = { Text(pesan) }
        )
    }
}

@Composable
private fun BarisToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// Helper kecil supaya Column bisa discroll tanpa nambah dependency baru di sini
@Composable
private fun Modifier.verticalScrollFallback(): Modifier {
    val scrollState = rememberScrollState()
    return this.verticalScroll(scrollState)
}

@Composable
private fun LogoTokoSection(logoPath: String?, onLogoDipilih: (android.net.Uri) -> Unit) {
    val pilihGambar = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onLogoDipilih(uri)
    }

    Text("Logo Toko", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        "Logo ini akan tampil di header aplikasi dan di bagian atas struk.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            if (!logoPath.isNullOrBlank() && File(logoPath).exists()) {
                AsyncImage(
                    model = File(logoPath),
                    contentDescription = "Logo toko",
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Icon(Icons.Filled.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        OutlinedButton(onClick = { pilihGambar.launch("image/*") }) {
            Text(if (logoPath.isNullOrBlank()) "Unggah Logo" else "Ganti Logo")
        }
    }
}
