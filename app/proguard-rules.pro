# Aturan ProGuard/R8 untuk NADA POS.
#
# STATUS: belum aktif - app/build.gradle.kts masih isMinifyEnabled = false (lihat komentar
# di sana untuk alasannya). File ini disiapkan lebih awal supaya begitu Anda siap
# mengaktifkan minifikasi/obfuscation, aturan dasarnya sudah ada dan tidak mulai dari nol.
#
# SEBELUM mengaktifkan isMinifyEnabled = true untuk rilis ke pelanggan sungguhan:
# WAJIB build & jalankan APK release hasilnya di HP fisik, lalu tes manual PENUH
# terutama fitur-fitur yang paling berisiko rusak akibat R8 (karena pakai reflection):
#   - Import & Export Excel (Apache POI)
#   - Scan barcode kamera (ML Kit)
#   - Cetak struk & scan printer Bluetooth
#   - Backup & Restore data
# Kalau salah satu fitur di atas gagal/crash setelah minify diaktifkan, tambahkan
# -keep rule yang sesuai lalu build & tes ulang - jangan asumsikan aturan di bawah
# ini sudah pasti lengkap untuk semua versi library.

# --- Room ---
-keep class com.nada.kasir.core.data.local.entity.** { *; }
-keep class com.nada.kasir.core.data.local.dao.** { *; }

# --- Apache POI (Excel) - reflection berat, rawan rusak kalau di-obfuscate ---
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class schemaorg_apache_xmlbeans.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.**

# --- ML Kit Barcode Scanning ---
-keep class com.google.mlkit.vision.barcode.** { *; }
-dontwarn com.google.mlkit.**

# --- Hilt / Dagger (biasanya sudah dihandle otomatis oleh plugin, ini jaga-jaga) ---
-dontwarn dagger.hilt.**

# --- Kotlin coroutines & data class umum (nama field dipakai untuk JSON manual di backup) ---
-keepclassmembers class com.nada.kasir.core.data.local.entity.** {
    <fields>;
}

# --- org.json (dipakai manual di BackupManager/EntityJsonMapper) ---
-dontwarn org.json.**
