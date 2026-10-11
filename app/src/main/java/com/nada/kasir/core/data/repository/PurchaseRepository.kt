package com.nada.kasir.core.data.repository

import androidx.room.withTransaction
import com.nada.kasir.core.data.local.AppDatabase
import com.nada.kasir.core.data.local.dao.HutangPiutangDao
import com.nada.kasir.core.data.local.dao.ProductDao
import com.nada.kasir.core.data.local.dao.PurchaseDao
import com.nada.kasir.core.data.local.dao.StockMovementDao
import com.nada.kasir.core.data.local.dao.SupplierDao
import com.nada.kasir.core.data.local.entity.HutangPiutangEntity
import com.nada.kasir.core.data.local.entity.PurchaseEntity
import com.nada.kasir.core.data.local.entity.PurchaseItemEntity
import com.nada.kasir.core.data.local.entity.PurchaseStatus
import com.nada.kasir.core.data.local.entity.StockMovementEntity
import com.nada.kasir.core.data.local.entity.TipeMutasiStok
import com.nada.kasir.core.domain.logic.HutangPiutangLogic
import com.nada.kasir.core.domain.logic.PembelianLogic
import com.nada.kasir.core.util.AppError
import com.nada.kasir.core.util.CurrencyFormatter
import com.nada.kasir.core.util.Result
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Masukan untuk satu pembelian ke supplier. */
data class PembelianInput(
    val supplierId: Long,
    val items: List<PembelianLogic.ItemInput>,
    val diskonSupplier: Double = 0.0,
    /** Dibayar sekarang (uang muka atau lunas). Sisanya menjadi hutang ke supplier. */
    val bayarSekarang: Double = 0.0,
    val jatuhTempo: Long? = null,
    /** true = barang sudah di tangan: stok langsung bertambah. false = pesan dulu, terima belakangan. */
    val terimaSekarang: Boolean = true,
    /** Saat barang diterima, harga beli produk diperbarui ke harga pembelian ini. */
    val perbaruiHargaBeli: Boolean = true,
    val keterangan: String? = null,
    val outletId: Long? = null
)

/**
 * Pembelian ke supplier (Phase 5 tahap 2).
 *
 * Semua perubahan yang menyentuh lebih dari satu tabel dibungkus satu transaksi database. Penting: nilai
 * `Result.Failure` yang dikembalikan dari dalam blok transaksi TIDAK membatalkan penulisan sebelumnya,
 * jadi setiap pengecekan di sini dilakukan SEBELUM penulisan pertama.
 */
@Singleton
class PurchaseRepository @Inject constructor(
    private val appDatabase: AppDatabase,
    private val purchaseDao: PurchaseDao,
    private val productDao: ProductDao,
    private val stockMovementDao: StockMovementDao,
    private val supplierDao: SupplierDao,
    private val hutangPiutangDao: HutangPiutangDao
) {
    fun observeAll(): Flow<List<PurchaseEntity>> = purchaseDao.observeAll()
    fun observeBySupplier(supplierId: Long): Flow<List<PurchaseEntity>> = purchaseDao.observeBySupplier(supplierId)
    fun observeByStatus(status: String): Flow<List<PurchaseEntity>> = purchaseDao.observeByStatus(status)
    fun observeItems(purchaseId: Long): Flow<List<PurchaseItemEntity>> = purchaseDao.observeItems(purchaseId)
    suspend fun findById(id: Long): PurchaseEntity? = purchaseDao.findById(id)

    /**
     * Simpan pembelian. Atomik: pembelian + item, (bila barang diterima) stok + mutasi stok + harga beli,
     * dan (bila ada sisa) catatan hutang ke supplier tersimpan bersamaan atau tidak sama sekali.
     * Barang yang tidak ditemukan membatalkan seluruh pembelian (tidak ada yang dilewati diam-diam).
     */
    suspend fun simpanPembelian(input: PembelianInput): Result<Long> {
        PembelianLogic.validasiPembelian(input.items, input.diskonSupplier, input.bayarSekarang)?.let {
            return Result.Failure(AppError.Lainnya(it))
        }
        return try {
            appDatabase.withTransaction<Result<Long>> {
                val supplier = supplierDao.findById(input.supplierId)
                    ?: return@withTransaction Result.Failure(AppError.Lainnya("Supplier tidak ditemukan."))
                if (!supplier.aktif) {
                    return@withTransaction Result.Failure(AppError.Lainnya("Supplier ${supplier.nama} nonaktif. Aktifkan dulu di menu Supplier."))
                }

                val namaProduk = HashMap<Long, String>()
                for (item in input.items) {
                    val produk = productDao.findById(item.productId)
                        ?: return@withTransaction Result.Failure(AppError.Lainnya("Ada barang yang tidak ditemukan. Muat ulang daftar barang."))
                    if (produk.deletedAt != null) {
                        return@withTransaction Result.Failure(AppError.Lainnya("Barang ${produk.nama} sudah dihapus dari daftar produk."))
                    }
                    namaProduk[item.productId] = produk.nama
                }

                val ringkasan = PembelianLogic.hitungRingkasan(input.items, input.diskonSupplier)
                val sisa = PembelianLogic.sisaHutang(ringkasan.total, input.bayarSekarang)
                val sekarang = System.currentTimeMillis()
                val awalan = PembelianLogic.awalanNomor(sekarang)
                val noPembelian = PembelianLogic.formatNomor(
                    awalan, PembelianLogic.urutBerikutnya(purchaseDao.getLatestNoPembelian(awalan), awalan)
                )

                val purchaseId = purchaseDao.insertPurchase(
                    PurchaseEntity(
                        noPembelian = noPembelian,
                        supplierId = supplier.id,
                        outletId = input.outletId,
                        tanggalPembelian = sekarang,
                        tanggalTerima = if (input.terimaSekarang) sekarang else null,
                        subtotal = ringkasan.subtotal,
                        diskonSupplier = ringkasan.diskonSupplier,
                        totalBayar = ringkasan.total,
                        hutangSisa = sisa,
                        status = if (input.terimaSekarang) PurchaseStatus.RECEIVED else PurchaseStatus.PENDING,
                        keterangan = input.keterangan?.trim()?.ifBlank { null },
                        createdAt = sekarang,
                        updatedAt = sekarang
                    )
                )
                val itemEntities = input.items.map {
                    PurchaseItemEntity(
                        purchaseId = purchaseId,
                        productId = it.productId,
                        namaProdukSnapshot = namaProduk.getValue(it.productId),
                        qty = it.qty,
                        hargaBeli = it.hargaBeli,
                        diskon = it.diskon,
                        subtotal = PembelianLogic.subtotalItem(it)
                    )
                }
                purchaseDao.insertItems(itemEntities)

                if (input.terimaSekarang) {
                    terimaItem(itemEntities, purchaseId, noPembelian, supplier.id, input.outletId, input.perbaruiHargaBeli, sekarang)
                }

                if (sisa > 0.0) {
                    val keterangan = buildString {
                        append("Pembelian $noPembelian")
                        if (input.bayarSekarang > 0.0) append(" • Uang muka ${CurrencyFormatter.format(input.bayarSekarang)}")
                    }
                    hutangPiutangDao.insert(
                        HutangPiutangEntity(
                            jenis = HutangPiutangLogic.JENIS_HUTANG,
                            pelaku = supplier.nama,
                            nomorReferensi = noPembelian,
                            jumlah = ringkasan.total,
                            sisa = sisa,
                            status = HutangPiutangLogic.STATUS_AKTIF,
                            tanggalTransaksi = sekarang,
                            tanggalJatuhTempo = input.jatuhTempo,
                            keterangan = keterangan,
                            createdAt = sekarang,
                            updatedAt = sekarang
                        )
                    )
                }
                Result.Success(purchaseId)
            }
        } catch (e: Exception) {
            Result.Failure(AppError.Lainnya("Pembelian gagal disimpan. Silakan coba lagi."))
        }
    }

    /** Terima barang dari pembelian berstatus menunggu: stok bertambah, mutasi tercatat, status jadi Diterima. */
    suspend fun terimaBarang(purchaseId: Long, perbaruiHargaBeli: Boolean = true): Result<Unit> {
        return try {
            appDatabase.withTransaction<Result<Unit>> {
                val purchase = purchaseDao.findById(purchaseId)
                    ?: return@withTransaction Result.Failure(AppError.Lainnya("Pembelian tidak ditemukan."))
                if (purchase.status != PurchaseStatus.PENDING) {
                    return@withTransaction Result.Failure(AppError.Lainnya("Hanya pembelian yang masih menunggu barang yang bisa diterima."))
                }
                val items = purchaseDao.getItems(purchaseId)
                val sekarang = System.currentTimeMillis()
                terimaItem(items, purchaseId, purchase.noPembelian, purchase.supplierId, purchase.outletId, perbaruiHargaBeli, sekarang)
                purchaseDao.updatePurchase(
                    purchase.copy(status = PurchaseStatus.RECEIVED, tanggalTerima = sekarang, updatedAt = sekarang)
                )
                Result.Success(Unit)
            }
        } catch (e: Exception) {
            Result.Failure(AppError.Lainnya("Gagal menerima barang. Silakan coba lagi."))
        }
    }

    /**
     * Batalkan pembelian. Ditolak bila:
     *  - hutangnya sudah pernah dicicil (uang sudah keluar, tidak bisa dibatalkan begitu saja), atau
     *  - barang sudah diterima tetapi stok sekarang lebih kecil dari yang diterima (sebagian sudah terjual).
     * Semua pengecekan dilakukan sebelum ada yang ditulis. Baris tidak dihapus.
     */
    suspend fun batalkan(purchaseId: Long): Result<Unit> {
        return try {
            appDatabase.withTransaction<Result<Unit>> {
                val purchase = purchaseDao.findById(purchaseId)
                    ?: return@withTransaction Result.Failure(AppError.Lainnya("Pembelian tidak ditemukan."))
                if (purchase.status == PurchaseStatus.CANCELLED) {
                    return@withTransaction Result.Failure(AppError.Lainnya("Pembelian ini sudah dibatalkan."))
                }
                val hutang = hutangPiutangDao.findByReferensi(purchase.noPembelian, HutangPiutangLogic.JENIS_HUTANG)
                if (hutang != null && hutangPiutangDao.countPembayaran(hutang.id) > 0) {
                    return@withTransaction Result.Failure(
                        AppError.Lainnya("Hutang pembelian ini sudah pernah dicicil, jadi tidak bisa dibatalkan.")
                    )
                }
                val items = purchaseDao.getItems(purchaseId)
                if (purchase.status == PurchaseStatus.RECEIVED) {
                    for (item in items) {
                        val stok = productDao.getStok(item.productId)
                        if (stok < item.qty) {
                            return@withTransaction Result.Failure(
                                AppError.Lainnya(
                                    "Stok ${item.namaProdukSnapshot} sekarang $stok, kurang dari ${item.qty} yang diterima " +
                                        "(sebagian sudah terjual). Pembelian tidak bisa dibatalkan."
                                )
                            )
                        }
                    }
                }

                val sekarang = System.currentTimeMillis()
                if (purchase.status == PurchaseStatus.RECEIVED) {
                    for (item in items) {
                        productDao.decreaseStock(item.productId, item.qty)
                        stockMovementDao.insert(
                            StockMovementEntity(
                                productId = item.productId,
                                tipe = TipeMutasiStok.KELUAR,
                                qty = item.qty,
                                referensiPembelianId = purchaseId,
                                supplierId = purchase.supplierId,
                                outletId = purchase.outletId,
                                keterangan = "Pembatalan pembelian ${purchase.noPembelian}",
                                tanggalWaktu = sekarang
                            )
                        )
                    }
                }
                if (hutang != null && hutang.status == HutangPiutangLogic.STATUS_AKTIF) {
                    hutangPiutangDao.updateStatusAndSisa(hutang.id, hutang.sisa, HutangPiutangLogic.STATUS_BATAL, sekarang)
                }
                purchaseDao.updatePurchase(
                    purchase.copy(status = PurchaseStatus.CANCELLED, hutangSisa = 0.0, updatedAt = sekarang)
                )
                Result.Success(Unit)
            }
        } catch (e: Exception) {
            Result.Failure(AppError.Lainnya("Gagal membatalkan pembelian. Silakan coba lagi."))
        }
    }

    /** Stok masuk + mutasi (+ harga beli terakhir) untuk setiap baris. Dipanggil dari dalam transaksi. */
    private suspend fun terimaItem(
        items: List<PurchaseItemEntity>,
        purchaseId: Long,
        noPembelian: String,
        supplierId: Long,
        outletId: Long?,
        perbaruiHargaBeli: Boolean,
        sekarang: Long
    ) {
        for (item in items) {
            productDao.increaseStock(item.productId, item.qty)
            stockMovementDao.insert(
                StockMovementEntity(
                    productId = item.productId,
                    tipe = TipeMutasiStok.MASUK,
                    qty = item.qty,
                    referensiPembelianId = purchaseId,
                    supplierId = supplierId,
                    outletId = outletId,
                    keterangan = "Penerimaan barang pembelian $noPembelian",
                    tanggalWaktu = sekarang
                )
            )
            if (perbaruiHargaBeli && item.qty > 0) {
                // Harga beli per unit setelah diskon baris.
                productDao.updateHargaBeli(item.productId, item.subtotal / item.qty)
            }
        }
    }
}
