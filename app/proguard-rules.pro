# Aturan R8 (minify + obfuscation) untuk NADA POS - berlaku untuk build RELEASE.
#
# STATUS: AKTIF (app/build.gradle.kts: isMinifyEnabled = true, isShrinkResources = true).
#
# Strategi: kode BUATAN SENDIRI (com.nada.kasir: lisensi, kalkulator, printer, UI, dll.)
# di-obfuscate sekeras mungkin. Library pihak ketiga yang berat refleksi (Apache POI /
# XMLBeans untuk Excel) SENGAJA di-keep utuh supaya Import/Export Excel tidak rusak.
#
# WAJIB setelah tiap kali mengubah file ini: build APK release, pasang di HP fisik, dan tes:
#   Login, aktivasi lisensi, scan barcode, Import & Export Excel, cetak struk Bluetooth,
#   Backup & Restore. Kalau ada yang crash, kirim log-nya - tambahkan -keep yang sesuai.

# ---------------------------------------------------------------------------
# Atribut & pengerasan
# ---------------------------------------------------------------------------
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions

# Nomor baris tetap disimpan agar crash log bisa dibaca kembali lewat mapping.txt,
# tapi nama file sumber disamarkan.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Pindahkan semua kelas hasil obfuscation ke satu package datar (struktur package asli hilang)
-repackageclasses 'n'
-allowaccessmodification

# Buang pengecekan null bawaan Kotlin yang membocorkan NAMA PARAMETER/VARIABEL asli.
# (Sengaja TIDAK menyentuh Intrinsics.checkNotNull karena itu dipakai oleh operator "!!")
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNullParameter(java.lang.Object, java.lang.String);
    public static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    public static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    public static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
}

# Buang log debug/info/verbose dari APK release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ---------------------------------------------------------------------------
# Enum milik app: nama konstanta disimpan ke database & file backup lewat .name / valueOf()
# (UserRole, TransactionStatus, MetodePembayaran, TipeMutasiStok, PaketAplikasi, dll.)
# ---------------------------------------------------------------------------
-keepclassmembers enum com.nada.kasir.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}

# ---------------------------------------------------------------------------
# Room (entity & DAO tetap terbaca; logika aplikasi di luar ini tetap di-obfuscate)
# ---------------------------------------------------------------------------
-keep class com.nada.kasir.core.data.local.entity.** { *; }
-keep class com.nada.kasir.core.data.local.dao.** { *; }
-keepclassmembers class com.nada.kasir.core.data.local.entity.** {
    <fields>;
}

# ---------------------------------------------------------------------------
# Apache POI + XMLBeans (Excel) - refleksi berat, dijaga utuh
# ---------------------------------------------------------------------------
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class schemaorg_apache_xmlbeans.** { *; }
-keep class org.openxmlformats.schemas.** { *; }
-keep class com.microsoft.schemas.** { *; }
-keep class org.etsi.uri.** { *; }
-keep class org.w3.x2000.** { *; }
-keep class org.apache.commons.** { *; }
-keep class org.apache.logging.log4j.** { *; }
-keep class com.graphbuilder.** { *; }
-keep class com.zaxxer.sparsebits.** { *; }

# Library opsional yang direferensikan POI/commons-compress tapi tidak ada di Android
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.**
-dontwarn com.microsoft.schemas.**
-dontwarn org.etsi.uri.**
-dontwarn org.w3.x2000.**
-dontwarn org.apache.commons.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.apache.batik.**
-dontwarn org.apache.pdfbox.**
-dontwarn org.apache.xml.security.**
-dontwarn org.apache.jcp.xml.dsig.**
-dontwarn de.rototor.pdfbox.**
-dontwarn net.sf.saxon.**
-dontwarn org.bouncycastle.**
-dontwarn org.osgi.**
-dontwarn aQute.**
-dontwarn org.slf4j.**
-dontwarn org.objectweb.asm.**
-dontwarn org.tukaani.**
-dontwarn org.brotli.**
-dontwarn com.github.luben.**
-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn com.graphbuilder.**
-dontwarn com.zaxxer.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn javax.xml.stream.**
-dontwarn javax.xml.crypto.**
-dontwarn javax.naming.**
-dontwarn javax.script.**

# ---------------------------------------------------------------------------
# ML Kit Barcode, Hilt, org.json
# (CameraX, Coil, Compose, Room, Hilt membawa aturan consumer sendiri)
# ---------------------------------------------------------------------------
-keep class com.google.mlkit.vision.barcode.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn dagger.hilt.**
-dontwarn org.json.**
