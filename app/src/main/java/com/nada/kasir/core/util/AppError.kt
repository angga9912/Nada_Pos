package com.nada.kasir.core.util

/**
 * Semua error ditampilkan sebagai pesan berbahasa manusia (poin 25).
 * Jangan pernah menampilkan exception teknis (mis. NullPointerException) ke pengguna.
 */
sealed class AppError(val pesan: String) {
    object ProdukTidakDitemukan : AppError("Produk belum ditemukan.")
    object StokTidakCukup : AppError("Stok tidak mencukupi.")
    object PrinterTidakTerhubung : AppError("Printer tidak terhubung. Transaksi tetap tersimpan.")
    object FormatExcelSalah : AppError("File Excel tidak sesuai format.")
    object TransaksiGagalDisimpan : AppError("Transaksi gagal disimpan. Silakan coba lagi.")
    object PembayaranKurang : AppError("Uang pembayaran belum mencukupi.")
    object BarcodeDuplikat : AppError("Barcode sudah terdaftar pada produk lain.")
    data class Lainnya(val detail: String) : AppError(detail)
}

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Failure(val error: AppError) : Result<Nothing>()
}
