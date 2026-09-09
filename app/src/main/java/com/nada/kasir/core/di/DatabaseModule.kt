package com.nada.kasir.core.di

import android.content.Context
import androidx.room.Room
import com.nada.kasir.core.data.local.AppDatabase
import com.nada.kasir.core.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            // fallbackToDestructiveMigration hanya untuk tahap development awal.
            // Ganti dengan Migration eksplisit sebelum rilis ke pelanggan.
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun provideStoreDao(db: AppDatabase): StoreDao = db.storeDao()
    @Provides fun provideUserDao(db: AppDatabase): UserDao = db.userDao()
    @Provides fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideProductDao(db: AppDatabase): ProductDao = db.productDao()
    @Provides fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideStockMovementDao(db: AppDatabase): StockMovementDao = db.stockMovementDao()
    @Provides fun provideSettingDao(db: AppDatabase): SettingDao = db.settingDao()
    @Provides fun providePrinterDao(db: AppDatabase): PrinterDao = db.printerDao()
}
