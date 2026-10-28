package com.nada.kasir.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class UkuranFotoTest {

    @Test
    fun `foto kamera besar didecode pada sample size pangkat dua yang menyisakan sisi minimal batas`() {
        assertEquals(2, UkuranFoto.hitungInSampleSize(4000, 3000, 1280))
        assertEquals(4, UkuranFoto.hitungInSampleSize(8000, 6000, 1280))
        // Orientasi potret dihitung dari sisi terpanjang, sama seperti lanskap.
        assertEquals(2, UkuranFoto.hitungInSampleSize(3000, 4000, 1280))
        // Tepat dua kali batas: setengahnya masih >= batas.
        assertEquals(2, UkuranFoto.hitungInSampleSize(2560, 1920, 1280))
    }

    @Test
    fun `foto yang sudah kecil tidak disubsample`() {
        assertEquals(1, UkuranFoto.hitungInSampleSize(1000, 800, 1280))
        assertEquals(1, UkuranFoto.hitungInSampleSize(2559, 1700, 1280))
    }

    @Test
    fun `ukuran tidak valid memakai sample size 1`() {
        assertEquals(1, UkuranFoto.hitungInSampleSize(0, 3000, 1280))
        assertEquals(1, UkuranFoto.hitungInSampleSize(4000, 3000, 0))
        assertEquals(1, UkuranFoto.hitungInSampleSize(-1, -1, 1280))
    }

    @Test
    fun `skala memperkecil sisi terpanjang ke batas dan tidak pernah memperbesar`() {
        assertEquals(0.64f, UkuranFoto.hitungSkala(2000, 1500, 1280), 0.0001f)
        assertEquals(0.64f, UkuranFoto.hitungSkala(1500, 2000, 1280), 0.0001f)
        assertEquals(1f, UkuranFoto.hitungSkala(1280, 960, 1280), 0f)
        assertEquals(1f, UkuranFoto.hitungSkala(640, 480, 1280), 0f)
        assertEquals(1f, UkuranFoto.hitungSkala(0, 0, 1280), 0f)
    }

    @Test
    fun `orientasi EXIF dipetakan ke derajat putar`() {
        assertEquals(90, UkuranFoto.derajatPutarDariExif(6))
        assertEquals(180, UkuranFoto.derajatPutarDariExif(3))
        assertEquals(270, UkuranFoto.derajatPutarDariExif(8))
        assertEquals(0, UkuranFoto.derajatPutarDariExif(1))
        assertEquals(0, UkuranFoto.derajatPutarDariExif(0))
        assertEquals(0, UkuranFoto.derajatPutarDariExif(99))
    }
}
