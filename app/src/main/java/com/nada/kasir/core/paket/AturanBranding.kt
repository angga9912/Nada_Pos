package com.nada.kasir.core.paket

import com.nada.kasir.core.data.local.entity.StoreEntity

/**
 * Logo toko adalah bagian "Custom Branding Toko" (Custom ke atas), bersama warna aplikasi.
 *
 * Di paket yang belum mencakupnya, data logo TIDAK dihapus (file dan pengaturan tetap tersimpan, jadi
 * logo muncul lagi begitu paket naik atau trial dipakai); logo hanya tidak ditampilkan dan tidak dicetak.
 * Semua titik yang menampilkan/mencetak logo (struk cetak, preview struk, header dashboard) memakai fungsi
 * ini, jadi aturannya hanya ada di satu tempat. Pada Basic struk mencetak nama toko biasa, tanpa kurung
 * "[ NAMA ]" yang menandakan tempat logo.
 */
fun StoreEntity.sesuaiPaket(paket: PaketAplikasi): StoreEntity =
    if (paket.mengizinkan(FiturPaket.LOGO_TOKO)) this
    else copy(logoPath = null, tampilkanLogoStruk = false)
