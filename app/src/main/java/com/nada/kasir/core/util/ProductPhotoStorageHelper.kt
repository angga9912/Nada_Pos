package com.nada.kasir.core.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * Menyalin foto produk yang dipilih pengguna (lewat file picker/galeri) ke
 * penyimpanan internal aplikasi, supaya path-nya stabil dan tidak bergantung
 * pada izin akses URI sementara dari picker - pola yang sama dengan
 * [LogoStorageHelper], tapi nama file dibuat unik (UUID) karena bisa ada
 * banyak produk sekaligus (bukan cuma satu logo toko).
 */
object ProductPhotoStorageHelper {
    fun simpanFotoDariUri(context: Context, uri: Uri): String? {
        return try {
            val folder = File(context.filesDir, "produk_foto").apply { if (!exists()) mkdirs() }
            val file = File(folder, "foto_${UUID.randomUUID()}.png")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}
