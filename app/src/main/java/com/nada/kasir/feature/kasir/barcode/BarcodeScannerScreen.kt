package com.nada.kasir.feature.kasir.barcode

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.nada.kasir.core.data.local.entity.ProductEntity
import com.nada.kasir.core.util.CurrencyFormatter
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

/**
 * Layar scan barcode pakai kamera HP (poin 5 & Phase 2), tampilan mengikuti
 * desain mockup "frame scanning + konfirmasi produk ditemukan": frame dengan
 * garis pemindai bergerak, lalu kartu konfirmasi (nama & harga produk) muncul
 * sesaat sebelum layar ini menutup sendiri.
 *
 * PENTING soal logic - TIDAK ADA yang berubah di alur data:
 * - Pencarian produk di kartu konfirmasi murni untuk tampilan (read-only, lewat
 *   BarcodeScannerViewModel yang memanggil method yang SAMA dengan yang dipakai
 *   KasirViewModel), bukan pengganti logic tambah-ke-keranjang.
 * - onDetected(kode) tetap dipanggil persis seperti sebelumnya di akhir, dan
 *   KasirViewModel.tambahDariBarcode yang tetap menentukan produk ditambahkan
 *   ke keranjang atau ditawarkan sebagai "Tambah Produk Baru" - sama sekali
 *   tidak diduplikasi di layar ini.
 *
 * Kegagalan membuka kamera/pemindai tetap DITAMPILKAN sebagai pesan di layar.
 */
@Composable
fun BarcodeScannerScreen(
    onDetected: (String) -> Unit,
    onClose: () -> Unit,
    viewModel: BarcodeScannerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var pesanError by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Barcode yang baru terbaca -> memicu pencarian produk untuk kartu konfirmasi,
    // lalu setelah jeda singkat memanggil onDetected(kode) (alur asli tidak berubah).
    var kodeTerbaca by remember { mutableStateOf<String?>(null) }
    var produkDitemukan by remember { mutableStateOf<ProductEntity?>(null) }
    var sedangMencari by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(kodeTerbaca) {
        val kode = kodeTerbaca ?: return@LaunchedEffect
        sedangMencari = true
        produkDitemukan = viewModel.cariProduk(kode)
        sedangMencari = false
        delay(900) // animasi singkat & halus sebelum kembali ke layar Kasir
        onDetected(kode)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            var sudahDeteksi by remember { mutableStateOf(false) }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    val executor = Executors.newSingleThreadExecutor()
                    val scanner = try {
                        BarcodeScanning.getClient(
                            BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(
                                    Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                                    Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39,
                                    Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_QR_CODE
                                ).build()
                        )
                    } catch (t: Throwable) {
                        previewView.post { pesanError = "Pemindai barcode gagal dimulai (${t.javaClass.simpleName})." }
                        return@AndroidView previewView
                    }

                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            analysis.setAnalyzer(executor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null && !sudahDeteksi) {
                                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    scanner.process(image)
                                        .addOnSuccessListener { barcodes ->
                                            val kode = barcodes.firstOrNull()?.rawValue
                                            if (kode != null && !sudahDeteksi) {
                                                sudahDeteksi = true
                                                kodeTerbaca = kode
                                            }
                                        }
                                        .addOnFailureListener { e ->
                                            previewView.post {
                                                if (pesanError == null) pesanError = "Pemindaian gagal (${e.javaClass.simpleName})."
                                            }
                                        }
                                        .addOnCompleteListener { imageProxy.close() }
                                } else {
                                    imageProxy.close()
                                }
                            }

                            cameraProvider.unbindAll()
                            cameraRef = cameraProvider.bindToLifecycle(
                                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                            )
                        } catch (t: Throwable) {
                            // Kamera gagal dibuka (mis. dipakai app lain) - tampilkan pesan, pengguna bisa kembali manual
                            previewView.post { pesanError = "Kamera gagal dibuka (${t.javaClass.simpleName})." }
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                }
            )

            // Tombol kembali & senter - overlay di atas feed kamera asli
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ScannerIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, deskripsi = "Kembali", onClick = onClose)
                val punyaFlash = cameraRef?.cameraInfo?.hasFlashUnit() == true
                if (punyaFlash) {
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

            if (kodeTerbaca == null) {
                Text(
                    "Arahkan kamera ke barcode produk",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp)
                )
            }

            // Frame pemindaian: bracket sudut + garis scan bergerak, freeze & jadi hijau saat terbaca
            BingkaiScan(
                sudahTerbaca = kodeTerbaca != null,
                modifier = Modifier.align(Alignment.Center)
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

            AnimatedVisibility(
                visible = kodeTerbaca != null,
                enter = slideInVertically(animationSpec = tween(350)) { it } + fadeIn(tween(350)),
                exit = slideOutVertically(animationSpec = tween(200)) { it } + fadeOut(tween(200)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                KartuKonfirmasiProduk(
                    sedangMencari = sedangMencari,
                    produk = produkDitemukan,
                    kodeBarcode = kodeTerbaca.orEmpty()
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Izin kamera dibutuhkan untuk scan barcode.", color = Color.White)
                TextButton(onClick = onClose) { Text("Kembali") }
            }
        }
    }
}

@Composable
private fun ScannerIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, deskripsi: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.18f),
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = deskripsi, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * Bingkai area pemindaian: 4 bracket sudut + garis horizontal yang bergerak naik-turun
 * ("garis scan bergerak" sesuai desain). Warna memakai warna branding toko yang aktif
 * (MaterialTheme.colorScheme.primary), BUKAN warna biru tetap - supaya kustomisasi
 * warna per toko (poin 2 & 21) tetap konsisten sampai ke layar scan.
 */
@Composable
private fun BingkaiScan(sudahTerbaca: Boolean, modifier: Modifier = Modifier) {
    val warnaAksen = if (sudahTerbaca) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val ukuran = 220.dp

    val infiniteTransition = rememberInfiniteTransition(label = "scanline")
    val progres by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanlineProgress"
    )

    Box(modifier = modifier.size(ukuran)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 3.dp.toPx()
            val panjangSudut = 26.dp.toPx()
            val w = size.width
            val h = size.height
            listOf(
                Offset(0f, 0f) to Offset(panjangSudut, 0f), Offset(0f, 0f) to Offset(0f, panjangSudut),
                Offset(w - panjangSudut, 0f) to Offset(w, 0f), Offset(w, 0f) to Offset(w, panjangSudut),
                Offset(0f, h - panjangSudut) to Offset(0f, h), Offset(0f, h) to Offset(panjangSudut, h),
                Offset(w - panjangSudut, h) to Offset(w, h), Offset(w, h - panjangSudut) to Offset(w, h)
            ).forEach { (dari, ke) ->
                drawLine(color = warnaAksen, start = dari, end = ke, strokeWidth = strokeWidth, cap = StrokeCap.Round)
            }
        }
        if (!sudahTerbaca) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .height(2.dp)
                    .align(Alignment.TopCenter)
                    .offset(y = ukuran * progres)
                    .background(warnaAksen.copy(alpha = 0.85f))
            )
        }
    }
}

/** Kartu konfirmasi di bagian bawah layar setelah barcode terbaca (poin "Produk ditemukan"). */
@Composable
private fun KartuKonfirmasiProduk(sedangMencari: Boolean, produk: ProductEntity?, kodeBarcode: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            when {
                sedangMencari -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Memeriksa kode $kodeBarcode...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                produk != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Produk ditemukan", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!produk.fotoPath.isNullOrBlank() && java.io.File(produk.fotoPath).exists()) {
                                AsyncImage(
                                    model = java.io.File(produk.fotoPath),
                                    contentDescription = produk.nama,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                                )
                            } else {
                                Icon(Icons.Filled.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(produk.nama, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium), maxLines = 1)
                            Text(CurrencyFormatter.format(produk.hargaJual), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                "+1 ke keranjang",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Barcode $kodeBarcode belum terdaftar", style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Kamu akan diarahkan untuk menambahkannya sebagai produk baru.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
