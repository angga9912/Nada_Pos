package com.nada.kasir.feature.kasir.barcode

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.nada.kasir.core.domain.model.KeranjangItem
import com.nada.kasir.core.util.AutoScanThrottle
import com.nada.kasir.core.util.CurrencyFormatter
import java.util.concurrent.Executors

/**
 * Pilihan Auto Scan untuk toggle di layar kamera (dipakai layar scan satu kali DAN layar Auto Scan).
 *
 * @param aktif pilihan toggle yang tersimpan.
 * @param terkunci true = paket belum mengizinkan (Basic): toggle tampil dengan gembok dan
 *   menekannya memicu [onTerkunciDiklik] (ajakan upgrade), bukan mengubah pilihan.
 */
data class OpsiAutoScan(
    val aktif: Boolean,
    val terkunci: Boolean,
    val onUbah: (Boolean) -> Unit,
    val onTerkunciDiklik: () -> Unit
)

/** Toggle "Auto Scan" berbentuk pil di bar atas layar kamera. */
@Composable
internal fun ToggleAutoScan(opsi: OpsiAutoScan, modifier: Modifier = Modifier) {
    val menyala = opsi.aktif && !opsi.terkunci
    Surface(
        onClick = { if (opsi.terkunci) opsi.onTerkunciDiklik() else opsi.onUbah(!opsi.aktif) },
        shape = RoundedCornerShape(50),
        color = if (menyala) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.18f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (opsi.terkunci) {
                Icon(Icons.Filled.Lock, contentDescription = "Terkunci", tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = when {
                    opsi.terkunci -> "Auto Scan"
                    opsi.aktif -> "Auto Scan: ON"
                    else -> "Auto Scan: OFF"
                },
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/**
 * Mode Auto Scan (paket Custom dan Pro): kamera menyala terus dan membaca barcode berulang kali
 * sampai kasir menutupnya.
 *
 * Tata letak: portrait = kamera di atas, daftar keranjang di bawah; landscape = kamera di kiri,
 * daftar di kanan. Daftar adalah SELURUH isi keranjang Kasir (bukan daftar terpisah), sehingga
 * tombol tutup kamera cukup kembali ke layar Kasir dengan keranjang yang sudah terisi.
 *
 * Layar ini TIDAK memegang logika keranjang/stok - semuanya lewat callback ke KasirViewModel
 * (tambahDariAutoScan, ubahQtyAutoScan).
 *
 * @param jeda true = pemindaian dihentikan sementara (dialog produk baru / pesan error sedang tampil).
 */
@Composable
fun AutoScanScreen(
    keranjang: List<KeranjangItem>,
    jeda: Boolean,
    opsiAutoScan: OpsiAutoScan,
    onBarcodeTerbaca: (String) -> Unit,
    onUbahQty: (Long, Int) -> Unit,
    onSelesai: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Tombol kembali sistem = tutup kamera & kembali ke Kasir (keranjang tetap terisi).
    BackHandler(onBack = onSelesai)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
        if (!hasCameraPermission) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Izin kamera dibutuhkan untuk scan barcode.", color = Color.White)
                TextButton(onClick = onSelesai) { Text("Kembali") }
            }
            return@Box
        }

        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (landscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                AreaKameraAuto(
                    jeda = jeda,
                    opsi = opsiAutoScan,
                    onBarcodeTerbaca = onBarcodeTerbaca,
                    onKembali = onSelesai,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                PanelDaftarScan(
                    keranjang = keranjang,
                    onUbahQty = onUbahQty,
                    onSelesai = onSelesai,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                AreaKameraAuto(
                    jeda = jeda,
                    opsi = opsiAutoScan,
                    onBarcodeTerbaca = onBarcodeTerbaca,
                    onKembali = onSelesai,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                PanelDaftarScan(
                    keranjang = keranjang,
                    onUbahQty = onUbahQty,
                    onSelesai = onSelesai,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }
    }
}

/** Area kamera: preview + pemindaian beruntun (tanpa "kunci" setelah satu barcode terbaca). */
@Composable
private fun AreaKameraAuto(
    jeda: Boolean,
    opsi: OpsiAutoScan,
    onBarcodeTerbaca: (String) -> Unit,
    onKembali: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    // Nilai terbaru dibaca dari dalam callback kamera/ML Kit yang dibuat sekali (di factory).
    val jedaTerbaru by rememberUpdatedState(jeda)
    val onBarcodeTerbaru by rememberUpdatedState(onBarcodeTerbaca)

    var pesanError by remember { mutableStateOf<String?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val throttle = remember { AutoScanThrottle() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        try {
            BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(
                        Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39,
                        Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_QR_CODE
                    ).build()
            )
        } catch (t: Throwable) {
            null
        }
    }

    LaunchedEffect(Unit) {
        if (scanner == null) pesanError = "Pemindai barcode gagal dimulai."
    }

    // Kamera lanjut setelah dijeda (mis. selesai menambah produk baru): mulai ulang jeda 1 detik
    // supaya barcode yang masih di depan kamera tidak langsung tertambah lagi.
    LaunchedEffect(jeda) {
        if (!jeda) throttle.tahanSemua(SystemClock.elapsedRealtime())
    }

    // Layar ini dipakai terus-menerus selama kasir berbelanja: bersihkan kamera, pemindai, dan thread.
    DisposableEffect(Unit) {
        onDispose {
            runCatching { cameraProviderRef?.unbindAll() }
            runCatching { scanner?.close() }
            executor.shutdown()
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        cameraProviderRef = cameraProvider
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        analysis.setAnalyzer(executor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage == null || scanner == null || jedaTerbaru) {
                                imageProxy.close()
                            } else {
                                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                scanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        val kode = barcodes.firstNotNullOfOrNull { it.rawValue }
                                        if (kode != null && !jedaTerbaru &&
                                            throttle.terima(kode, SystemClock.elapsedRealtime())
                                        ) {
                                            onBarcodeTerbaru(kode)
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        if (pesanError == null) pesanError = "Pemindaian gagal (${e.javaClass.simpleName})."
                                    }
                                    .addOnCompleteListener { imageProxy.close() }
                            }
                        }

                        cameraProvider.unbindAll()
                        cameraRef = cameraProvider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                        )
                    } catch (t: Throwable) {
                        pesanError = "Kamera gagal dibuka (${t.javaClass.simpleName})."
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        // Bar atas: kembali (tutup kamera) | toggle Auto Scan | senter
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScannerIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, deskripsi = "Tutup kamera", onClick = onKembali)
            ToggleAutoScan(opsi)
            if (cameraRef?.cameraInfo?.hasFlashUnit() == true) {
                ScannerIconButton(
                    icon = if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    deskripsi = "Senter",
                    onClick = {
                        torchOn = !torchOn
                        cameraRef?.cameraControl?.enableTorch(torchOn)
                    }
                )
            } else {
                Spacer(Modifier.size(40.dp))
            }
        }

        BingkaiScan(
            sudahTerbaca = false,
            ukuran = 150.dp,
            modifier = Modifier.align(Alignment.Center)
        )

        Text(
            "Arahkan kamera ke barcode produk",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
        )

        pesanError?.let { pesan ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            ) {
                Text(
                    pesan,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

/** Panel daftar keranjang + total + tombol tutup kamera. */
@Composable
private fun PanelDaftarScan(
    keranjang: List<KeranjangItem>,
    onUbahQty: (Long, Int) -> Unit,
    onSelesai: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalItem = keranjang.sumOf { it.qty }
    val total = keranjang.sumOf { it.subtotal }
    val listState = rememberLazyListState()

    // Gulir ke produk yang barusan berubah (baru masuk / qty bertambah) supaya terlihat tanpa disentuh.
    var keranjangSebelumnya by remember { mutableStateOf(keranjang) }
    LaunchedEffect(keranjang) {
        val indeksBerubah = keranjang.indexOfFirst { baru ->
            keranjangSebelumnya.firstOrNull { it.productId == baru.productId }?.qty != baru.qty
        }
        if (indeksBerubah >= 0) listState.animateScrollToItem(indeksBerubah)
        keranjangSebelumnya = keranjang
    }

    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Keranjang · $totalItem item",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium)
                )
                Text(CurrencyFormatter.format(total), style = MaterialTheme.typography.titleSmall)
            }
            HorizontalDivider()

            if (keranjang.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "Belum ada produk. Scan barcode untuk menambahkan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(keranjang, key = { it.productId }) { item ->
                        BarisScan(item = item, onUbahQty = onUbahQty)
                        HorizontalDivider()
                    }
                }
            }

            Button(
                onClick = onSelesai,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            ) {
                Text(if (keranjang.isEmpty()) "Tutup Kamera" else "Tutup Kamera & Lihat Belanjaan")
            }
        }
    }
}

@Composable
private fun BarisScan(item: KeranjangItem, onUbahQty: (Long, Int) -> Unit) {
    // Qty di atas stok yang tercatat: tetap boleh masuk di Auto Scan, tapi pembayaran akan ditolak
    // sampai qty dikurangi atau stok dikoreksi - jadi kasir diberi tahu lebih awal di sini.
    val stokKurang = item.qty > item.stokTersedia
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.nama,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${CurrencyFormatter.format(item.harga)} × ${item.qty} · ${CurrencyFormatter.format(item.subtotal)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(4.dp))
            TombolQtyScan(label = "−", onClick = { onUbahQty(item.productId, item.qty - 1) })
            Text("${item.qty}", modifier = Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodyMedium)
            TombolQtyScan(label = "+", onClick = { onUbahQty(item.productId, item.qty + 1) })
        }
        if (stokKurang) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Stok tersisa ${item.stokTersedia.coerceAtLeast(0)} - kurangi qty atau koreksi stok sebelum bayar",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun TombolQtyScan(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        contentPadding = PaddingValues(0.dp),
        shape = CircleShape
    ) {
        Text(label)
    }
}
