package com.nada.kasir.core.domain.model

/** Representasi item di keranjang kasir (state UI, belum tersimpan ke DB). */
data class KeranjangItem(
    val productId: Long,
    val nama: String,
    val harga: Double,
    val qty: Int,
    val stokTersedia: Int,
    val diskonItem: Double = 0.0
) {
    val subtotal: Double get() = (harga * qty) - diskonItem
}
