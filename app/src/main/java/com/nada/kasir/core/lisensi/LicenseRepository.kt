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

private const val KEY_PAKET_AKTIF = KunciPengaturanLisensi.PAKET_AKTIF
private const val KEY_LISENSI_KADALUARSA = KunciPengaturanLisensi.KADALUARSA // "-1" berarti LIFETIME (tidak pernah expired)
private const val KEY_LISENSI_KODE = KunciPengaturanLisensi.KODE
private const val KEY_LISENSI_WAKTU_TERAKHIR = KunciPengaturanLisensi.WAKTU_TERAKHIR

data class StatusLisensi(
    val paket: PaketAplikasi,
    val kadaluarsaMillis: Long?, // null jika Basic/tidak ada expiry
    val isLifetime: Boolean
)

@Singleton
class LicenseRepository @Inject constructor(
    private val settingDao: SettingDao,
    private val paketRepository: PaketRepository,
    private val deviceIdProvider: DeviceIdProvider
) {
    /** ID Perangkat untuk ditampilkan ke pengguna (dikirim ke penjual saat meminta kode). */
    fun idPerangkatTampil(): String = deviceIdProvider.idTampil

    /**
     * Waktu yang dipakai untuk menilai kadaluarsa: jam HP, tapi tidak pernah lebih mundur dari
     * waktu terakhir yang tercatat (lihat [WaktuLisensi]). Sekaligus memperbarui catatan itu.
     */
    private suspend fun waktuEfektifDanCatat(): Long {
        val sekarang = System.currentTimeMillis()
        val terakhir = settingDao.get(KEY_LISENSI_WAKTU_TERAKHIR)?.toLongOrNull() ?: 0L
        val berikutnya = WaktuLisensi.terakhirDilihatBerikutnya(sekarang, terakhir)
        if (berikutnya != terakhir) {
            settingDao.upsert(SettingEntity(KEY_LISENSI_WAKTU_TERAKHIR, berikutnya.toString()))
        }
        return WaktuLisensi.efektif(sekarang, terakhir)
    }

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
        val hasil = LicenseKeyValidator.validasiUntukPerangkat(kode, deviceIdProvider.id)
            ?: return Result.Failure(
                AppError.Lainnya(
                    "Kode aktivasi tidak valid atau bukan untuk perangkat ini. Periksa penulisannya, " +
                        "dan pastikan kode dibuat untuk ID Perangkat ${deviceIdProvider.idTampil}."
                )
            )

        // Kode dengan expiry di masa lalu (misal salah kirim kode lama) langsung ditolak
        val sekarangEfektif = waktuEfektifDanCatat()
        if (hasil.kadaluarsaMillis != null && hasil.kadaluarsaMillis < sekarangEfektif) {
            return Result.Failure(AppError.Lainnya("Kode ini sudah kadaluarsa."))
        }

        paketRepository.setPaketAktif(hasil.tier)
        settingDao.upsert(SettingEntity(KEY_LISENSI_KODE, kode.trim().uppercase()))
        settingDao.upsert(SettingEntity(KEY_LISENSI_KADALUARSA, (hasil.kadaluarsaMillis ?: -1L).toString()))
        return Result.Success(hasil.tier)
    }

    /**
     * Dipanggil setiap aplikasi dibuka:
     * 1. Re-verifikasi tanda tangan kriptografi kode lisensi aktif TERHADAP ID PERANGKAT INI.
     *    Jika tier bukan BASIC namun tanda tangan tidak valid, kode di DB dimanipulasi, atau
     *    kode ternyata dibuat untuk HP lain, turunkan paksa ke BASIC.
     * 2. Pengecekan kadaluarsa langganan bulanan.
     */
    suspend fun cekDanTurunkanJikaKadaluarsa() {
        val sekarangEfektif = waktuEfektifDanCatat()
        val paketAktif = settingDao.get(KEY_PAKET_AKTIF)
        if (paketAktif != null && paketAktif != PaketAplikasi.BASIC.name) {
            val kode = settingDao.get(KEY_LISENSI_KODE)
            val hasilValidasi = if (!kode.isNullOrBlank()) LicenseKeyValidator.validasiUntukPerangkat(kode, deviceIdProvider.id) else null
            if (hasilValidasi == null || hasilValidasi.tier.name != paketAktif) {
                // Lisensi tidak valid atau diutak-atik langsung di database -> turunkan ke Basic
                paketRepository.setPaketAktif(PaketAplikasi.BASIC)
                settingDao.upsert(SettingEntity(KEY_LISENSI_KADALUARSA, "0"))
                return
            }
        }

        val kadaluarsaStr = settingDao.get(KEY_LISENSI_KADALUARSA) ?: return
        val kadaluarsa = kadaluarsaStr.toLongOrNull() ?: return
        if (kadaluarsa == -1L) return // lifetime, aman
        if (kadaluarsa < sekarangEfektif) {
            paketRepository.setPaketAktif(PaketAplikasi.BASIC)
            settingDao.upsert(SettingEntity(KEY_LISENSI_KADALUARSA, "0"))
        }
    }

    suspend fun getKadaluarsaMillis(): Long? = settingDao.get(KEY_LISENSI_KADALUARSA)?.toLongOrNull()?.takeIf { it > 0 }
    suspend fun isLifetime(): Boolean = settingDao.get(KEY_LISENSI_KADALUARSA)?.toLongOrNull() == -1L
}
