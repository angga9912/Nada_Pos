# Peta repositori NADA POS

Dokumen ini menjelaskan fungsi tiap repo dan alur publikasi agar sumber kode, distribusi APK, situs, dan dashboard tidak tertukar.

## Fungsi repo

| Repo | Peran | Sumber kebenaran |
|---|---|---|
| [Nada_Pos](https://github.com/angga9912/Nada_Pos) | Aplikasi Android NADA POS, bot trial WhatsApp, aturan Firebase, dan workflow build/release. | **Ya** — kode aplikasi dan konfigurasi build berada di sini. |
| [Nada_Pos_Download](https://github.com/angga9912/Nada_Pos_Download) | Kanal publik untuk mengunduh APK rilis. APK seharusnya disalin oleh workflow dari repo utama. | Tidak — jangan mengedit kode aplikasi di sini. |
| [NadaGrowth](https://github.com/angga9912/NadaGrowth) | Situs profil, informasi produk, dan pemasaran Nada Growth/NADA POS. | Tidak — situs menautkan ke rilis APK. |
| [nada_pantau](https://github.com/angga9912/nada_pantau) | Dashboard web admin untuk membaca rekap penjualan cabang dari Firestore. | Tidak — kebijakan akses Firestore dikelola di repo utama. |

## Alur rilis APK

1. Perubahan aplikasi dan versi dibuat di `Nada_Pos`.
2. Workflow `.github/workflows/android-build.yml` membangun APK `full` yang ditandatangani.
3. Dari push ke `main`, workflow membuat GitHub Release di repo utama dan—jika secret `DOWNLOAD_REPO_TOKEN` tersedia serta valid—menyalin APK yang sama ke `Nada_Pos_Download`.
4. Tautan unduh stabil yang sebaiknya dipakai situs dan pesan pelanggan adalah:
   `https://github.com/angga9912/Nada_Pos_Download/releases/latest/download/NadaPOS.apk`
5. Pengguna mengunduh aset `NadaPOS.apk` dari tab **Releases**, bukan ZIP kode sumber dari tombol **Code → Download ZIP**.

### Jika versi di repo unduhan tertinggal

Periksa run workflow `Build & Test NADA KASIR CUSTOM` di `Nada_Pos` dan pastikan secret `DOWNLOAD_REPO_TOKEN` aktif, belum kedaluwarsa, memiliki akses ke repo `angga9912/Nada_Pos_Download`, serta izin **Contents: Read and write**. Workflow menampilkan peringatan dan melewati publikasi ke repo unduhan bila token tidak tersedia. Sesudah perbaikan, jalankan build release dari push ke `main`; run manual tidak menerbitkan release.

## Alur dashboard cabang

Aplikasi `Nada_Pos` mengirim rekap cabang ke Firestore. Repo `nada_pantau` menyediakan dashboard web yang membaca data tersebut. Aturan keamanan Firestore tetap harus ditinjau dan dipelihara di repo utama (`firebase/firestore.rules`); konfigurasi Firebase di halaman web bukan pengganti aturan akses.

## Catatan pemeliharaan

- Perbarui rilis dan sumber aplikasi di `Nada_Pos`; repo unduhan hanya berisi aset distribusi dan petunjuk instalasi.
- Saat mengganti tautan APK di `NadaGrowth` atau konfigurasi `APK_URL` bot trial, gunakan URL `releases/latest/download/NadaPOS.apk`, bukan tag versi tetap.
- Pastikan release terbaru `Nada_Pos_Download` benar-benar memuat `NadaPOS.apk` dari versi terbaru sebelum mengumumkan rilis.
