package com.nada.kasir.core.lisensi

/**
 * Kunci tabel `settings` yang menyimpan status lisensi PERANGKAT ini.
 *
 * Dikumpulkan di satu tempat karena BackupManager harus memperlakukannya khusus: status lisensi
 * milik perangkat, bukan milik data toko, jadi TIDAK ikut dibackup dan TIDAK boleh ditimpa saat
 * restore (kalau ikut ditimpa, file backup bisa menyisipkan paket berbayar atau memundurkan
 * penanda waktu anti-manipulasi jam).
 */
object KunciPengaturanLisensi {
    const val PAKET_AKTIF = "paket_aktif"
    const val KADALUARSA = "lisensi_kadaluarsa_millis" // "-1" = LIFETIME, "0" = sudah turun ke Basic
    const val KODE = "lisensi_kode_aktif"
    const val WAKTU_TERAKHIR = "lisensi_waktu_terakhir"

    val SEMUA: Set<String> = setOf(PAKET_AKTIF, KADALUARSA, KODE, WAKTU_TERAKHIR)
}
