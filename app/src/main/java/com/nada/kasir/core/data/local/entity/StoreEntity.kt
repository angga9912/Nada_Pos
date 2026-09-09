package com.nada.kasir.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Identitas toko. SEMUA identitas ditampilkan pada struk & UI harus dibaca dari sini.
 * Jangan pernah hardcode nama toko/logo/warna di kode UI (lihat poin 2 & 21 brief).
 */
@Entity(tableName = "stores")
data class StoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nama: String = "TOKO DEMO",
    val alamat: String = "",
    val whatsapp: String = "",
    val telepon: String = "",
    val pemilik: String = "",
    val slogan: String = "",
    val footerStruk: String = "Terima kasih telah berbelanja.",
    val formatNoTransaksi: String = "INV-{yyyyMMdd}-{XXXX}",
    val mataUang: String = "Rp",
    val ukuranKertas: String = "58mm", // "58mm" atau "80mm"
    val logoPath: String? = null,
    val warnaUtama: String = "#2E7D32",
    val tampilkanLogoStruk: Boolean = true,
    val tampilkanAlamatStruk: Boolean = true,
    val tampilkanWaStruk: Boolean = true,
    val tampilkanHargaBeliStruk: Boolean = false, // WAJIB false secara default (poin 10)
    val tampilkanDiskonStruk: Boolean = true,
    val ukuranFontStruk: String = "NORMAL"
)
