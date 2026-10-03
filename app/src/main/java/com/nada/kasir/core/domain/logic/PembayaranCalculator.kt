package com.nada.kasir.core.domain.logic

/**
 * Logika perhitungan pembayaran tunai (poin 11), dipisah dari Repository/ViewModel
 * agar bisa diuji sebagai unit test murni (tanpa Android/Room).
 */
object PembayaranCalculator {
    fun hitungKembalian(uangDiterima: Double, total: Double): Double = uangDiterima - total

    fun cukup(uangDiterima: Double, total: Double): Boolean = uangDiterima >= total

    /**
     * Diskon transaksi valid jika angka nyata, tidak negatif, dan tidak melebihi subtotal
     * (supaya total tidak pernah negatif dan kembalian tidak pernah melebihi uang yang diterima).
     */
    fun diskonValid(diskon: Double, subtotal: Double): Boolean =
        !diskon.isNaN() && !diskon.isInfinite() && diskon >= 0.0 && diskon <= maxOf(subtotal, 0.0)

    /** Menyesuaikan diskon ke rentang 0..subtotal (dipakai saat isi keranjang berubah). */
    fun batasiDiskon(diskon: Double, subtotal: Double): Double =
        if (diskon.isNaN()) 0.0 else diskon.coerceIn(0.0, maxOf(subtotal, 0.0))
}
