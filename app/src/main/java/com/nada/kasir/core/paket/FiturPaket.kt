package com.nada.kasir.core.paket

/**
 * Fitur yang dibatasi per paket (F-17). SATU sumber kebenaran: menu di Pengaturan, penjaga rute
 * di NavGraph, dan tabel di Info Paket semuanya merujuk ke sini, supaya menu yang disembunyikan
 * tidak tetap bisa dibuka lewat rute (atau sebaliknya).
 *
 * Fitur dasar (Kasir, Produk, Stok, Cetak Struk) tidak dibatasi sehingga tidak ada di sini.
 */
enum class FiturPaket(val nama: String, val paketMinimum: PaketAplikasi) {
    BACKUP("Backup & Restore Data", PaketAplikasi.CUSTOM),
    PENGGUNA("Manajemen Pengguna", PaketAplikasi.PRO),
    SUPPLIER("Supplier", PaketAplikasi.PRO),
    PEMBELIAN("Pembelian ke Supplier", PaketAplikasi.PRO),
    OUTLET("Outlet / Cabang", PaketAplikasi.PRO),
    HUTANG_PIUTANG("Hutang / Piutang", PaketAplikasi.PRO);

    /** Untuk pesan: "Custom dan Pro" atau "Pro". */
    val labelPaketTersedia: String
        get() = if (paketMinimum == PaketAplikasi.PRO) PaketAplikasi.PRO.label
        else "${paketMinimum.label} dan ${PaketAplikasi.PRO.label}"
}

/** true jika paket ini boleh memakai [fitur]. */
fun PaketAplikasi.mengizinkan(fitur: FiturPaket): Boolean = mencakup(fitur.paketMinimum)
