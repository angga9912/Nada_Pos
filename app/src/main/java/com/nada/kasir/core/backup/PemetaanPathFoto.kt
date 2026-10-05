package com.nada.kasir.core.backup

/**
 * Menentukan path RELATIF (terhadap filesDir) sebuah foto/logo yang tercatat di backup (F-07).
 *
 * Di JSON backup, `fotoPath`/`logoPath` tersimpan sebagai path ABSOLUT milik HP/aplikasi asal,
 * misalnya "/data/user/0/com.nada.kasir/files/produk_foto/foto_x.png". Sedangkan di dalam zip,
 * file foto disimpan dengan nama relatif ("produk_foto/foto_x.png").
 *
 * Dulu restore hanya mengenali path yang berawalan filesDir HP SAAT INI. Akibatnya backup dari
 * package lain (mis. APK demo `com.nada.kasir.demo` dipulihkan ke `com.nada.kasir`) atau dari
 * profil pengguna Android lain (`/data/user/10/...`) tidak dikenali dan fotonya tidak ditulis.
 *
 * Sekarang pencocokan dilakukan terhadap nama entri yang BENAR-BENAR ada di zip, tidak bergantung
 * pada awalan path HP asal. Objek ini murni Kotlin supaya bisa diuji unit.
 */
object PemetaanPathFoto {

    /** Nama entri zip yang bukan foto. */
    private const val ENTRI_JSON = "backup.json"

    /**
     * @param pathLama path absolut dari JSON backup (tidak tepercaya).
     * @param namaEntriZip semua nama entri di dalam zip.
     * @param basisFilesDir `context.filesDir.absolutePath` HP saat ini.
     * @return path relatif terhadap filesDir, atau null bila tidak bisa dipetakan dengan aman.
     */
    fun cariRelatif(pathLama: String?, namaEntriZip: Collection<String>, basisFilesDir: String): String? {
        if (pathLama.isNullOrBlank()) return null
        // Path dari backup tidak tepercaya: segmen ".." atau "." tidak pernah dipakai.
        if (pathLama.split('/').any { it == ".." || it == "." }) return null

        // 1) Path milik HP/aplikasi ini sendiri.
        val basis = basisFilesDir.trimEnd('/') + "/"
        if (pathLama.startsWith(basis)) {
            val relatif = pathLama.removePrefix(basis)
            return relatif.takeIf { it.isNotBlank() && layak(it) }
        }

        // 2) Path milik HP/aplikasi lain: Android selalu menaruh file internal di ".../files/<relatif>",
        //    jadi cari entri zip terpanjang yang cocok dengan ujung path tersebut.
        return namaEntriZip
            .filter { it != ENTRI_JSON && layak(it) && pathLama.endsWith("/files/$it") }
            .maxByOrNull { it.length }
    }

    /** Nama entri/relatif yang aman dipakai sebagai path di bawah filesDir. */
    private fun layak(relatif: String): Boolean =
        relatif.isNotBlank() &&
            !relatif.startsWith("/") &&
            relatif.split('/').none { it.isEmpty() || it == ".." || it == "." }
}
