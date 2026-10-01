package com.nada.kasir.core.lisensi

import org.junit.Assert.assertEquals
import org.junit.Test

class WaktuLisensiTest {

    private val hari = 24L * 60 * 60 * 1000
    private val batas = WaktuLisensi.BATAS_MAJU_PER_CEK_MS

    @Test
    fun `pengecekan pertama mencatat waktu sekarang`() {
        assertEquals(1_000L, WaktuLisensi.terakhirDilihatBerikutnya(sekarang = 1_000L, terakhirDilihat = 0L))
        assertEquals(1_000L, WaktuLisensi.efektif(sekarang = 1_000L, terakhirDilihat = 0L))
    }

    @Test
    fun `waktu normal yang maju dicatat apa adanya`() {
        val terakhir = 100 * hari
        val sekarang = terakhir + 2 * hari
        assertEquals(sekarang, WaktuLisensi.terakhirDilihatBerikutnya(sekarang, terakhir))
        assertEquals(sekarang, WaktuLisensi.efektif(sekarang, terakhir))
    }

    @Test
    fun `jam dimundurkan tidak memundurkan waktu efektif maupun catatan`() {
        val terakhir = 100 * hari
        val sekarang = terakhir - 40 * hari // jam HP dimundurkan 40 hari
        assertEquals(terakhir, WaktuLisensi.efektif(sekarang, terakhir))
        assertEquals(terakhir, WaktuLisensi.terakhirDilihatBerikutnya(sekarang, terakhir))
    }

    @Test
    fun `jam yang melompat sangat jauh ke depan hanya memajukan catatan sebatas batas per cek`() {
        val terakhir = 100 * hari
        val sekarang = terakhir + 3000 * hari // salah set jam ke jauh di masa depan
        val catatanBaru = WaktuLisensi.terakhirDilihatBerikutnya(sekarang, terakhir)
        assertEquals(terakhir + batas, catatanBaru)

        // Setelah jam dibetulkan, catatan tidak "macet" bertahun-tahun di masa depan: waktu efektif
        // paling jauh hanya selisih satu batas (31 hari) di depan waktu sebenarnya.
        val jamDibetulkan = terakhir + 5 * hari
        assertEquals(terakhir + batas, WaktuLisensi.efektif(jamDibetulkan, catatanBaru))
    }

    @Test
    fun `kemajuan catatan per pengecekan tidak pernah melebihi batas`() {
        val terakhir = 50 * hari
        val hasil = WaktuLisensi.terakhirDilihatBerikutnya(terakhir + 500 * hari, terakhir)
        assertEquals(terakhir + batas, hasil)
    }
}
