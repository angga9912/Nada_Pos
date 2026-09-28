package com.nada.kasir.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v1 -> v2: tambah kolom nomorAntrian dan namaPembeli pada transaksi
        db.execSQL("ALTER TABLE transactions ADD COLUMN nomorAntrian INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE transactions ADD COLUMN namaPembeli TEXT DEFAULT NULL")
    }
}

/**
 * v2 -> v3: tambah kolom [PaymentEntity.catatanMetode] (nullable) untuk menyimpan
 * nama metode manual saat kasir memilih "Lainnya" (mis. "Transfer BCA").
 *
 * WAJIB didaftarkan di DatabaseModule via .addMigrations(MIGRATION_2_3) - tanpa ini,
 * fallbackToDestructiveMigration() akan MENGHAPUS SELURUH data lokal (produk,
 * transaksi, stok) saat pengguna update ke versi dengan skema baru ini.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE payments ADD COLUMN catatanMetode TEXT DEFAULT NULL")
    }
}
