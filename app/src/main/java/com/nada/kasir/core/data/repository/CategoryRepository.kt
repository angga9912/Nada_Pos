package com.nada.kasir.core.data.repository

import com.nada.kasir.core.data.local.dao.CategoryDao
import com.nada.kasir.core.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao
) {
    fun observeAll(): Flow<List<CategoryEntity>> = categoryDao.observeAll()
}
