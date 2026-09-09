package com.nada.kasir.core.lisensi

import com.nada.kasir.core.data.local.dao.SettingDao
import com.nada.kasir.core.data.local.entity.SettingEntity
import com.nada.kasir.core.paket.PaketAplikasi
import com.nada.kasir.core.paket.PaketRepository
import com.nada.kasir.core.util.AppError
import com.nada.kasir.core.util.Result
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val KEY_LISENSI_KADALUARSA = "lisensi_kadaluarsa_millis" // "-1" berarti LIFETIME (tidak pernah expired)
private const val KEY_LISENSI_KODE = "lisensi_kode_aktif"

data class StatusLisensi(
    val paket: PaketAplikasi,
    val kadaluarsaMillis: Long?, // null jika Basic/tidak ada expiry
    val isLifetime: Boolean
)

@Singleton
class LicenseRepository @Inject constructor(
    private val settingDao: SettingDao,
    private val paketRepository: PaketRepository
) {
    /** Gabungan status paket aktif + info kadaluarsa, untuk ditampilkan di UI. */
    fun observeStatus(): Flow<StatusLisensi> = kotlinx.coroutines.flow.combine(
        paketRepository.observePaketAktif(),
        settingDao.observeAll()
    ) { paket, settings ->
        val kadaluarsaStr = settings.firstOrNull { it.key == KEY_LISENSI_KADALUARSA }?.value
        val kadaluarsa = kadaluarsaStr?.toLongOrNull()
        StatusLisensi(
            paket = paket,
            kadaluarsaMillis = kadaluarsa?.takeIf { it > 0 },
            isLifetime = kadaluarsa == -1L
        )
    }

    /** Aktivasi kode lisensi (poin monetisasi baru: freemium Basic gratis, Custom/Pro berbayar). */
    suspend fun aktivasi(kode: String): Result<PaketAplikasi> {
        val hasil = LicenseKeyValidator.validasi(kode)
            ?: return Result.Failure(AppError.Lainnya("Kode aktivasi tidak valid. Periksa kembali penulisannya."))

        // Kode dengan expiry di masa lalu (misal salah kirim kode lama) langsung ditolak
        if (hasil.kadaluarsaMillis != null && hasil.kadaluarsaMillis < System.currentTimeMillis()) {
            return Result.Failure(AppError.Lainnya("Kode ini sudah kadaluarsa."))
        }

        paketRepository.setPaketAktif(hasil.tier)
        settingDao.upsert(SettingEntity(KEY_LISENSI_KODE, kode.trim().uppercase()))
        settingDao.upsert(SettingEntity(KEY_LISENSI_KADALUARSA, (hasil.kadaluarsaMillis ?: -1L).toString()))
        return Result.Success(hasil.tier)
    }

    /**
     * Dipanggil setiap aplikasi dibuka (poin langganan bulanan): kalau lisensi
     * berbasis expiry sudah lewat, otomatis turunkan ke Basic. Lisensi LIFETIME
     * (kadaluarsaMillis == -1) tidak pernah dicek/diturunkan.
     */
    suspend fun cekDanTurunkanJikaKadaluarsa() {
        val kadaluarsaStr = settingDao.get(KEY_LISENSI_KADALUARSA) ?: return
        val kadaluarsa = kadaluarsaStr.toLongOrNull() ?: return
        if (kadaluarsa == -1L) return // lifetime, aman
        if (kadaluarsa < System.currentTimeMillis()) {
            paketRepository.setPaketAktif(PaketAplikasi.BASIC)
            settingDao.upsert(SettingEntity(KEY_LISENSI_KADALUARSA, "0"))
        }
    }

    suspend fun getKadaluarsaMillis(): Long? = settingDao.get(KEY_LISENSI_KADALUARSA)?.toLongOrNull()?.takeIf { it > 0 }
    suspend fun isLifetime(): Boolean = settingDao.get(KEY_LISENSI_KADALUARSA)?.toLongOrNull() == -1L
}
