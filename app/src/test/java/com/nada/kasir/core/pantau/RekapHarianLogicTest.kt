package com.nada.kasir.core.pantau

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class RekapHarianLogicTest {

    private val jakarta = ZoneId.of("Asia/Jakarta")

    private fun rekap(
        tanggal: String = "2026-10-30",
        kode: String = "CBG-01",
        total: Double = 1_250_000.0,
        transaksi: Int = 42
    ) = RekapHarian(
        tanggal = tanggal,
        kodeCabang = kode,
        kodeDokumen = RekapHarianLogic.bersihkanKode(kode),
        namaCabang = "Cabang Satu",
        jumlahTransaksi = transaksi,
        totalPenjualan = total,
        totalDiskon = 15_000.0,
        produkTerjual = 120,
        estimasiKeuntungan = 310_000.0,
        metodePembayaran = listOf(
            RekapMetode("TUNAI", 900_000.0, 30),
            RekapMetode("QRIS", 350_000.0, 12)
        ),
        produkTerlaris = listOf(RekapProduk("Kopi Susu", 40, 600_000.0)),
        diperbaruiPada = 1_761_800_000_000L
    )

    // ---- bersihkanKode & idDokumen ----

    @Test
    fun `bersihkanKode memakai huruf besar dan mengganti karakter terlarang`() {
        assertEquals("CBG-01", RekapHarianLogic.bersihkanKode("cbg-01"))
        assertEquals("CBG_01", RekapHarianLogic.bersihkanKode("cbg 01"))
        assertEquals("A_B_C", RekapHarianLogic.bersihkanKode("a/b.c"))
        assertEquals("CBG-01", RekapHarianLogic.bersihkanKode("  cbg-01  "))
        assertEquals("CAF_", RekapHarianLogic.bersihkanKode("Café"))
    }

    @Test
    fun `bersihkanKode kosong atau null menjadi PUSAT`() {
        assertEquals("PUSAT", RekapHarianLogic.bersihkanKode(null))
        assertEquals("PUSAT", RekapHarianLogic.bersihkanKode(""))
        assertEquals("PUSAT", RekapHarianLogic.bersihkanKode("   "))
    }

    @Test
    fun `id dokumen gabungan tanggal dan kode cabang`() {
        assertEquals("2026-10-30_CBG-01", RekapHarianLogic.idDokumen("2026-10-30", "CBG-01"))
    }

    // ---- rentang hari ----

    @Test
    fun `rentang hari memakai batas tengah malam zona waktu cabang`() {
        val (awal, akhir) = RekapHarianLogic.rentangHari(LocalDate.of(2026, 10, 30), jakarta)
        // 00:00 WIB = 17:00 UTC hari sebelumnya
        assertEquals(Instant.parse("2026-10-29T17:00:00Z").toEpochMilli(), awal)
        assertEquals(24L * 60 * 60 * 1000 - 1, akhir - awal)
    }

    @Test
    fun `hari berurutan tidak saling tumpang tindih`() {
        val (_, akhirKemarin) = RekapHarianLogic.rentangHari(LocalDate.of(2026, 10, 29), jakarta)
        val (awalHariIni, _) = RekapHarianLogic.rentangHari(LocalDate.of(2026, 10, 30), jakarta)
        assertEquals(awalHariIni - 1, akhirKemarin)
    }

    // ---- tanggal untuk dikirim ----

    @Test
    fun `tanggal untuk dikirim berurutan dari terlama ke hari ini`() {
        val hasil = RekapHarianLogic.tanggalUntukDikirim(LocalDate.of(2026, 10, 30), 2)
        assertEquals(
            listOf(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 29), LocalDate.of(2026, 10, 30)),
            hasil
        )
    }

    @Test
    fun `tanggal untuk dikirim melewati pergantian bulan`() {
        val hasil = RekapHarianLogic.tanggalUntukDikirim(LocalDate.of(2026, 3, 1), 2)
        assertEquals(
            listOf(LocalDate.of(2026, 2, 27), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 1)),
            hasil
        )
    }

    @Test
    fun `hari ke belakang nol atau negatif hanya mengirim hari ini`() {
        val hariIni = LocalDate.of(2026, 10, 30)
        assertEquals(listOf(hariIni), RekapHarianLogic.tanggalUntukDikirim(hariIni, 0))
        assertEquals(listOf(hariIni), RekapHarianLogic.tanggalUntukDikirim(hariIni, -5))
    }

    // ---- keMap / dariMap ----

    @Test
    fun `keMap lalu dariMap mengembalikan data yang sama`() {
        val asli = rekap()
        assertEquals(asli, RekapHarianLogic.dariMap(RekapHarianLogic.keMap(asli)))
    }

    @Test
    fun `dariMap menerima angka Long maupun Double seperti yang dikembalikan Firestore`() {
        val dariFirestore: Map<String, Any?> = mapOf(
            "tanggal" to "2026-10-30",
            "kodeDokumen" to "PUSAT",
            "jumlahTransaksi" to 7L,
            "totalPenjualan" to 100000L, // Firestore bisa mengembalikan Long untuk angka bulat
            "totalDiskon" to 0.0,
            "produkTerjual" to 9L,
            "estimasiKeuntungan" to 25000L,
            "diperbaruiPada" to 1_761_800_000_000L
        )
        val hasil = RekapHarianLogic.dariMap(dariFirestore)
        assertNotNull(hasil)
        assertEquals(100000.0, hasil!!.totalPenjualan, 0.0)
        assertEquals(7, hasil.jumlahTransaksi)
        assertEquals("PUSAT", hasil.kodeCabang) // jatuh ke kodeDokumen bila kodeCabang hilang
        assertEquals(emptyList<RekapMetode>(), hasil.metodePembayaran)
    }

    @Test
    fun `dariMap menolak dokumen tanpa tanggal atau kode dokumen`() {
        assertNull(RekapHarianLogic.dariMap(mapOf("kodeDokumen" to "PUSAT")))
        assertNull(RekapHarianLogic.dariMap(mapOf("tanggal" to "2026-10-30")))
        assertNull(RekapHarianLogic.dariMap(mapOf("tanggal" to 20261030L, "kodeDokumen" to "PUSAT")))
    }

    @Test
    fun `dariMap melewati elemen rincian yang rusak tanpa gagal total`() {
        val m: Map<String, Any?> = mapOf(
            "tanggal" to "2026-10-30",
            "kodeDokumen" to "PUSAT",
            "metodePembayaran" to listOf(
                mapOf("metode" to "TUNAI", "total" to 5000.0, "jumlahTransaksi" to 2L),
                mapOf("total" to 1.0),          // tanpa nama metode -> dilewati
                "bukan map"                      // bukan Map -> dilewati
            )
        )
        val hasil = RekapHarianLogic.dariMap(m)!!
        assertEquals(listOf(RekapMetode("TUNAI", 5000.0, 2)), hasil.metodePembayaran)
    }

    // ---- validasi kode toko ----

    @Test
    fun `validasi kode toko`() {
        assertNull(RekapHarianLogic.validasiTokoId("abcDEF123456"))
        assertNull(RekapHarianLogic.validasiTokoId("  abcDEF123456  "))
        assertNotNull(RekapHarianLogic.validasiTokoId(""))
        assertNotNull(RekapHarianLogic.validasiTokoId("abc"))          // terlalu pendek
        assertNotNull(RekapHarianLogic.validasiTokoId("abc def 1234")) // spasi
        assertNotNull(RekapHarianLogic.validasiTokoId("abc/def/1234")) // garis miring
    }

    // ---- agregat ----

    @Test
    fun `total gabungan semua cabang`() {
        val daftar = listOf(rekap(kode = "A", total = 100.0, transaksi = 1), rekap(kode = "B", total = 250.0, transaksi = 4))
        assertEquals(350.0, RekapHarianLogic.totalPenjualan(daftar), 0.0)
        assertEquals(5, RekapHarianLogic.totalTransaksi(daftar))
        assertEquals(0.0, RekapHarianLogic.totalPenjualan(emptyList()), 0.0)
    }
}
