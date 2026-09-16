import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

// === SECRET lisensi (poin perbaikan #1 - sebelumnya tidak sinkron dengan tools/generate_license.py) ===
// Dibaca dari SATU file "license.secret" di root project (di-gitignore, tidak pernah dicommit).
// tools/generate_license.py membaca file yang SAMA PERSIS, jadi keduanya tidak bisa lagi tidak-sinkron.
// Kalau file tidak ada (mis. clone baru belum setup), fallback ke nilai dev-only yang JELAS tidak aman
// untuk produksi - supaya build tetap jalan untuk development sehari-hari tanpa menghalangi siapapun.
val licenseSecretFile = rootProject.file("license.secret")
val licenseSecret: String = if (licenseSecretFile.exists()) {
    licenseSecretFile.readText().trim()
} else {
    logger.warn("PERINGATAN: license.secret tidak ditemukan - memakai secret dev-only yang TIDAK AMAN untuk produksi. Lihat license.secret.example.")
    "DEV-ONLY-TIDAK-AMAN-UNTUK-PRODUKSI-SELALU-BUAT-license.secret-SEBELUM-RILIS"
}

// === Signing config untuk release build (poin perbaikan #2 - sebelumnya release tidak ditandatangani) ===
// Dibaca dari "keystore.properties" di root project (di-gitignore, tidak pernah dicommit).
// Lihat keystore.properties.example + tools/generate_keystore.sh untuk cara membuat keystore-nya.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreTersedia = keystorePropertiesFile.exists()
val keystoreProperties = Properties().apply {
    if (keystoreTersedia) load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.nada.kasir"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nada.kasir"
        minSdk = 26 // Android 8.0+. Dinaikkan dari 24 karena Apache POI (Excel) butuh MethodHandle.invoke (API 26+)
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"

        buildConfigField("String", "LICENSE_SECRET", "\"$licenseSecret\"")
    }

    // Product flavors = titik kustomisasi per pelanggan (poin 21).
    // Tambahkan flavor baru untuk tiap pelanggan tanpa mengubah kode inti.
    flavorDimensions += "client"
    productFlavors {
        create("demo") {
            dimension = "client"
            applicationIdSuffix = ".demo"
            resValue("string", "app_name", "NADA POS")
        }
        // create("tokoMakmur") {
        //     dimension = "client"
        //     applicationIdSuffix = ".tokomakmur"
        //     resValue("string", "app_name", "KASIR TOKO MAKMUR")
        // }
    }

    buildFeatures {
        compose = true
        buildConfig = true // wajib di AGP 8+ untuk bisa pakai buildConfigField di atas
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    signingConfigs {
        if (keystoreTersedia) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Minifikasi/obfuscation (R8) SENGAJA belum diaktifkan: beberapa dependency di project ini
            // (Apache POI, ML Kit, Room, Hilt) pakai reflection dan butuh proguard-rules.pro yang teruji
            // dengan build fisik sebelum aman diaktifkan - risiko silent-break (mis. Import/Export Excel
            // tiba-tiba gagal) kalau dinyalakan tanpa pengujian di perangkat asli. Aktifkan setelah
            // proguard-rules.pro divalidasi dengan build & test manual penuh (lihat komentar di file itu).
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystoreTersedia) {
                signingConfigs.getByName("release")
            } else {
                // Belum ada keystore.properties -> tetap bisa build & test lokal (pakai signing debug
                // bawaan), TAPI APK hasilnya TIDAK BOLEH didistribusikan ke pelanggan/Play Store.
                logger.warn("PERINGATAN: keystore.properties tidak ditemukan - release APK memakai signing DEBUG (hanya untuk testing lokal, jangan didistribusikan).")
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")

    // Room (database lokal - wajib offline, poin 16 & 19)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Hilt (DI)
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // CameraX + ML Kit (barcode - Phase 2)
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Excel - Phase 3
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    // org.json bawaan Android SDK adalah stub yang error saat dipanggil di unit test JVM;
    // dependency ini menyediakan implementasi asli khusus untuk classpath unit test.
    testImplementation("org.json:json:20231013")
}
