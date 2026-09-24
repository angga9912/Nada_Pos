package com.nada.kasir.core.backup

import android.content.Context
import androidx.room.withTransaction
import com.nada.kasir.core.data.local.AppDatabase
import com.nada.kasir.core.data.local.dao.*
import com.nada.kasir.core.data.local.entity.SettingEntity
import com.nada.kasir.core.data.local.entity.UserRole
import com.nada.kasir.core.lisensi.LicenseKeyValidator
import com.nada.kasir.core.paket.PaketAplikasi
import com.nada.kasir.core.util.AppError
import com.nada.kasir.core.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

private const val BACKUP_VERSION = 2
private const val HMAC_KEY_STRING = "NADA-POS-SECURE-BACKUP-INTEGRITY-2026"

/**
 * BACKUP DATA / RESTORE DATA (poin 17).
 * Dilengkapi pengecekan integritas HMAC-SHA256 untuk mendeteksi manipulasi data
 * di luar aplikasi, serta sanitasi paket lisensi saat pemulihan data.
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
    private val settingDao: SettingDao,
    private val auditLogDao: AuditLogDao
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
            val dibuatPada = System.currentTimeMillis()
            root.put("dibuatPada", dibuatPada)

            val storesArr = EntityJsonMapper.listToJsonArray(storeDao.getAllForBackup(), EntityJsonMapper::storeToJson)
            val usersArr = EntityJsonMapper.listToJsonArray(userDao.getAllForBackup(), EntityJsonMapper::userToJson)
            val categoriesArr = EntityJsonMapper.listToJsonArray(categoryDao.getAllForBackup(), EntityJsonMapper::categoryToJson)
            val productsArr = EntityJsonMapper.listToJsonArray(productDao.getAllForBackup(), EntityJsonMapper::productToJson)
            val transactionsArr = EntityJsonMapper.listToJsonArray(transactionDao.getAllTransactionsForBackup(), EntityJsonMapper::transactionToJson)
            val transactionItemsArr = EntityJsonMapper.listToJsonArray(transactionDao.getAllItemsForBackup(), EntityJsonMapper::itemToJson)
            val paymentsArr = EntityJsonMapper.listToJsonArray(transactionDao.getAllPaymentsForBackup(), EntityJsonMapper::paymentToJson)
            val stockMovementsArr = EntityJsonMapper.listToJsonArray(stockMovementDao.getAllForBackup(), EntityJsonMapper::stockMovementToJson)
            val printersArr = EntityJsonMapper.listToJsonArray(printerDao.getAllForBackup(), EntityJsonMapper::printerToJson)
            val settingsArr = EntityJsonMapper.listToJsonArray(settingDao.getAllForBackup(), EntityJsonMapper::settingToJson)
            val auditLogsArr = EntityJsonMapper.listToJsonArray(auditLogDao.getAllForBackup(), EntityJsonMapper::auditLogToJson)

            root.put("stores", storesArr)
            root.put("users", usersArr)
            root.put("categories", categoriesArr)
            root.put("products", productsArr)
            root.put("transactions", transactionsArr)
            root.put("transactionItems", transactionItemsArr)
            root.put("payments", paymentsArr)
            root.put("stockMovements", stockMovementsArr)
            root.put("printers", printersArr)
            root.put("settings", settingsArr)
            root.put("auditLogs", auditLogsArr)

            // Hitung HMAC integritas untuk melindungi file dari tampering
            val hmac = hitungHmacPayload(root)
            root.put("integritasHmac", hmac)

            val namaFile = "nada-kasir-backup-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale("id","ID")).format(Date(dibuatPada))}.json"
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

        // Verifikasi integritas HMAC jika file memiliki signature integritas
        if (root.has("integritasHmac")) {
            val expectedHmac = root.getString("integritasHmac")
            val calculatedHmac = hitungHmacPayload(root)
            if (!MessageDigest.isEqual(expectedHmac.toByteArray(Charsets.UTF_8), calculatedHmac.toByteArray(Charsets.UTF_8))) {
                return@withContext Result.Failure(AppError.Lainnya("File backup tidak valid atau telah dimodifikasi di luar aplikasi."))
            }
        }

        val stores = try { EntityJsonMapper.jsonArrayToList(root.getJSONArray("stores"), EntityJsonMapper::storeFromJson) } catch (e: Exception) { emptyList() }
        val rawUsers = try { EntityJsonMapper.jsonArrayToList(root.getJSONArray("users"), EntityJsonMapper::userFromJson) } catch (e: Exception) { emptyList() }
        val categories = try { EntityJsonMapper.jsonArrayToList(root.getJSONArray("categories"), EntityJsonMapper::categoryFromJson) } catch (e: Exception) { emptyList() }
        val products = try { EntityJsonMapper.jsonArrayToList(root.getJSONArray("products"), EntityJsonMapper::productFromJson) } catch (e: Exception) { emptyList() }
        val printers = try { EntityJsonMapper.jsonArrayToList(root.getJSONArray("printers"), EntityJsonMapper::printerFromJson) } catch (e: Exception) { emptyList() }
        val rawSettings = try { EntityJsonMapper.jsonArrayToList(root.getJSONArray("settings"), EntityJsonMapper::settingFromJson) } catch (e: Exception) { emptyList() }
        val stockMovements = try { EntityJsonMapper.jsonArrayToList(root.getJSONArray("stockMovements"), EntityJsonMapper::stockMovementFromJson) } catch (e: Exception) { emptyList() }
        val auditLogs = try { EntityJsonMapper.jsonArrayToList(root.getJSONArray("auditLogs"), EntityJsonMapper::auditLogFromJson) } catch (e: Exception) { emptyList() }

        // Sanitasi Pengaturan Lisensi: Jangan izinkan PRO/CUSTOM di-restore tanpa kode lisensi ECDSA sah
        val sanitizedSettings = rawSettings.toMutableList()
        val restoredPaket = sanitizedSettings.firstOrNull { it.key == "paket_aktif" }?.value
        val restoredKode = sanitizedSettings.firstOrNull { it.key == "lisensi_kode_aktif" }?.value
        if (restoredPaket != null && restoredPaket != PaketAplikasi.BASIC.name) {
            val valid = restoredKode != null && LicenseKeyValidator.validasi(restoredKode)?.tier?.name == restoredPaket
            if (!valid) {
                // Kode lisensi tidak valid -> turunkan paksa paket ke BASIC
                sanitizedSettings.removeAll { it.key == "paket_aktif" }
                sanitizedSettings.add(SettingEntity(key = "paket_aktif", value = PaketAplikasi.BASIC.name))
            }
        }

        // Sanitasi Pengguna: Pastikan tidak ada akun dengan password kosong dan minimal ada 1 Admin
        val users = rawUsers.filter { it.passwordHash.isNotBlank() }
        val adaAdmin = users.any { it.role == UserRole.ADMIN }
        if (users.isNotEmpty() && !adaAdmin) {
            return@withContext Result.Failure(AppError.Lainnya("File backup tidak valid: tidak ditemukan akun Administrator."))
        }

        // Transaksi butuh pemetaan ID lama -> ID baru supaya item & payment tetap terhubung ke induknya
        val transaksiJsonArray = try { root.getJSONArray("transactions") } catch (e: Exception) { null }
        val itemJsonArray = try { root.getJSONArray("transactionItems") } catch (e: Exception) { null }
        val paymentJsonArray = try { root.getJSONArray("payments") } catch (e: Exception) { null }

        return@withContext try {
            appDatabase.withTransaction {
                // Tahap 2: baru sekarang data lama dihapus
                storeDao.clearAll(); userDao.clearAll(); categoryDao.clearAll(); productDao.clearAll()
                printerDao.clearAll(); settingDao.clearAll(); stockMovementDao.clearAll(); auditLogDao.clearAll()
                transactionDao.clearPayments(); transactionDao.clearItems(); transactionDao.clearTransactions()

                if (stores.isNotEmpty()) storeDao.insertAll(stores)
                if (users.isNotEmpty()) userDao.insertAll(users)
                if (categories.isNotEmpty()) categoryDao.insertAll(categories)
                if (products.isNotEmpty()) productDao.insertAll(products)
                if (printers.isNotEmpty()) printerDao.insertAll(printers)
                sanitizedSettings.forEach { settingDao.upsert(it) }

                val petaIdLamaKeBaru = mutableMapOf<Long, Long>()
                if (transaksiJsonArray != null) {
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

                if (stockMovements.isNotEmpty()) {
                    val mappedStockMovements = stockMovements.map { m ->
                        if (m.referensiTransaksiId != null && petaIdLamaKeBaru.containsKey(m.referensiTransaksiId)) {
                            m.copy(referensiTransaksiId = petaIdLamaKeBaru[m.referensiTransaksiId])
                        } else {
                            m
                        }
                    }
                    stockMovementDao.insertAll(mappedStockMovements)
                }

                if (auditLogs.isNotEmpty()) auditLogDao.insertAll(auditLogs)
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(AppError.TransaksiGagalDisimpan)
        }
    }

    private fun hitungHmacPayload(root: JSONObject): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(HMAC_KEY_STRING.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKey)

        val builder = StringBuilder()
        builder.append(root.optLong("dibuatPada"))
        builder.append(root.optJSONArray("stores")?.toString() ?: "")
        builder.append(root.optJSONArray("users")?.toString() ?: "")
        builder.append(root.optJSONArray("products")?.toString() ?: "")
        builder.append(root.optJSONArray("transactions")?.toString() ?: "")
        builder.append(root.optJSONArray("settings")?.toString() ?: "")

        val signatureBytes = mac.doFinal(builder.toString().toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(signatureBytes)
    }
}
