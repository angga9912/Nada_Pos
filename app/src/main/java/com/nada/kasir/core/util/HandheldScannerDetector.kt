package com.nada.kasir.core.util

/**
 * Scanner barcode fisik (handheld) biasanya bekerja sebagai "keyboard virtual":
 * mengetik seluruh kode dalam waktu sangat singkat (~<50ms per karakter) lalu
 * mengirim Enter di akhir. Ketikan manual manusia jauh lebih lambat.
 *
 * Kelas ini membedakan dua pola tersebut dari aliran karakter yang masuk ke
 * TextField pencarian produk di halaman Kasir, tanpa perlu app terpisah/mode khusus.
 *
 * Cara pakai: panggil onCharTyped() setiap kali teks di search field berubah
 * (bandingkan panjang teks lama vs baru untuk tahu karakter apa yang baru masuk).
 */
class HandheldScannerDetector(
    private val ambangWaktuAntarKarakterMs: Long = 40L,
    private val minimalPanjangKode: Int = 4,
    private val onBarcodeTerdeteksi: (String) -> Unit
) {
    private var buffer = StringBuilder()
    private var waktuKarakterTerakhir = 0L

    fun onCharTyped(char: Char) {
        val sekarang = System.currentTimeMillis()
        if (sekarang - waktuKarakterTerakhir > ambangWaktuAntarKarakterMs && buffer.isNotEmpty()) {
            // Jeda terlalu lama -> dianggap ketikan manual baru, reset buffer
            buffer.clear()
        }
        buffer.append(char)
        waktuKarakterTerakhir = sekarang
    }

    /** Panggil saat menerima tanda akhir input dari scanner (biasanya karakter newline/Enter). */
    fun onEnterOrNewline() {
        if (buffer.length >= minimalPanjangKode) {
            onBarcodeTerdeteksi(buffer.toString())
        }
        buffer.clear()
    }

    fun reset() {
        buffer.clear()
        waktuKarakterTerakhir = 0L
    }
}
