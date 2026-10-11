package com.nada.kasir.core.paket

import com.nada.kasir.core.data.local.entity.MetodePembayaran
import com.nada.kasir.core.data.local.entity.PaymentEntity
import com.nada.kasir.core.data.local.entity.StoreEntity
import com.nada.kasir.core.data.local.entity.TransactionEntity
import com.nada.kasir.core.data.local.entity.TransactionItemEntity
import com.nada.kasir.core.data.local.entity.TransactionStatus
import com.nada.kasir.core.printer.StrukFormatter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AturanBrandingTest {

    private val fileLogo: File = File.createTempFile("logo_toko_uji", ".png").apply { writeBytes(byteArrayOf(1, 2, 3)) }

    @After
    fun bersihkan() { fileLogo.delete() }

    private fun toko() = StoreEntity(
        nama = "TOKO MAKMUR JAYA", logoPath = fileLogo.absolutePath, tampilkanLogoStruk = true, ukuranKertas = "58mm"
    )

    private val transaksi = TransactionEntity(
        id = 1, noTransaksi = "INV-20261001-0001", tanggalWaktu = 1_700_000_000_000L,
        userId = 1, subtotal = 7000.0, diskon = 0.0, total = 7000.0, status = TransactionStatus.COMPLETED
    )
    private val items = listOf(
        TransactionItemEntity(transactionId = 1, productId = 1, namaProdukSnapshot = "Indomie Goreng", qty = 2, harga = 3500.0, subtotal = 7000.0)
    )
    private val payment = PaymentEntity(transactionId = 1, metode = MetodePembayaran.TUNAI, jumlahDiterima = 10000.0, kembalian = 3000.0)

    private fun preview(store: StoreEntity) = StrukFormatter.buatStrukPreviewText(store, transaksi, items, payment)

    @Test
    fun `basic: logo dan toggle logo dimatikan tetapi data toko lain utuh`() {
        val hasil = toko().sesuaiPaket(PaketAplikasi.BASIC)
        assertEquals(null, hasil.logoPath)
        assertFalse(hasil.tampilkanLogoStruk)
        assertEquals("TOKO MAKMUR JAYA", hasil.nama)
        assertEquals("58mm", hasil.ukuranKertas)
    }

    @Test
    fun `custom dan pro: toko tidak diubah sama sekali`() {
        val asli = toko()
        assertSame(asli, asli.sesuaiPaket(PaketAplikasi.CUSTOM))
        assertSame(asli, asli.sesuaiPaket(PaketAplikasi.PRO))
    }

    @Test
    fun `preview struk custom menampilkan logo`() {
        assertTrue(preview(toko().sesuaiPaket(PaketAplikasi.CUSTOM)).contains("[GAMBAR LOGO TOKO]"))
    }

    @Test
    fun `preview struk basic tidak menampilkan logo dan hanya mencetak nama toko biasa`() {
        val teks = preview(toko().sesuaiPaket(PaketAplikasi.BASIC))
        assertFalse(teks.contains("[GAMBAR LOGO TOKO]"))
        assertFalse("nama toko tidak boleh berkurung (penanda tempat logo)", teks.contains("[ TOKO MAKMUR JAYA ]"))
        assertTrue(teks.contains("TOKO MAKMUR JAYA"))
    }

    @Test
    fun `data logo di toko asli tidak berubah sehingga muncul lagi saat paket naik`() {
        val asli = toko()
        asli.sesuaiPaket(PaketAplikasi.BASIC)
        assertEquals(fileLogo.absolutePath, asli.logoPath)
        assertTrue(asli.tampilkanLogoStruk)
    }
}
