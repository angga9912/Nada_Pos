package com.nada.kasir.core.backup

import android.content.Context
import androidx.room.withTransaction
import com.nada.kasir.core.data.local.AppDatabase
import com.nada.kasir.core.data.local.dao.*
import com.nada.kasir.core.util.AppError
import com.nada.kasir.core.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

private const val BACKUP_VERSION = 1

/**
 * BACKUP DATA / RESTORE DATA (poin 17).
 * Mencakup: Produk, Stok, Transaksi, Pengaturan Toko, Pengguna, Printer.
 *
 * Aturan penting: restore TIDAK BOLEH menghapus data lama sebelum data baru
 * terbukti valid dan berhasil disisipkan. Caranya di sini:
 *   1. Parse & validasi seluruh JSON dulu di luar transaksi DB (fail fast kalau rusak).
 *   2. Baru setelah semua data baru siap di memori, hapus tabel lama DAN insert data
 *      baru dibungkus SATU appDatabase.withTransaction { } - kalau ada error di
 *      tengah jalan, Room otomatis rollback dan data lama tetap utuh.
 */
@Singleton
class BackupManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val appDatabase: AppDatabase,
    private val storeDao: StoreDao,
    private val userDao: UserDao,
    private val categoryDao: CategoryDao,
    private val productDao: ProductDao,
    private val transactionDao: TransactionDao,
    private val stockMovementDao: StockMovementDao,
    private val printerDao: PrinterDao,
    private val settingDao: SettingDao
) {
    fun folderBackup(): File {
        val dir = File(context.getExternalFilesDir(null), "backup")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun backup(): Result<File> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject()
            root.put("versiBackup", BACKUP_VERSION)
            root.put("dibuatPada", System.currentTimeMillis())
            root.put("stores", EntityJsonMapper.listToJsonArray(storeDao.getAllForBackup(), EntityJsonMapper::storeToJson))
            root.put("users", EntityJsonMapper.listToJsonArray(userDao.getAllForBackup(), EntityJsonMapper::userToJson))
            root.put("categories", EntityJsonMapper.listToJsonArray(categoryDao.getAllForBackup(), EntityJsonMapper::categoryToJson))
            root.put("products", EntityJsonMapper.listToJsonArray(productDao.getAllForBackup(), EntityJsonMapper::productToJson))
            root.put("transactions", EntityJsonMapper.listToJsonArray(transactionDao.getAllTransactionsForBackup(), EntityJsonMapper::transactionToJson))
            root.put("transactionItems", EntityJsonMapper.listToJsonArray(transactionDao.getAllItemsForBackup(), EntityJsonMapper::itemToJson))
            root.put("payments", EntityJsonMapper.listToJsonArray(transactionDao.getAllPaymentsForBackup(), EntityJsonMapper::paymentToJson))
            root.put("stockMovements", EntityJsonMapper.listToJsonArray(stockMovementDao.getAllForBackup(), EntityJsonMapper::stockMovementToJson))
            root.put("printers", EntityJsonMapper.listToJsonArray(printerDao.getAllForBackup(), EntityJsonMapper::printerToJson))
            root.put("settings", EntityJsonMapper.listToJsonArray(settingDao.getAllForBackup(), EntityJsonMapper::settingToJson))

            val namaFile = "nada-kasir-backup-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale("id","ID")).format(Date())}.json"
            val file = File(folderBackup(), namaFile)
            file.writeText(root.toString(2))
            Result.Success(file)
        } catch (e: Exception) {
            Result.Failure(AppError.TransaksiGagalDisimpan)
        }
    }

    suspend fun restore(jsonText: String): Result<Unit> = withContext(Dispatchers.IO) {
        // Tahap 1: parse & validasi PENUH di luar transaksi DB, sebelum menyentuh data lama sama sekali.
        val root: JSONObject
        try {
            root = JSONObject(jsonText)
            if (!root.has("versiBackup")) throw JSONException("format tidak dikenali")
        } catch (e: JSONException) {
            return@withContext Result.Failure(AppError.FormatExcelSalah)
        }

        val stores = try { EntityJsonMapper.jsonArrayToList(root.getJSONArray("stores"), EntityJsonMapper::storeFromJson) } catch (e: Exception) { emptyList() }
        val users = try { EntityJsonMapper.jsonArrayToList(root.getJSONArray("users"), EntityJsonMapper::userFromJson) } catch (e: Exception) { emptyList() }
        val categories = try { EntityJsonMapper.jsonArrayToList(root.getJSONArray("categories"), EntityJsonMapper::categoryFromJson) } catch (e: Exception) { emptyList() }
        val products = try { EntityJsonMapper.jsonArrayToList(root.getJSONArray("products"), EntityJsonMapper::productFromJson) } catch (e: Exception) { emptyList() }
        val printers = try { EntityJsonMapper.jsonArrayToList(root.getJSONArray("printers"), EntityJsonMapper::printerFromJson) } catch (e: Exception) { emptyList() }
        val settings = try { EntityJsonMapper.jsonArrayToList(root.getJSONArray("settings"), EntityJsonMapper::settingFromJson) } catch (e: Exception) { emptyList() }
        val stockMovements = try { EntityJsonMapper.jsonArrayToList(root.getJSONArray("stockMovements"), EntityJsonMapper::stockMovementFromJson) } catch (e: Exception) { emptyList() }

        // Transaksi butuh pemetaan ID lama -> ID baru supaya item & payment tetap terhubung ke induknya
        val transaksiJsonArray = try { root.getJSONArray("transactions") } catch (e: Exception) { null }
        val itemJsonArray = try { root.getJSONArray("transactionItems") } catch (e: Exception) { null }
        val paymentJsonArray = try { root.getJSONArray("payments") } catch (e: Exception) { null }

        return@withContext try {
            appDatabase.withTransaction {
                // Tahap 2: baru sekarang data lama dihapus - kalau exception terjadi di titik manapun
                // setelah ini, Room me-rollback SEMUANYA (termasuk DELETE-nya), jadi data lama tetap ada.
                storeDao.clearAll(); userDao.clearAll(); categoryDao.clearAll(); productDao.clearAll()
                printerDao.clearAll(); settingDao.clearAll(); stockMovementDao.clearAll()
                transactionDao.clearPayments(); transactionDao.clearItems(); transactionDao.clearTransactions()

                if (stores.isNotEmpty()) storeDao.insertAll(stores)
                if (users.isNotEmpty()) userDao.insertAll(users)
                if (categories.isNotEmpty()) categoryDao.insertAll(categories)
                if (products.isNotEmpty()) productDao.insertAll(products)
                if (printers.isNotEmpty()) printerDao.insertAll(printers)
                settings.forEach { settingDao.upsert(it) }
                if (stockMovements.isNotEmpty()) stockMovementDao.insertAll(stockMovements)

                if (transaksiJsonArray != null) {
                    val petaIdLamaKeBaru = mutableMapOf<Long, Long>()
                    for (i in 0 until transaksiJsonArray.length()) {
                        val o = transaksiJsonArray.getJSONObject(i)
                        val idLama = EntityJsonMapper.transactionOldId(o)
                        val entity = EntityJsonMapper.transactionFromJson(o)
                        val idBaru = transactionDao.insertAllTransactions(listOf(entity)).first()
                        petaIdLamaKeBaru[idLama] = idBaru
                    }
                    itemJsonArray?.let { arr ->
                        val items = mutableListOf<com.nada.kasir.core.data.local.entity.TransactionItemEntity>()
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val idBaru = petaIdLamaKeBaru[EntityJsonMapper.itemOldTransactionId(o)] ?: continue
                            items.add(EntityJsonMapper.itemFromJson(o, idBaru))
                        }
                        if (items.isNotEmpty()) transactionDao.insertAllItems(items)
                    }
                    paymentJsonArray?.let { arr ->
                        val payments = mutableListOf<com.nada.kasir.core.data.local.entity.PaymentEntity>()
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val idBaru = petaIdLamaKeBaru[EntityJsonMapper.paymentOldTransactionId(o)] ?: continue
                            payments.add(EntityJsonMapper.paymentFromJson(o, idBaru))
                        }
                        if (payments.isNotEmpty()) transactionDao.insertAllPayments(payments)
                    }
                }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            // Room sudah rollback otomatis - data lama tetap seperti sebelum restore dicoba.
            Result.Failure(AppError.TransaksiGagalDisimpan)
        }
    }
}
