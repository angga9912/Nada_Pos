package com.nada.kasir.core.printer

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/**
 * Membangun perintah ESC/POS mentah (poin 8). Kompatibel dengan hampir semua
 * printer thermal Bluetooth generik 58mm/80mm di pasaran (protokol standar,
 * bukan spesifik satu merek).
 */
class EscPosBuilder {
    private val buffer = ByteArrayOutputStream()

    companion object {
        private const val ESC = 0x1B
        private const val GS = 0x1D
    }

    fun reset(): EscPosBuilder {
        buffer.write(ESC); buffer.write(0x40) // ESC @ - initialize printer
        return this
    }

    fun alignLeft(): EscPosBuilder = align(0)
    fun alignCenter(): EscPosBuilder = align(1)
    fun alignRight(): EscPosBuilder = align(2)

    private fun align(mode: Int): EscPosBuilder {
        buffer.write(ESC); buffer.write(0x61); buffer.write(mode) // ESC a n
        return this
    }

    fun bold(on: Boolean): EscPosBuilder {
        buffer.write(ESC); buffer.write(0x45); buffer.write(if (on) 1 else 0) // ESC E n
        return this
    }

    fun fontSizeNormal(): EscPosBuilder = fontSize(0)
    fun fontSizeBesar(): EscPosBuilder = fontSize(0x11) // double width & height

    private fun fontSize(mode: Int): EscPosBuilder {
        buffer.write(GS); buffer.write(0x21); buffer.write(mode) // GS ! n
        return this
    }

    fun text(text: String): EscPosBuilder {
        // CP437 dipakai karena hampir semua printer thermal murah pakai code page ini secara default.
        val bytes = try {
            text.toByteArray(charset("CP437"))
        } catch (e: Exception) {
            text.toByteArray() // fallback kalau charset tidak tersedia di device tertentu
        }
        buffer.write(bytes)
        return this
    }

    fun textLine(text: String = ""): EscPosBuilder {
        text(text)
        newLine()
        return this
    }

    fun newLine(): EscPosBuilder {
        buffer.write(0x0A)
        return this
    }

    /** Garis pemisah sepanjang lebar kertas (32 kolom untuk 58mm, 48 kolom untuk 80mm). */
    fun garis(lebarKolom: Int): EscPosBuilder {
        textLine("-".repeat(lebarKolom))
        return this
    }

    /** Dua kolom rata kiri-kanan dalam satu baris, mis. label vs nilai (TOTAL ... 13.000). */
    fun baris2Kolom(kiri: String, kanan: String, lebarKolom: Int): EscPosBuilder {
        val sisaUntukSpasi = (lebarKolom - kiri.length - kanan.length).coerceAtLeast(1)
        textLine(kiri + " ".repeat(sisaUntukSpasi) + kanan)
        return this
    }

    fun feedAndCut(): EscPosBuilder {
        newLine(); newLine(); newLine()
        buffer.write(GS); buffer.write(0x56); buffer.write(0x00) // GS V 0 - full cut
        return this
    }

    /**
     * Cetak gambar (logo toko) sebagai raster bitmap monokrom, pakai perintah
     * ESC/POS standar "GS v 0". Gambar diskalakan ke lebar target (dalam dot,
     * bukan kolom karakter) lalu di-threshold jadi hitam/putih murni karena
     * printer thermal tidak mendukung grayscale.
     */
    fun image(bitmap: Bitmap, lebarDotsTarget: Int): EscPosBuilder {
        val lebarDots = (lebarDotsTarget / 8) * 8 // harus kelipatan 8 (1 byte = 8 dot horizontal)
        if (lebarDots <= 0 || bitmap.width <= 0) return this

        val rasio = lebarDots.toDouble() / bitmap.width
        val tinggiDots = (bitmap.height * rasio).toInt().coerceAtLeast(1)
        val skala = Bitmap.createScaledBitmap(bitmap, lebarDots, tinggiDots, true)

        val lebarByte = lebarDots / 8
        val data = ByteArray(lebarByte * tinggiDots)

        for (y in 0 until tinggiDots) {
            for (x in 0 until lebarDots) {
                val pixel = skala.getPixel(x, y)
                val alpha = (pixel ushr 24) and 0xFF
                val r = (pixel ushr 16) and 0xFF
                val g = (pixel ushr 8) and 0xFF
                val b = pixel and 0xFF
                val abuAbu = (r + g + b) / 3
                // threshold sederhana: piksel gelap & tidak transparan -> dicetak (bit 1)
                val hitam = alpha > 128 && abuAbu < 160
                if (hitam) {
                    val indexByte = y * lebarByte + (x / 8)
                    val posisiBit = 7 - (x % 8)
                    data[indexByte] = (data[indexByte].toInt() or (1 shl posisiBit)).toByte()
                }
            }
        }

        buffer.write(GS); buffer.write('v'.code); buffer.write('0'.code); buffer.write(0) // GS v 0 m=0 (normal)
        buffer.write(lebarByte and 0xFF); buffer.write((lebarByte shr 8) and 0xFF)
        buffer.write(tinggiDots and 0xFF); buffer.write((tinggiDots shr 8) and 0xFF)
        buffer.write(data)
        return this
    }

    fun build(): ByteArray = buffer.toByteArray()
}
