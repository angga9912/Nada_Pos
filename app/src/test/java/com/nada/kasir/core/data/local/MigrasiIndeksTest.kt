package com.nada.kasir.core.data.local

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Menjaga migrasi 6 -> 7 (F-04) tetap cocok dengan entity tanpa perlu emulator.
 *
 * Room menolak membuka database bila indeks hasil migrasi berbeda dari yang diharapkan entity
 * (nama, unik/tidak, kolom). Tes ini membandingkan SQL di [SQL_MIGRASI_6_7] dengan skema yang diekspor Room saat
 * build (app/schemas/.../7.json), jadi salah ketik atau indeks yang terlupa ketahuan di CI.
 */
class MigrasiIndeksTest {

    private data class Indeks(val nama: String, val tabel: String, val unik: Boolean, val kolom: List<String>)

    private val fileSkema = File("schemas/com.nada.kasir.core.data.local.AppDatabase/7.json")

    /** Indeks yang sudah dibuat migrasi lebih lama dan karenanya tidak ada di [SQL_MIGRASI_6_7]. */
    private val indeksLama = setOf("index_hutang_piutang_pembayaran_hutangPiutangId")

    private val polaSql = Regex("CREATE (UNIQUE )?INDEX IF NOT EXISTS `([^`]+)` ON `([^`]+)` \\(([^)]*)\\)")

    private fun dariMigrasi(): List<Indeks> = SQL_MIGRASI_6_7.mapNotNull { sql ->
        polaSql.matchEntire(sql)?.let { m ->
            Indeks(
                nama = m.groupValues[2], tabel = m.groupValues[3], unik = m.groupValues[1].isNotEmpty(),
                kolom = m.groupValues[4].split(",").map { it.trim().trim('`') }
            )
        }
    }

    private fun skema(): JSONObject {
        assertTrue(
            "Skema Room tidak ditemukan di ${fileSkema.absolutePath}. Jalankan build (KSP) dulu agar 7.json dibuat.",
            fileSkema.exists()
        )
        return JSONObject(fileSkema.readText()).getJSONObject("database")
    }

    private fun dariSkema(): List<Indeks> {
        val hasil = mutableListOf<Indeks>()
        val entities = skema().getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            val indices = e.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                val ix = indices.getJSONObject(j)
                val kolom = ix.getJSONArray("columnNames")
                hasil += Indeks(
                    nama = ix.getString("name"), tabel = e.getString("tableName"), unik = ix.getBoolean("unique"),
                    kolom = (0 until kolom.length()).map { kolom.getString(it) }
                )
            }
        }
        return hasil
    }

    @Test
    fun `skema yang diekspor berversi 7`() {
        assertEquals(7, skema().getInt("version"))
    }

    @Test
    fun `setiap pernyataan indeks di migrasi 6 ke 7 terbaca oleh pola uji`() {
        assertEquals(
            "Semua CREATE INDEX di migrasi harus berformat standar Room",
            SQL_MIGRASI_6_7.count { it.startsWith("CREATE") }, dariMigrasi().size
        )
    }

    @Test
    fun `semua indeks baru di skema dibuat migrasi dengan definisi yang sama`() {
        val dibuatMigrasi = dariMigrasi().associateBy { it.nama }
        val diharapkan = dariSkema().filter { it.nama !in indeksLama }
        assertTrue("Skema seharusnya punya indeks baru", diharapkan.isNotEmpty())
        diharapkan.forEach { ix ->
            assertEquals("Migrasi 6->7 belum membuat indeks ${ix.nama}", ix, dibuatMigrasi[ix.nama])
        }
    }

    @Test
    fun `migrasi tidak membuat indeks yang tidak ada di entity`() {
        val dikenal = dariSkema().map { it.nama }.toSet()
        dariMigrasi().forEach { ix ->
            assertTrue("Indeks ${ix.nama} dibuat migrasi tetapi tidak ada di entity manapun", ix.nama in dikenal)
        }
    }

    @Test
    fun `indeks dari migrasi lama tetap ada di skema`() {
        val nama = dariSkema().map { it.nama }.toSet()
        indeksLama.forEach { assertTrue("Indeks $it hilang dari skema", it in nama) }
    }

    @Test
    fun `hanya nomor transaksi yang dibuat unik`() {
        val unik = dariSkema().filter { it.unik }.map { it.nama }
        assertEquals(listOf("index_transactions_noTransaksi"), unik)
    }
}
