package com.nada.kasir.core.pantau

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PantauCabangKonfigurasiTest {

    @Test
    fun `siap kirim hanya bila saklar menyala dan kode toko valid`() {
        assertTrue(PantauCabangPengaturan.Konfigurasi(aktif = true, tokoId = "abcDEF123456").siapKirim)
        assertFalse(PantauCabangPengaturan.Konfigurasi(aktif = false, tokoId = "abcDEF123456").siapKirim)
        assertFalse(PantauCabangPengaturan.Konfigurasi(aktif = true, tokoId = "").siapKirim)
        assertFalse(PantauCabangPengaturan.Konfigurasi(aktif = true, tokoId = "pendek").siapKirim)
        assertFalse(PantauCabangPengaturan.Konfigurasi(aktif = true, tokoId = "ada spasi di sini").siapKirim)
    }

    @Test
    fun `konfigurasi bawaan belum siap kirim`() {
        assertFalse(PantauCabangPengaturan.Konfigurasi().siapKirim)
    }
}
