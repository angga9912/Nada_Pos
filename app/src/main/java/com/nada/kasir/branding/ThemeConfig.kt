package com.nada.kasir.branding

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Menghasilkan skema warna Compose secara RUNTIME dari StoreEntity.warnaUtama
 * (poin 2 & 21). TIDAK ADA warna yang hardcode di kode UI - satu source code
 * bisa dipakai untuk banyak toko dengan warna berbeda cukup dari data toko.
 */
object ThemeConfig {

    /** Warna default kalau data toko belum ada / hex tidak valid. */
    private val WARNA_FALLBACK = Color(0xFF2E7D32)

    fun buatColorScheme(warnaHex: String): ColorScheme {
        val primary = parseHexAman(warnaHex)
        return lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            secondary = primary.copy(alpha = 0.7f),
            tertiary = primary
        )
    }

    private fun parseHexAman(hex: String): Color {
        return try {
            val bersih = hex.trim().removePrefix("#")
            val nilai = bersih.toLong(16)
            when (bersih.length) {
                6 -> Color(0xFF000000 or nilai)
                8 -> Color(nilai)
                else -> WARNA_FALLBACK
            }
        } catch (e: Exception) {
            WARNA_FALLBACK
        }
    }

    /** Pilihan warna preset yang ditawarkan di Pengaturan Toko - tetap bisa custom hex manual. */
    val PRESET_WARNA = listOf(
        "#2E7D32" to "Hijau", "#1565C0" to "Biru", "#C62828" to "Merah",
        "#EF6C00" to "Oranye", "#6A1B9A" to "Ungu", "#00838F" to "Toska"
    )
}
