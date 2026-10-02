package com.nada.kasir.core.data.repository

import com.nada.kasir.core.data.local.dao.HutangPiutangDao
import com.nada.kasir.core.data.local.entity.HutangPiutangEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HutangPiutangRepository @Inject constructor(
    private val dao: HutangPiutangDao
) {
    fun observeAll(): Flow<List<HutangPiutangEntity>> = dao.observeAll()

    fun observeByJenis(jenis: String): Flow<List<HutangPiutangEntity>> = dao.observeByJenis(jenis)

    suspend fun save(entity: HutangPiutangEntity): Long = when {
        entity.id == 0L -> dao.insert(entity)
        else -> {
            dao.update(entity)
            entity.id
        }
    }

    suspend fun updatePelunasan(id: Long, sisa: Double, status: String) =
        dao.updateStatusAndSisa(id, sisa, status, System.currentTimeMillis())

    suspend fun findById(id: Long): HutangPiutangEntity? = dao.findById(id)
}
