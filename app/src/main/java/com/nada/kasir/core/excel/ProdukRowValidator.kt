package com.nada.kasir.core.excel

/**
 * Validasi satu baris data produk dari Excel, dipisah sebagai fungsi murni
 * (tanpa Apache POI/Android) supaya bisa diuji sebagai unit test JVM biasa.
 */
data class ProdukRowInput(
    val kodeProduk: String,
    val barcode: String,
    val nama: String,
    val hargaBeliText: String,
    val hargaJualText: String,
    val stokText: String
)

sealed class ProdukRowValidationResult {
    data class Valid(
        val kodeProduk: String, val barcode: String?, val nama: String,
        val hargaBeli: Double, val hargaJual: Double, val stok: Int
    ) : ProdukRowValidationResult()
    data class Invalid(val alasan: String) : ProdukRowValidationResult()
}

object ProdukRowValidator {
    fun validasi(input: ProdukRowInput): ProdukRowValidationResult {
        if (input.kodeProduk.isBlank()) return ProdukRowValidationResult.Invalid("Kode Produk kosong")
        if (input.nama.isBlank()) return ProdukRowValidationResult.Invalid("Nama Produk kosong")

        val hargaBeli = input.hargaBeliText.trim().toDoubleOrNull()
        val hargaJual = input.hargaJualText.trim().toDoubleOrNull()
        val stok = input.stokText.trim().toIntOrNull()

        if (hargaBeli == null || hargaBeli < 0) return ProdukRowValidationResult.Invalid("Harga Beli tidak valid")
        if (hargaJual == null || hargaJual < 0) return ProdukRowValidationResult.Invalid("Harga Jual tidak valid")
        if (stok == null || stok < 0) return ProdukRowValidationResult.Invalid("Stok tidak valid")

        return ProdukRowValidationResult.Valid(
            kodeProduk = input.kodeProduk.trim(),
            barcode = input.barcode.trim().ifBlank { null },
            nama = input.nama.trim(),
            hargaBeli = hargaBeli, hargaJual = hargaJual, stok = stok
        )
    }

    /** Header wajib ada persis seperti ini di baris pertama sheet DATA_PRODUK (poin 15 & 25). */
    val HEADER_WAJIB = listOf("Kode Produk", "Barcode", "Nama Produk", "Kategori", "Satuan", "Harga Beli", "Harga Jual", "Stok", "Stok Minimum")

    fun headerValid(headerDitemukan: List<String>): Boolean {
        // Cukup cocokkan kolom-kolom kunci (tidak strip-sensitive ke kapitalisasi/spasi berlebih)
        val dinormalisasi = headerDitemukan.map { it.trim().lowercase() }
        val wajibDinormalisasi = HEADER_WAJIB.map { it.lowercase() }
        return wajibDinormalisasi.all { it in dinormalisasi }
    }
}
