package com.nada.kasir.core.domain.logic

import java.util.Calendar

/**
 * Aturan murni (tanpa Android/Room) untuk Hutang & Piutang supaya bisa diuji unit.
 * Uang disimpan sebagai Double dalam rupiah utuh; selisih di bawah [TOLERANSI_RUPIAH] dianggap nol.
 */
object HutangPiutangLogic {
    const val JENIS_PIUTANG = "PIUTANG" // pelanggan berutang ke toko
    const val JENIS_HUTANG = "HUTANG"   // toko berutang (mis. ke supplier)

    const val STATUS_AKTIF = "AKTIF"
    const val STATUS_LUNAS = "LUNAS"
    const val STATUS_BATAL = "BATAL"

    const val TOLERANSI_RUPIAH = 0.5
    private const val MS_PER_HARI = 86_400_000.0

    sealed class HasilBayar {
        data class Ok(val sisaBaru: Double, val status: String) : HasilBayar()
        data class Gagal(val pesan: String) : HasilBayar()
    }

    enum class KondisiJatuhTempo { TANPA, BELUM, HARI_INI, TERLAMBAT, SELESAI }

    fun jenisValid(jenis: String) = jenis == JENIS_PIUTANG || jenis == JENIS_HUTANG

    fun labelJenis(jenis: String) = if (jenis == JENIS_HUTANG) "Hutang" else "Piutang"

    /** Pesan error bila input catatan baru tidak valid, null bila valid. */
    fun validasiCatatanBaru(jenis: String, pelaku: String, jumlah: Double): String? = when {
        !jenisValid(jenis) -> "Jenis harus Piutang atau Hutang."
        pelaku.isBlank() -> "Nama pihak wajib diisi."
        jumlah.isNaN() || jumlah <= 0.0 -> "Jumlah harus lebih dari 0."
        else -> null
    }

    /** Hasil bila [jumlah] dibayarkan pada catatan dengan [sisa] dan [status] saat ini. */
    fun hitungPembayaran(sisa: Double, status: String, jumlah: Double): HasilBayar {
        if (status != STATUS_AKTIF) {
            val kata = if (status == STATUS_LUNAS) "sudah lunas" else "sudah dibatalkan"
            return HasilBayar.Gagal("Catatan ini $kata.")
        }
        if (jumlah.isNaN() || jumlah <= 0.0) return HasilBayar.Gagal("Jumlah pembayaran harus lebih dari 0.")
        if (jumlah > sisa + TOLERANSI_RUPIAH) return HasilBayar.Gagal("Jumlah pembayaran melebihi sisa.")
        val sisaBaru = (sisa - jumlah).let { if (it < TOLERANSI_RUPIAH) 0.0 else it }
        return HasilBayar.Ok(sisaBaru, if (sisaBaru == 0.0) STATUS_LUNAS else STATUS_AKTIF)
    }

    /** Uang muka untuk penjualan piutang di Kasir harus >= 0 dan KURANG dari total. */
    fun validasiUangMuka(uangMuka: Double, total: Double): String? = when {
        uangMuka.isNaN() || uangMuka < 0.0 -> "Uang muka tidak boleh negatif."
        uangMuka >= total - TOLERANSI_RUPIAH -> "Uang muka sudah menutup total belanja. Pakai metode pembayaran lain."
        else -> null
    }

    fun awalHari(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Selisih hari kalender; negatif bila jatuh tempo sudah lewat. */
    fun selisihHari(jatuhTempo: Long, sekarang: Long): Int =
        Math.round((awalHari(jatuhTempo) - awalHari(sekarang)) / MS_PER_HARI).toInt()

    fun kondisiJatuhTempo(status: String, jatuhTempo: Long?, sekarang: Long): KondisiJatuhTempo = when {
        status != STATUS_AKTIF -> KondisiJatuhTempo.SELESAI
        jatuhTempo == null -> KondisiJatuhTempo.TANPA
        else -> when {
            selisihHari(jatuhTempo, sekarang) < 0 -> KondisiJatuhTempo.TERLAMBAT
            selisihHari(jatuhTempo, sekarang) == 0 -> KondisiJatuhTempo.HARI_INI
            else -> KondisiJatuhTempo.BELUM
        }
    }

    /** Jatuh tempo [hari] hari dari [sekarang]; null bila tanpa jatuh tempo. */
    fun jatuhTempoDariHari(hari: Int?, sekarang: Long): Long? =
        if (hari == null || hari <= 0) null else awalHari(sekarang) + hari * 86_400_000L
}
