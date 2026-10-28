package com.nada.kasir.core.util

/**
 * Pengatur jeda untuk mode Auto Scan (kamera menyala terus dan membaca barcode berulang kali).
 *
 * Aturan: barcode yang SAMA baru diterima lagi setelah [jedaBarcodeSamaMs] sejak terakhir
 * diterima (qty bertambah paling cepat 1x per jeda). Barcode yang BERBEDA langsung diterima.
 * Jeda dihitung per barcode (bukan hanya barcode terakhir), supaya dua barcode yang sama-sama
 * terlihat kamera tidak saling bergantian lolos di setiap frame.
 *
 * Kelas murni (tanpa Android) supaya bisa diuji unit. Waktu dikirim dari luar
 * (di aplikasi: SystemClock.elapsedRealtime()), bukan dibaca di sini.
 */
class AutoScanThrottle(private val jedaBarcodeSamaMs: Long = JEDA_BARCODE_SAMA_MS) {

    private val waktuTerimaPerKode = HashMap<String, Long>()

    /** true = barcode ini dihitung sebagai scan baru; false = diabaikan (masih dalam jeda). */
    fun terima(kode: String, sekarangMs: Long): Boolean {
        if (kode.isBlank()) return false
        val terakhir = waktuTerimaPerKode[kode]
        if (terakhir != null && sekarangMs - terakhir < jedaBarcodeSamaMs) return false
        // Buang catatan yang jedanya sudah lewat supaya peta tidak tumbuh terus selama sesi panjang.
        waktuTerimaPerKode.values.removeAll { sekarangMs - it >= jedaBarcodeSamaMs }
        waktuTerimaPerKode[kode] = sekarangMs
        return true
    }

    /**
     * Mulai ulang jeda untuk semua barcode yang baru-baru ini diterima, tanpa menghitungnya sebagai
     * scan. Dipanggil saat kamera lanjut setelah berhenti (mis. selesai menambah produk baru),
     * supaya barcode yang masih di depan kamera tidak langsung tertambah lagi.
     */
    fun tahanSemua(sekarangMs: Long) {
        for (kode in waktuTerimaPerKode.keys.toList()) waktuTerimaPerKode[kode] = sekarangMs
    }

    fun reset() = waktuTerimaPerKode.clear()

    companion object {
        const val JEDA_BARCODE_SAMA_MS = 1_000L
    }
}
