package com.nada.kasir.core.printer

import android.graphics.BitmapFactory
import com.nada.kasir.core.data.local.entity.PaymentEntity
import com.nada.kasir.core.data.local.entity.StoreEntity
import com.nada.kasir.core.data.local.entity.TransactionEntity
import com.nada.kasir.core.data.local.entity.TransactionItemEntity
import com.nada.kasir.core.util.CurrencyFormatter
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Menyusun struk (poin 9) menjadi perintah ESC/POS siap cetak, mengikuti
 * pengaturan custom toko (poin 10): tampil/sembunyi logo, alamat, WA, diskon,
 * dan TIDAK PERNAH menampilkan harga beli.
 */
object StrukFormatter {

    fun buatStruk(
        store: StoreEntity,
        transaction: TransactionEntity,
        items: List<TransactionItemEntity>,
        payment: PaymentEntity?
    ): ByteArray {
        val lebar = if (store.ukuranKertas == "80mm") 48 else 32
        val lebarDotsGambar = if (store.ukuranKertas == "80mm") 576 else 384 // resolusi umum printer thermal 203dpi
        val sdfTanggal = SimpleDateFormat("dd/MM/yyyy", Locale("id", "ID"))
        val sdfJam = SimpleDateFormat("HH:mm", Locale("id", "ID"))
        val tanggalTransaksi = Date(transaction.tanggalWaktu)

        val builder = EscPosBuilder().reset()

        // Header: logo asli (jika sudah diunggah di Pengaturan Toko) dicetak sebagai gambar,
        // fallback ke nama toko bertanda kurung kalau belum ada logo.
        builder.alignCenter()
        val logoBitmap = store.logoPath?.takeIf { it.isNotBlank() && File(it).exists() }
            ?.let { BitmapFactory.decodeFile(it) }
        if (store.tampilkanLogoStruk && logoBitmap != null) {
            builder.image(logoBitmap, lebarDotsGambar / 2) // logo dicetak setengah lebar kertas, tidak dominan
            builder.newLine()
            builder.bold(true).textLine(store.nama).bold(false)
        } else if (store.tampilkanLogoStruk) {
            builder.bold(true).textLine("[ ${store.nama} ]").bold(false)
        } else {
            builder.bold(true).textLine(store.nama).bold(false)
        }
        if (store.tampilkanAlamatStruk && store.alamat.isNotBlank()) builder.textLine(store.alamat)
        if (store.tampilkanWaStruk && store.whatsapp.isNotBlank()) builder.textLine("WA: ${store.whatsapp}")
        builder.garis(lebar)

        // Nomor antrian - dicetak besar & tebal agar mudah dipanggil (reset otomatis tiap hari)
        if (transaction.nomorAntrian > 0) {
            builder.alignCenter()
            builder.fontSizeBesar().bold(true)
            builder.textLine("NO. ANTRIAN: ${transaction.nomorAntrian}")
            builder.fontSizeNormal().bold(false)
            builder.garis(lebar)
        }

        // Info transaksi
        builder.alignLeft()
        builder.textLine("No: ${transaction.noTransaksi}")
        builder.textLine("Tanggal: ${sdfTanggal.format(tanggalTransaksi)}")
        builder.textLine("Jam: ${sdfJam.format(tanggalTransaksi)}")
        if (!transaction.namaPembeli.isNullOrBlank()) {
            builder.textLine("Pembeli: ${transaction.namaPembeli}")
        }
        builder.garis(lebar)

        // Daftar item
        items.forEach { item ->
            builder.textLine(item.namaProdukSnapshot)
            val kiri = "${item.qty} x ${CurrencyFormatter.format(item.harga).removePrefix(store.mataUang).trim()}"
            val kanan = CurrencyFormatter.format(item.subtotal).removePrefix(store.mataUang).trim()
            builder.baris2Kolom(kiri, kanan, lebar)
        }
        builder.garis(lebar)

        // Total, diskon, pembayaran, kembalian
        if (store.tampilkanDiskonStruk && transaction.diskon > 0) {
            builder.baris2Kolom("DISKON", CurrencyFormatter.format(transaction.diskon), lebar)
        }
        builder.bold(true)
        builder.baris2Kolom("TOTAL", CurrencyFormatter.format(transaction.total), lebar)
        builder.bold(false)
        payment?.let {
            builder.baris2Kolom(it.metode.name, CurrencyFormatter.format(it.jumlahDiterima), lebar)
            if (it.kembalian > 0) {
                builder.baris2Kolom("KEMBALI", CurrencyFormatter.format(it.kembalian), lebar)
            }
        }
        builder.garis(lebar)

        // Footer
        builder.alignCenter()
        if (store.footerStruk.isNotBlank()) builder.textLine(store.footerStruk)

        builder.feedAndCut()
        return builder.build()
    }

    /**
     * Versi PREVIEW (teks biasa, tanpa perintah ESC/POS) untuk ditampilkan ke
     * pengguna sebelum benar-benar mencetak. Layout dibuat semirip mungkin
     * dengan hasil cetak fisik agar tidak ada kejutan di kertas struk asli.
     */
    fun buatStrukPreviewText(
        store: StoreEntity,
        transaction: TransactionEntity,
        items: List<TransactionItemEntity>,
        payment: PaymentEntity?
    ): String {
        val lebar = if (store.ukuranKertas == "80mm") 48 else 32
        val sdfTanggal = SimpleDateFormat("dd/MM/yyyy", Locale("id", "ID"))
        val sdfJam = SimpleDateFormat("HH:mm", Locale("id", "ID"))
        val tanggalTransaksi = Date(transaction.tanggalWaktu)
        val sb = StringBuilder()

        fun tengah(teks: String) {
            val sisa = (lebar - teks.length).coerceAtLeast(0)
            sb.append(" ".repeat(sisa / 2)).append(teks).append('\n')
        }
        fun garis() { sb.append("-".repeat(lebar)).append('\n') }
        fun kiriKanan(kiri: String, kanan: String) {
            val sisa = (lebar - kiri.length - kanan.length).coerceAtLeast(1)
            sb.append(kiri).append(" ".repeat(sisa)).append(kanan).append('\n')
        }

        val punyaLogo = store.logoPath?.takeIf { it.isNotBlank() && java.io.File(it).exists() } != null
        if (store.tampilkanLogoStruk && punyaLogo) {
            tengah("[GAMBAR LOGO TOKO]")
            tengah(store.nama)
        } else if (store.tampilkanLogoStruk) {
            tengah("[ ${store.nama} ]")
        } else {
            tengah(store.nama)
        }
        if (store.tampilkanAlamatStruk && store.alamat.isNotBlank()) tengah(store.alamat)
        if (store.tampilkanWaStruk && store.whatsapp.isNotBlank()) tengah("WA: ${store.whatsapp}")
        garis()

        if (transaction.nomorAntrian > 0) {
            tengah("NO. ANTRIAN: ${transaction.nomorAntrian}")
            garis()
        }

        sb.append("No: ${transaction.noTransaksi}\n")
        sb.append("Tanggal: ${sdfTanggal.format(tanggalTransaksi)}\n")
        sb.append("Jam: ${sdfJam.format(tanggalTransaksi)}\n")
        if (!transaction.namaPembeli.isNullOrBlank()) {
            sb.append("Pembeli: ${transaction.namaPembeli}\n")
        }
        garis()

        items.forEach { item ->
            sb.append(item.namaProdukSnapshot).append('\n')
            val kiri = "${item.qty} x ${CurrencyFormatter.format(item.harga).removePrefix(store.mataUang).trim()}"
            val kanan = CurrencyFormatter.format(item.subtotal).removePrefix(store.mataUang).trim()
            kiriKanan(kiri, kanan)
        }
        garis()

        if (store.tampilkanDiskonStruk && transaction.diskon > 0) {
            kiriKanan("DISKON", CurrencyFormatter.format(transaction.diskon))
        }
        kiriKanan("TOTAL", CurrencyFormatter.format(transaction.total))
        payment?.let {
            kiriKanan(it.metode.name, CurrencyFormatter.format(it.jumlahDiterima))
            if (it.kembalian > 0) kiriKanan("KEMBALI", CurrencyFormatter.format(it.kembalian))
        }
        garis()

        if (store.footerStruk.isNotBlank()) tengah(store.footerStruk)

        return sb.toString()
    }
}
