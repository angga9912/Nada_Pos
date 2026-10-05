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
        id = o.optLong("id", 0L),
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
        id = o.optLong("id", 0L),
        nama = o.getString("nama"), username = o.getString("username"),
        passwordHash = o.getString("passwordHash"), role = UserRole.valueOf(o.getString("role")),
        aktif = o.optBoolean("aktif", true)
    )

    fun categoryToJson(c: CategoryEntity) = JSONObject().apply { put("id", c.id); put("nama", c.nama) }
    fun categoryFromJson(o: JSONObject) = CategoryEntity(
        id = o.optLong("id", 0L),
        nama = o.getString("nama")
    )

    fun productToJson(p: ProductEntity) = JSONObject().apply {
        put("id", p.id); put("kodeProduk", p.kodeProduk); put("barcode", p.barcode ?: JSONObject.NULL)
        put("nama", p.nama); put("categoryId", p.categoryId ?: JSONObject.NULL); put("satuan", p.satuan)
        put("hargaBeli", p.hargaBeli); put("hargaJual", p.hargaJual); put("stok", p.stok)
        put("stokMinimum", p.stokMinimum); put("fotoPath", p.fotoPath ?: JSONObject.NULL)
        put("supplierId", p.supplierId ?: JSONObject.NULL); put("outletId", p.outletId ?: JSONObject.NULL)
        put("isActive", p.isActive); put("deletedAt", p.deletedAt ?: JSONObject.NULL)
    }
    fun productFromJson(o: JSONObject) = ProductEntity(
        id = o.optLong("id", 0L),
        kodeProduk = o.getString("kodeProduk"),
        barcode = if (o.isNull("barcode")) null else o.optString("barcode"),
        nama = o.getString("nama"),
        categoryId = if (o.isNull("categoryId")) null else o.optLong("categoryId"),
        satuan = o.optString("satuan", "pcs"), hargaBeli = o.optDouble("hargaBeli", 0.0),
        hargaJual = o.optDouble("hargaJual", 0.0), stok = o.optInt("stok", 0),
        stokMinimum = o.optInt("stokMinimum", 5),
        supplierId = optLongOrNull(o, "supplierId"),
        outletId = optLongOrNull(o, "outletId"),
        fotoPath = if (o.isNull("fotoPath")) null else o.optString("fotoPath"),
        isActive = o.optBoolean("isActive", true),
        deletedAt = if (o.isNull("deletedAt")) null else o.optLong("deletedAt")
    )

    fun transactionToJson(t: TransactionEntity) = JSONObject().apply {
        put("id", t.id); put("noTransaksi", t.noTransaksi); put("nomorAntrian", t.nomorAntrian)
        put("namaPembeli", t.namaPembeli ?: JSONObject.NULL); put("tanggalWaktu", t.tanggalWaktu)
        put("userId", t.userId); put("subtotal", t.subtotal); put("diskon", t.diskon)
        put("total", t.total); put("status", t.status.name)
        put("outletId", t.outletId ?: JSONObject.NULL); put("hutangSisa", t.hutangSisa)
    }
    fun transactionFromJson(o: JSONObject) = TransactionEntity(
        id = o.optLong("id", 0L),
        noTransaksi = o.getString("noTransaksi"), nomorAntrian = o.optInt("nomorAntrian", 0),
        namaPembeli = if (o.isNull("namaPembeli")) null else o.optString("namaPembeli"),
        outletId = optLongOrNull(o, "outletId"),
        tanggalWaktu = o.getLong("tanggalWaktu"),
        userId = o.optLong("userId", 1L), subtotal = o.getDouble("subtotal"), diskon = o.optDouble("diskon", 0.0),
        total = o.getDouble("total"), hutangSisa = o.optDouble("hutangSisa", 0.0),
        status = TransactionStatus.valueOf(o.optString("status", "COMPLETED"))
    )
    // id lama transaksi disimpan terpisah agar transaction_items & payments bisa dipetakan ulang saat restore
    fun transactionOldId(o: JSONObject) = o.getLong("id")

    fun itemToJson(i: TransactionItemEntity) = JSONObject().apply {
        put("id", i.id); put("transactionId", i.transactionId); put("productId", i.productId)
        put("namaProdukSnapshot", i.namaProdukSnapshot); put("qty", i.qty); put("harga", i.harga)
        put("diskon", i.diskon); put("subtotal", i.subtotal)
    }
    fun itemFromJson(o: JSONObject, transactionIdBaru: Long) = TransactionItemEntity(
        id = o.optLong("id", 0L),
        transactionId = transactionIdBaru, productId = o.optLong("productId", 0L),
        namaProdukSnapshot = o.getString("namaProdukSnapshot"), qty = o.getInt("qty"),
        harga = o.getDouble("harga"), diskon = o.optDouble("diskon", 0.0), subtotal = o.getDouble("subtotal")
    )
    fun itemOldTransactionId(o: JSONObject) = o.getLong("transactionId")

    fun paymentToJson(p: PaymentEntity) = JSONObject().apply {
        put("id", p.id); put("transactionId", p.transactionId); put("metode", p.metode.name)
        put("jumlahDiterima", p.jumlahDiterima); put("kembalian", p.kembalian)
        put("catatanMetode", p.catatanMetode)
        put("hutangPiutangId", p.hutangPiutangId ?: JSONObject.NULL)
    }
    fun paymentFromJson(o: JSONObject, transactionIdBaru: Long) = PaymentEntity(
        id = o.optLong("id", 0L),
        transactionId = transactionIdBaru, metode = MetodePembayaran.valueOf(o.getString("metode")),
        jumlahDiterima = o.getDouble("jumlahDiterima"), kembalian = o.getDouble("kembalian"),
        hutangPiutangId = optLongOrNull(o, "hutangPiutangId"),
        catatanMetode = if (o.has("catatanMetode") && !o.isNull("catatanMetode")) o.getString("catatanMetode") else null
    )
    fun paymentOldTransactionId(o: JSONObject) = o.getLong("transactionId")

    fun stockMovementToJson(m: StockMovementEntity) = JSONObject().apply {
        put("id", m.id); put("productId", m.productId); put("tipe", m.tipe.name); put("qty", m.qty)
        put("referensiTransaksiId", m.referensiTransaksiId ?: JSONObject.NULL)
        put("referensiPembelianId", m.referensiPembelianId ?: JSONObject.NULL)
        put("supplierId", m.supplierId ?: JSONObject.NULL)
        put("outletId", m.outletId ?: JSONObject.NULL)
        put("keterangan", m.keterangan ?: JSONObject.NULL)
        put("tanggalWaktu", m.tanggalWaktu)
    }

    /** Long nullable yang aman untuk backup lama (kunci belum ada) maupun nilai null. */
    private fun optLongOrNull(o: JSONObject, key: String): Long? =
        if (!o.has(key) || o.isNull(key)) null else o.optLong(key)

    fun stockMovementFromJson(o: JSONObject) = StockMovementEntity(
        id = o.optLong("id", 0L),
        productId = o.optLong("productId", 0L), tipe = TipeMutasiStok.valueOf(o.getString("tipe")),
        qty = o.getInt("qty"), referensiTransaksiId = if (o.isNull("referensiTransaksiId")) null else o.optLong("referensiTransaksiId"),
        // Backup lama menyimpan nama supplier (String) di kunci "supplier"; nama tidak bisa dipetakan
        // ke id, jadi diabaikan. Backup baru memakai supplierId.
        referensiPembelianId = optLongOrNull(o, "referensiPembelianId"),
        supplierId = optLongOrNull(o, "supplierId"),
        outletId = optLongOrNull(o, "outletId"),
        keterangan = if (o.isNull("keterangan")) null else o.optString("keterangan"),
        tanggalWaktu = o.getLong("tanggalWaktu")
    )

    fun printerToJson(p: PrinterEntity) = JSONObject().apply {
        put("id", p.id); put("nama", p.nama); put("macAddress", p.macAddress)
        put("ukuranKertas", p.ukuranKertas); put("isDefault", p.isDefault)
    }
    fun printerFromJson(o: JSONObject) = PrinterEntity(
        id = o.optLong("id", 0L),
        nama = o.getString("nama"), macAddress = o.getString("macAddress"),
        ukuranKertas = o.optString("ukuranKertas", "58mm"), isDefault = o.optBoolean("isDefault", false)
    )

    fun auditLogToJson(a: com.nada.kasir.core.data.local.entity.AuditLogEntity) = JSONObject().apply {
        put("id", a.id)
        put("userId", a.userId)
        put("aksi", a.aksi)
        put("detail", a.detail)
        put("tanggalWaktu", a.tanggalWaktu)
    }

    fun auditLogFromJson(o: JSONObject) = com.nada.kasir.core.data.local.entity.AuditLogEntity(
        id = o.optLong("id", 0L),
        userId = o.getLong("userId"),
        aksi = o.getString("aksi"),
        detail = o.getString("detail"),
        tanggalWaktu = o.getLong("tanggalWaktu")
    )

    // ---- Data Phase 5 (F-02): id dipertahankan apa adanya saat restore, jadi referensi antar tabel
    // (supplierId, outletId, purchaseId, hutangPiutangId) tetap menunjuk ke baris yang benar. ----

    fun supplierToJson(s: SupplierEntity) = JSONObject().apply {
        put("id", s.id); put("nama", s.nama); put("kontak", s.kontak); put("telepon", s.telepon)
        put("alamat", s.alamat); put("npwp", s.npwp); put("catatan", s.catatan); put("aktif", s.aktif)
        put("createdAt", s.createdAt); put("updatedAt", s.updatedAt)
    }
    fun supplierFromJson(o: JSONObject) = SupplierEntity(
        id = o.optLong("id", 0L), nama = o.getString("nama"), kontak = o.optString("kontak"),
        telepon = o.optString("telepon"), alamat = o.optString("alamat"), npwp = o.optString("npwp"),
        catatan = o.optString("catatan"), aktif = o.optBoolean("aktif", true),
        createdAt = o.optLong("createdAt", 0L), updatedAt = o.optLong("updatedAt", 0L)
    )

    fun outletToJson(t: OutletEntity) = JSONObject().apply {
        put("id", t.id); put("nama", t.nama); put("kode", t.kode); put("alamat", t.alamat)
        put("telepon", t.telepon); put("aktif", t.aktif); put("createdAt", t.createdAt); put("updatedAt", t.updatedAt)
    }
    fun outletFromJson(o: JSONObject) = OutletEntity(
        id = o.optLong("id", 0L), nama = o.getString("nama"), kode = o.getString("kode"),
        alamat = o.optString("alamat"), telepon = o.optString("telepon"), aktif = o.optBoolean("aktif", true),
        createdAt = o.optLong("createdAt", 0L), updatedAt = o.optLong("updatedAt", 0L)
    )

    fun hutangPiutangToJson(h: HutangPiutangEntity) = JSONObject().apply {
        put("id", h.id); put("jenis", h.jenis); put("pelaku", h.pelaku)
        put("nomorReferensi", h.nomorReferensi ?: JSONObject.NULL)
        put("jumlah", h.jumlah); put("sisa", h.sisa); put("status", h.status)
        put("tanggalTransaksi", h.tanggalTransaksi)
        put("tanggalJatuhTempo", h.tanggalJatuhTempo ?: JSONObject.NULL)
        put("keterangan", h.keterangan ?: JSONObject.NULL)
        put("createdAt", h.createdAt); put("updatedAt", h.updatedAt)
    }
    fun hutangPiutangFromJson(o: JSONObject) = HutangPiutangEntity(
        id = o.optLong("id", 0L), jenis = o.getString("jenis"), pelaku = o.getString("pelaku"),
        nomorReferensi = if (o.isNull("nomorReferensi")) null else o.optString("nomorReferensi"),
        jumlah = o.getDouble("jumlah"), sisa = o.getDouble("sisa"), status = o.optString("status", "AKTIF"),
        tanggalTransaksi = o.getLong("tanggalTransaksi"),
        tanggalJatuhTempo = optLongOrNull(o, "tanggalJatuhTempo"),
        keterangan = if (o.isNull("keterangan")) null else o.optString("keterangan"),
        createdAt = o.optLong("createdAt", 0L), updatedAt = o.optLong("updatedAt", 0L)
    )

    fun purchaseToJson(p: PurchaseEntity) = JSONObject().apply {
        put("id", p.id); put("noPembelian", p.noPembelian); put("supplierId", p.supplierId)
        put("outletId", p.outletId ?: JSONObject.NULL); put("tanggalPembelian", p.tanggalPembelian)
        put("tanggalTerima", p.tanggalTerima ?: JSONObject.NULL); put("subtotal", p.subtotal)
        put("diskonSupplier", p.diskonSupplier); put("totalBayar", p.totalBayar)
        put("hutangSisa", p.hutangSisa); put("status", p.status.name)
        put("keterangan", p.keterangan ?: JSONObject.NULL)
        put("createdAt", p.createdAt); put("updatedAt", p.updatedAt)
    }
    fun purchaseFromJson(o: JSONObject) = PurchaseEntity(
        id = o.optLong("id", 0L), noPembelian = o.getString("noPembelian"), supplierId = o.getLong("supplierId"),
        outletId = optLongOrNull(o, "outletId"), tanggalPembelian = o.getLong("tanggalPembelian"),
        tanggalTerima = optLongOrNull(o, "tanggalTerima"), subtotal = o.getDouble("subtotal"),
        diskonSupplier = o.optDouble("diskonSupplier", 0.0), totalBayar = o.getDouble("totalBayar"),
        hutangSisa = o.optDouble("hutangSisa", 0.0), status = PurchaseStatus.valueOf(o.getString("status")),
        keterangan = if (o.isNull("keterangan")) null else o.optString("keterangan"),
        createdAt = o.optLong("createdAt", 0L), updatedAt = o.optLong("updatedAt", 0L)
    )

    fun purchaseItemToJson(i: PurchaseItemEntity) = JSONObject().apply {
        put("id", i.id); put("purchaseId", i.purchaseId); put("productId", i.productId)
        put("namaProdukSnapshot", i.namaProdukSnapshot); put("qty", i.qty); put("hargaBeli", i.hargaBeli)
        put("diskon", i.diskon); put("subtotal", i.subtotal)
    }
    fun purchaseItemFromJson(o: JSONObject) = PurchaseItemEntity(
        id = o.optLong("id", 0L), purchaseId = o.getLong("purchaseId"), productId = o.getLong("productId"),
        namaProdukSnapshot = o.getString("namaProdukSnapshot"), qty = o.getInt("qty"),
        hargaBeli = o.getDouble("hargaBeli"), diskon = o.optDouble("diskon", 0.0), subtotal = o.getDouble("subtotal")
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
