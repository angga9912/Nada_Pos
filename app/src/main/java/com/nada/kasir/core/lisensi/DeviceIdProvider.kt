package com.nada.kasir.core.lisensi

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Menyediakan ID Perangkat (lihat [PerangkatId]) untuk pengikatan lisensi.
 *
 * Sumber utama: ANDROID_ID. Nilainya bertahan selama aplikasi diinstal ulang dengan kunci
 * tanda tangan yang sama, dan hanya berubah saat reset pabrik. Ini yang membuat kode lisensi
 * tetap berlaku setelah update/instal ulang APK di HP yang sama, tapi tidak di HP lain.
 *
 * Cadangan (sangat jarang terpakai): kalau ANDROID_ID kosong atau bernilai bug emulator lama,
 * dibuat UUID acak yang disimpan di SharedPreferences (bukan di database Room, jadi tidak ikut
 * tertimpa saat restore backup).
 */
@Singleton
class DeviceIdProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** ID kanonik (12 hex) - dipakai untuk verifikasi tanda tangan. */
    val id: String by lazy { hitung() }

    /** ID untuk ditampilkan/disalin pengguna, mis. "A1B2-C3D4-E5F6". */
    val idTampil: String get() = PerangkatId.tampil(id)

    private fun hitung(): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()
        val sumber = androidId?.takeIf { it.isNotBlank() && it != ANDROID_ID_EMULATOR_RUSAK } ?: idCadangan()
        return PerangkatId.turunkan(sumber)
    }

    private fun idCadangan(): String {
        val prefs = context.getSharedPreferences(NAMA_PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KUNCI_ID_CADANGAN, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KUNCI_ID_CADANGAN, it).apply()
        }
    }

    private companion object {
        const val NAMA_PREFS = "nada_perangkat"
        const val KUNCI_ID_CADANGAN = "id_cadangan"
        // Nilai ANDROID_ID yang sama di banyak perangkat pada Android lama (bug yang terkenal).
        const val ANDROID_ID_EMULATOR_RUSAK = "9774d56d682e549c"
    }
}
