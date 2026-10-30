import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

// === Firebase (modul Pantau Cabang, paket Pro) ===
// Plugin google-services HANYA dipasang bila app/google-services.json ada. Tanpa file itu aplikasi
// tetap bisa dibuild (CI, tes unit) - fitur Pantau Cabang otomatis menampilkan "Firebase belum dikonfigurasi".
// File itu diunduh dari Firebase Console (Project settings > Your apps). Daftarkan KEDUA package:
// com.nada.kasir (full) dan com.nada.kasir.demo (demo) di project yang sama, supaya satu file cukup untuk dua flavor.
// Isinya bukan rahasia (keamanan data dijaga firebase/firestore.rules), jadi boleh dicommit.
if (rootProject.file("app/google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.warn("INFO: app/google-services.json tidak ditemukan - fitur Pantau Cabang (Firebase) nonaktif pada build ini.")
}

// === Kunci PUBLIK lisensi ===
// Lisensi memakai tanda tangan digital ECDSA (bukan lagi secret bersama/HMAC). APK hanya membawa
// kunci PUBLIK (aman dilihat siapa pun, tidak bisa dipakai membuat kode). Kunci PRIVAT untuk membuat
// kode aktivasi hanya ada pada Anda (license.private / GitHub Secret NADA_LICENSE_PRIVATE_KEY).
// Buat pasangan kuncinya sekali saja: python3 tools/generate_license.py --init
// lalu upload file "license.public" ke root repo (isinya kunci publik, aman dicommit).
val licensePublicFile = rootProject.file("license.public")
val licensePublicKey: String = if (licensePublicFile.exists()) {
    licensePublicFile.readText().trim()
} else {
    logger.warn("PERINGATAN: license.public tidak ditemukan - APK ini TIDAK akan bisa mengaktifkan kode lisensi apapun. Jalankan: python3 tools/generate_license.py --init")
    ""
}

// === Kunci PUBLIK trial (opsional) ===
// Kode trial otomatis lewat WhatsApp (folder whatsapp-trial-bot/) ditandatangani kunci TERPISAH dari
// kunci lisensi utama, supaya kebocoran server bot tidak bisa dipakai membuat lisensi permanen.
// Aplikasi hanya menerima kode dari kunci ini bila terikat perangkat dan masa berlakunya pendek
// (lihat LicenseKeyValidator.validasiTrial). File kosong/tidak ada = fitur trial otomatis nonaktif.
// Buat sekali: python3 tools/generate_license.py --init --private-file license.trial.private --public-file license.trial.public
val trialPublicFile = rootProject.file("license.trial.public")
val trialPublicKey: String = if (trialPublicFile.exists()) trialPublicFile.readText().trim() else ""

// === Signing config untuk release build (poin perbaikan #2 - sebelumnya release tidak ditandatangani) ===
// Dibaca dari "keystore.properties" di root project (di-gitignore, tidak pernah dicommit).
// Lihat keystore.properties.example + tools/generate_keystore.sh untuk cara membuat keystore-nya.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreTersedia = keystorePropertiesFile.exists()
val keystoreProperties = Properties().apply {
    if (keystoreTersedia) load(FileInputStream(keystorePropertiesFile))
}

// === Versi aplikasi - dibaca dari version.properties, JANGAN diubah manual di sini ===
// version.properties otomatis dinaikkan oleh workflow android-build.yml (job build-release)
// setiap kali build APK release BERHASIL dari push ke main - lihat komentar di file itu.
val versionPropertiesFile = rootProject.file("app/version.properties")
val versionProperties = Properties().apply {
    if (versionPropertiesFile.exists()) load(FileInputStream(versionPropertiesFile))
}
val appVersionCode = versionProperties.getProperty("VERSION_CODE", "1").toInt()
val appVersionName = versionProperties.getProperty("VERSION_NAME", "1.0.0")

android {
    namespace = "com.nada.kasir"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nada.kasir"
        minSdk = 26 // Android 8.0+. Dinaikkan dari 24 karena Apache POI (Excel) butuh MethodHandle.invoke (API 26+)
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField("String", "LICENSE_PUBLIC_KEY", "\"$licensePublicKey\"")
        buildConfigField("String", "TRIAL_LICENSE_PUBLIC_KEY", "\"$trialPublicKey\"")
    }

    // Product flavors = titik kustomisasi per pelanggan (poin 21).
    // Tambahkan flavor baru untuk tiap pelanggan tanpa mengubah kode inti.
    flavorDimensions += "client"
    productFlavors {
        // FULL = versi utama yang dijual/didistribusikan ke pelanggan (package com.nada.kasir).
        // Fitur berbayar TIDAK dibedakan lewat flavor, tapi lewat kode aktivasi lisensi
        // (BASIC gratis; PRO/CUSTOM terbuka setelah kode aktivasi dimasukkan).
        create("full") {
            dimension = "client"
            // Nama aplikasi (label di launcher/home screen) ikut menampilkan versi, diambil
            // dari appVersionName yang sudah dibaca dari version.properties di atas - jadi
            // setiap kali workflow android-build.yml menaikkan versi otomatis, nama aplikasi
            // yang terpasang juga otomatis berubah, TANPA perlu diedit manual di sini.
            resValue("string", "app_name", "NADA POS v$appVersionName")
        }
        // DEMO = versi coba, package terpisah (com.nada.kasir.demo) sehingga bisa terpasang
        // berdampingan dengan versi full di HP yang sama. Ditambah label "DEMO" supaya kedua
        // ikon aplikasi di launcher tidak terlihat identik saat sama-sama terpasang.
        create("demo") {
            dimension = "client"
            applicationIdSuffix = ".demo"
            resValue("string", "app_name", "NADA POS DEMO v$appVersionName")
        }
        // create("tokoMakmur") {
        //     dimension = "client"
        //     applicationIdSuffix = ".tokomakmur"
        //     resValue("string", "app_name", "KASIR TOKO MAKMUR v$appVersionName")
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
        // F-09: ANDROID_ID (dasar ID Perangkat lisensi) di Android 8+ terikat pada kunci tanda tangan APK.
        // Tanpa ini, APK debug/demo dari CI memakai keystore debug acak milik runner (berbeda tiap build),
        // sehingga ID Perangkat di APK demo TIDAK sama dengan di APK full release di HP yang sama. Bila
        // keystore.properties ada, debug/demo ikut ditandatangani kunci yang sama dengan release.
        debug {
            if (keystoreTersedia) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            // R8 AKTIF: kode di-obfuscate (nama kelas/method/variabel diacak), dipangkas, dan
            // resource yang tidak terpakai dibuang -> APK lebih kecil & jauh lebih sulit dibaca
            // setelah didekompilasi. Aturan pengecualian ada di proguard-rules.pro.
            // WAJIB tes APK release di HP fisik (Excel, scan barcode, printer, backup, lisensi)
            // sebelum dibagikan ke pelanggan. Simpan mapping.txt tiap rilis (untuk baca crash log).
            isMinifyEnabled = true
            isShrinkResources = true
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

// Ekspor skema Room (exportSchema = true di AppDatabase) ke app/schemas/ setiap kali build. MigrasiIndeksTest
// membaca file hasil build itu untuk memastikan SQL migrasi cocok dengan anotasi di entity. Menyimpan (commit)
// folder app/schemas/ ke repo disarankan agar kelak bisa dipakai untuk tes migrasi antarversi.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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

    // Firebase - Pantau Cabang (rekap harian cabang -> admin). Versi diatur BoM; tanpa modul -ktx.
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    // Task.await() untuk Firebase
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    // WAJIB bersama Firebase: Firebase menarik "listenablefuture:9999.0-empty-to-avoid-conflict-with-guava"
    // (artefak kosong) yang menggantikan versi asli, sehingga CameraX (AutoScanScreen/BarcodeScannerScreen)
    // gagal compile: "Cannot access class ListenableFuture". Guava asli menyediakan class tersebut.
    implementation("com.google.guava:guava:33.2.1-android")

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
