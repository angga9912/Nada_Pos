package com.nada.kasir.core.backup

import com.nada.kasir.core.data.local.entity.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Konversi manual entity <-> JSON untuk Backup/Restore (poin 17).
 * Dipisah dari BackupManager supaya bisa diuji sebagai unit test murni.
 */
object EntityJsonMapper {

    fun storeToJson(s: StoreEntity) = JSONObject().apply {
        put("id", s.id); put("nama", s.nama); put("alamat", s.alamat); put("whatsapp", s.whatsapp)
        put("telepon", s.telepon); put("pemilik", s.pemilik); put("slogan", s.slogan)
        put("footerStruk", s.footerStruk); put("formatNoTransaksi", s.formatNoTransaksi)
        put("mataUang", s.mataUang); put("ukuranKertas", s.ukuranKertas)
        put("logoPath", s.logoPath ?: JSONObject.NULL); put("warnaUtama", s.warnaUtama)
        put("tampilkanLogoStruk", s.tampilkanLogoStruk); put("tampilkanAlamatStruk", s.tampilkanAlamatStruk)
        put("tampilkanWaStruk", s.tampilkanWaStruk); put("tampilkanHargaBeliStruk", s.tampilkanHargaBeliStruk)
        put("tampilkanDiskonStruk", s.tampilkanDiskonStruk); put("ukuranFontStruk", s.ukuranFontStruk)
    }

    fun storeFromJson(o: JSONObject) = StoreEntity(
        id = 0, // id dibiarkan auto-generate ulang saat restore
        nama = o.getString("nama"), alamat = o.optString("alamat"), whatsapp = o.optString("whatsapp"),
        telepon = o.optString("telepon"), pemilik = o.optString("pemilik"), slogan = o.optString("slogan"),
        footerStruk = o.optString("footerStruk"), formatNoTransaksi = o.optString("formatNoTransaksi"),
        mataUang = o.optString("mataUang", "Rp"), ukuranKertas = o.optString("ukuranKertas", "58mm"),
        logoPath = if (o.isNull("logoPath")) null else o.optString("logoPath"),
        warnaUtama = o.optString("warnaUtama", "#2E7D32"),
        tampilkanLogoStruk = o.optBoolean("tampilkanLogoStruk", true),
        tampilkanAlamatStruk = o.optBoolean("tampilkanAlamatStruk", true),
        tampilkanWaStruk = o.optBoolean("tampilkanWaStruk", true),
        tampilkanHargaBeliStruk = false, // WAJIB false, tidak boleh diambil dari file backup luar (poin 10)
        tampilkanDiskonStruk = o.optBoolean("tampilkanDiskonStruk", true),
        ukuranFontStruk = o.optString("ukuranFontStruk", "NORMAL")
    )

    fun userToJson(u: UserEntity) = JSONObject().apply {
        put("id", u.id); put("nama", u.nama); put("username", u.username)
        put("passwordHash", u.passwordHash); put("role", u.role.name); put("aktif", u.aktif)
    }
    fun userFromJson(o: JSONObject) = UserEntity(
        nama = o.getString("nama"), username = o.getString("username"),
        passwordHash = o.getString("passwordHash"), role = UserRole.valueOf(o.getString("role")),
        aktif = o.optBoolean("aktif", true)
    )

    fun categoryToJson(c: CategoryEntity) = JSONObject().apply { put("id", c.id); put("nama", c.nama) }
    fun categoryFromJson(o: JSONObject) = CategoryEntity(nama = o.getString("nama"))

    fun productToJson(p: ProductEntity) = JSONObject().apply {
        put("id", p.id); put("kodeProduk", p.kodeProduk); put("barcode", p.barcode ?: JSONObject.NULL)
        put("nama", p.nama); put("categoryId", p.categoryId ?: JSONObject.NULL); put("satuan", p.satuan)
        put("hargaBeli", p.hargaBeli); put("hargaJual", p.hargaJual); put("stok", p.stok)
        put("stokMinimum", p.stokMinimum); put("fotoPath", p.fotoPath ?: JSONObject.NULL)
        put("isActive", p.isActive); put("deletedAt", p.deletedAt ?: JSONObject.NULL)
    }
    fun productFromJson(o: JSONObject) = ProductEntity(
        kodeProduk = o.getString("kodeProduk"),
        barcode = if (o.isNull("barcode")) null else o.optString("barcode"),
        nama = o.getString("nama"),
        categoryId = if (o.isNull("categoryId")) null else o.optLong("categoryId"),
        satuan = o.optString("satuan", "pcs"), hargaBeli = o.optDouble("hargaBeli", 0.0),
        hargaJual = o.optDouble("hargaJual", 0.0), stok = o.optInt("stok", 0),
        stokMinimum = o.optInt("stokMinimum", 5),
        fotoPath = if (o.isNull("fotoPath")) null else o.optString("fotoPath"),
        isActive = o.optBoolean("isActive", true),
        deletedAt = if (o.isNull("deletedAt")) null else o.optLong("deletedAt")
    )

    fun transactionToJson(t: TransactionEntity) = JSONObject().apply {
        put("id", t.id); put("noTransaksi", t.noTransaksi); put("nomorAntrian", t.nomorAntrian)
        put("namaPembeli", t.namaPembeli ?: JSONObject.NULL); put("tanggalWaktu", t.tanggalWaktu)
        put("userId", t.userId); put("subtotal", t.subtotal); put("diskon", t.diskon)
        put("total", t.total); put("status", t.status.name)
    }
    fun transactionFromJson(o: JSONObject) = TransactionEntity(
        noTransaksi = o.getString("noTransaksi"), nomorAntrian = o.optInt("nomorAntrian", 0),
        namaPembeli = if (o.isNull("namaPembeli")) null else o.optString("namaPembeli"),
        tanggalWaktu = o.getLong("tanggalWaktu"),
        userId = o.optLong("userId", 1L), subtotal = o.getDouble("subtotal"), diskon = o.optDouble("diskon", 0.0),
        total = o.getDouble("total"), status = TransactionStatus.valueOf(o.optString("status", "COMPLETED"))
    )
    // id lama transaksi disimpan terpisah agar transaction_items & payments bisa dipetakan ulang saat restore
    fun transactionOldId(o: JSONObject) = o.getLong("id")

    fun itemToJson(i: TransactionItemEntity) = JSONObject().apply {
        put("id", i.id); put("transactionId", i.transactionId); put("productId", i.productId)
        put("namaProdukSnapshot", i.namaProdukSnapshot); put("qty", i.qty); put("harga", i.harga)
        put("diskon", i.diskon); put("subtotal", i.subtotal)
    }
    fun itemFromJson(o: JSONObject, transactionIdBaru: Long) = TransactionItemEntity(
        transactionId = transactionIdBaru, productId = o.optLong("productId", 0L),
        namaProdukSnapshot = o.getString("namaProdukSnapshot"), qty = o.getInt("qty"),
        harga = o.getDouble("harga"), diskon = o.optDouble("diskon", 0.0), subtotal = o.getDouble("subtotal")
    )
    fun itemOldTransactionId(o: JSONObject) = o.getLong("transactionId")

    fun paymentToJson(p: PaymentEntity) = JSONObject().apply {
        put("id", p.id); put("transactionId", p.transactionId); put("metode", p.metode.name)
        put("jumlahDiterima", p.jumlahDiterima); put("kembalian", p.kembalian)
    }
    fun paymentFromJson(o: JSONObject, transactionIdBaru: Long) = PaymentEntity(
        transactionId = transactionIdBaru, metode = MetodePembayaran.valueOf(o.getString("metode")),
        jumlahDiterima = o.getDouble("jumlahDiterima"), kembalian = o.getDouble("kembalian")
    )
    fun paymentOldTransactionId(o: JSONObject) = o.getLong("transactionId")

    fun stockMovementToJson(m: StockMovementEntity) = JSONObject().apply {
        put("id", m.id); put("productId", m.productId); put("tipe", m.tipe.name); put("qty", m.qty)
        put("referensiTransaksiId", m.referensiTransaksiId ?: JSONObject.NULL)
        put("supplier", m.supplier ?: JSONObject.NULL); put("keterangan", m.keterangan ?: JSONObject.NULL)
        put("tanggalWaktu", m.tanggalWaktu)
    }
    fun stockMovementFromJson(o: JSONObject) = StockMovementEntity(
        productId = o.optLong("productId", 0L), tipe = TipeMutasiStok.valueOf(o.getString("tipe")),
        qty = o.getInt("qty"), referensiTransaksiId = if (o.isNull("referensiTransaksiId")) null else o.optLong("referensiTransaksiId"),
        supplier = if (o.isNull("supplier")) null else o.optString("supplier"),
        keterangan = if (o.isNull("keterangan")) null else o.optString("keterangan"),
        tanggalWaktu = o.getLong("tanggalWaktu")
    )

    fun printerToJson(p: PrinterEntity) = JSONObject().apply {
        put("id", p.id); put("nama", p.nama); put("macAddress", p.macAddress)
        put("ukuranKertas", p.ukuranKertas); put("isDefault", p.isDefault)
    }
    fun printerFromJson(o: JSONObject) = PrinterEntity(
        nama = o.getString("nama"), macAddress = o.getString("macAddress"),
        ukuranKertas = o.optString("ukuranKertas", "58mm"), isDefault = o.optBoolean("isDefault", false)
    )

    fun settingToJson(s: SettingEntity) = JSONObject().apply { put("key", s.key); put("value", s.value) }
    fun settingFromJson(o: JSONObject) = SettingEntity(key = o.getString("key"), value = o.getString("value"))

    fun <T> listToJsonArray(list: List<T>, mapper: (T) -> JSONObject): JSONArray {
        val arr = JSONArray()
        list.forEach { arr.put(mapper(it)) }
        return arr
    }

    fun <T> jsonArrayToList(arr: JSONArray, mapper: (JSONObject) -> T): List<T> {
        val result = mutableListOf<T>()
        for (i in 0 until arr.length()) result.add(mapper(arr.getJSONObject(i)))
        return result
    }
}
