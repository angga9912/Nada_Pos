package com.nada.kasir.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HandheldScannerDetectorTest {

    @Test
    fun `ketikan cepat diikuti enter dikenali sebagai barcode`() {
        var hasil: String? = null
        val detector = HandheldScannerDetector(
            ambangWaktuAntarKarakterMs = 1000L, // longgar untuk simulasi tanpa delay asli di unit test
            onBarcodeTerdeteksi = { hasil = it }
        )
        "8991002123456".forEach { detector.onCharTyped(it) }
        detector.onEnterOrNewline()

        assertEquals("8991002123456", hasil)
    }

    @Test
    fun `kode terlalu pendek tidak dianggap barcode valid`() {
        var hasil: String? = null
        val detector = HandheldScannerDetector(
            minimalPanjangKode = 4,
            onBarcodeTerdeteksi = { hasil = it }
        )
        "12".forEach { detector.onCharTyped(it) }
        detector.onEnterOrNewline()

        assertNull(hasil)
    }

    @Test
    fun `buffer kosong setelah enter, siap untuk scan berikutnya`() {
        val hasilList = mutableListOf<String>()
        val detector = HandheldScannerDetector(onBarcodeTerdeteksi = { hasilList.add(it) })

        "1234567890".forEach { detector.onCharTyped(it) }
        detector.onEnterOrNewline()
        "0987654321".forEach { detector.onCharTyped(it) }
        detector.onEnterOrNewline()

        assertEquals(listOf("1234567890", "0987654321"), hasilList)
    }
}
