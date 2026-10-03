package com.nada.kasir.core.domain.logic

import com.nada.kasir.core.data.local.entity.TransactionItemEntity

/**
 * Hitung estimasi keuntungan (poin 3 & 14), dipisah sebagai fungsi murni agar
 * bisa diuji tanpa Room/Android. "Estimasi" karena memakai harga beli produk
 * SAAT INI, bukan harga beli historis di waktu transaksi terjadi.
 */
object KeuntunganCalculator {
    fun hitungTotalKeuntungan(
        items: List<TransactionItemEntity>,
        hargaBeliPerProduk: Map<Long, Double>,
        diskonTransaksiTambahan: Double = 0.0
    ): Double {
        val keuntunganItem = items.sumOf { item ->
            val hargaBeli = hargaBeliPerProduk[item.productId] ?: 0.0
            val keuntunganPerUnit = item.harga - hargaBeli
            (keuntunganPerUnit * item.qty) - item.diskon
        }
        return maxOf(0.0, keuntunganItem - diskonTransaksiTambahan)
    }
}
