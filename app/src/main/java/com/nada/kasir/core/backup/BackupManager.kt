package com.nada.kasir.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.nada.kasir.core.data.local.AppDatabase
import com.nada.kasir.core.data.local.dao.*
import com.nada.kasir.core.util.AppError
import com.nada.kasir.core.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val BACKUP_VERSION = 1
private const val ENTRI_JSON = "backup.json"

/**
 * BACKUP DATA / RESTORE DATA (poin 17).
 * Mencakup: Produk, Stok, Transaksi, Pengaturan Toko, Pengguna, Printer, DAN foto produk
 * + logo toko (sebelumnya cuma path-nya yang tersimpan di JSON, file fotonya sendiri
 * tertinggal di penyimpanan internal HP lama - jadi hilang setelah pindah HP/instal ulang.
 * Sekarang file backup berupa .zip: "backup.json" + file foto asli, bukan .json polos).
 *
 * Aturan penting: restore TIDAK BOLEH menghapus data lama sebelum data baru
 * terbukti valid dan berhasil disisipkan. Caranya di sini:
 *   1. Baca & validasi SELURUH isi zip (JSON + semua file foto) ke memori dulu,
 *      di luar transaksi DB (fail fast kalau rusak, belum ada yang ditulis ke disk/DB).
 *   2. Baru setelah semua data baru terbukti valid, foto ditulis ke disk DAN tabel lama
 *      dihapus + data baru di-insert, dibungkus SATU appDatabase.withTransaction { } -
 *      kalau ada error di tengah jalan, Room otomatis rollback dan data lama tetap utuh.
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

    /**
     * Path foto (fotoPath/logoPath) tersimpan sebagai path ABSOLUT (mis.
     * "/data/user/0/com.nada.kasir/files/produk_foto/foto_x.png"), sedangkan di dalam zip
     * disimpan sebagai path RELATIF terhadap filesDir ("produk_foto/foto_x.png") - supaya
     * tetap benar walau di-restore ke HP lain dengan absolute path yang beda persis.
     * null kalau path-nya di luar filesDir (dianggap tidak valid/tidak dikenal, dilewati).
     */
    private fun keRelatif(absolutePath: String?): String? {
        if (absolutePath.isNullOrBlank()) return null
        val basis = context.filesDir.absolutePath
        if (!absolutePath.startsWith(basis)) return null
        val relatif = absolutePath.removePrefix(basis).trimStart('/')
        // Tolak path kosong dan path yang mencoba keluar folder (mis. "../databases/x") - path ini
        // bisa berasal dari file backup yang tidak tepercaya.
        if (relatif.isBlank() || relatif.split('/').any { it == ".." || it == "." }) return null
        return relatif
    }

    suspend fun backup(): Result<File> = withContext(Dispatchers.IO) {
        try {
            val stores = storeDao.getAllForBackup()
            val products = productDao.getAllForBackup()

            val root = JSONObject()
            root.put("versiBackup", BACKUP_VERSION)
            root.put("dibuatPada", System.currentTimeMillis())
            root.put("stores", EntityJsonMapper.listToJsonArray(stores, EntityJsonMapper::storeToJson))
            root.put("users", EntityJsonMapper.listToJsonArray(userDao.getAllForBackup(), EntityJsonMapper::userToJson))
            root.put("categories", EntityJsonMapper.listToJsonArray(categoryDao.getAllForBackup(), EntityJsonMapper::categoryToJson))
            root.put("products", EntityJsonMapper.listToJsonArray(products, EntityJsonMapper::productToJson))
            root.put("transactions", EntityJsonMapper.listToJsonArray(transactionDao.getAllTransactionsForBackup(), EntityJsonMapper::transactionToJson))
            root.put("transactionItems", EntityJsonMapper.listToJsonArray(transactionDao.getAllItemsForBackup(), EntityJsonMapper::itemToJson))
            root.put("payments", EntityJsonMapper.listToJsonArray(transactionDao.getAllPaymentsForBackup(), EntityJsonMapper::paymentToJson))
            root.put("stockMovements", EntityJsonMapper.listToJsonArray(stockMovementDao.getAllForBackup(), EntityJsonMapper::stockMovementToJson))
            root.put("printers", EntityJsonMapper.listToJsonArray(printerDao.getAllForBackup(), EntityJsonMapper::printerToJson))
            root.put("settings", EntityJsonMapper.listToJsonArray(settingDao.getAllForBackup(), EntityJsonMapper::settingToJson))

            // Kumpulkan path relatif semua foto yang benar-benar ada (produk + logo toko),
            // pakai Set supaya tidak dobel kalau ada path yang sama kebetulan dipakai 2 baris.
            val relatifFotoDipakai = linkedSetOf<String>()
            products.forEach { keRelatif(it.fotoPath)?.let { rel -> relatifFotoDipakai += rel } }
            stores.forEach { keRelatif(it.logoPath)?.let { rel -> relatifFotoDipakai += rel } }

            val namaFile = "nada-kasir-backup-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale("id","ID")).format(Date())}.zip"
            val file = File(folderBackup(), namaFile)
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry(ENTRI_JSON))
                zip.write(root.toString(2).toByteArray())
                zip.closeEntry()

                relatifFotoDipakai.forEach { relatif ->
                    val fotoFile = File(context.filesDir, relatif)
                    if (fotoFile.exists()) {
                        zip.putNextEntry(ZipEntry(relatif))
                        fotoFile.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                    // Kalau file fotonya sudah tidak ada di disk (misal terhapus manual), data
                    // produk/toko tetap ikut backup seperti biasa - cuma fotonya yang dilewati,
                    // bukan menggagalkan seluruh proses backup.
                }
            }
            Result.Success(file)
        } catch (e: Exception) {
            Result.Failure(AppError.TransaksiGagalDisimpan)
        }
    }

    /** Restore dari file .zip hasil [backup]. [zipUri] didapat dari file picker sistem (SAF). */
    suspend fun restore(zipUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        // Tahap 1: baca SELURUH entri zip ke memori dulu (JSON + semua foto), validasi JSON-nya,
        // SEBELUM menyentuh disk (folder foto) atau data lama sama sekali - fail fast kalau rusak.
        val entriFoto = mutableMapOf<String, ByteArray>()
        var jsonText: String? = null
        try {
            val input = context.contentResolver.openInputStream(zipUri) ?: return@withContext Result.Failure(AppError.FormatExcelSalah)
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val bytes = ByteArrayOutputStream().apply { zip.copyTo(this) }.toByteArray()
                    if (entry.name == ENTRI_JSON) jsonText = String(bytes) else entriFoto[entry.name] = bytes
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            return@withContext Result.Failure(AppError.FormatExcelSalah)
        }

        val root: JSONObject
        try {
            root = JSONObject(jsonText ?: throw JSONException("file backup.json tidak ditemukan di dalam zip"))
            if (!root.has("versiBackup")) throw JSONException("format tidak dikenali")
        } catch (e: JSONException) {
            return@withContext Result.Failure(AppError.FormatExcelSalah)
        }

        // Parsing KETAT: kalau ada satu bagian saja yang hilang/cacat, seluruh restore dibatalkan
        // SEBELUM data lama disentuh. (Sebelumnya bagian yang gagal diparse diam-diam dianggap
        // daftar kosong, lalu semua tabel tetap dihapus -> data hilang.)
        val stores: List<com.nada.kasir.core.data.local.entity.StoreEntity>
        val users: List<com.nada.kasir.core.data.local.entity.UserEntity>
        val categories: List<com.nada.kasir.core.data.local.entity.CategoryEntity>
        val products: List<com.nada.kasir.core.data.local.entity.ProductEntity>
        val printers: List<com.nada.kasir.core.data.local.entity.PrinterEntity>
        val settings: List<com.nada.kasir.core.data.local.entity.SettingEntity>
        val stockMovements: List<com.nada.kasir.core.data.local.entity.StockMovementEntity>
        val transaksiJsonArray: org.json.JSONArray
        val itemJsonArray: org.json.JSONArray
        val paymentJsonArray: org.json.JSONArray
        try {
            stores = EntityJsonMapper.jsonArrayToList(root.getJSONArray("stores"), EntityJsonMapper::storeFromJson)
            users = EntityJsonMapper.jsonArrayToList(root.getJSONArray("users"), EntityJsonMapper::userFromJson)
            categories = EntityJsonMapper.jsonArrayToList(root.getJSONArray("categories"), EntityJsonMapper::categoryFromJson)
            products = EntityJsonMapper.jsonArrayToList(root.getJSONArray("products"), EntityJsonMapper::productFromJson)
            printers = EntityJsonMapper.jsonArrayToList(root.getJSONArray("printers"), EntityJsonMapper::printerFromJson)
            settings = EntityJsonMapper.jsonArrayToList(root.getJSONArray("settings"), EntityJsonMapper::settingFromJson)
            stockMovements = EntityJsonMapper.jsonArrayToList(root.getJSONArray("stockMovements"), EntityJsonMapper::stockMovementFromJson)

            // Transaksi butuh pemetaan ID lama -> ID baru supaya item & payment tetap terhubung ke induknya.
            // Ketiganya juga divalidasi penuh di sini (bukan baru di dalam transaksi DB).
            transaksiJsonArray = root.getJSONArray("transactions")
            itemJsonArray = root.getJSONArray("transactionItems")
            paymentJsonArray = root.getJSONArray("payments")
            for (i in 0 until transaksiJsonArray.length()) {
                val o = transaksiJsonArray.getJSONObject(i)
                EntityJsonMapper.transactionOldId(o); EntityJsonMapper.transactionFromJson(o)
            }
            for (i in 0 until itemJsonArray.length()) {
                val o = itemJsonArray.getJSONObject(i)
                EntityJsonMapper.itemOldTransactionId(o); EntityJsonMapper.itemFromJson(o, 0L)
            }
            for (i in 0 until paymentJsonArray.length()) {
                val o = paymentJsonArray.getJSONObject(i)
                EntityJsonMapper.paymentOldTransactionId(o); EntityJsonMapper.paymentFromJson(o, 0L)
            }
        } catch (e: Exception) {
            return@withContext Result.Failure(AppError.FormatExcelSalah)
        }

        // Aplikasi selalu punya minimal 1 akun (admin awal dibuat otomatis). Backup tanpa pengguna
        // dianggap tidak valid - kalau dipulihkan, tidak akan ada akun yang bisa dipakai login.
        if (users.isEmpty()) return@withContext Result.Failure(AppError.FormatExcelSalah)

        // Tulis kembali file foto (produk & logo toko) ke filesDir SEBELUM transaksi DB, dengan
        // path relatif yang sama seperti waktu backup - supaya fotoPath/logoPath yang ada di JSON
        // (path absolut, ikut dibawa apa adanya oleh productFromJson/storeFromJson) tetap valid
        // walau backup ini dipulihkan di HP lain dengan absolute path filesDir yang berbeda,
        // karena SEBENARNYA path yang dipakai untuk MEMBACA foto nanti (mis. Coil AsyncImage)
        // adalah string fotoPath apa adanya - jadi kalau HP tujuan beda user/absolute path,
        // fotoPath lama tidak otomatis cocok. Untuk itu path di JSON ditimpa ke absolute path
        // filesDir HP SAAT INI dulu, baru fotonya ditulis ke lokasi itu.
        fun tulisFotoDanPetakanUlang(pathLama: String?): String? {
            // Path yang mengandung ".." berasal dari backup tidak tepercaya -> dibuang, bukan dipakai.
            if (pathLama != null && pathLama.split('/').any { it == ".." }) return null
            val relatif = keRelatif(pathLama) ?: return pathLama
            val bytes = entriFoto[relatif] ?: return pathLama
            return try {
                val tujuan = File(context.filesDir, relatif)
                // Lapis kedua: pastikan hasil akhirnya benar-benar berada di dalam filesDir.
                val basisKanonik = context.filesDir.canonicalPath + File.separator
                if (!tujuan.canonicalPath.startsWith(basisKanonik)) return null
                tujuan.parentFile?.mkdirs()
                tujuan.writeBytes(bytes)
                tujuan.absolutePath
            } catch (e: Exception) {
                pathLama
            }
        }
        val storesSiap = stores.map { it.copy(logoPath = tulisFotoDanPetakanUlang(it.logoPath)) }
        val productsSiap = products.map { it.copy(fotoPath = tulisFotoDanPetakanUlang(it.fotoPath)) }

        return@withContext try {
            appDatabase.withTransaction {
                // Tahap 2: baru sekarang data lama dihapus - kalau exception terjadi di titik manapun
                // setelah ini, Room me-rollback SEMUANYA (termasuk DELETE-nya), jadi data lama tetap ada.
                storeDao.clearAll(); userDao.clearAll(); categoryDao.clearAll(); productDao.clearAll()
                printerDao.clearAll(); settingDao.clearAll(); stockMovementDao.clearAll()
                transactionDao.clearPayments(); transactionDao.clearItems(); transactionDao.clearTransactions()

                if (storesSiap.isNotEmpty()) storeDao.insertAll(storesSiap)
                if (users.isNotEmpty()) userDao.insertAll(users)
                if (categories.isNotEmpty()) categoryDao.insertAll(categories)
                if (productsSiap.isNotEmpty()) productDao.insertAll(productsSiap)
                if (printers.isNotEmpty()) printerDao.insertAll(printers)
                settings.forEach { settingDao.upsert(it) }
                if (stockMovements.isNotEmpty()) stockMovementDao.insertAll(stockMovements)

                run {
                    val petaIdLamaKeBaru = mutableMapOf<Long, Long>()
                    for (i in 0 until transaksiJsonArray.length()) {
                        val o = transaksiJsonArray.getJSONObject(i)
                        val idLama = EntityJsonMapper.transactionOldId(o)
                        val entity = EntityJsonMapper.transactionFromJson(o)
                        val idBaru = transactionDao.insertAllTransactions(listOf(entity)).first()
                        petaIdLamaKeBaru[idLama] = idBaru
                    }
                    itemJsonArray.let { arr ->
                        val items = mutableListOf<com.nada.kasir.core.data.local.entity.TransactionItemEntity>()
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val idBaru = petaIdLamaKeBaru[EntityJsonMapper.itemOldTransactionId(o)] ?: continue
                            items.add(EntityJsonMapper.itemFromJson(o, idBaru))
                        }
                        if (items.isNotEmpty()) transactionDao.insertAllItems(items)
                    }
                    paymentJsonArray.let { arr ->
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
