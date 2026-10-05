package com.nada.kasir.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PemetaanPathFotoTest {

    private val basis = "/data/user/0/com.nada.kasir/files"
    private val entri = listOf("backup.json", "produk_foto/foto_a.png", "branding/logo_toko.png")

    @Test
    fun `path milik aplikasi ini dipetakan ke relatif`() {
        val hasil = PemetaanPathFoto.cariRelatif("$basis/produk_foto/foto_a.png", entri, basis)
        assertEquals("produk_foto/foto_a.png", hasil)
    }

    @Test
    fun `backup dari APK demo dipulihkan ke APK full`() {
        // F-07: package asal berbeda dengan package tujuan.
        val lama = "/data/user/0/com.nada.kasir.demo/files/produk_foto/foto_a.png"
        assertEquals("produk_foto/foto_a.png", PemetaanPathFoto.cariRelatif(lama, entri, basis))
    }

    @Test
    fun `backup dari profil pengguna Android lain dipulihkan`() {
        val lama = "/data/user/10/com.nada.kasir/files/branding/logo_toko.png"
        assertEquals("branding/logo_toko.png", PemetaanPathFoto.cariRelatif(lama, entri, basis))
    }

    @Test
    fun `path data data lama juga dikenali`() {
        val lama = "/data/data/com.nada.kasir/files/produk_foto/foto_a.png"
        assertEquals("produk_foto/foto_a.png", PemetaanPathFoto.cariRelatif(lama, entri, basis))
    }

    @Test
    fun `entri terpanjang yang menang bila ada dua yang cocok`() {
        val daftar = listOf("foto.png", "produk_foto/foto.png")
        val lama = "/data/user/0/lain/files/produk_foto/foto.png"
        assertEquals("produk_foto/foto.png", PemetaanPathFoto.cariRelatif(lama, daftar, basis))
    }

    @Test
    fun `path tanpa entri zip yang cocok menjadi null`() {
        val lama = "/data/user/0/com.nada.kasir.demo/files/produk_foto/tidak_ada.png"
        assertNull(PemetaanPathFoto.cariRelatif(lama, entri, basis))
    }

    @Test
    fun `path dengan titik titik ditolak`() {
        assertNull(PemetaanPathFoto.cariRelatif("$basis/../databases/nada.db", entri, basis))
        assertNull(PemetaanPathFoto.cariRelatif("/x/files/../produk_foto/foto_a.png", entri, basis))
    }

    @Test
    fun `nama entri berbahaya tidak pernah dipilih`() {
        val jahat = listOf("../databases/nada.db", "/etc/passwd", "a//b.png")
        assertNull(PemetaanPathFoto.cariRelatif("/data/user/0/lain/files/../databases/nada.db", jahat, basis))
        assertNull(PemetaanPathFoto.cariRelatif("/data/user/0/lain/files//etc/passwd", jahat, basis))
        assertNull(PemetaanPathFoto.cariRelatif("/data/user/0/lain/files/a//b.png", jahat, basis))
    }

    @Test
    fun `backup json bukan foto`() {
        assertNull(PemetaanPathFoto.cariRelatif("/data/user/0/lain/files/backup.json", listOf("backup.json"), basis))
    }

    @Test
    fun `awalan mirip tetapi folder berbeda tidak dianggap milik aplikasi ini`() {
        // "/files2" bukan "/files": jangan dipetakan lewat awalan.
        val lama = "/data/user/0/com.nada.kasir/files2/produk_foto/foto_a.png"
        assertNull(PemetaanPathFoto.cariRelatif(lama, entri, basis))
    }

    @Test
    fun `path kosong atau null menjadi null`() {
        assertNull(PemetaanPathFoto.cariRelatif(null, entri, basis))
        assertNull(PemetaanPathFoto.cariRelatif("  ", entri, basis))
    }
}
