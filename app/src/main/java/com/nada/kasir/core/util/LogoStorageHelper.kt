package com.nada.kasir.core.util

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Menyalin logo toko yang dipilih pengguna (lewat file picker/SAF) ke
 * penyimpanan internal aplikasi, supaya path-nya stabil dan tidak bergantung
 * pada izin akses URI sementara dari picker (poin 1 & 2 brief awal).
 */
object LogoStorageHelper {
    fun simpanLogoDariUri(context: Context, uri: Uri): String? {
        return try {
            val folder = File(context.filesDir, "branding").apply { if (!exists()) mkdirs() }
            val file = File(folder, "logo_toko.png")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}
