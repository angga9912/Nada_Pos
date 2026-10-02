package com.nada.kasir.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS outlets (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                nama TEXT NOT NULL,
                kode TEXT NOT NULL,
                alamat TEXT NOT NULL DEFAULT '',
                telepon TEXT NOT NULL DEFAULT '',
                aktif INTEGER NOT NULL DEFAULT 1,
                createdAt INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS suppliers (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                nama TEXT NOT NULL,
                kontak TEXT NOT NULL DEFAULT '',
                telepon TEXT NOT NULL DEFAULT '',
                alamat TEXT NOT NULL DEFAULT '',
                npwp TEXT NOT NULL DEFAULT '',
                catatan TEXT NOT NULL DEFAULT '',
                aktif INTEGER NOT NULL DEFAULT 1,
                createdAt INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS hutang_piutang (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                jenis TEXT NOT NULL,
                pelaku TEXT NOT NULL,
                nomorReferensi TEXT,
                jumlah REAL NOT NULL DEFAULT 0,
                sisa REAL NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'AKTIF',
                tanggalTransaksi INTEGER NOT NULL DEFAULT 0,
                tanggalJatuhTempo INTEGER,
                keterangan TEXT,
                createdAt INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }
}
