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

    /**
     * Backup versi 1 (sebelum data Phase 5 ikut dibackup) tidak membawa tabel supplier, outlet, dan
     * pembelian. Referensi `referensiPembelianId`, `supplierId`, dan `outletId` pada mutasi stok di
     * backup itu pasti menggantung setelah restore, jadi dikosongkan (F-02).
     */
    fun bersihkanReferensiFase5(mutasi: List<StockMovementEntity>): List<StockMovementEntity> =
        mutasi.map { it.copy(referensiPembelianId = null, supplierId = null, outletId = null) }
}
