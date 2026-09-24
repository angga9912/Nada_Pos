package com.nada.kasir.feature.kasir.barcode

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Layar scan barcode pakai kamera HP (poin 5 & Phase 2).
 * Dipakai dari halaman Kasir sebagai dialog full-screen.
 * onDetected dipanggil sekali per sesi scan (auto-tutup setelah ketemu).
 *
 * Kegagalan membuka kamera/pemindai sekarang DITAMPILKAN sebagai pesan di layar (bukan
 * diam-diam ditelan), supaya penyebabnya kelihatan kalau ada masalah di perangkat tertentu.
 */
@Composable
fun BarcodeScannerScreen(
    onDetected: (String) -> Unit,
    onClose: () -> Unit
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

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            var sudahDeteksi by remember { mutableStateOf(false) }
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
            var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }

            DisposableEffect(lifecycleOwner) {
                onDispose {
                    try { cameraProviderRef?.unbindAll() } catch (e: Exception) { /* abaikan */ }
                    try { scanner?.close() } catch (e: Exception) { /* abaikan */ }
                    try { executor.shutdown() } catch (e: Exception) { /* abaikan */ }
                }
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    if (scanner == null) {
                        previewView.post { pesanError = "Pemindai barcode gagal dimulai." }
                        return@AndroidView previewView
                    }

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
                                if (mediaImage != null && !sudahDeteksi) {
                                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    scanner.process(image)
                                        .addOnSuccessListener { barcodes ->
                                            val kode = barcodes.firstOrNull()?.rawValue
                                            if (kode != null && !sudahDeteksi) {
                                                sudahDeteksi = true
                                                onDetected(kode)
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
                            cameraProvider.bindToLifecycle(
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
            Text(
                "Arahkan kamera ke barcode",
                modifier = Modifier.align(Alignment.TopCenter),
                color = androidx.compose.ui.graphics.Color.White
            )
            pesanError?.let { pesan ->
                Text(
                    pesan,
                    modifier = Modifier.align(Alignment.Center),
                    color = androidx.compose.ui.graphics.Color.Yellow
                )
            }
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.BottomCenter)) {
                Text("Tutup", color = androidx.compose.ui.graphics.Color.White)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Izin kamera dibutuhkan untuk scan barcode.")
                TextButton(onClick = onClose) { Text("Kembali") }
            }
        }
    }
}
