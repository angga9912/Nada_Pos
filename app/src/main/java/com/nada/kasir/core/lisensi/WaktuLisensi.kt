package com.nada.kasir.core.lisensi

/**
 * Perlindungan sederhana terhadap pemunduran jam HP untuk memperpanjang lisensi berlangganan.
 *
 * Aplikasi mencatat "waktu terakhir yang pernah dilihat". Waktu yang dipakai untuk menilai
 * kadaluarsa tidak pernah lebih mundur dari catatan itu. Batas maju per pengecekan mencegah
 * jam yang tak sengaja dimajukan jauh (mis. ke tahun 2035) membuat catatan "macet" di masa depan
 * dan menurunkan lisensi pengguna jujur walau jam sudah dibetulkan.
 *
 * KETERBATASAN (jujur): ini perlindungan offline. Pengguna yang menghapus data aplikasi lalu
 * mengaktifkan ulang kodenya sambil memundurkan jam masih bisa lolos. Menutupnya sepenuhnya
 * butuh server, yang sengaja tidak dipakai aplikasi ini.
 */
object WaktuLisensi {
    const val BATAS_MAJU_PER_CEK_MS: Long = 31L * 24 * 60 * 60 * 1000

    /** Waktu yang dipakai untuk menilai kadaluarsa. */
    fun efektif(sekarang: Long, terakhirDilihat: Long): Long = maxOf(sekarang, terakhirDilihat)

    /** Nilai baru untuk disimpan sebagai "waktu terakhir dilihat". */
    fun terakhirDilihatBerikutnya(sekarang: Long, terakhirDilihat: Long): Long =
        if (terakhirDilihat <= 0L) sekarang
        else maxOf(terakhirDilihat, minOf(sekarang, terakhirDilihat + BATAS_MAJU_PER_CEK_MS))
}
