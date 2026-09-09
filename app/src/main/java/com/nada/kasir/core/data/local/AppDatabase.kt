package com.nada.kasir.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.nada.kasir.core.data.local.dao.*
import com.nada.kasir.core.data.local.entity.*

/**
 * Database lokal Android (Room/SQLite). Semua transaksi/produk/stok WAJIB tersimpan
 * di sini, bukan di Excel (Excel hanya untuk import/export/laporan, lihat poin 15/16).
 */
@Database(
    entities = [
        StoreEntity::class,
        UserEntity::class,
        CategoryEntity::class,
        ProductEntity::class,
        TransactionEntity::class,
        TransactionItemEntity::class,
        PaymentEntity::class,
        StockMovementEntity::class,
        PrinterEntity::class,
        SettingEntity::class,
        AuditLogEntity::class
    ],
    version = 2, // v2: tambah nomorAntrian & namaPembeli di transactions
    exportSchema = false // aktifkan + set room.schemaLocation kalau nanti butuh migration history formal
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun storeDao(): StoreDao
    abstract fun userDao(): UserDao
    abstract fun categoryDao(): CategoryDao
    abstract fun productDao(): ProductDao
    abstract fun transactionDao(): TransactionDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun settingDao(): SettingDao
    abstract fun printerDao(): PrinterDao

    companion object {
        const val DB_NAME = "nada_kasir.db"
    }
}
