package com.nada.kasir.core.lisensi

import com.nada.kasir.core.paket.PaketAplikasi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AturanAktivasiTrialTest {

    private val sekarang = 1_800_000_000_000L
    private val trial = HasilValidasiLisensi(PaketAplikasi.PRO, sekarang + 1000, terikatPerangkat = true, trial = true)
    private val berbayar = HasilValidasiLisensi(PaketAplikasi.PRO, sekarang + 1000, terikatPerangkat = true, trial = false)

    @Test
    fun `kode berbayar selalu boleh`() {
        assertTrue(AturanAktivasiTrial.boleh(berbayar, PaketAplikasi.CUSTOM, sekarang + 5000, sekarang))
    }

    @Test
    fun `trial boleh saat paket Basic atau belum ada paket`() {
        assertTrue(AturanAktivasiTrial.boleh(trial, null, null, sekarang))
        assertTrue(AturanAktivasiTrial.boleh(trial, PaketAplikasi.BASIC, 0L, sekarang))
    }

    @Test
    fun `trial ditolak saat paket berbayar masih aktif`() {
        assertFalse(AturanAktivasiTrial.boleh(trial, PaketAplikasi.CUSTOM, sekarang + 86_400_000L, sekarang))
    }

    @Test
    fun `trial ditolak saat ada paket lifetime`() {
        assertFalse(AturanAktivasiTrial.boleh(trial, PaketAplikasi.PRO, -1L, sekarang))
    }

    @Test
    fun `trial boleh bila paket sebelumnya sudah kadaluarsa`() {
        assertTrue(AturanAktivasiTrial.boleh(trial, PaketAplikasi.CUSTOM, sekarang - 1L, sekarang))
    }
}
