package com.nada.kasir.core.backup

import com.nada.kasir.core.data.local.entity.HutangPiutangEntity
import com.nada.kasir.core.data.local.entity.MetodePembayaran
import com.nada.kasir.core.data.local.entity.OutletEntity
import com.nada.kasir.core.data.local.entity.PaymentEntity
import com.nada.kasir.core.data.local.entity.ProductEntity
import com.nada.kasir.core.data.local.entity.PurchaseEntity
import com.nada.kasir.core.data.local.entity.PurchaseItemEntity
import com.nada.kasir.core.data.local.entity.PurchaseStatus
import com.nada.kasir.core.data.local.entity.StoreEntity
import com.nada.kasir.core.data.local.entity.SupplierEntity
import com.nada.kasir.core.data.local.entity.TransactionEntity
import com.nada.kasir.core.data.local.entity.UserEntity
import com.nada.kasir.core.data.local.entity.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class EntityJsonMapperTest {

    @Test
    fun `user bisa di-roundtrip ke json dan kembali`() {
        val user = UserEntity(
            id = 1, nama = "Admin", username = "admin",
            passwordHash = "PBKDF2:120000:salt:hash", role = UserRole.ADMIN, aktif = true
        )
        val json = EntityJsonMapper.userToJson(user)
        val hasil = EntityJsonMapper.userFromJson(json)

        assertEquals(user.id, hasil.id)
        assertEquals(user.nama, hasil.nama)
        assertEquals(user.username, hasil.username)
        assertEquals(user.passwordHash, hasil.passwordHash)
        assertEquals(user.role, hasil.role)
    }

    @Test
    fun `produk bisa di-roundtrip ke json dan kembali tanpa kehilangan data`() {
        val original = ProductEntity(
            id = 5, kodeProduk = "SKU-1", barcode = "12345", nama = "Kopi Sachet",
            categoryId = 2, hargaBeli = 1000.0, hargaJual = 1500.0, stok = 20, stokMinimum = 5
        )
        val json = EntityJsonMapper.productToJson(original)
        val hasil = EntityJsonMapper.productFromJson(json)

        assertEquals(original.id, hasil.id)
        assertEquals(original.kodeProduk, hasil.kodeProduk)
        assertEquals(original.barcode, hasil.barcode)
        assertEquals(original.hargaJual, hasil.hargaJual, 0.0)
        assertEquals(original.stok, hasil.stok)
    }

    @Test
    fun `restore toko tidak pernah mengaktifkan tampilkan harga beli di struk`() {
        // Simulasi file backup yang (sengaja/tidak sengaja) berisi true - harus tetap dipaksa false
        val json = EntityJsonMapper.storeToJson(StoreEntity(tampilkanHargaBeliStruk = false))
        json.put("tampilkanHargaBeliStruk", true)

        val hasil = EntityJsonMapper.storeFromJson(json)

        assertFalse("Harga beli tidak boleh pernah tampil di struk meski file backup memaksakannya", hasil.tampilkanHargaBeliStruk)
    }

    @Test
    fun `audit log bisa di-roundtrip ke json dan kembali`() {
        val original = com.nada.kasir.core.data.local.entity.AuditLogEntity(
            id = 12, userId = 2, aksi = "BATAL_TRANSAKSI",
            detail = "Pembatalan transaksi INV-20260924-0001", tanggalWaktu = 1700000000000L
        )
        val json = EntityJsonMapper.auditLogToJson(original)
        val hasil = EntityJsonMapper.auditLogFromJson(json)

        assertEquals(original.id, hasil.id)
        assertEquals(original.userId, hasil.userId)
        assertEquals(original.aksi, hasil.aksi)
        assertEquals(original.detail, hasil.detail)
        assertEquals(original.tanggalWaktu, hasil.tanggalWaktu)
    }

    // ---- F-02: kolom dan entitas Phase 5 ikut backup ----

    @Test
    fun `produk membawa supplierId dan outletId`() {
        val asli = ProductEntity(
            id = 5, kodeProduk = "SKU-1", barcode = null, nama = "Kopi", categoryId = null,
            hargaBeli = 1000.0, hargaJual = 1500.0, stok = 3, supplierId = 7L, outletId = 2L
        )
        val hasil = EntityJsonMapper.productFromJson(EntityJsonMapper.productToJson(asli))
        assertEquals(7L, hasil.supplierId)
        assertEquals(2L, hasil.outletId)
    }

    @Test
    fun `produk dari backup lama tanpa kunci phase 5 tetap terbaca`() {
        val json = EntityJsonMapper.productToJson(
            ProductEntity(id = 1, kodeProduk = "A", barcode = null, nama = "A", categoryId = null,
                hargaBeli = 1.0, hargaJual = 2.0, stok = 1)
        )
        json.remove("supplierId"); json.remove("outletId")
        val hasil = EntityJsonMapper.productFromJson(json)
        assertNull(hasil.supplierId)
        assertNull(hasil.outletId)
    }

    @Test
    fun `transaksi membawa hutangSisa dan outletId`() {
        val asli = TransactionEntity(
            id = 9, noTransaksi = "INV-20261005-0001", tanggalWaktu = 1700000000000L, userId = 1,
            subtotal = 50000.0, diskon = 0.0, total = 50000.0, outletId = 3L, hutangSisa = 20000.0
        )
        val hasil = EntityJsonMapper.transactionFromJson(EntityJsonMapper.transactionToJson(asli))
        assertEquals(20000.0, hasil.hutangSisa, 0.0)
        assertEquals(3L, hasil.outletId)
    }

    @Test
    fun `transaksi dari backup lama tanpa kunci phase 5 tetap terbaca`() {
        val json = EntityJsonMapper.transactionToJson(
            TransactionEntity(id = 1, noTransaksi = "X", tanggalWaktu = 1L, userId = 1,
                subtotal = 1.0, diskon = 0.0, total = 1.0)
        )
        json.remove("hutangSisa"); json.remove("outletId")
        val hasil = EntityJsonMapper.transactionFromJson(json)
        assertEquals(0.0, hasil.hutangSisa, 0.0)
        assertNull(hasil.outletId)
    }

    @Test
    fun `payment membawa hutangPiutangId`() {
        val asli = PaymentEntity(
            id = 1, transactionId = 4, metode = MetodePembayaran.HUTANG,
            jumlahDiterima = 0.0, kembalian = 0.0, hutangPiutangId = 11L
        )
        val hasil = EntityJsonMapper.paymentFromJson(EntityJsonMapper.paymentToJson(asli), 99L)
        assertEquals(11L, hasil.hutangPiutangId)
        assertEquals(99L, hasil.transactionId)
    }

    @Test
    fun `supplier outlet dan hutang piutang bisa di-roundtrip`() {
        val sup = SupplierEntity(id = 3, nama = "PT Maju", kontak = "Budi", telepon = "0812", aktif = false,
            createdAt = 10L, updatedAt = 20L)
        val outlet = OutletEntity(id = 2, nama = "Cabang A", kode = "CA", alamat = "Jl. X", createdAt = 1L, updatedAt = 2L)
        val hp = HutangPiutangEntity(id = 8, jenis = "PIUTANG", pelaku = "Ibu Sari", nomorReferensi = null,
            jumlah = 100000.0, sisa = 40000.0, status = "AKTIF", tanggalTransaksi = 5L, tanggalJatuhTempo = 9L,
            keterangan = "kasbon", createdAt = 1L, updatedAt = 2L)

        assertEquals(sup, EntityJsonMapper.supplierFromJson(EntityJsonMapper.supplierToJson(sup)))
        assertEquals(outlet, EntityJsonMapper.outletFromJson(EntityJsonMapper.outletToJson(outlet)))
        assertEquals(hp, EntityJsonMapper.hutangPiutangFromJson(EntityJsonMapper.hutangPiutangToJson(hp)))
    }

    @Test
    fun `pembelian dan itemnya bisa di-roundtrip dengan id dipertahankan`() {
        val pembelian = PurchaseEntity(id = 6, noPembelian = "PUR-20261005-0001", supplierId = 3, outletId = null,
            tanggalPembelian = 100L, tanggalTerima = null, subtotal = 90000.0, diskonSupplier = 5000.0,
            totalBayar = 85000.0, hutangSisa = 30000.0, status = PurchaseStatus.RECEIVED, keterangan = null,
            createdAt = 1L, updatedAt = 2L)
        val item = PurchaseItemEntity(id = 4, purchaseId = 6, productId = 5, namaProdukSnapshot = "Kopi",
            qty = 10, hargaBeli = 9000.0, diskon = 0.0, subtotal = 90000.0)

        assertEquals(pembelian, EntityJsonMapper.purchaseFromJson(EntityJsonMapper.purchaseToJson(pembelian)))
        assertEquals(item, EntityJsonMapper.purchaseItemFromJson(EntityJsonMapper.purchaseItemToJson(item)))
    }
}
