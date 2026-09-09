package com.nada.kasir.branding

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeConfigTest {

    @Test
    fun `warna hex valid 6 digit menghasilkan primary color yang sesuai`() {
        val scheme = ThemeConfig.buatColorScheme("#1565C0")
        assertEquals(Color(0xFF1565C0), scheme.primary)
    }

    @Test
    fun `warna hex tidak valid jatuh ke warna fallback`() {
        val scheme = ThemeConfig.buatColorScheme("bukan-warna")
        assertEquals(Color(0xFF2E7D32), scheme.primary)
    }
}
