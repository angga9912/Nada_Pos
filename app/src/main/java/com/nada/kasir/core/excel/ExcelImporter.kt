package com.nada.kasir.core.excel

import android.content.Context
import android.net.Uri
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream

data class ImportProdukResult(
    val berhasil: List<ProdukRowValidationResult.Valid>,
    val gagal: List<Pair<Int, String>>, // nomor baris (mulai dari 2) + alasan
    val formatSalah: Boolean = false
)

/**
 * Import DATA_PRODUK dari file .xlsx (poin 6 & 15).
 * Hanya membaca & memvalidasi di sini - penyimpanan ke Room dilakukan oleh
 * caller (ViewModel/Repository) di dalam satu DB transaction agar konsisten.
 */
class ExcelImporter(private val context: Context) {

    fun bacaDanValidasiProduk(uri: Uri): ImportProdukResult {
        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: return ImportProdukResult(emptyList(), emptyList(), formatSalah = true)

        return inputStream.use { stream ->
            try {
                val workbook = WorkbookFactory.create(stream)
                val sheet = workbook.getSheet("DATA_PRODUK") ?: workbook.getSheetAt(0)
                val headerRow = sheet.getRow(0)
                    ?: return ImportProdukResult(emptyList(), emptyList(), formatSalah = true)

                val header = (0 until headerRow.lastCellNum).map { i -> teksSel(headerRow, i) }
                if (!ProdukRowValidator.headerValid(header)) {
                    workbook.close()
                    return ImportProdukResult(emptyList(), emptyList(), formatSalah = true)
                }

                val berhasil = mutableListOf<ProdukRowValidationResult.Valid>()
                val gagal = mutableListOf<Pair<Int, String>>()

                for (r in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(r) ?: continue
                    if (isRowKosong(row)) continue

                    val input = ProdukRowInput(
                        kodeProduk = teksSel(row, 0), barcode = teksSel(row, 1), nama = teksSel(row, 2),
                        hargaBeliText = teksSel(row, 5), hargaJualText = teksSel(row, 6), stokText = teksSel(row, 7)
                    )
                    when (val hasil = ProdukRowValidator.validasi(input)) {
                        is ProdukRowValidationResult.Valid -> berhasil.add(hasil)
                        is ProdukRowValidationResult.Invalid -> gagal.add((r + 1) to hasil.alasan)
                    }
                }
                workbook.close()
                ImportProdukResult(berhasil, gagal)
            } catch (e: Exception) {
                ImportProdukResult(emptyList(), emptyList(), formatSalah = true)
            }
        }
    }

    private fun isRowKosong(row: Row): Boolean =
        (0 until row.lastCellNum.coerceAtLeast(0)).all { teksSel(row, it).isBlank() }

    private fun teksSel(row: Row, index: Int): String {
        val cell = row.getCell(index) ?: return ""
        return try {
            when (cell.cellType) {
                org.apache.poi.ss.usermodel.CellType.NUMERIC -> {
                    val d = cell.numericCellValue
                    if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
                }
                else -> cell.toString().trim()
            }
        } catch (e: Exception) {
            ""
        }
    }
}
