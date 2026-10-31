package com.nada.kasir.core.pantau

import com.nada.kasir.core.data.local.dao.SettingDao
import com.nada.kasir.core.data.local.entity.SettingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val KEY_PANTAU_AKTIF = "pantau_aktif"
private const val KEY_PANTAU_TOKO_ID = "pantau_toko_id"
private const val KEY_PANTAU_TERAKHIR_KIRIM = "pantau_terakhir_kirim"
private const val KEY_PANTAU_STATUS = "pantau_status_terakhir"

/** Setelan modul Pantau Cabang per HP (disimpan di tabel settings, seperti fitur lain). */
@Singleton
class PantauCabangPengaturan @Inject constructor(
    private val settingDao: SettingDao
) {
    data class Konfigurasi(
        val aktif: Boolean = false,
        val tokoId: String = "",
        /** Waktu (epoch millis) terakhir rekap berhasil terkirim; null = belum pernah. */
        val terakhirKirim: Long? = null,
        /** Pesan kegagalan pengiriman terakhir; null bila pengiriman terakhir berhasil. */
        val pesanTerakhir: String? = null
    ) {
        /** Siap dipakai mengirim: dinyalakan dan kode toko valid. */
        val siapKirim: Boolean get() = aktif && RekapHarianLogic.validasiTokoId(tokoId) == null
    }

    fun observe(): Flow<Konfigurasi> = settingDao.observeAll().map { list ->
        fun nilai(k: String) = list.firstOrNull { it.key == k }?.value
        Konfigurasi(
            aktif = nilai(KEY_PANTAU_AKTIF) == "true",
            tokoId = nilai(KEY_PANTAU_TOKO_ID).orEmpty(),
            terakhirKirim = nilai(KEY_PANTAU_TERAKHIR_KIRIM)?.toLongOrNull(),
            pesanTerakhir = nilai(KEY_PANTAU_STATUS)?.takeIf { it.isNotBlank() }
        )
    }

    suspend fun baca(): Konfigurasi = Konfigurasi(
        aktif = settingDao.get(KEY_PANTAU_AKTIF) == "true",
        tokoId = settingDao.get(KEY_PANTAU_TOKO_ID).orEmpty(),
        terakhirKirim = settingDao.get(KEY_PANTAU_TERAKHIR_KIRIM)?.toLongOrNull(),
        pesanTerakhir = settingDao.get(KEY_PANTAU_STATUS)?.takeIf { it.isNotBlank() }
    )

    suspend fun simpan(aktif: Boolean, tokoId: String) {
        settingDao.upsert(SettingEntity(key = KEY_PANTAU_AKTIF, value = aktif.toString()))
        settingDao.upsert(SettingEntity(key = KEY_PANTAU_TOKO_ID, value = tokoId.trim()))
    }

    suspend fun catatKirim(waktu: Long) {
        settingDao.upsert(SettingEntity(key = KEY_PANTAU_TERAKHIR_KIRIM, value = waktu.toString()))
    }

    /** Simpan pesan kegagalan terakhir; null menghapusnya (pengiriman terakhir sukses). */
    suspend fun catatStatus(pesan: String?) {
        settingDao.upsert(SettingEntity(key = KEY_PANTAU_STATUS, value = pesan.orEmpty()))
    }
}
