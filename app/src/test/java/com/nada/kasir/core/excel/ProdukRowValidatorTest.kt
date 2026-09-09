package com.nada.kasir.core.excel

import org.junit.Assert.*
import org.junit.Test

class ProdukRowValidatorTest {

    @Test
    fun `baris valid lengkap diterima`() {
        val input = ProdukRowInput("SKU001", "8991002123456", "Indomie Goreng", "2500", "3500", "100")
        val hasil = ProdukRowValidator.validasi(input)
        assertTrue(hasil is ProdukRowValidationResult.Valid)
        hasil as ProdukRowValidationResult.Valid
        assertEquals("SKU001", hasil.kodeProduk)
        assertEquals(3500.0, hasil.hargaJual, 0.0)
        assertEquals(100, hasil.stok)
    }

    @Test
    fun `kode produk kosong ditolak`() {
        val input = ProdukRowInput("", "123", "Nama", "1000", "2000", "10")
        val hasil = ProdukRowValidator.validasi(input)
        assertTrue(hasil is ProdukRowValidationResult.Invalid)
    }

    @Test
    fun `harga jual bukan angka ditolak`() {
        val input = ProdukRowInput("SKU002", "", "Nama", "1000", "abc", "10")
        val hasil = ProdukRowValidator.validasi(input)
        assertTrue(hasil is ProdukRowValidationResult.Invalid)
    }

    @Test
    fun `barcode kosong tetap valid (opsional)`() {
        val input = ProdukRowInput("SKU003", "", "Nama Produk", "1000", "1500", "5")
        val hasil = ProdukRowValidator.validasi(input)
        assertTrue(hasil is ProdukRowValidationResult.Valid)
        assertNull((hasil as ProdukRowValidationResult.Valid).barcode)
    }

    @Test
    fun `header sesuai spesifikasi dianggap valid`() {
        assertTrue(ProdukRowValidator.headerValid(ProdukRowValidator.HEADER_WAJIB))
    }

    @Test
    fun `header yang hilang kolom dianggap tidak valid`() {
        val headerRusak = listOf("Kode Produk", "Nama Produk", "Harga Jual")
        assertFalse(ProdukRowValidator.headerValid(headerRusak))
    }
}
