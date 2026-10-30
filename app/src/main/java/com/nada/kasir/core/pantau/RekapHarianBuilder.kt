package com.nada.kasir.core.pantau

import com.nada.kasir.core.data.local.entity.OutletEntity
import com.nada.kasir.core.data.repository.ReportRepository
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Membuat [RekapHarian] dari laporan yang SUDAH ada ([ReportRepository]) - tidak ada query baru dan
 * tidak ada perubahan pada core.
 *
 * Satu HP = satu cabang: semua transaksi di HP ini ikut direkap (outletId = null pada query),
 * sedangkan identitas cabang diambil dari outlet aktif HP ini ([outlet]). Bila belum ada outlet aktif,
 * identitasnya "PUSAT".
 */
@Singleton
class RekapHarianBuilder @Inject constructor(
    private val reportRepository: ReportRepository
) {
    suspend fun bangun(
        tanggal: LocalDate,
        outlet: OutletEntity?,
        zona: ZoneId = ZoneId.systemDefault(),
        sekarang: Long = System.currentTimeMillis()
    ): RekapHarian {
        val (awal, akhir) = RekapHarianLogic.rentangHari(tanggal, zona)
        val laporan = reportRepository.laporanPeriode(awal, akhir, sertakanProdukTerlaris = true, outletId = null)
        val kodeCabang = outlet?.kode?.trim().orEmpty().ifEmpty { RekapHarianLogic.KODE_TANPA_CABANG }
        return RekapHarian(
            tanggal = tanggal.toString(), // ISO yyyy-MM-dd
            kodeCabang = kodeCabang,
            kodeDokumen = RekapHarianLogic.bersihkanKode(kodeCabang),
            namaCabang = outlet?.nama ?: "Pusat",
            jumlahTransaksi = laporan.jumlahTransaksi,
            totalPenjualan = laporan.totalPenjualan,
            totalDiskon = laporan.totalDiskon,
            produkTerjual = laporan.produkTerjual,
            estimasiKeuntungan = laporan.estimasiKeuntungan,
            metodePembayaran = laporan.ringkasanMetodePembayaran.map {
                RekapMetode(it.metode, it.total, it.jumlahTransaksi)
            },
            produkTerlaris = laporan.produkTerlaris.map { RekapProduk(it.nama, it.totalQty, it.totalOmzet) },
            diperbaruiPada = sekarang
        )
    }
}
