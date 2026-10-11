package com.nada.kasir.core.domain.logic

import com.nada.kasir.core.data.local.entity.PurchaseStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Aturan murni (tanpa Android/Room) untuk Pembelian ke Supplier (Phase 5 tahap 2), supaya bisa diuji unit.
 * Uang disimpan sebagai Double dalam rupiah utuh; selisih di bawah [TOLERANSI_RUPIAH] dianggap nol.
 */
object PembelianLogic {
    const val TOLERANSI_RUPIAH = HutangPiutangLogic.TOLERANSI_RUPIAH
    private const val AWALAN_NOMOR = "PUR-"

    /** Satu baris barang dalam pembelian. [diskon] adalah potongan untuk seluruh baris (bukan per unit). */
    data class ItemInput(val productId: Long, val qty: Int, val hargaBeli: Double, val diskon: Double = 0.0)

    data class Ringkasan(val subtotal: Double, val diskonSupplier: Double, val total: Double)

    fun subtotalItem(item: ItemInput): Double = item.qty * item.hargaBeli - item.diskon

    fun validasiItem(item: ItemInput): String? = when {
        item.qty <= 0 -> "Jumlah barang harus lebih dari 0."
        item.hargaBeli.isNaN() || item.hargaBeli < 0.0 -> "Harga beli tidak valid."
        item.diskon.isNaN() || item.diskon < 0.0 -> "Diskon barang tidak valid."
        item.diskon > item.qty * item.hargaBeli + TOLERANSI_RUPIAH -> "Diskon barang melebihi harganya."
        else -> null
    }

    fun hitungRingkasan(items: List<ItemInput>, diskonSupplier: Double): Ringkasan {
        val subtotal = items.sumOf { subtotalItem(it) }
        return Ringkasan(subtotal = subtotal, diskonSupplier = diskonSupplier, total = subtotal - diskonSupplier)
    }

    /** Pesan error bila input pembelian tidak valid, null bila valid. Supplier dan produk dicek di repository. */
    fun validasiPembelian(items: List<ItemInput>, diskonSupplier: Double, bayarSekarang: Double): String? {
        if (items.isEmpty()) return "Tambahkan minimal satu barang."
        if (items.map { it.productId }.toSet().size != items.size) return "Barang yang sama muncul lebih dari sekali."
        items.firstNotNullOfOrNull { validasiItem(it) }?.let { return it }
        val subtotal = items.sumOf { subtotalItem(it) }
        if (diskonSupplier.isNaN() || diskonSupplier < 0.0) return "Diskon supplier tidak valid."
        if (diskonSupplier > subtotal + TOLERANSI_RUPIAH) return "Diskon supplier melebihi subtotal."
        val total = subtotal - diskonSupplier
        if (bayarSekarang.isNaN() || bayarSekarang < 0.0) return "Pembayaran tidak boleh negatif."
        if (bayarSekarang > total + TOLERANSI_RUPIAH) return "Pembayaran melebihi total pembelian."
        return null
    }

    /** Sisa yang menjadi hutang ke supplier; selisih sangat kecil dianggap lunas. */
    fun sisaHutang(total: Double, bayarSekarang: Double): Double =
        (total - bayarSekarang).let { if (it < TOLERANSI_RUPIAH) 0.0 else it }

    // ---- Nomor pembelian: PUR-YYYYMMDD-0001 (urut per hari, menggantikan angka acak) ----

    fun awalanNomor(millis: Long): String =
        AWALAN_NOMOR + SimpleDateFormat("yyyyMMdd", Locale("id", "ID")).format(Date(millis)) + "-"

    /** Urutan berikutnya setelah [nomorTerakhir] (null = belum ada pembelian pada awalan itu). */
    fun urutBerikutnya(nomorTerakhir: String?, awalan: String): Int {
        if (nomorTerakhir == null || !nomorTerakhir.startsWith(awalan)) return 1
        val sufiks = nomorTerakhir.removePrefix(awalan).takeWhile { it.isDigit() }
        return (sufiks.toIntOrNull() ?: 0) + 1
    }

    fun formatNomor(awalan: String, urut: Int): String = awalan + urut.toString().padStart(4, '0')

    fun labelStatus(status: PurchaseStatus): String = when (status) {
        PurchaseStatus.PENDING -> "Menunggu barang"
        PurchaseStatus.RECEIVED -> "Diterima"
        PurchaseStatus.CANCELLED -> "Dibatalkan"
    }
}
