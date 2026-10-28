package com.nada.kasir.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Menyalin foto produk yang dipilih pengguna (lewat file picker/galeri) ke
 * penyimpanan internal aplikasi, supaya path-nya stabil dan tidak bergantung
 * pada izin akses URI sementara dari picker - pola yang sama dengan
 * [LogoStorageHelper], tapi nama file dibuat unik (UUID) karena bisa ada
 * banyak produk sekaligus (bukan cuma satu logo toko).
 *
 * Foto juga bisa diambil langsung dari kamera: [siapkanFotoKamera] menyiapkan file sementara
 * untuk aplikasi kamera, lalu [simpanFotoDariKamera] memperkecil dan menyimpannya ke folder
 * yang sama dengan foto dari galeri.
 */
object ProductPhotoStorageHelper {
    /** Sisi terpanjang (piksel) foto kamera setelah diperkecil. */
    private const val SISI_MAKS_FOTO_KAMERA = 1280
    private const val KUALITAS_JPEG = 85
    private const val FOLDER_FOTO = "produk_foto"
    private const val FOLDER_SEMENTARA = "foto_kamera"
    private const val UMUR_MAKS_SEMENTARA_MS = 24L * 60 * 60 * 1000

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

    /**
     * Menyiapkan file sementara (di cache aplikasi) beserta URI FileProvider-nya untuk diberikan ke
     * aplikasi kamera. File belum ada isinya sampai kamera menulisnya. Mengembalikan null bila gagal.
     * Sisa file sementara yang lebih tua dari 24 jam (mis. aplikasi ditutup sistem saat kamera terbuka)
     * dibersihkan di sini.
     */
    fun siapkanFotoKamera(context: Context): Pair<File, Uri>? {
        return try {
            val folder = File(context.cacheDir, FOLDER_SEMENTARA).apply { if (!exists()) mkdirs() }
            val batas = System.currentTimeMillis() - UMUR_MAKS_SEMENTARA_MS
            folder.listFiles()?.forEach { if (it.isFile && it.lastModified() < batas) it.delete() }
            val file = File(folder, "kamera_${UUID.randomUUID()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            file to uri
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Mengubah foto mentah dari kamera menjadi foto produk: diputar sesuai EXIF, diperkecil (sisi terpanjang
     * [SISI_MAKS_FOTO_KAMERA] piksel), lalu disimpan sebagai JPEG di folder foto produk. File sementara
     * selalu dihapus. Mengembalikan path file yang tersimpan, atau null bila gagal. Panggil dari thread IO.
     */
    fun simpanFotoDariKamera(context: Context, fileSementara: File): String? {
        var untukDibersihkan: Bitmap? = null // bitmap terakhir yang masih hidup, di-recycle di finally
        try {
            if (!fileSementara.exists() || fileSementara.length() == 0L) return null
            val jalur = fileSementara.absolutePath

            val batas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(jalur, batas)
            if (batas.outWidth <= 0 || batas.outHeight <= 0) return null

            val opsi = BitmapFactory.Options().apply {
                inSampleSize = UkuranFoto.hitungInSampleSize(batas.outWidth, batas.outHeight, SISI_MAKS_FOTO_KAMERA)
            }
            val hasilDecode = BitmapFactory.decodeFile(jalur, opsi) ?: return null
            untukDibersihkan = hasilDecode

            val derajat = runCatching {
                UkuranFoto.derajatPutarDariExif(
                    ExifInterface(jalur).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                )
            }.getOrDefault(0)
            val skala = UkuranFoto.hitungSkala(hasilDecode.width, hasilDecode.height, SISI_MAKS_FOTO_KAMERA)
            val hasilAkhir: Bitmap = if (derajat != 0 || skala < 1f) {
                val matrix = Matrix().apply {
                    if (derajat != 0) postRotate(derajat.toFloat())
                    if (skala < 1f) postScale(skala, skala)
                }
                val diubah = Bitmap.createBitmap(hasilDecode, 0, 0, hasilDecode.width, hasilDecode.height, matrix, true)
                if (diubah !== hasilDecode) hasilDecode.recycle()
                untukDibersihkan = diubah
                diubah
            } else {
                hasilDecode
            }

            val folder = File(context.filesDir, FOLDER_FOTO).apply { if (!exists()) mkdirs() }
            val file = File(folder, "foto_${UUID.randomUUID()}.jpg")
            val tertulis = file.outputStream().use { output ->
                hasilAkhir.compress(Bitmap.CompressFormat.JPEG, KUALITAS_JPEG, output)
            }
            if (!tertulis) {
                file.delete()
                return null
            }
            return file.absolutePath
        } catch (_: Exception) {
            return null
        } catch (_: OutOfMemoryError) {
            return null
        } finally {
            untukDibersihkan?.recycle()
            fileSementara.delete()
        }
    }

    fun hapusFoto(fotoPath: String?) {
        if (!fotoPath.isNullOrBlank()) {
            try {
                val file = File(fotoPath)
                if (file.exists() && file.isFile) {
                    file.delete()
                }
            } catch (_: Exception) {
            }
        }
    }
}
