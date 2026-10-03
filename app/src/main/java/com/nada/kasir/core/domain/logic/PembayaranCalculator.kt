package com.nada.kasir.core.domain.logic

/**
 * Logika perhitungan pembayaran tunai (poin 11), dipisah dari Repository/ViewModel
 * agar bisa diuji sebagai unit test murni (tanpa Android/Room).
 */
object PembayaranCalculator {
    fun hitungKembalian(uangDiterima: Double, total: Double): Double = uangDiterima - total

    fun cukup(uangDiterima: Double, total: Double): Boolean = uangDiterima >= total
}
