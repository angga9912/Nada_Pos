package com.nada.kasir.core.pantau

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Jembatan Hilt untuk pekerja (dibuat WorkManager, bukan Hilt). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PantauCabangTitikMasuk {
    fun pengirim(): PantauCabangPengirim
}

/**
 * Pekerja berkala: mengirim rekap harian ke Firebase. Hanya jalan saat ada internet (batasan WorkManager).
 * Gagal sementara diulang beberapa kali; setelah itu menunggu putaran berikutnya.
 */
class PantauCabangWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pengirim = EntryPointAccessors
            .fromApplication(applicationContext, PantauCabangTitikMasuk::class.java)
            .pengirim()
        val hasil = pengirim.kirimOtomatis()
        return if (hasil.berhasil || runAttemptCount >= MAKS_PERCOBAAN) Result.success() else Result.retry()
    }

    private companion object {
        const val MAKS_PERCOBAAN = 3
    }
}

/** Menyalakan/mematikan pengiriman berkala. */
object PantauCabangScheduler {
    private const val NAMA_KERJA = "pantau_cabang_rekap_berkala"
    private const val INTERVAL_JAM = 3L

    fun jadwalkan(context: Context) {
        val batasan = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val permintaan = PeriodicWorkRequestBuilder<PantauCabangWorker>(INTERVAL_JAM, TimeUnit.HOURS)
            .setConstraints(batasan)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(NAMA_KERJA, ExistingPeriodicWorkPolicy.UPDATE, permintaan)
    }

    fun batalkan(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(NAMA_KERJA)
    }

    /**
     * Dipanggil saat aplikasi dibuka: menyamakan jadwal dengan setelan tersimpan (mis. setelah restore
     * backup ke HP baru). Berjalan di latar; kegagalan membaca setelan diabaikan.
     */
    fun sinkronkan(context: Context, pengaturan: PantauCabangPengaturan) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            runCatching {
                if (pengaturan.baca().siapKirim) jadwalkan(context) else batalkan(context)
            }
        }
    }
}
