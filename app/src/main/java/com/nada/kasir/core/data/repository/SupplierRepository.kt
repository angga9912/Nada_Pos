package com.nada.kasir.core.data.repository

import com.nada.kasir.core.data.local.dao.SupplierDao
import com.nada.kasir.core.data.local.entity.SupplierEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupplierRepository @Inject constructor(
    private val supplierDao: SupplierDao
) {
    fun observeAll(): Flow<List<SupplierEntity>> = supplierDao.observeAll()

    fun observeActive(): Flow<List<SupplierEntity>> = supplierDao.observeActive()

    suspend fun save(entity: SupplierEntity): Long = when {
        entity.id == 0L -> supplierDao.insert(entity)
        else -> {
            supplierDao.update(entity)
            entity.id
        }
    }

    suspend fun setAktif(id: Long, aktif: Boolean) =
        supplierDao.setAktif(id, aktif, System.currentTimeMillis())

    suspend fun findById(id: Long): SupplierEntity? = supplierDao.findById(id)
}
