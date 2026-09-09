package com.nada.kasir.core.backup

import com.nada.kasir.core.data.local.entity.ProductEntity
import com.nada.kasir.core.data.local.entity.StoreEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EntityJsonMapperTest {

    @Test
    fun `produk bisa di-roundtrip ke json dan kembali tanpa kehilangan data`() {
        val original = ProductEntity(
            id = 5, kodeProduk = "SKU-1", barcode = "12345", nama = "Kopi Sachet",
            categoryId = 2, hargaBeli = 1000.0, hargaJual = 1500.0, stok = 20, stokMinimum = 5
        )
        val json = EntityJsonMapper.productToJson(original)
        val hasil = EntityJsonMapper.productFromJson(json)

        assertEquals(original.kodeProduk, hasil.kodeProduk)
        assertEquals(original.barcode, hasil.barcode)
        assertEquals(original.hargaJual, hasil.hargaJual, 0.0)
        assertEquals(original.stok, hasil.stok)
    }

    @Test
    fun `restore toko tidak pernah mengaktifkan tampilkan harga beli di struk`() {
        // Simulasi file backup yang (sengaja/tidak sengaja) berisi true - harus tetap dipaksa false
        val json = EntityJsonMapper.storeToJson(StoreEntity(tampilkanHargaBeliStruk = false))
        json.put("tampilkanHargaBeliStruk", true)

        val hasil = EntityJsonMapper.storeFromJson(json)

        assertFalse("Harga beli tidak boleh pernah tampil di struk meski file backup memaksakannya", hasil.tampilkanHargaBeliStruk)
    }
}
