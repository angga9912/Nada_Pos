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
enum class PaketAplikasi(
    val label: String,
    val deskripsi: String,
    /** Harga langganan per bulan dalam Rupiah. 0 = gratis. */
    val hargaPerBulan: Int,
    /** true jika harga di atas hanya titik awal ("mulai dari"), bukan harga tetap. */
    val hargaMulaiDari: Boolean = false
) {
    BASIC("Basic", "Kasir, Produk, Stok, dan Cetak Struk - kebutuhan inti toko.", hargaPerBulan = 0),
    CUSTOM(
        "Custom", "Semua fitur Basic + Custom Branding, Excel, Laporan, dan Backup/Restore.",
        hargaPerBulan = 25_000, hargaMulaiDari = true
    ),
    PRO("Pro", "Semua fitur Custom + Multi User (Admin & Kasir) dengan hak akses terpisah.", hargaPerBulan = 50_000);

    fun mencakup(fitur: PaketAplikasi): Boolean = this.ordinal >= fitur.ordinal

    /** Harga lengkap untuk tombol & pesan: "Gratis", "Mulai Rp25.000/bulan", "Rp50.000/bulan". */
    val hargaLabel: String
        get() = if (hargaPerBulan == 0) "Gratis"
        else (if (hargaMulaiDari) "Mulai " else "") + "Rp${titik(hargaPerBulan)}/bulan"

    /** Versi ringkas untuk kolom tabel yang sempit: "Gratis", "Mulai Rp25rb/bln", "Rp50rb/bln". */
    val hargaSingkat: String
        get() = if (hargaPerBulan == 0) "Gratis"
        else (if (hargaMulaiDari) "Mulai " else "") + "Rp${hargaPerBulan / 1000}rb/bln"

    private fun titik(n: Int): String = n.toString().reversed().chunked(3).joinToString(".").reversed()
}
