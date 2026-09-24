package com.nada.kasir.core.data.repository

import androidx.room.withTransaction
import com.nada.kasir.core.data.local.AppDatabase
import com.nada.kasir.core.data.local.dao.ProductDao
import com.nada.kasir.core.data.local.dao.StockMovementDao
import com.nada.kasir.core.data.local.dao.TransactionDao
import com.nada.kasir.core.data.local.entity.*
import com.nada.kasir.core.domain.logic.PembayaranCalculator
import com.nada.kasir.core.domain.model.KeranjangItem
import com.nada.kasir.core.util.AppError
import com.nada.kasir.core.util.NomorAntrianGenerator
import com.nada.kasir.core.util.NomorTransaksiGenerator
import com.nada.kasir.core.util.Result
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val appDatabase: AppDatabase,
    private val transactionDao: TransactionDao,
    private val productDao: ProductDao,
    private val stockMovementDao: StockMovementDao,
    private val nomorTransaksiGenerator: NomorTransaksiGenerator,
    private val nomorAntrianGenerator: NomorAntrianGenerator
) {

    /**
     * Menyimpan transaksi kasir. INI ADALAH BAGIAN PALING KRITIS (poin 19):
     * insert transaksi + item + payment + pengurangan stok + catat mutasi stok
     * HARUS atomik. Kalau salah satu gagal, semua di-rollback oleh Room
     * lewat appDatabase.withTransaction { ... }, sehingga tidak mungkin terjadi
     * "transaksi tersimpan tapi stok tidak berkurang" atau sebaliknya.
     */
    suspend fun simpanTransaksiKasir(
        userId: Long,
        items: List<KeranjangItem>,
        diskonTotal: Double,
        metode: MetodePembayaran,
        jumlahDiterima: Double,
        namaPembeli: String? = null,
        catatanMetode: String? = null
    ): Result<Long> {
        val subtotal = items.sumOf { it.harga * it.qty }
        val total = subtotal - diskonTotal

        if (metode == MetodePembayaran.TUNAI && !PembayaranCalculator.cukup(jumlahDiterima, total)) {
            return Result.Failure(AppError.PembayaranKurang)
        }

        // Validasi stok sebelum masuk transaction block (fail fast, pesan jelas per produk)
        for (item in items) {
            val stokSaatIni = productDao.getStok(item.productId)
            if (stokSaatIni < item.qty) {
                return Result.Failure(AppError.StokTidakCukup)
            }
        }

        val sekarang = System.currentTimeMillis()

        return try {
            val transactionId = appDatabase.withTransaction {
                val noTransaksi = nomorTransaksiGenerator.generate()
                val nomorAntrian = nomorAntrianGenerator.generate()

                val trxId = transactionDao.insertTransaction(
                    TransactionEntity(
                        noTransaksi = noTransaksi,
                        nomorAntrian = nomorAntrian,
                        namaPembeli = namaPembeli?.trim()?.ifBlank { null },
                        tanggalWaktu = sekarang,
                        userId = userId,
                        subtotal = subtotal,
                        diskon = diskonTotal,
                        total = total,
                        status = TransactionStatus.COMPLETED
                    )
                )

                val itemEntities = items.map {
                    TransactionItemEntity(
                        transactionId = trxId,
                        productId = it.productId,
                        namaProdukSnapshot = it.nama,
                        qty = it.qty,
                        harga = it.harga,
                        diskon = it.diskonItem,
                        subtotal = (it.harga * it.qty) - it.diskonItem
                    )
                }
                transactionDao.insertItems(itemEntities)

                val kembalian = if (metode == MetodePembayaran.TUNAI) PembayaranCalculator.hitungKembalian(jumlahDiterima, total) else 0.0
                transactionDao.insertPayment(
                    PaymentEntity(
                        transactionId = trxId,
                        metode = metode,
                        jumlahDiterima = jumlahDiterima,
                        kembalian = kembalian,
                        catatanMetode = catatanMetode?.trim()?.ifBlank { null }
                    )
                )

                // Kurangi stok + catat mutasi keluar untuk tiap item
                items.forEach { item ->
                    productDao.decreaseStock(item.productId, item.qty)
                    stockMovementDao.insert(
                        StockMovementEntity(
                            productId = item.productId,
                            tipe = TipeMutasiStok.KELUAR,
                            qty = item.qty,
                            referensiTransaksiId = trxId,
                            tanggalWaktu = sekarang,
                            keterangan = "Penjualan Kasir No: $noTransaksi"
                        )
                    )
                }

                trxId
            }
            Result.Success(transactionId)
        } catch (e: Exception) {
            // Karena dibungkus withTransaction, jika sampai sini terjadi exception,
            // seluruh perubahan sudah di-rollback otomatis oleh Room.
            Result.Failure(AppError.TransaksiGagalDisimpan)
        }
    }

    /**
     * Pembatalan transaksi (poin 13): status -> CANCELLED, stok dikembalikan,
     * baris asli TIDAK dihapus (audit trail tetap ada).
     */
    suspend fun batalkanTransaksi(transactionId: Long): Result<Unit> {
        return try {
            appDatabase.withTransaction {
                val trx = transactionDao.findById(transactionId)
                    ?: return@withTransaction Result.Failure(AppError.Lainnya("Transaksi tidak ditemukan."))

                if (trx.status == TransactionStatus.CANCELLED) {
                    return@withTransaction Result.Failure(AppError.Lainnya("Transaksi ini sudah dibatalkan sebelumnya."))
                }

                val items = transactionDao.getItems(transactionId)
                val waktuBatal = System.currentTimeMillis()
                items.forEach { item ->
                    productDao.increaseStock(item.productId, item.qty)
                    stockMovementDao.insert(
                        StockMovementEntity(
                            productId = item.productId,
                            tipe = TipeMutasiStok.MASUK,
                            qty = item.qty,
                            referensiTransaksiId = transactionId,
                            tanggalWaktu = waktuBatal,
                            keterangan = "Pembatalan Transaksi ${trx.noTransaksi}"
                        )
                    )
                }
                transactionDao.updateStatus(transactionId, TransactionStatus.CANCELLED)
                Result.Success(Unit)
            }
        } catch (e: Exception) {
            Result.Failure(AppError.TransaksiGagalDisimpan)
        }
    }

    fun observeRiwayat(query: String, startMillis: Long, endMillis: Long): Flow<List<TransactionEntity>> =
        transactionDao.observeRiwayat(query, startMillis, endMillis)

    fun observeTotalPenjualanHariIni(startMillis: Long, endMillis: Long): Flow<Double> =
        transactionDao.observeTotalPenjualan(startMillis, endMillis)

    fun observeJumlahTransaksiHariIni(startMillis: Long, endMillis: Long): Flow<Int> =
        transactionDao.observeJumlahTransaksi(startMillis, endMillis)

    suspend fun getDetail(transactionId: Long): Triple<TransactionEntity?, List<TransactionItemEntity>, PaymentEntity?> {
        val trx = transactionDao.findById(transactionId)
        val items = transactionDao.getItems(transactionId)
        val payment = transactionDao.getPayment(transactionId)
        return Triple(trx, items, payment)
    }

    /** Untuk section "Transaksi Terbaru" di Dashboard (redesign UI, tidak mengubah data). */
    suspend fun getTransaksiTerbaruDenganItem(limit: Int = 5): List<Pair<TransactionEntity, List<TransactionItemEntity>>> {
        val transaksi = transactionDao.getTransaksiTerbaru(limit)
        return transaksi.map { it to transactionDao.getItems(it.id) }
    }

    // === Untuk header "wallet style" Dashboard versi Pro ===
    fun observeTotalOmzetSemuaWaktu(): Flow<Double> = transactionDao.observeTotalOmzetSemuaWaktu()

    fun observeTotalQtyTerjual(startMillis: Long, endMillis: Long): Flow<Int> =
        transactionDao.observeTotalQtyTerjual(startMillis, endMillis)

    suspend fun getProdukPopuler(start: Long, end: Long, limit: Int = 5) =
        transactionDao.getProdukTerlaris(start, end, limit)
}
