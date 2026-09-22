package com.nada.kasir.core.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.sin

/**
 * Bunyi "bip" pendek sebagai tanda barcode berhasil terbaca (kamera maupun scanner fisik).
 *
 * Nada dibuat sendiri (gelombang sinus 2,6 kHz, 140 ms) dan diputar lewat AudioTrack pada
 * jalur media, jadi tidak bergantung pada tabel nada bawaan tiap merek HP (ToneGenerator kadang
 * diam di HP tertentu). ToneGenerator hanya dipakai sebagai cadangan kalau AudioTrack gagal.
 * Volume mengikuti VOLUME MEDIA HP - kalau volume media 0, tidak ada suara.
 */
object BeepPlayer {
    private const val SAMPLE_RATE = 44100
    private const val FREKUENSI_HZ = 2600.0
    private const val DURASI_MS = 140

    private val handler = Handler(Looper.getMainLooper())

    private val nada: ShortArray by lazy {
        val jumlah = SAMPLE_RATE * DURASI_MS / 1000
        val fade = SAMPLE_RATE * 8 / 1000 // naik/turun halus 8 ms agar tidak berbunyi "klik"
        val puncak = Short.MAX_VALUE.toDouble() * 0.9
        ShortArray(jumlah) { i ->
            val selubung = when {
                i < fade -> i.toDouble() / fade
                i > jumlah - fade -> (jumlah - i).toDouble() / fade
                else -> 1.0
            }
            (sin(2.0 * PI * FREKUENSI_HZ * i / SAMPLE_RATE) * selubung * puncak).toInt().toShort()
        }
    }

    /**
     * Dipanggil sekali saat aplikasi dibuka (lihat [com.nada.kasir.NadaKasirApp]) untuk
     * "memanaskan" gelombang nada ([nada], properti `by lazy`) di awal - supaya bip pertama
     * saat scan barcode langsung terdengar tanpa jeda hitung gelombang. Context tidak
     * dipakai (AudioTrack di sini tidak butuh Context sama sekali), cuma disediakan supaya
     * pemanggilan di Application.onCreate() konsisten dan jelas maksudnya.
     */
    fun init(context: android.content.Context) {
        nada // akses saja untuk memicu inisialisasi lazy-nya lebih awal
    }

    fun beep() {
        if (!bunyiLewatAudioTrack()) bunyiLewatToneGenerator()
    }

    private fun bunyiLewatAudioTrack(): Boolean {
        try {
            val data = nada
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(data.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            if (track.state != AudioTrack.STATE_INITIALIZED || track.write(data, 0, data.size) <= 0) {
                track.release()
                return false
            }
            track.play()
            handler.postDelayed({ track.release() }, DURASI_MS + 300L)
            return true
        } catch (e: Throwable) {
            return false
        }
    }

    private fun bunyiLewatToneGenerator() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            handler.postDelayed({ tone.release() }, 400)
        } catch (e: Throwable) {
            // Perangkat tidak bisa memutar nada - abaikan, scan tetap berjalan tanpa bunyi
        }
    }
}
