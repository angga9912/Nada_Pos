package com.nada.kasir.core.domain.logic

import com.nada.kasir.core.data.local.entity.TransactionItemEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class KeuntunganCalculatorTest {

    @Test
    fun `keuntungan dihitung dari selisih harga jual dan harga beli dikurangi diskon`() {
        val items = listOf(
            TransactionItemEntity(transactionId = 1, productId = 10, namaProdukSnapshot = "Indomie", qty = 2, harga = 3500.0, diskon = 0.0, subtotal = 7000.0),
            TransactionItemEntity(transactionId = 1, productId = 11, namaProdukSnapshot = "Aqua", qty = 2, harga = 3000.0, diskon = 500.0, subtotal = 5500.0)
        )
        val hargaBeli = mapOf(10L to 2500.0, 11L to 2200.0)

        val hasil = KeuntunganCalculator.hitungTotalKeuntungan(items, hargaBeli)

        // Indomie: (3500-2500)*2 = 2000 ; Aqua: (3000-2200)*2 - 500 = 1100 ; total = 3100
        assertEquals(3100.0, hasil, 0.01)
    }

    @Test
    fun `produk tanpa data harga beli dianggap keuntungan penuh dari harga jual`() {
        val items = listOf(
            TransactionItemEntity(transactionId = 1, productId = 99, namaProdukSnapshot = "Produk Baru", qty = 1, harga = 1000.0, diskon = 0.0, subtotal = 1000.0)
        )
        val hasil = KeuntunganCalculator.hitungTotalKeuntungan(items, emptyMap())
        assertEquals(1000.0, hasil, 0.01)
    }
}
