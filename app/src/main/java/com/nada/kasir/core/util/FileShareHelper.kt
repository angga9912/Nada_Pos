package com.nada.kasir.core.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object FileShareHelper {
    /** Membuka Share Sheet Android untuk file export/backup (poin 15 & 17). */
    fun bagikanFile(context: Context, file: File, mimeType: String = "application/octet-stream") {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Bagikan file").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Membuka Share Sheet Android untuk membagikan struk teks (poin 9 & 15). */
    fun bagikanTeks(context: Context, teks: String, judul: String = "Bagikan Struk") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, teks)
        }
        context.startActivity(Intent.createChooser(intent, judul).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
