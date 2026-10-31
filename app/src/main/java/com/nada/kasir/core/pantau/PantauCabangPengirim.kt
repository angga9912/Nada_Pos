package com.nada.kasir.core.pantau

import com.nada.kasir.core.data.repository.OutletRepository
import com.nada.kasir.core.paket.FiturPaket
import com.nada.kasir.core.paket.PaketRepository
import com.nada.kasir.core.paket.mengizinkan
import com.nada.kasir.core.util.Result
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Hasil satu putaran pengiriman rekap. [jumlahHari] = berapa hari yang berhasil terkirim. */
data class HasilPengiriman(val berhasil: Boolean, val pesan: String, val jumlahHari: Int = 0)

/**
 * Menghitung rekap hari ini + 2 hari sebelumnya dari data lokal, lalu mengirimnya ke Firebase.
 * Dipakai tombol "Kirim rekap sekarang" ([kirimSekarang]) dan pekerja berkala ([kirimOtomatis]).
 * Idempotent: dokumen per tanggal/cabang ditimpa, jadi aman dipanggil berulang.
 */
@Singleton
class PantauCabangPengirim @Inject constructor(
    private val pengaturan: PantauCabangPengaturan,
    private val repository: PantauCabangRepository,
    private val builder: RekapHarianBuilder,
    private val outletRepository: OutletRepository,
    private val paketRepository: PaketRepository
) {
    /** Dari pekerja berkala: tidak melakukan apa-apa bila saklar "otomatis" mati. */
    suspend fun kirimOtomatis(): HasilPengiriman {
        if (!pengaturan.baca().aktif) return HasilPengiriman(true, "Pengiriman otomatis nonaktif.")
        return kirimSekarang()
    }

    /** Dari tombol manual: tidak bergantung pada saklar otomatis, tetapi butuh paket Pro dan kode toko valid. */
    suspend fun kirimSekarang(
        hariIni: LocalDate = LocalDate.now(),
        zona: ZoneId = ZoneId.systemDefault()
    ): HasilPengiriman {
        if (!paketRepository.observePaketAktif().first().mengizinkan(FiturPaket.PANTAU_CABANG)) {
            return HasilPengiriman(false, "Pantau Cabang hanya tersedia di paket Pro.")
        }
        val konfig = pengaturan.baca()
        RekapHarianLogic.validasiTokoId(konfig.tokoId)?.let { return HasilPengiriman(false, it) }

        val outlet = outletRepository.observeOutletAktifEfektif().first()
        var terkirim = 0
        for (tanggal in RekapHarianLogic.tanggalUntukDikirim(hariIni)) {
            val rekap = builder.bangun(tanggal, outlet, zona)
            when (val hasil = repository.kirim(konfig.tokoId, rekap)) {
                is Result.Success -> terkirim++
                is Result.Failure -> {
                    pengaturan.catatStatus(hasil.error.pesan)
                    return HasilPengiriman(false, hasil.error.pesan, terkirim)
                }
            }
        }
        pengaturan.catatKirim(System.currentTimeMillis())
        pengaturan.catatStatus(null)
        return HasilPengiriman(true, "Rekap $terkirim hari berhasil dikirim.", terkirim)
    }
}
