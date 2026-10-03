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
    fun bukaWhatsAppUpgrade(context: Context, paketTujuan: PaketAplikasi) {
        val pesan = "Halo, saya ingin upgrade Nada POS ke paket ${paketTujuan.label}. Mohon info lebih lanjut."
        bukaWhatsApp(context, pesan)
    }

    fun bukaWhatsApp(context: Context, pesan: String) {
        val uri = Uri.parse("https://wa.me/$NOMOR_WA?text=${Uri.encode(pesan)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    }
}
