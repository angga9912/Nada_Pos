package com.nada.kasir.core.domain.logic

import com.nada.kasir.core.data.local.entity.OutletEntity

/**
 * Aturan murni untuk pelabelan cabang (Phase 5 tahap 4, opsi A): "outlet aktif" memberi label
 * `outletId` pada transaksi, pembelian, dan mutasi stok; laporan bisa difilter per cabang. Stok produk
 * tetap SATU pool gabungan semua cabang.
 */
object OutletAktifLogic {
    const val KEY_OUTLET_AKTIF = "outlet_aktif_id"

    /** Nilai setting kosong/rusak = tidak ada outlet aktif. */
    fun parseId(nilai: String?): Long? = nilai?.trim()?.toLongOrNull()?.takeIf { it > 0 }

    /**
     * Outlet yang benar-benar dipakai untuk memberi label. Null (tanpa label) bila fitur Outlet tidak ada di
     * paket, belum ada pilihan, atau outlet pilihan sudah dinonaktifkan/dihapus.
     */
    fun tentukan(idTersimpan: Long?, outletAktif: List<OutletEntity>, paketMengizinkan: Boolean): OutletEntity? {
        if (!paketMengizinkan || idTersimpan == null) return null
        return outletAktif.firstOrNull { it.id == idTersimpan && it.aktif }
    }

    /** Nama + kode wajib; kode dan nama harus unik (tanpa membedakan huruf besar/kecil) di antara outlet lain. */
    fun validasiForm(nama: String, kode: String, semua: List<OutletEntity>, idSendiri: Long = 0L): String? {
        val n = nama.trim()
        val k = kode.trim()
        if (n.isEmpty()) return "Nama outlet wajib diisi."
        if (k.isEmpty()) return "Kode outlet wajib diisi."
        val lain = semua.filter { it.id != idSendiri }
        if (lain.any { it.nama.equals(n, ignoreCase = true) }) return "Nama outlet \"$n\" sudah dipakai."
        if (lain.any { it.kode.equals(k, ignoreCase = true) }) return "Kode outlet \"$k\" sudah dipakai."
        return null
    }

    /** Teks filter laporan: null = semua cabang. */
    fun labelFilter(idFilter: Long?, semua: List<OutletEntity>): String =
        if (idFilter == null) "Semua cabang" else semua.firstOrNull { it.id == idFilter }?.nama ?: "Cabang tidak ditemukan"
}
