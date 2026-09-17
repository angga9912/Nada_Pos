package com.nada.kasir.feature.onboarding

import android.content.Context

private const val PREF_NAME = "nada_onboarding_prefs"
private const val KEY_SUDAH_LIHAT = "sudah_lihat_onboarding"

/**
 * Penyimpanan sederhana (SharedPreferences) untuk menandai apakah pengguna di HP ini
 * sudah pernah melihat layar onboarding - supaya cuma tampil SEKALI saat aplikasi
 * pertama kali dibuka, bukan setiap kali buka aplikasi.
 */
object OnboardingPreference {
    fun sudahLihat(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SUDAH_LIHAT, false)

    fun tandaiSudahLihat(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SUDAH_LIHAT, true)
            .apply()
    }
}
