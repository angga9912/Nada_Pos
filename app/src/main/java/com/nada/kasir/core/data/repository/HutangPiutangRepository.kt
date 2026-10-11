package com.nada.kasir.core.data.repository

import androidx.room.withTransaction
import com.nada.kasir.core.data.local.AppDatabase
import com.nada.kasir.core.data.local.dao.HutangPiutangDao
import com.nada.kasir.core.data.local.dao.PurchaseDao
import com.nada.kasir.core.data.local.dao.TransactionDao
import com.nada.kasir.core.data.local.entity.HutangPiutangEntity
import com.nada.kasir.core.data.local.entity.HutangPiutangPembayaranEntity
import com.nada.kasir.core.domain.logic.HutangPiutangLogic
import com.nada.kasir.core.util.AppError
import com.nada.kasir.core.util.Result
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hutang & Piutang (Phase 5). Semua perubahan yang menyentuh lebih dari satu tabel dibungkus
 * satu transaksi database, jadi pembayaran tidak mungkin tercatat tanpa sisa ikut berubah.
 */
@Singleton
class HutangPiutangRepository @Inject constructor(
    private val appDatabase: AppDatabase,
    private val dao: HutangPiutangDao,
    private val transactionDao: TransactionDao,
    private val purchaseDao: PurchaseDao
) {
    fun observeAll(): Flow<List<HutangPiutangEntity>> = dao.observeAll()

    fun observeByJenis(jenis: String): Flow<List<HutangPiutangEntity>> = dao.observeByJenis(jenis)

    fun observePembayaran(id: Long): Flow<List<HutangPiutangPembayaranEntity>> = dao.observePembayaran(id)

    suspend fun findById(id: Long): HutangPiutangEntity? = dao.findById(id)

    /** Catatan manual (di luar penjualan Kasir), mis. kasbon pelanggan atau utang ke warung lain. */
    suspend fun catatBaru(
        jenis: String,
        pelaku: String,
        jumlah: Double,
        jatuhTempo: Long?,
        keterangan: String?
    ): Result<Long> {
        HutangPiutangLogic.validasiCatatanBaru(jenis, pelaku, jumlah)?.let {
            return Result.Failure(AppError.Lainnya(it))
        }
        val sekarang = System.currentTimeMillis()
        return try {
            val id = dao.insert(
                HutangPiutangEntity(
                    jenis = jenis, pelaku = pelaku.trim(), jumlah = jumlah, sisa = jumlah,
                    status = HutangPiutangLogic.STATUS_AKTIF, tanggalTransaksi = sekarang,
                    tanggalJatuhTempo = jatuhTempo, keterangan = keterangan?.trim()?.ifBlank { null },
                    createdAt = sekarang, updatedAt = sekarang
                )
            )
            Result.Success(id)
        } catch (e: Exception) {
            Result.Failure(AppError.Lainnya("Catatan gagal disimpan. Silakan coba lagi."))
        }
    }

    /**
     * Ubah data catatan. Nama, jatuh tempo, dan keterangan selalu boleh diubah. Jumlah hanya boleh
     * diubah untuk catatan manual yang belum pernah dibayar (catatan dari Kasir mengikuti transaksinya).
     */
    suspend fun ubah(
        id: Long,
        pelaku: String,
        jatuhTempo: Long?,
        keterangan: String?,
        jumlahBaru: Double?
    ): Result<Unit> {
        if (pelaku.isBlank()) return Result.Failure(AppError.Lainnya("Nama pihak wajib diisi."))
        return try {
            appDatabase.withTransaction {
                val lama = dao.findById(id)
                    ?: return@withTransaction Result.Failure(AppError.Lainnya("Catatan tidak ditemukan."))
                if (lama.status == HutangPiutangLogic.STATUS_BATAL) {
                    return@withTransaction Result.Failure(AppError.Lainnya("Catatan yang sudah dibatalkan tidak bisa diubah."))
                }
                var jumlah = lama.jumlah
                var sisa = lama.sisa
                var status = lama.status
                if (jumlahBaru != null && jumlahBaru != lama.jumlah) {
                    if (lama.nomorReferensi != null) {
                        return@withTransaction Result.Failure(AppError.Lainnya("Jumlah catatan dari penjualan tidak bisa diubah."))
                    }
                    if (dao.countPembayaran(id) > 0) {
                        return@withTransaction Result.Failure(AppError.Lainnya("Jumlah tidak bisa diubah karena sudah ada pembayaran."))
                    }
                    if (jumlahBaru.isNaN() || jumlahBaru <= 0.0) {
                        return@withTransaction Result.Failure(AppError.Lainnya("Jumlah harus lebih dari 0."))
                    }
                    jumlah = jumlahBaru; sisa = jumlahBaru; status = HutangPiutangLogic.STATUS_AKTIF
                }
                dao.update(
                    lama.copy(
                        pelaku = pelaku.trim(), jumlah = jumlah, sisa = sisa, status = status,
                        tanggalJatuhTempo = jatuhTempo, keterangan = keterangan?.trim()?.ifBlank { null },
                        updatedAt = System.currentTimeMillis()
                    )
                )
                Result.Success(Unit)
            }
        } catch (e: Exception) {
            Result.Failure(AppError.Lainnya("Perubahan gagal disimpan. Silakan coba lagi."))
        }
    }

    /**
     * Catat satu pembayaran/cicilan. Atomik: riwayat pembayaran, sisa, status, dan (untuk piutang
     * hasil penjualan Kasir) kolom `transactions.hutangSisa` berubah bersamaan atau tidak sama sekali.
     */
    suspend fun catatPembayaran(id: Long, jumlah: Double, catatan: String?, userId: Long?): Result<Unit> {
        return try {
            appDatabase.withTransaction {
                val catatanHp = dao.findById(id)
                    ?: return@withTransaction Result.Failure(AppError.Lainnya("Catatan tidak ditemukan."))
                when (val hasil = HutangPiutangLogic.hitungPembayaran(catatanHp.sisa, catatanHp.status, jumlah)) {
                    is HutangPiutangLogic.HasilBayar.Gagal ->
                        Result.Failure(AppError.Lainnya(hasil.pesan))
                    is HutangPiutangLogic.HasilBayar.Ok -> {
                        val sekarang = System.currentTimeMillis()
                        dao.insertPembayaran(
                            HutangPiutangPembayaranEntity(
                                hutangPiutangId = id, jumlah = jumlah, tanggal = sekarang,
                                catatan = catatan?.trim()?.ifBlank { null }, userId = userId, createdAt = sekarang
                            )
                        )
                        dao.updateStatusAndSisa(id, hasil.sisaBaru, hasil.status, sekarang)
                        transactionDao.getTransactionIdByHutangPiutang(id)?.let {
                            transactionDao.updateHutangSisa(it, hasil.sisaBaru)
                        }
                        // Hutang hasil pembelian ke supplier: sisa di tabel pembelian ikut diperbarui.
                        if (catatanHp.jenis == HutangPiutangLogic.JENIS_HUTANG) {
                            catatanHp.nomorReferensi?.let { no ->
                                purchaseDao.findByNoPembelian(no)?.let { pembelian ->
                                    purchaseDao.updateHutangSisa(pembelian.id, hasil.sisaBaru, sekarang)
                                }
                            }
                        }
                        Result.Success(Unit)
                    }
                }
            }
        } catch (e: Exception) {
            Result.Failure(AppError.Lainnya("Pembayaran gagal disimpan. Silakan coba lagi."))
        }
    }

    /**
     * Batalkan catatan manual yang salah input. Hanya untuk catatan yang belum pernah dibayar dan
     * bukan hasil penjualan Kasir (untuk itu, batalkan transaksinya di Riwayat). Baris tidak dihapus.
     */
    suspend fun batalkan(id: Long): Result<Unit> {
        return try {
            appDatabase.withTransaction {
                val lama = dao.findById(id)
                    ?: return@withTransaction Result.Failure(AppError.Lainnya("Catatan tidak ditemukan."))
                when {
                    lama.status == HutangPiutangLogic.STATUS_BATAL ->
                        Result.Failure(AppError.Lainnya("Catatan ini sudah dibatalkan."))
                    lama.nomorReferensi != null ->
                        Result.Failure(AppError.Lainnya("Catatan ini terhubung ke transaksi/pembelian. Batalkan lewat Riwayat (penjualan) atau menu Pembelian."))
                    dao.countPembayaran(id) > 0 ->
                        Result.Failure(AppError.Lainnya("Catatan yang sudah ada pembayarannya tidak bisa dibatalkan."))
                    else -> {
                        dao.updateStatusAndSisa(id, lama.sisa, HutangPiutangLogic.STATUS_BATAL, System.currentTimeMillis())
                        Result.Success(Unit)
                    }
                }
            }
        } catch (e: Exception) {
            Result.Failure(AppError.Lainnya("Pembatalan gagal. Silakan coba lagi."))
        }
    }
}
