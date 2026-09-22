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
                else -> 1.0
            }
            (sin(2.0 * PI * FREKUENSI_HZ * i / SAMPLE_RATE) * selubung * puncak).toInt().toShort()
        }
    }

    fun beep() {
        if (TAMPILKAN_DIAGNOSA) {
            beepDiagnosa()
            return
        }
        if (!bunyiLewatAudioTrack()) bunyiLewatToneGenerator(AudioManager.STREAM_MUSIC)
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

            val tertulis = track.write(data, 0, data.size)
            if (track.state != AudioTrack.STATE_INITIALIZED || tertulis <= 0) {
                alasanGagal = "state=${track.state} tulis=$tertulis"
                track.release()
                return false
            }
            track.play()
            handler.postDelayed({ track.release() }, DURASI_MS + 300L)
            return true
        } catch (e: Throwable) {
            alasanGagal = "${e.javaClass.simpleName}: ${e.message}"
            return false
        }
    }

    private fun bunyiLewatToneGenerator(stream: Int) {
        try {
            val tone = ToneGenerator(stream, 100)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            handler.postDelayed({ tone.release() }, 400)
        } catch (e: Throwable) {
            // Perangkat tidak bisa memutar nada - abaikan, scan tetap berjalan tanpa bunyi
        }
    }

    // ---------------------------------------------------------------------------------------
    // Mode diagnosa (sementara)
    // ---------------------------------------------------------------------------------------
    private fun beepDiagnosa() {
        val hasil = if (bunyiLewatAudioTrack()) "1=AudioTrack OK" else "1=AudioTrack GAGAL ($alasanGagal)"
        handler.postDelayed({ bunyiLewatToneGenerator(AudioManager.STREAM_MUSIC) }, 500)
        handler.postDelayed({ bunyiLewatToneGenerator(AudioManager.STREAM_ALARM) }, 1000)
        tampilkanDiagnosa(hasil)
    }

    private fun tampilkanDiagnosa(hasil: String) {
        val ctx = appContext ?: return
        try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val volume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maks = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val mute = am.isStreamMute(AudioManager.STREAM_MUSIC)
            val keluaran = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).joinToString(",") { it.type.toString() }
            Toast.makeText(
                ctx,
                "Diagnosa bip (3 bunyi berurutan: 1=media, 2=tone media, 3=tone alarm)\n" +
                    "$hasil | volume media $volume/$maks, mute=$mute | keluaran=[$keluaran]",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Throwable) {
            // Diagnosa tidak boleh mengganggu scan
        }
    }
}
