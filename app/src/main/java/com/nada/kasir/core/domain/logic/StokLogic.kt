package com.nada.kasir.core.domain.logic

import com.nada.kasir.core.data.local.entity.StockMovementEntity
import com.nada.kasir.core.data.local.entity.TipeMutasiStok

/**
 * Aturan murni (tanpa Android/Room) untuk layar Stok: stok masuk, stok keluar, dan opname/penyesuaian
 * (Phase 5 tahap 3), supaya bisa diuji unit.
 *
 * Konvensi arah mutasi sama dengan seluruh aplikasi: `qty` selalu positif dan arah ditentukan `tipe`
 * (naik = MASUK, turun = KELUAR); lihat [MutasiStokManual].
 */
object StokLogic {
    /** Batas wajar satu kali input; mencegah salah ketik (mis. ekstra nol) merusak stok. */
    const val QTY_MAKS = 1_000_000

    enum class AlasanKeluar(val label: String) {
        RUSAK("Rusak"),
        KADALUARSA("Kadaluarsa"),
        HILANG("Hilang"),
        PEMAKAIAN_SENDIRI("Dipakai sendiri"),
        RETUR_SUPPLIER("Retur ke supplier"),
        LAINNYA("Lainnya")
    }

    fun validasiQty(qty: Int): String? = when {
        qty <= 0 -> "Jumlah harus lebih dari 0."
        qty > QTY_MAKS -> "Jumlah terlalu besar (maksimal $QTY_MAKS sekali input)."
        else -> null
    }

    fun validasiHargaBeli(hargaBeli: Double?): String? = when {
        hargaBeli == null -> null
        hargaBeli.isNaN() || hargaBeli < 0.0 -> "Harga beli tidak valid."
        else -> null
    }

    /** Stok keluar tidak boleh melebihi stok sistem (kecuali diizinkan), dan alasan "Lainnya" wajib dicatat. */
    fun validasiStokKeluar(
        stokSaatIni: Int,
        qty: Int,
        alasan: AlasanKeluar,
        catatan: String?,
        izinkanMinus: Boolean = false
    ): String? {
        validasiQty(qty)?.let { return it }
        if (!izinkanMinus && qty > stokSaatIni) return "Stok sekarang $stokSaatIni, tidak cukup untuk dikurangi $qty."
        if (alasan == AlasanKeluar.LAINNYA && catatan.isNullOrBlank()) return "Alasan \"Lainnya\" wajib diisi catatan."
        return null
    }

    /** Opname: stok fisik hasil hitung, tidak boleh negatif, dan harus berbeda dari stok sistem. */
    fun validasiOpname(stokSistem: Int, stokFisik: Int): String? = when {
        stokFisik < 0 -> "Stok fisik tidak boleh negatif."
        stokFisik > QTY_MAKS -> "Stok fisik terlalu besar (maksimal $QTY_MAKS)."
        stokFisik == stokSistem -> "Stok fisik sama dengan stok sistem, tidak ada yang disesuaikan."
        else -> null
    }

    /** Positif = stok bertambah, negatif = berkurang. */
    fun selisihOpname(stokSistem: Int, stokFisik: Int): Int = stokFisik - stokSistem

    // ---- Keterangan mutasi (teks yang tampil di riwayat) ----

    fun keteranganMasuk(catatan: String?): String = gabung("Stok masuk manual", catatan)

    fun keteranganKeluar(alasan: AlasanKeluar, catatan: String?): String =
        gabung("Stok keluar: ${alasan.label}", catatan)

    fun keteranganOpname(catatan: String?): String = gabung("Stok opname", catatan)

    private fun gabung(dasar: String, catatan: String?): String {
        val c = catatan?.trim().orEmpty()
        return if (c.isEmpty()) dasar else "$dasar — $c"
    }

    // ---- Tampilan riwayat ----

    /** true untuk perubahan stok yang dilakukan langsung oleh pengguna (bukan akibat penjualan/pembelian). */
    fun mutasiManual(m: StockMovementEntity): Boolean =
        m.referensiTransaksiId == null && m.referensiPembelianId == null

    fun menambah(tipe: TipeMutasiStok): Boolean =
        tipe == TipeMutasiStok.MASUK || tipe == TipeMutasiStok.PEMBATALAN

    /** "+5" atau "−3" (tanda minus sungguhan agar rata dengan plus). */
    fun teksQty(tipe: TipeMutasiStok, qty: Int): String = when (tipe) {
        TipeMutasiStok.MASUK, TipeMutasiStok.PEMBATALAN -> "+$qty"
        TipeMutasiStok.KELUAR, TipeMutasiStok.PENJUALAN -> "−$qty"
        TipeMutasiStok.PENYESUAIAN -> "±$qty"
    }
}
