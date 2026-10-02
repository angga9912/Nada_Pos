package com.nada.kasir.core.data.repository

import com.nada.kasir.core.data.local.dao.OutletDao
import com.nada.kasir.core.data.local.entity.OutletEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OutletRepository @Inject constructor(
    private val outletDao: OutletDao
) {
    fun observeAll(): Flow<List<OutletEntity>> = outletDao.observeAll()

    fun observeActive(): Flow<List<OutletEntity>> = outletDao.observeActive()

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
}
