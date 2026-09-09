package com.nada.kasir.core.printer

import com.nada.kasir.core.data.local.entity.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrukFormatterPreviewTest {

    private val store = StoreEntity(
        nama = "TOKO MAKMUR JAYA", alamat = "Jl. Raya Bekasi No. 123", whatsapp = "08123456789",
        footerStruk = "Terima kasih telah berbelanja.", ukuranKertas = "58mm"
    )
    private val transaksi = TransactionEntity(
        id = 1, noTransaksi = "INV-20260904-0001", tanggalWaktu = System.currentTimeMillis(),
        userId = 1, subtotal = 13000.0, diskon = 0.0, total = 13000.0, status = TransactionStatus.COMPLETED
    )
    private val items = listOf(
        TransactionItemEntity(transactionId = 1, productId = 1, namaProdukSnapshot = "Indomie Goreng", qty = 2, harga = 3500.0, subtotal = 7000.0),
        TransactionItemEntity(transactionId = 1, productId = 2, namaProdukSnapshot = "Aqua 600ml", qty = 2, harga = 3000.0, subtotal = 6000.0)
    )
    private val payment = PaymentEntity(transactionId = 1, metode = MetodePembayaran.TUNAI, jumlahDiterima = 20000.0, kembalian = 7000.0)

    @Test
    fun `preview memuat nama toko, item, total, dan kembalian`() {
        val teks = StrukFormatter.buatStrukPreviewText(store, transaksi, items, payment)

        assertTrue(teks.contains("TOKO MAKMUR JAYA"))
        assertTrue(teks.contains("Indomie Goreng"))
        assertTrue(teks.contains("Aqua 600ml"))
        assertTrue(teks.contains("TOTAL"))
        assertTrue(teks.contains("KEMBALI"))
        assertTrue(teks.contains(transaksi.noTransaksi))
    }

    @Test
    fun `preview tidak pernah menampilkan harga beli`() {
        val teks = StrukFormatter.buatStrukPreviewText(store, transaksi, items, payment)
        // Memastikan tidak ada label harga beli tercetak, sesuai aturan poin 10
        assertFalse(teks.contains("Harga Beli", ignoreCase = true))
    }

    @Test
    fun `preview toko yang menyembunyikan alamat tidak menampilkan alamat`() {
        val storeTanpaAlamat = store.copy(tampilkanAlamatStruk = false)
        val teks = StrukFormatter.buatStrukPreviewText(storeTanpaAlamat, transaksi, items, payment)
        assertFalse(teks.contains(store.alamat))
    }

    @Test
    fun `preview menampilkan nomor antrian jika diisi`() {
        val transaksiDenganAntrian = transaksi.copy(nomorAntrian = 7)
        val teks = StrukFormatter.buatStrukPreviewText(store, transaksiDenganAntrian, items, payment)
        assertTrue(teks.contains("NO. ANTRIAN: 7"))
    }

    @Test
    fun `preview tidak menampilkan baris nomor antrian jika nol (belum di-generate)`() {
        val teks = StrukFormatter.buatStrukPreviewText(store, transaksi, items, payment) // nomorAntrian default 0
        assertFalse(teks.contains("NO. ANTRIAN"))
    }

    @Test
    fun `preview menampilkan nama pembeli jika diisi`() {
        val transaksiDenganPembeli = transaksi.copy(namaPembeli = "Budi Santoso")
        val teks = StrukFormatter.buatStrukPreviewText(store, transaksiDenganPembeli, items, payment)
        assertTrue(teks.contains("Pembeli: Budi Santoso"))
    }

    @Test
    fun `preview tidak menampilkan baris pembeli jika nama pembeli kosong`() {
        val teks = StrukFormatter.buatStrukPreviewText(store, transaksi, items, payment) // namaPembeli null
        assertFalse(teks.contains("Pembeli:"))
    }
}
