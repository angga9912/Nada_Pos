package com.nada.kasir

import android.app.Application
import com.nada.kasir.core.pantau.PantauCabangPengaturan
import com.nada.kasir.core.pantau.PantauCabangScheduler
import com.nada.kasir.core.util.BeepPlayer
import com.nada.kasir.core.util.CrashReporter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NadaKasirApp : Application() {
    @Inject lateinit var pantauPengaturan: PantauCabangPengaturan

    override fun onCreate() {
        super.onCreate()
        CrashReporter.pasang(this)
        BeepPlayer.init(this)
        // Samakan jadwal Pantau Cabang dengan setelan tersimpan (mis. setelah restore backup).
        PantauCabangScheduler.sinkronkan(this, pantauPengaturan)
    }
}
