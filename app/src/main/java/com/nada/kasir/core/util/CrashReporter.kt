package com.nada.kasir.core.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.nada.kasir.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pencatat crash sederhana: kalau aplikasi berhenti karena error, penyebabnya (stack trace)
 * disimpan ke file lokal. Saat aplikasi dibuka lagi, MainActivity menampilkan isinya supaya
 * bisa disalin dan dikirim ke pengembang - tanpa perlu kabel/ADB/logcat.
 *
 * Catatan: hanya menangkap error Java/Kotlin. Crash di kode native (mis. library scanner)
 * tidak tertangkap oleh cara ini.
 */
object CrashReporter {
    private const val NAMA_FILE = "crash_terakhir.txt"
    private const val MAKS_KARAKTER = 12000

    fun pasang(context: Context) {
        val appContext = context.applicationContext
        val penanganBawaan = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val waktu = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val laporan = buildString {
                    append("Waktu  : ").append(waktu).append('\n')
                    append("Aplikasi: ").append(BuildConfig.APPLICATION_ID).append(" v")
                        .append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
                    append("Perangkat: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                        .append(", Android ").append(Build.VERSION.RELEASE)
                        .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
                    append("Thread : ").append(thread.name).append("\n\n")
                    append(Log.getStackTraceString(error))
                }
                File(appContext.filesDir, NAMA_FILE).writeText(laporan.take(MAKS_KARAKTER))
            } catch (_: Throwable) {
                // Pencatat tidak boleh menyebabkan crash baru
            }
            penanganBawaan?.uncaughtException(thread, error)
        }
    }

    /** Laporan crash terakhir, atau null kalau tidak ada. */
    fun ambil(context: Context): String? {
        val berkas = File(context.filesDir, NAMA_FILE)
        if (!berkas.exists()) return null
        return runCatching { berkas.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun hapus(context: Context) {
        File(context.filesDir, NAMA_FILE).delete()
    }
}
