package com.nada.kasir.core.paket

import com.nada.kasir.core.data.local.dao.SettingDao
import com.nada.kasir.core.data.local.entity.SettingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val KEY_PAKET_AKTIF = "paket_aktif"
private const val KEY_SARAN_UPGRADE_DISMISS_TANGGAL = "saran_upgrade_dismiss_tanggal"

@Singleton
class PaketRepository @Inject constructor(
    private val settingDao: SettingDao
) {
    /**
     * Default BASIC (bukan PRO) - karena aplikasi ini sekarang model freemium
     * publik: instalasi baru selalu mulai dari gratis, upgrade lewat aktivasi
     * lisensi (lihat LicenseRepository), bukan dipilih bebas oleh pengguna.
     */
    fun observePaketAktif(): Flow<PaketAplikasi> = settingDao.observeAll().map { list ->
        val value = list.firstOrNull { it.key == KEY_PAKET_AKTIF }?.value
        value?.let { runCatching { PaketAplikasi.valueOf(it) }.getOrNull() } ?: PaketAplikasi.BASIC
    }

    suspend fun setPaketAktif(paket: PaketAplikasi) {
        settingDao.upsert(SettingEntity(key = KEY_PAKET_AKTIF, value = paket.name))
    }

    /**
     * Tanggal (format "yyyy-MM-dd") terakhir kali kasir menutup banner saran upgrade di layar
     * Kasir - null kalau belum pernah ditutup. Dipakai supaya banner tidak muncul lagi di HARI
     * yang sama setelah ditutup, tapi otomatis muncul lagi besok kalau ambang transaksi harian
     * tercapai lagi (bukan ditutup permanen selamanya - upsell-nya tetap relevan tiap hari toko
     * ramai, bukan cuma sekali seumur hidup).
     */
    fun observeSaranUpgradeDismissedTanggal(): Flow<String?> = settingDao.observeAll().map { list ->
        list.firstOrNull { it.key == KEY_SARAN_UPGRADE_DISMISS_TANGGAL }?.value
    }

    suspend fun dismissSaranUpgradeHariIni(tanggalHariIni: String) {
        settingDao.upsert(SettingEntity(key = KEY_SARAN_UPGRADE_DISMISS_TANGGAL, value = tanggalHariIni))
    }
}
