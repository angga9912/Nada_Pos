package com.nada.kasir.core.data.repository

import com.nada.kasir.core.data.local.dao.PrinterDao
import com.nada.kasir.core.data.local.entity.PrinterEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrinterRepository @Inject constructor(
    private val printerDao: PrinterDao
) {
    fun observeAll(): Flow<List<PrinterEntity>> = printerDao.observeAll()

    suspend fun getDefault(): PrinterEntity? = printerDao.getDefault()

    /** Simpan/pilih printer default (poin 8: "Simpan printer default"). */
    suspend fun setSebagaiDefault(printer: PrinterEntity) {
        printerDao.clearDefault()
        val id = if (printer.id == 0L) printerDao.insert(printer) else printer.id
        printerDao.setDefault(id)
    }
}
