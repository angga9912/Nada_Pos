package com.nada.kasir.core.util

/**
 * Perhitungan murni untuk memperkecil dan meluruskan foto hasil kamera sebelum disimpan sebagai
 * foto produk. Dipisah dari [ProductPhotoStorageHelper] (yang memakai Bitmap/Context) supaya bisa
 * diuji unit tanpa Android.
 *
 * Foto kamera HP bisa 4000x3000 piksel (beberapa MB). Foto produk cukup kecil untuk thumbnail dan
 * kartu produk, sedangkan file foto ikut Backup/Restore (dibaca ke memori saat restore), jadi
 * foto diperkecil dulu agar backup tidak membengkak.
 */
object UkuranFoto {

    /**
     * Faktor `inSampleSize` (pangkat dua) untuk `BitmapFactory`, dipilih supaya sisi terpanjang hasil
     * decode masih >= [sisiMaks]. Penskalaan halus ke [sisiMaks] dilakukan sesudahnya lewat [hitungSkala],
     * jadi gambar tidak pernah diperbesar.
     */
    fun hitungInSampleSize(lebar: Int, tinggi: Int, sisiMaks: Int): Int {
        if (lebar <= 0 || tinggi <= 0 || sisiMaks <= 0) return 1
        val terpanjang = maxOf(lebar, tinggi)
        var sample = 1
        while (terpanjang / (sample * 2) >= sisiMaks) sample *= 2
        return sample
    }

    /** Skala (<= 1) agar sisi terpanjang tidak melebihi [sisiMaks]. Gambar yang sudah kecil tidak diubah (1f). */
    fun hitungSkala(lebar: Int, tinggi: Int, sisiMaks: Int): Float {
        val terpanjang = maxOf(lebar, tinggi)
        if (terpanjang <= 0 || sisiMaks <= 0 || terpanjang <= sisiMaks) return 1f
        return sisiMaks.toFloat() / terpanjang
    }

    /**
     * Derajat putar searah jarum jam dari tag orientasi EXIF. Kamera menyimpan piksel apa adanya dan
     * mencatat orientasinya di EXIF, jadi hasil decode harus diputar sendiri sebelum disimpan ulang.
     * Nilai: 6 = ROTATE_90, 3 = ROTATE_180, 8 = ROTATE_270; lainnya dianggap tidak perlu diputar.
     */
    fun derajatPutarDariExif(orientasi: Int): Int = when (orientasi) {
        6 -> 90
        3 -> 180
        8 -> 270
        else -> 0
    }
}
