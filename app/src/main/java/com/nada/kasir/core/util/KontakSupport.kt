package com.nada.kasir.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.nada.kasir.core.paket.PaketAplikasi

/**
 * Kontak resmi untuk permintaan upgrade paket / bantuan pelanggan.
 * Nomor disimpan di satu tempat agar mudah diubah tanpa menyentuh layar UI.
 */
object KontakSupport {
    // Format internasional (62) tanpa tanda "+", sesuai kebutuhan link wa.me
    const val NOMOR_WA = "628131766720"

    /**
     * Membuka WhatsApp dengan pesan yang sudah diisi otomatis sesuai paket
     * yang ingin dituju, supaya penjual langsung tahu konteks tanpa pembeli
     * perlu mengetik ulang.
     */
    fun bukaWhatsAppUpgrade(context: Context, paketTujuan: PaketAplikasi, idPerangkat: String? = null) {
        val pesan = "Halo, saya ingin upgrade Nada POS ke paket ${paketTujuan.label}. Mohon info lebih lanjut." +
            blokIdPerangkat(idPerangkat)
        bukaWhatsApp(context, pesan)
    }

    /**
     * Minta kode aktivasi TRIAL (bukan beli langsung). Durasi "14 hari" di teks pesan
     * ini cuma label buat penjual - kode trial-nya sendiri di-generate manual lewat
     * workflow "Generate Kode Lisensi", jadi durasi sebenarnya ditentukan penjual saat
     * generate, tidak dipaksa dari sini. Ganti angkanya di sini kalau kebijakan durasi
     * trial berubah.
     */
    fun bukaWhatsAppTrial(context: Context, paketTujuan: PaketAplikasi, idPerangkat: String? = null) {
        val pesan = "Halo, saya ingin coba TRIAL 14 hari Nada POS paket ${paketTujuan.label}. Boleh minta kode aktivasi trial-nya?" +
            blokIdPerangkat(idPerangkat)
        bukaWhatsApp(context, pesan)
    }

    /** Kode lisensi terikat ke satu HP, jadi penjual butuh ID Perangkat untuk membuatkan kodenya. */
    private fun blokIdPerangkat(idPerangkat: String?): String =
        if (idPerangkat.isNullOrBlank()) ""
        else "\n\nID Perangkat saya: $idPerangkat\n(Mohon kode aktivasi dibuat untuk ID ini.)"

    fun bukaWhatsApp(context: Context, pesan: String) {
        val uri = Uri.parse("https://wa.me/$NOMOR_WA?text=${Uri.encode(pesan)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    }
}
