package com.nada.kasir.core.domain.logic

import com.nada.kasir.core.domain.logic.HutangPiutangLogic.HasilBayar
import com.nada.kasir.core.domain.logic.HutangPiutangLogic.KondisiJatuhTempo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HutangPiutangLogicTest {

    private val hari = 86_400_000L
    // 15 Okt 2026 12:00 WIB kira-kira; tepatnya tidak penting, hanya selisih hari kalender yang diuji.
    private val sekarang = 1_792_000_000_000L

    // ---- validasi catatan baru ----

    @Test
    fun `catatan baru valid`() {
        assertNull(HutangPiutangLogic.validasiCatatanBaru("PIUTANG", "Ibu Sari", 50_000.0))
        assertNull(HutangPiutangLogic.validasiCatatanBaru("HUTANG", "Toko Sumber", 1.0))
    }

    @Test
    fun `catatan baru ditolak bila jenis salah, nama kosong, atau jumlah tidak positif`() {
        assertNotNull(HutangPiutangLogic.validasiCatatanBaru("LAIN", "A", 100.0))
        assertNotNull(HutangPiutangLogic.validasiCatatanBaru("PIUTANG", "   ", 100.0))
        assertNotNull(HutangPiutangLogic.validasiCatatanBaru("PIUTANG", "A", 0.0))
        assertNotNull(HutangPiutangLogic.validasiCatatanBaru("PIUTANG", "A", -5.0))
        assertNotNull(HutangPiutangLogic.validasiCatatanBaru("PIUTANG", "A", Double.NaN))
    }

    // ---- pembayaran / cicilan ----

    @Test
    fun `cicilan sebagian mengurangi sisa dan status tetap aktif`() {
        val hasil = HutangPiutangLogic.hitungPembayaran(100_000.0, "AKTIF", 40_000.0)
        assertTrue(hasil is HasilBayar.Ok)
        hasil as HasilBayar.Ok
        assertEquals(60_000.0, hasil.sisaBaru, 0.0)
        assertEquals("AKTIF", hasil.status)
    }

    @Test
    fun `pembayaran yang menutup sisa membuat status lunas`() {
        val hasil = HutangPiutangLogic.hitungPembayaran(60_000.0, "AKTIF", 60_000.0) as HasilBayar.Ok
        assertEquals(0.0, hasil.sisaBaru, 0.0)
        assertEquals("LUNAS", hasil.status)
    }

    @Test
    fun `selisih di bawah toleransi dianggap lunas`() {
        val hasil = HutangPiutangLogic.hitungPembayaran(60_000.0, "AKTIF", 59_999.7) as HasilBayar.Ok
        assertEquals(0.0, hasil.sisaBaru, 0.0)
        assertEquals("LUNAS", hasil.status)
    }

    @Test
    fun `pembayaran melebihi sisa ditolak`() {
        assertTrue(HutangPiutangLogic.hitungPembayaran(60_000.0, "AKTIF", 60_001.0) is HasilBayar.Gagal)
    }

    @Test
    fun `pembayaran nol, negatif, atau NaN ditolak`() {
        assertTrue(HutangPiutangLogic.hitungPembayaran(60_000.0, "AKTIF", 0.0) is HasilBayar.Gagal)
        assertTrue(HutangPiutangLogic.hitungPembayaran(60_000.0, "AKTIF", -1.0) is HasilBayar.Gagal)
        assertTrue(HutangPiutangLogic.hitungPembayaran(60_000.0, "AKTIF", Double.NaN) is HasilBayar.Gagal)
    }

    @Test
    fun `catatan lunas atau batal tidak bisa dibayar lagi`() {
        assertTrue(HutangPiutangLogic.hitungPembayaran(0.0, "LUNAS", 1_000.0) is HasilBayar.Gagal)
        assertTrue(HutangPiutangLogic.hitungPembayaran(50_000.0, "BATAL", 1_000.0) is HasilBayar.Gagal)
    }

    // ---- uang muka penjualan piutang ----

    @Test
    fun `uang muka nol atau sebagian valid`() {
        assertNull(HutangPiutangLogic.validasiUangMuka(0.0, 100_000.0))
        assertNull(HutangPiutangLogic.validasiUangMuka(40_000.0, 100_000.0))
    }

    @Test
    fun `uang muka negatif atau sama dengan total ditolak`() {
        assertNotNull(HutangPiutangLogic.validasiUangMuka(-1.0, 100_000.0))
        assertNotNull(HutangPiutangLogic.validasiUangMuka(100_000.0, 100_000.0))
        assertNotNull(HutangPiutangLogic.validasiUangMuka(150_000.0, 100_000.0))
    }

    // ---- jatuh tempo ----

    @Test
    fun `selisih hari memakai hari kalender`() {
        assertEquals(3, HutangPiutangLogic.selisihHari(sekarang + 3 * hari, sekarang))
        assertEquals(0, HutangPiutangLogic.selisihHari(sekarang, sekarang))
        assertEquals(-2, HutangPiutangLogic.selisihHari(sekarang - 2 * hari, sekarang))
    }

    @Test
    fun `kondisi jatuh tempo`() {
        assertEquals(KondisiJatuhTempo.TANPA, HutangPiutangLogic.kondisiJatuhTempo("AKTIF", null, sekarang))
        assertEquals(KondisiJatuhTempo.BELUM, HutangPiutangLogic.kondisiJatuhTempo("AKTIF", sekarang + 5 * hari, sekarang))
        assertEquals(KondisiJatuhTempo.HARI_INI, HutangPiutangLogic.kondisiJatuhTempo("AKTIF", sekarang, sekarang))
        assertEquals(KondisiJatuhTempo.TERLAMBAT, HutangPiutangLogic.kondisiJatuhTempo("AKTIF", sekarang - 3 * hari, sekarang))
    }

    @Test
    fun `catatan lunas atau batal tidak pernah terlambat`() {
        assertEquals(KondisiJatuhTempo.SELESAI, HutangPiutangLogic.kondisiJatuhTempo("LUNAS", sekarang - 10 * hari, sekarang))
        assertEquals(KondisiJatuhTempo.SELESAI, HutangPiutangLogic.kondisiJatuhTempo("BATAL", sekarang - 10 * hari, sekarang))
    }

    @Test
    fun `jatuh tempo dari jumlah hari`() {
        assertNull(HutangPiutangLogic.jatuhTempoDariHari(null, sekarang))
        assertNull(HutangPiutangLogic.jatuhTempoDariHari(0, sekarang))
        val tujuhHari = HutangPiutangLogic.jatuhTempoDariHari(7, sekarang)!!
        assertEquals(7, HutangPiutangLogic.selisihHari(tujuhHari, sekarang))
        assertFalse(HutangPiutangLogic.kondisiJatuhTempo("AKTIF", tujuhHari, sekarang) == KondisiJatuhTempo.TERLAMBAT)
    }

    @Test
    fun `label jenis`() {
        assertEquals("Piutang", HutangPiutangLogic.labelJenis("PIUTANG"))
        assertEquals("Hutang", HutangPiutangLogic.labelJenis("HUTANG"))
    }
}
