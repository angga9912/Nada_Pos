package com.nada.kasir.core.paket

/**
 * Struktur paket komersial (poin 29 brief awal): satu source code, 3 tingkat
 * fitur yang bisa diaktifkan per pelanggan lewat Pengaturan Toko - tanpa perlu
 * membuat versi kode terpisah.
 *
 * BASIC   - Kasir, Produk, Stok, Print Struk (kebutuhan inti toko kecil)
 * CUSTOM  - Semua Basic + Custom Branding, Excel Import/Export, Laporan, Backup/Restore
 * PRO     - Semua Custom + Manajemen Pengguna (multi user Admin/Kasir)
 *           (siap diperluas: Hutang/Piutang, Supplier, Multi Cabang - Phase 5)
 */
enum class PaketAplikasi(val label: String, val deskripsi: String) {
    BASIC("Basic", "Kasir, Produk, Stok, dan Cetak Struk - kebutuhan inti toko."),
    CUSTOM("Custom", "Semua fitur Basic + Custom Branding, Excel, Laporan, dan Backup/Restore."),
    PRO("Pro", "Semua fitur Custom + Multi User (Admin & Kasir) dengan hak akses terpisah.");

    fun mencakup(fitur: PaketAplikasi): Boolean = this.ordinal >= fitur.ordinal
}
