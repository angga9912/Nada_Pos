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

/**
 * v3 -> v4: Phase 5 - tambah tabel supplier, outlet, hutang_piutang
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Tabel outlet
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

        // Tabel supplier
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

        // Tabel hutang_piutang
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

/**
 * v4 -> v5: Phase 5 - tambah kolom outlet/supplier ke transaksi, produk, stok_movements
 * dan tambah tabel purchases/purchase_items untuk pembelian supplier
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Tambah kolom ke transactions
        db.execSQL("ALTER TABLE transactions ADD COLUMN outletId INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE transactions ADD COLUMN hutangSisa REAL NOT NULL DEFAULT 0")

        // Tambah kolom ke products
        db.execSQL("ALTER TABLE products ADD COLUMN supplierId INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE products ADD COLUMN outletId INTEGER DEFAULT NULL")

        // Tambah kolom ke payments
        db.execSQL("ALTER TABLE payments ADD COLUMN hutangPiutangId INTEGER DEFAULT NULL")

        // Tambah kolom ke stock_movements
        db.execSQL("ALTER TABLE stock_movements ADD COLUMN referensiPembelianId INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE stock_movements ADD COLUMN supplierId INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE stock_movements ADD COLUMN outletId INTEGER DEFAULT NULL")

        // Buat tabel purchases
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS purchases (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                noPembelian TEXT NOT NULL,
                supplierId INTEGER NOT NULL,
                outletId INTEGER,
                tanggalPembelian INTEGER NOT NULL,
                tanggalTerima INTEGER,
                subtotal REAL NOT NULL,
                diskonSupplier REAL NOT NULL DEFAULT 0,
                totalBayar REAL NOT NULL,
                hutangSisa REAL NOT NULL,
                status TEXT NOT NULL DEFAULT 'PENDING',
                keterangan TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // Buat tabel purchase_items
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS purchase_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                purchaseId INTEGER NOT NULL,
                productId INTEGER NOT NULL,
                namaProdukSnapshot TEXT NOT NULL,
                qty INTEGER NOT NULL,
                hargaBeli REAL NOT NULL,
                diskon REAL NOT NULL DEFAULT 0,
                subtotal REAL NOT NULL
            )
            """.trimIndent()
        )
    }
}

/**
 * v5 -> v6: Phase 5 tahap 1 - tabel riwayat pembayaran/cicilan hutang & piutang.
 * Nama indeks WAJIB "index_<tabel>_<kolom>" supaya cocok dengan yang dibuat Room dari entity.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS hutang_piutang_pembayaran (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                hutangPiutangId INTEGER NOT NULL,
                jumlah REAL NOT NULL,
                tanggal INTEGER NOT NULL,
                catatan TEXT,
                userId INTEGER,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_hutang_piutang_pembayaran_hutangPiutangId " +
                "ON hutang_piutang_pembayaran (hutangPiutangId)"
        )
    }
}


/**
 * Migrasi 6 -> 7 (F-04): indeks untuk kolom yang sering dicari/digabung, dan nomor transaksi unik.
 *
 * Nama indeks WAJIB "index_<tabel>_<kolom>" dan SQL-nya HARUS sama dengan yang dibuat Room dari anotasi
 * `@Index` di entity; Room menolak membuka database bila indeks hasil migrasi berbeda. Karena itu daftar
 * pernyataan dipisah sebagai konstanta: `MigrasiIndeksTest` membandingkannya dengan skema yang diekspor Room
 * (app/schemas/.../7.json), jadi salah ketik ketahuan di CI, bukan di HP pelanggan.
 *
 * Indeks UNIK hanya untuk `transactions.noTransaksi`. Sebelum dibuat, nomor ganda (kalau ada) diberi akhiran
 * "-<id>" (dan lapis kedua "-DUP-<id>" bila akhiran itu bertabrakan) supaya migrasi tidak gagal di data lama. Barcode dan kode produk sengaja TIDAK dibuat unik di
 * database: produk yang sudah dihapus (soft delete) boleh dipakai ulang kodenya, dan itu dijaga di
 * `ProductRepository`.
 */
internal val SQL_MIGRASI_6_7: List<String> = listOf(
    "UPDATE `transactions` SET `noTransaksi` = `noTransaksi` || '-' || `id` " +
        "WHERE `id` NOT IN (SELECT MIN(`id`) FROM `transactions` GROUP BY `noTransaksi`)",
    // Lapis kedua: hanya berpengaruh bila akhiran "-<id>" di atas kebetulan bertabrakan dengan nomor yang sudah ada
    // (nyaris mustahil, tetapi tanpa ini CREATE UNIQUE INDEX akan gagal dan aplikasi tidak bisa dibuka setelah update).
    "UPDATE `transactions` SET `noTransaksi` = `noTransaksi` || '-DUP-' || `id` " +
        "WHERE EXISTS (SELECT 1 FROM `transactions` o WHERE o.`noTransaksi` = `transactions`.`noTransaksi` AND o.`id` < `transactions`.`id`)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_noTransaksi` ON `transactions` (`noTransaksi`)",
    "CREATE INDEX IF NOT EXISTS `index_transactions_tanggalWaktu` ON `transactions` (`tanggalWaktu`)",
    "CREATE INDEX IF NOT EXISTS `index_transaction_items_transactionId` ON `transaction_items` (`transactionId`)",
    "CREATE INDEX IF NOT EXISTS `index_transaction_items_productId` ON `transaction_items` (`productId`)",
    "CREATE INDEX IF NOT EXISTS `index_payments_transactionId` ON `payments` (`transactionId`)",
    "CREATE INDEX IF NOT EXISTS `index_stock_movements_productId` ON `stock_movements` (`productId`)",
    "CREATE INDEX IF NOT EXISTS `index_stock_movements_referensiTransaksiId` ON `stock_movements` (`referensiTransaksiId`)",
    "CREATE INDEX IF NOT EXISTS `index_stock_movements_tanggalWaktu` ON `stock_movements` (`tanggalWaktu`)",
    "CREATE INDEX IF NOT EXISTS `index_products_barcode` ON `products` (`barcode`)",
    "CREATE INDEX IF NOT EXISTS `index_products_kodeProduk` ON `products` (`kodeProduk`)",
    "CREATE INDEX IF NOT EXISTS `index_products_categoryId` ON `products` (`categoryId`)",
    "CREATE INDEX IF NOT EXISTS `index_purchases_supplierId` ON `purchases` (`supplierId`)",
    "CREATE INDEX IF NOT EXISTS `index_purchases_noPembelian` ON `purchases` (`noPembelian`)",
    "CREATE INDEX IF NOT EXISTS `index_purchase_items_purchaseId` ON `purchase_items` (`purchaseId`)",
    "CREATE INDEX IF NOT EXISTS `index_hutang_piutang_nomorReferensi` ON `hutang_piutang` (`nomorReferensi`)"
)

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        SQL_MIGRASI_6_7.forEach { db.execSQL(it) }
    }
}
