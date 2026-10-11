package com.nada.kasir.core.lisensi

import com.nada.kasir.core.paket.PaketAplikasi

/**
 * Aturan kapan kode TRIAL boleh menggantikan lisensi yang tersimpan.
 *
 * Aplikasi hanya menyimpan SATU lisensi. Bila toko yang sudah berlangganan memasukkan kode trial, lisensi
 * berbayarnya akan tertimpa, lalu setelah trial habis aplikasi turun ke Basic (bukan kembali ke paket
 * berbayarnya). Karena itu trial hanya boleh dipakai saat tidak ada paket aktif. Objek ini murni Kotlin
 * supaya bisa diuji unit.
 */
object AturanAktivasiTrial {

    /**
     * @param paketTersimpan paket yang sedang tersimpan (null/BASIC = tidak ada paket berbayar).
     * @param kadaluarsaTersimpan nilai setting kadaluarsa: -1 = lifetime, 0/null = tidak ada, selain itu epoch millis.
     */
    fun boleh(
        hasilBaru: HasilValidasiLisensi,
        paketTersimpan: PaketAplikasi?,
        kadaluarsaTersimpan: Long?,
        sekarangMillis: Long
    ): Boolean {
        if (!hasilBaru.trial) return true
        if (paketTersimpan == null || paketTersimpan == PaketAplikasi.BASIC) return true
        val masihAktif = kadaluarsaTersimpan == -1L ||
            (kadaluarsaTersimpan != null && kadaluarsaTersimpan > sekarangMillis)
        return !masihAktif
    }
}
