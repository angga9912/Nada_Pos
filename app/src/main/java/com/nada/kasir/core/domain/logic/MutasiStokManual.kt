package com.nada.kasir.core.domain.logic

import com.nada.kasir.core.data.local.entity.StockMovementEntity
import com.nada.kasir.core.data.local.entity.TipeMutasiStok
import kotlin.math.abs

/**
 * Membuat catatan mutasi untuk perubahan stok yang dilakukan LANGSUNG pada produk
 * (stok awal produk baru, edit angka stok di form produk, stok awal dari import Excel).
 *
 * Aturan yang sama dengan mutasi lain di aplikasi: `qty` selalu positif dan arah ditentukan
 * oleh `tipe`. Karena itu naik = MASUK dan turun = KELUAR (tipe PENYESUAIAN tidak dipakai di sini
 * karena tidak bisa menyatakan arah). Dengan begitu rumus stok tetap konsisten untuk semua mutasi:
 *   stok = MASUK + PEMBATALAN - KELUAR - PENJUALAN
 *
 * Objek ini murni Kotlin (tanpa Room/Android) supaya bisa diuji unit.
 */
object MutasiStokManual {
    const val KET_STOK_AWAL = "Stok awal (produk baru)"
    const val KET_STOK_AWAL_IMPORT = "Stok awal (import Excel)"
    const val KET_PENYESUAIAN = "Penyesuaian manual (edit produk)"

    /**
     * @return mutasi untuk selisih [stokLama] -> [stokBaru], atau null jika stok tidak berubah.
     */
    fun buat(
        productId: Long,
        stokLama: Int,
        stokBaru: Int,
        keterangan: String,
        waktu: Long
    ): StockMovementEntity? {
        val selisih = stokBaru - stokLama
        if (selisih == 0) return null
        return StockMovementEntity(
            productId = productId,
            tipe = if (selisih > 0) TipeMutasiStok.MASUK else TipeMutasiStok.KELUAR,
            qty = abs(selisih),
            keterangan = keterangan,
            tanggalWaktu = waktu
        )
    }
}
