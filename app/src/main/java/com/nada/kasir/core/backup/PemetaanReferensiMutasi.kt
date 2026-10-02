package com.nada.kasir.core.backup

import com.nada.kasir.core.data.local.entity.StockMovementEntity

/**
 * Saat restore, setiap transaksi disisipkan sebagai baris BARU sehingga mendapat id baru (id lama
 * tidak dipertahankan). Mutasi stok yang menunjuk ke transaksi (`referensiTransaksiId`) harus ikut
 * dipetakan ke id baru itu. Tanpa pemetaan, referensi menunjuk ke transaksi LAIN yang kebetulan
 * memakai id yang sama (bukan sekadar menggantung) - lebih berbahaya karena tidak terlihat salah.
 *
 * Objek ini murni Kotlin supaya bisa diuji unit.
 */
object PemetaanReferensiMutasi {

    /**
     * @param petaIdLamaKeBaru id transaksi di file backup -> id transaksi setelah disisipkan.
     * Referensi yang transaksinya tidak ada di backup menjadi null (tidak dibiarkan menggantung).
     */
    fun petakan(
        mutasi: List<StockMovementEntity>,
        petaIdLamaKeBaru: Map<Long, Long>
    ): List<StockMovementEntity> = mutasi.map { m ->
        val lama = m.referensiTransaksiId
        when {
            lama == null -> m
            else -> m.copy(referensiTransaksiId = petaIdLamaKeBaru[lama])
        }
    }
}
