package com.nada.kasir

import android.app.Application
import com.nada.kasir.core.util.BeepPlayer
import com.nada.kasir.core.util.CrashReporter
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NadaKasirApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.pasang(this)
        BeepPlayer.init(this)
    }
}
