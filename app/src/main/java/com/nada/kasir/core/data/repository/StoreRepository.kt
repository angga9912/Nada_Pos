package com.nada.kasir.core.data.repository

import com.nada.kasir.core.data.local.dao.StoreDao
import com.nada.kasir.core.data.local.entity.StoreEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoreRepository @Inject constructor(
    private val storeDao: StoreDao
) {
    fun observeStore(): Flow<StoreEntity?> = storeDao.observeStore()

    suspend fun getOrCreateDefault(): StoreEntity {
        return storeDao.getStoreOnce() ?: run {
            val default = StoreEntity()
            val id = storeDao.insert(default)
            default.copy(id = id)
        }
    }

    suspend fun simpan(store: StoreEntity) {
        if (store.id == 0L) storeDao.insert(store) else storeDao.update(store)
    }
}
