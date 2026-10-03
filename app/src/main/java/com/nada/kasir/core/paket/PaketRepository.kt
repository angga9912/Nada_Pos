package com.nada.kasir.core.paket

import com.nada.kasir.core.data.local.dao.SettingDao
import com.nada.kasir.core.data.local.entity.SettingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val KEY_PAKET_AKTIF = "paket_aktif"

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
}
