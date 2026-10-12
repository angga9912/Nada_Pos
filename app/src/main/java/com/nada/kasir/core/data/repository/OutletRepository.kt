package com.nada.kasir.core.data.repository

import com.nada.kasir.core.data.local.dao.OutletDao
import com.nada.kasir.core.data.local.dao.SettingDao
import com.nada.kasir.core.data.local.entity.OutletEntity
import com.nada.kasir.core.data.local.entity.SettingEntity
import com.nada.kasir.core.domain.logic.OutletAktifLogic
import com.nada.kasir.core.paket.FiturPaket
import com.nada.kasir.core.paket.PaketRepository
import com.nada.kasir.core.paket.mengizinkan
import com.nada.kasir.core.util.AppError
import com.nada.kasir.core.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OutletRepository @Inject constructor(
    private val outletDao: OutletDao,
    private val settingDao: SettingDao,
    private val paketRepository: PaketRepository
) {
    fun observeAll(): Flow<List<OutletEntity>> = outletDao.observeAll()
    fun observeActive(): Flow<List<OutletEntity>> = outletDao.observeActive()

    /** Simpan outlet baru/perubahan. Mengembalikan error bila nama/kode kosong atau bentrok. */
    suspend fun simpan(entity: OutletEntity): Result<Long> {
        val semua = outletDao.observeAll().first()
        OutletAktifLogic.validasiForm(entity.nama, entity.kode, semua, entity.id)?.let {
            return Result.Failure(AppError.Lainnya(it))
        }
        val bersih = entity.copy(nama = entity.nama.trim(), kode = entity.kode.trim(), updatedAt = System.currentTimeMillis())
        return Result.Success(
            if (bersih.id == 0L) outletDao.insert(bersih) else { outletDao.update(bersih); bersih.id }
        )
    }

    suspend fun save(entity: OutletEntity): Long = when {
        entity.id == 0L -> outletDao.insert(entity)
        else -> {
            outletDao.update(entity)
            entity.id
        }
    }

    suspend fun setAktif(id: Long, aktif: Boolean) =
        outletDao.setAktif(id, aktif, System.currentTimeMillis())

    suspend fun findById(id: Long): OutletEntity? = outletDao.findById(id)

    // ---- Outlet aktif (pelabelan cabang) ----

    /** Id pilihan yang tersimpan (belum tentu dipakai: lihat [observeOutletAktifEfektif]). */
    fun observeIdTersimpan(): Flow<Long?> = settingDao.observeAll().map { list ->
        OutletAktifLogic.parseId(list.firstOrNull { it.key == OutletAktifLogic.KEY_OUTLET_AKTIF }?.value)
    }

    /** null = tanpa cabang (semua transaksi baru tidak diberi label). */
    suspend fun setIdAktif(id: Long?) {
        settingDao.upsert(SettingEntity(key = OutletAktifLogic.KEY_OUTLET_AKTIF, value = id?.toString() ?: ""))
    }

    /** Outlet yang benar-benar dipakai memberi label saat ini; null bila tidak ada/tidak diizinkan paket. */
    fun observeOutletAktifEfektif(): Flow<OutletEntity?> = combine(
        observeIdTersimpan(), outletDao.observeActive(), paketRepository.observePaketAktif()
    ) { id, aktif, paket ->
        OutletAktifLogic.tentukan(id, aktif, paket.mengizinkan(FiturPaket.OUTLET))
    }

    /** Id outlet untuk label transaksi/pembelian/mutasi yang sedang dibuat. */
    suspend fun idAktifEfektif(): Long? = observeOutletAktifEfektif().first()?.id
}
