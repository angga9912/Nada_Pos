package com.nada.kasir.core.pantau

import java.time.LocalDate
import java.time.ZoneId

/** Ringkasan satu metode pembayaran dalam rekap harian. */
data class RekapMetode(val metode: String, val total: Double, val jumlahTransaksi: Int)

/** Satu produk terlaris dalam rekap harian. */
data class RekapProduk(val nama: String, val qty: Int, val omzet: Double)

/**
 * Rekap penjualan SATU HARI dari SATU cabang (HP) - dikirim ke Firebase supaya admin bisa memantau
 * dari jauh (modul Pantau Cabang, paket Pro). Hanya angka ringkasan; tidak ada data pelanggan,
 * password, atau harga beli per produk.
 *
 * @property tanggal format "yyyy-MM-dd" (zona waktu HP cabang).
 * @property kodeDokumen kode cabang yang sudah dibersihkan ([RekapHarianLogic.bersihkanKode]); dipakai
 *   di id dokumen dan diperiksa aturan keamanan Firestore.
 */
data class RekapHarian(
    val tanggal: String,
    val kodeCabang: String,
    val kodeDokumen: String,
    val namaCabang: String,
    val jumlahTransaksi: Int,
    val totalPenjualan: Double,
    val totalDiskon: Double,
    val produkTerjual: Int,
    val estimasiKeuntungan: Double,
    val metodePembayaran: List<RekapMetode>,
    val produkTerlaris: List<RekapProduk>,
    val diperbaruiPada: Long
)

/**
 * Aturan murni (tanpa Android/Firebase) untuk rekap harian, supaya mudah dites di JVM.
 */
object RekapHarianLogic {
    /** Kode cabang bila HP belum memilih outlet aktif. */
    const val KODE_TANPA_CABANG = "PUSAT"

    /** Berapa hari ke belakang ikut dikirim ulang (menangkap hari kemarin & HP yang sempat offline). */
    const val HARI_KE_BELAKANG_DEFAULT = 2

    /**
     * Kode cabang aman untuk id dokumen Firestore: hanya huruf besar, angka, "-" dan "_".
     * Karakter lain (spasi, "/", titik, dll) menjadi "_". Kosong -> [KODE_TANPA_CABANG].
     */
    fun bersihkanKode(kode: String?): String {
        val bersih = kode.orEmpty().trim().uppercase()
            .map { c -> if (c in 'A'..'Z' || c in '0'..'9' || c == '-' || c == '_') c else '_' }
            .joinToString("")
        return bersih.ifEmpty { KODE_TANPA_CABANG }
    }

    /** Id dokumen rekap: "2026-10-30_CBG-01". Satu dokumen per tanggal per cabang (kirim ulang = timpa). */
    fun idDokumen(tanggal: String, kodeDokumen: String): String = "${tanggal}_$kodeDokumen"

    /** Awal dan akhir hari (epoch millis, inklusif) untuk query laporan. */
    fun rentangHari(tanggal: LocalDate, zona: ZoneId): Pair<Long, Long> {
        val awal = tanggal.atStartOfDay(zona).toInstant().toEpochMilli()
        val akhir = tanggal.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli() - 1
        return awal to akhir
    }

    /** Hari ini dan [hariKeBelakang] hari sebelumnya, urut dari yang terlama. */
    fun tanggalUntukDikirim(hariIni: LocalDate, hariKeBelakang: Int = HARI_KE_BELAKANG_DEFAULT): List<LocalDate> =
        (hariKeBelakang.coerceAtLeast(0) downTo 0).map { hariIni.minusDays(it.toLong()) }

    /** Bentuk Map yang aman disimpan di Firestore (hanya String/Number/List/Map). */
    fun keMap(r: RekapHarian): Map<String, Any> = mapOf(
        "tanggal" to r.tanggal,
        "kodeCabang" to r.kodeCabang,
        "kodeDokumen" to r.kodeDokumen,
        "namaCabang" to r.namaCabang,
        "jumlahTransaksi" to r.jumlahTransaksi.toLong(),
        "totalPenjualan" to r.totalPenjualan,
        "totalDiskon" to r.totalDiskon,
        "produkTerjual" to r.produkTerjual.toLong(),
        "estimasiKeuntungan" to r.estimasiKeuntungan,
        "metodePembayaran" to r.metodePembayaran.map {
            mapOf("metode" to it.metode, "total" to it.total, "jumlahTransaksi" to it.jumlahTransaksi.toLong())
        },
        "produkTerlaris" to r.produkTerlaris.map {
            mapOf("nama" to it.nama, "qty" to it.qty.toLong(), "omzet" to it.omzet)
        },
        "diperbaruiPada" to r.diperbaruiPada
    )

    /** Kebalikan [keMap]. null bila field wajib (tanggal/kodeDokumen) hilang atau rusak. */
    fun dariMap(m: Map<String, Any?>): RekapHarian? {
        val tanggal = m["tanggal"] as? String ?: return null
        val kodeDokumen = m["kodeDokumen"] as? String ?: return null
        fun angka(k: String): Double = (m[k] as? Number)?.toDouble() ?: 0.0
        fun bulat(k: String): Int = (m[k] as? Number)?.toInt() ?: 0
        val metode = (m["metodePembayaran"] as? List<*>).orEmpty().mapNotNull { e ->
            val x = e as? Map<*, *> ?: return@mapNotNull null
            RekapMetode(
                metode = x["metode"] as? String ?: return@mapNotNull null,
                total = (x["total"] as? Number)?.toDouble() ?: 0.0,
                jumlahTransaksi = (x["jumlahTransaksi"] as? Number)?.toInt() ?: 0
            )
        }
        val produk = (m["produkTerlaris"] as? List<*>).orEmpty().mapNotNull { e ->
            val x = e as? Map<*, *> ?: return@mapNotNull null
            RekapProduk(
                nama = x["nama"] as? String ?: return@mapNotNull null,
                qty = (x["qty"] as? Number)?.toInt() ?: 0,
                omzet = (x["omzet"] as? Number)?.toDouble() ?: 0.0
            )
        }
        return RekapHarian(
            tanggal = tanggal,
            kodeCabang = m["kodeCabang"] as? String ?: kodeDokumen,
            kodeDokumen = kodeDokumen,
            namaCabang = m["namaCabang"] as? String ?: kodeDokumen,
            jumlahTransaksi = bulat("jumlahTransaksi"),
            totalPenjualan = angka("totalPenjualan"),
            totalDiskon = angka("totalDiskon"),
            produkTerjual = bulat("produkTerjual"),
            estimasiKeuntungan = angka("estimasiKeuntungan"),
            metodePembayaran = metode,
            produkTerlaris = produk,
            diperbaruiPada = (m["diperbaruiPada"] as? Number)?.toLong() ?: 0L
        )
    }

    private val POLA_TOKO_ID = Regex("^[A-Za-z0-9_-]{6,128}$")

    /** Kode toko = id dokumen Firestore (biasanya uid akun admin). Null bila valid, selain itu pesan untuk pengguna. */
    fun validasiTokoId(mentah: String): String? {
        val t = mentah.trim()
        if (t.isEmpty()) return "Kode toko wajib diisi."
        if (!POLA_TOKO_ID.matches(t)) return "Kode toko hanya boleh huruf, angka, \"-\" dan \"_\" (minimal 6 karakter)."
        return null
    }

    /** Gabungan beberapa rekap (mis. semua cabang di satu hari) untuk tampilan admin. */
    fun totalPenjualan(daftar: List<RekapHarian>): Double = daftar.sumOf { it.totalPenjualan }
    fun totalTransaksi(daftar: List<RekapHarian>): Int = daftar.sumOf { it.jumlahTransaksi }
}
