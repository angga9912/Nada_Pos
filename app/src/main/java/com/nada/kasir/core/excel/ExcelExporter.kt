package com.nada.kasir.core.excel

import android.content.Context
import com.nada.kasir.core.data.local.entity.*
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Export produk/penjualan/stok ke .xlsx sesuai kolom yang ditentukan (poin 15).
 * Excel HANYA untuk laporan/backup pihak luar - bukan database utama (poin 15).
 */
class ExcelExporter(private val context: Context) {

    private fun folderExport(): File {
        val dir = File(context.getExternalFilesDir(null), "export")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun buatHeader(sheet: org.apache.poi.ss.usermodel.Sheet, kolom: List<String>) {
        val row = sheet.createRow(0)
        kolom.forEachIndexed { i, judul -> row.createCell(i).setCellValue(judul) }
    }

    private fun isi(cell: Cell, value: Any?) {
        when (value) {
            null -> cell.setCellValue("")
            is String -> cell.setCellValue(value)
            is Double -> cell.setCellValue(value)
            is Int -> cell.setCellValue(value.toDouble())
            is Boolean -> cell.setCellValue(if (value) "Ya" else "Tidak")
            else -> cell.setCellValue(value.toString())
        }
    }

    fun exportProduk(products: List<ProductEntity>, namaKategori: (Long?) -> String): File {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("DATA_PRODUK")
        val kolom = listOf("Kode Produk", "Barcode", "Nama Produk", "Kategori", "Satuan", "Harga Beli", "Harga Jual", "Stok", "Stok Minimum")
        buatHeader(sheet, kolom)
        products.forEachIndexed { idx, p ->
            val row = sheet.createRow(idx + 1)
            val nilai = listOf(p.kodeProduk, p.barcode ?: "", p.nama, namaKategori(p.categoryId), p.satuan, p.hargaBeli, p.hargaJual, p.stok, p.stokMinimum)
            nilai.forEachIndexed { i, v -> isi(row.createCell(i), v) }
        }
        return simpan(wb, "DATA_PRODUK")
    }

    fun exportPenjualan(
        transactions: List<TransactionEntity>,
        items: List<TransactionItemEntity>,
        payments: Map<Long, PaymentEntity>,
        products: Map<Long, ProductEntity>,
        namaKasir: (Long) -> String
    ): File {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("PENJUALAN")
        val kolom = listOf("Tanggal", "Jam", "No Transaksi", "Kode Produk", "Barcode", "Nama Produk", "Qty", "Harga", "Diskon", "Subtotal", "Total", "Kasir", "Metode Pembayaran")
        buatHeader(sheet, kolom)
        val sdfTgl = SimpleDateFormat("dd/MM/yyyy", Locale("id", "ID"))
        val sdfJam = SimpleDateFormat("HH:mm", Locale("id", "ID"))

        var rowIndex = 1
        val itemsByTransaction = items.groupBy { it.transactionId }
        transactions.forEach { trx ->
            val tanggal = Date(trx.tanggalWaktu)
            val payment = payments[trx.id]
            itemsByTransaction[trx.id]?.forEach { item ->
                val produk = products[item.productId]
                val row = sheet.createRow(rowIndex++)
                val nilai = listOf(
                    sdfTgl.format(tanggal), sdfJam.format(tanggal), trx.noTransaksi,
                    produk?.kodeProduk ?: "", produk?.barcode ?: "", item.namaProdukSnapshot,
                    item.qty, item.harga, item.diskon, item.subtotal, trx.total,
                    namaKasir(trx.userId), payment?.metode?.name ?: ""
                )
                nilai.forEachIndexed { i, v -> isi(row.createCell(i), v) }
            }
        }
        return simpan(wb, "PENJUALAN")
    }

    fun exportStokMasuk(movements: List<StockMovementEntity>, namaProduk: (Long) -> String): File =
        exportMutasiStok(movements.filter { it.tipe == TipeMutasiStok.MASUK }, namaProduk, tipeMasuk = true)

    fun exportStokKeluar(movements: List<StockMovementEntity>, namaProduk: (Long) -> String): File =
        exportMutasiStok(movements.filter { it.tipe != TipeMutasiStok.MASUK }, namaProduk, tipeMasuk = false)

    /** Export ringkasan laporan (poin 14). */
    fun exportLaporan(
        namaSheet: String,
        totalPenjualan: Double, jumlahTransaksi: Int, produkTerjual: Int,
        totalDiskon: Double, estimasiKeuntungan: Double
    ): File {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet(namaSheet)
        buatHeader(sheet, listOf("Keterangan", "Nilai"))
        val baris = listOf(
            "Total Penjualan" to totalPenjualan, "Jumlah Transaksi" to jumlahTransaksi.toDouble(),
            "Produk Terjual" to produkTerjual.toDouble(), "Total Diskon" to totalDiskon,
            "Estimasi Keuntungan" to estimasiKeuntungan
        )
        baris.forEachIndexed { idx, (label, nilai) ->
            val row = sheet.createRow(idx + 1)
            isi(row.createCell(0), label); isi(row.createCell(1), nilai)
        }
        return simpan(wb, namaSheet)
    }

    private fun exportMutasiStok(movements: List<StockMovementEntity>, namaProduk: (Long) -> String, tipeMasuk: Boolean): File {
        val wb = XSSFWorkbook()
        val namaSheet = if (tipeMasuk) "STOK_MASUK" else "STOK_KELUAR"
        val sheet = wb.createSheet(namaSheet)
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID"))
        if (tipeMasuk) {
            buatHeader(sheet, listOf("Tanggal", "Kode Produk", "Nama Produk", "Qty", "Harga Beli", "Supplier", "Keterangan"))
            movements.forEachIndexed { idx, m ->
                val row = sheet.createRow(idx + 1)
                listOf(sdf.format(Date(m.tanggalWaktu)), "", namaProduk(m.productId), m.qty, "", m.supplier ?: "", m.keterangan ?: "")
                    .forEachIndexed { i, v -> isi(row.createCell(i), v) }
            }
        } else {
            buatHeader(sheet, listOf("Tanggal", "Kode Produk", "Nama Produk", "Qty", "Alasan", "Keterangan"))
            movements.forEachIndexed { idx, m ->
                val row = sheet.createRow(idx + 1)
                listOf(sdf.format(Date(m.tanggalWaktu)), "", namaProduk(m.productId), m.qty, m.tipe.name, m.keterangan ?: "")
                    .forEachIndexed { i, v -> isi(row.createCell(i), v) }
            }
        }
        return simpan(wb, namaSheet)
    }

    private fun simpan(wb: XSSFWorkbook, prefix: String): File {
        val namaFile = "${prefix}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale("id","ID")).format(Date())}.xlsx"
        val file = File(folderExport(), namaFile)
        FileOutputStream(file).use { wb.write(it) }
        wb.close()
        return file
    }
}
