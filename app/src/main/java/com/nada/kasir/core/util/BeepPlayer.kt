package com.nada.kasir.core.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlin.math.PI
import kotlin.math.sin

/**
 * Bunyi "bip" pendek sebagai tanda barcode berhasil terbaca (kamera maupun scanner fisik).
 *
 * Nada utama dibuat sendiri (sinus 2,6 kHz, 140 ms) dan diputar lewat AudioTrack pada jalur
 * media; ToneGenerator dipakai sebagai cadangan. Volume mengikuti VOLUME MEDIA HP.
 *
 * MODE DIAGNOSA (TAMPILKAN_DIAGNOSA = true): sementara, untuk mencari kenapa bip tidak terdengar
 * di HP tertentu. Setiap scan memutar 3 bip berurutan lewat 3 jalur berbeda dan menampilkan
 * Toast berisi volume media & perangkat keluaran suara. Setelah masalah selesai, ubah jadi false.
 */
object BeepPlayer {
    private const val TAMPILKAN_DIAGNOSA = false

    private const val SAMPLE_RATE = 44100
    private const val FREKUENSI_HZ = 2600.0
    private const val DURASI_MS = 140

    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var alasanGagal: String = ""

    /** Dipanggil sekali dari NadaKasirApp.onCreate. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val nada: ShortArray by lazy {
        val jumlah = SAMPLE_RATE * DURASI_MS / 1000
        val fade = SAMPLE_RATE * 8 / 1000 // naik/turun halus 8 ms agar tidak berbunyi "klik"
        val puncak = Short.MAX_VALUE.toDouble() * 0.9
        ShortArray(jumlah) { i ->
            val selubung = when {
                i < fade -> i.toDouble() / fade
                i > jumlah - fade -> (jumlah - i).toDouble() / fade
