package com.nada.kasir.core.util

/** Label metode pembayaran untuk ditampilkan ke pengguna (nama enum disimpan apa adanya di database). */
fun labelMetodePembayaran(metode: String): String = when (metode) {
    "HUTANG" -> "Piutang (belum dibayar)"
    "LAINNYA" -> "Lainnya"
    else -> metode
}
