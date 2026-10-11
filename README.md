# NADA KASIR CUSTOM

Aplikasi kasir Android yang dapat dikustomisasi per toko/UMKM. Dibangun dengan
Kotlin + Jetpack Compose + Room (MVVM + Repository, offline-first).

## Status: MODEL BISNIS FREEMIUM — AKTIVASI LISENSI (selesai — siap diuji)

Perubahan model bisnis: dari "instalasi custom per toko" menjadi **satu
aplikasi, didistribusikan luas, upgrade sendiri lewat kode aktivasi** (model
yang sama dipakai Qasir/Pawoon/Kasir Pintar).

- ✅ **Default paket sekarang BASIC** (bukan Pro) — instalasi baru selalu mulai
  dari gratis, sesuai model freemium publik.
- ✅ **Pemilih paket bebas-pilih di Pengaturan Toko DIHAPUS** (sebelumnya
  siapa saja bisa tap "Pro" dan langsung dapat semua fitur gratis - jelas
  tidak bisa dipakai untuk aplikasi publik).
- ✅ **Sistem Aktivasi Lisensi** (`LicenseKeyValidator` + `LicenseRepository`)
  — validasi kode OFFLINE (tanpa server/internet) memakai **tanda tangan digital
  ECDSA P-256**. Format: `NADA-{TIER}-{EXPIRY}-{TANDA-TANGAN}`, mendukung:
  - Langganan bulanan (expiry tanggal tertentu, auto-turun ke Basic kalau lewat)
  - Sekali bayar/lifetime (`LIFETIME`, tidak pernah expired)
- ✅ **Pengecekan kadaluarsa otomatis** setiap aplikasi dibuka (`LoginViewModel`),
  konsisten dengan model langganan bulanan ala Qasir/Pawoon.
- ✅ **`tools/generate_license.py`** — script yang Anda jalankan sendiri untuk
  generate kode setelah pelanggan bayar (transfer/QRIS langsung ke Anda, TANPA
  potongan platform manapun).
- ✅ UI baru di Pengaturan Toko: "Status Lisensi" (paket aktif + tanggal
  kadaluarsa/lifetime) + input "Kode Aktivasi".
- ✅ Unit test `LicenseKeyValidatorTest` — memverifikasi kode valid diterima,
  kode dipalsukan/diubah ditolak, format salah ditolak, BASIC tidak butuh kode.

### PENTING sebelum rilis ke publik

**Lisensi memakai tanda tangan digital (ECDSA), bukan lagi secret bersama (HMAC).**
Versi lama menanam SECRET di dalam APK - siapa pun yang membongkar APK bisa membuat kode
lisensi sendiri. Sekarang APK hanya membawa **kunci publik** yang cuma bisa MEMERIKSA
kode, tidak bisa MEMBUATNYA. Kunci privat (pembuat kode) hanya ada pada Anda.

Setup sekali saja:

1. Jalankan `python3 tools/generate_license.py --init` (di Termux/komputer Anda; butuh
   Python 3.8+, tanpa library tambahan). Ini membuat `license.private` (**RAHASIA**) dan
   `license.public`.
2. Upload `license.public` ke root repo GitHub. Build APK membacanya otomatis
   (`BuildConfig.LICENSE_PUBLIC_KEY`); build release dihentikan kalau file ini tidak ada.
3. Salin isi `license.private` (64 karakter) ke GitHub Secret **`NADA_LICENSE_PRIVATE_KEY`**
   (dipakai workflow "Generate Kode Lisensi") dan simpan cadangan offline (mis. password
   manager). **Jangan pernah upload `license.private` ke GitHub.** Kalau hilang, Anda tidak
   bisa membuat kode baru untuk APK yang sudah beredar.
4. Build ulang APK. Kode dari generator versi lama (HMAC) **tidak berlaku lagi** - buatkan
   ulang untuk pelanggan yang sudah punya kode.
5. Secret lama `NADA_LICENSE_SECRET` dan file `license.secret.example` sudah tidak dipakai
   dan boleh dihapus.

Cek sebuah kode kapan saja: `python3 tools/generate_license.py --cek NADA-PRO-... --device ID-PERANGKAT`

### Kode terikat perangkat (F-12)
Kode aktivasi sekarang **hanya berlaku di satu HP**. Tanda tangan dibuat atas pesan yang memuat
**ID Perangkat** (12 karakter hex, mis. `A1B2-C3D4-E5F6`, diturunkan dari ANDROID_ID dengan SHA-256).
Format kode tidak berubah; aplikasi menghitung ID HP-nya sendiri saat memverifikasi, jadi kode yang
dibuat untuk HP A otomatis tidak valid di HP B.

- Pelanggan melihat ID-nya di **Pengaturan Toko → Status Lisensi** (ada tombol Salin). ID itu juga
  otomatis tertulis di pesan WhatsApp saat mereka menekan tombol upgrade/trial di layar Info Paket.
- ID bertahan setelah update atau instal ulang APK di HP yang sama, tetapi berubah setelah reset pabrik
  (pelanggan perlu dibuatkan kode baru untuk ID barunya).
- **Masa peralihan:** kode lama (tanpa ID perangkat) yang sudah terlanjur dijual masih diterima supaya
  pelanggan lama tidak kehilangan paketnya. Setelah semua pelanggan lama dibuatkan kode baru, ubah
  `IZINKAN_KODE_TANPA_PERANGKAT` di `LicenseKeyValidator.kt` menjadi `false` lalu build ulang.
- Status lisensi adalah milik perangkat: **tidak ikut dibackup dan tidak ditimpa saat restore**.
- Perlindungan jam: aplikasi mencatat waktu terakhir yang pernah dilihat sehingga memundurkan jam HP tidak
  memperpanjang langganan. Ini perlindungan offline; menghapus data aplikasi lalu mengaktifkan ulang
  sambil memundurkan jam masih bisa lolos (hanya validasi online yang menutupnya sepenuhnya).

### Risiko operasional lisensi (F-09)
- **ID Perangkat terikat ke kunci tanda tangan APK.** Di Android 8+, ANDROID_ID bergantung pada kombinasi
  kunci tanda tangan, pengguna, dan perangkat. Jika keystore rilis hilang atau dibuat ulang, ID semua
  pelanggan berubah dan **semua kode lisensi yang sudah terbit berhenti berlaku**. Perlakukan keystore
  sama pentingnya dengan `license.private`: simpan salinan di dua tempat terpisah dari repo.
- **Penjaga di CI:** job `build-release` mencetak sidik jari SHA-256 keystore dan menggagalkan build bila
  berbeda dari GitHub Variable `KEYSTORE_SHA256`. Setelah keystore dibuat, jalankan
  `tools/generate_keystore.sh` (sidik jari tercetak di akhir) atau lihat log build pertama, lalu isi
  variable itu di Settings → Secrets and variables → Actions → **Variables**.
- **APK demo dan full memakai kunci yang sama** selama secret keystore ada: job `build` menandatangani
  APK demo debug dengan keystore rilis, dan `build.gradle.kts` melakukan hal yang sama untuk build debug
  lokal bila `keystore.properties` ada. Akibatnya ID Perangkat di APK demo sama dengan di APK full, dan
  build demo baru bisa dipasang menimpa build demo lama. Tanpa secret (mis. PR dari fork), build memakai
  keystore debug runner dan ID-nya berbeda. Jangan minta pelanggan mengambil ID dari APK yang berbeda dari
  APK yang akan mereka pakai; teks di Pengaturan Toko juga mengingatkan hal ini.
  *Perhatian saat pertama kali:* APK demo lama (ditandatangani kunci debug acak) tidak bisa ditimpa oleh
  APK demo baru; pasang ulang (data demo hilang) sekali saja.
- **Kadaluarsa dicek berkala.** Selain saat aplikasi dibuka, `SessionHolderViewModel` mengecek lisensi
  setiap 5 menit selama aplikasi hidup, sehingga toko yang membiarkan aplikasi terbuka berhari-hari tetap
  turun ke Basic saat langganan habis.
- **Kode tanpa ID Perangkat (V1)** masih diterima selama `IZINKAN_KODE_TANPA_PERANGKAT = true` dan berlaku
  di HP mana pun. Kode V1 berlangganan akan hilang sendiri saat kedaluwarsa; kode V1 *lifetime* adalah
  yang paling berisiko tersebar. Setelah semua kode V1 yang pernah dijual kedaluwarsa atau diganti,
  ubah flag itu menjadi `false`.
- **Tidak ada pencabutan kode.** Aplikasi tidak punya izin internet, jadi kode yang sudah terbit tidak
  bisa dibatalkan dari jauh. Mitigasinya: terbitkan kode berlangganan pendek (bulanan) dan hindari
  kode lifetime.

Batasan yang tetap perlu dipahami: validasi berjalan offline di dalam APK, jadi orang yang
sangat berniat masih bisa mem-patch APK agar selalu menganggap lisensi valid (bukan
membuat kode palsu). Obfuscation R8 (aktif di build release) mempersulit hal itu; untuk
perlindungan penuh di skala besar, pertimbangkan validasi online ke server.

### Trial otomatis lewat WhatsApp
Folder `whatsapp-trial-bot/` berisi webhook (Vercel) yang membalas **kode trial** otomatis (paket Custom/Pro,
14 hari) saat pembeli menekan tombol trial di aplikasi atau mengirim ID Perangkat ke nomor WhatsApp Nada POS. Panduan lengkap ada di `whatsapp-trial-bot/README.md`.

- Kode trial ditandatangani **kunci trial terpisah** (`license.trial.public` di root repo, `license.trial.private`
  hanya di server bot), jadi kebocoran server tidak bisa dipakai membuat lisensi berbayar.
- Aplikasi hanya menerima kode trial yang **terikat ID Perangkat**, **bukan LIFETIME**, dan berlaku **paling
  lama 16 hari** (`LicenseKeyValidator.validasiTrial`). Tanpa file `license.trial.public`, fitur ini nonaktif.
- Kode trial **tidak boleh menimpa paket yang masih aktif** (`AturanAktivasiTrial`), supaya toko berlangganan
  tidak turun ke Basic setelah mencoba trial.
- Pembatasan di server: satu trial per ID Perangkat, 3 permintaan per nomor per 30 hari, 30 per hari.

### Signing APK Release (perbaikan poin 2)

Sebelumnya build `release` tidak ditandatangani sama sekali (tidak bisa
dipublikasikan/diupdate secara resmi). Sekarang sudah ada signing config:

1. Jalankan `./tools/generate_keystore.sh` **di komputer Anda sendiri** (bukan
   di CI/cloud) untuk membuat keystore. **Backup file `.keystore` hasilnya ke
   tempat aman** - kalau hilang, Anda tidak bisa lagi merilis update untuk APK
   yang sudah terlanjur didistribusikan dengan keystore itu.
2. Copy `keystore.properties.example` jadi `keystore.properties`, isi sesuai
   keystore yang baru dibuat. File ini juga otomatis di-`.gitignore`.
3. Build APK release yang sudah ditandatangani: `./gradlew assembleFullRelease`
   (hasil di `app/build/outputs/apk/full/release/`; versi coba: `assembleDemoRelease`).
4. Untuk build otomatis lewat GitHub Actions, isi GitHub Secrets
   `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (lihat
   komentar job `build-release` di `.github/workflows/android-build.yml`) -
   job ini otomatis aktif begitu secret-secret itu terisi. Isi juga GitHub *Variable*
   `KEYSTORE_SHA256` (sidik jari keystore) supaya CI menolak keystore yang tertukar.
5. Kalau `keystore.properties` belum ada, `assembleFullRelease` tetap bisa
   jalan (pakai signing debug bawaan) supaya tidak menghalangi testing lokal -
   tapi APK hasilnya **tidak boleh didistribusikan ke pelanggan**.

### Cara pakai sehari-hari
```bash
# Minta pelanggan mengirim "ID Perangkat" (tampil di Pengaturan Toko / pesan WhatsApp mereka).
# Setelah pelanggan transfer untuk paket Pro langganan 1 bulan:
python3 tools/generate_license.py --tier PRO --bulan 1 --device A1B2-C3D4-E5F6

# Setelah pelanggan bayar sekali untuk paket Custom permanen:
python3 tools/generate_license.py --tier CUSTOM --expiry LIFETIME --device A1B2-C3D4-E5F6
```
Workflow GitHub "Generate Kode Lisensi" juga meminta isian **ID Perangkat**.
Kirim kode yang muncul ke pelanggan lewat WhatsApp → mereka masukkan di
Pengaturan Toko → Aktivasi → fitur langsung terbuka.



Masalah yang dilaporkan: layar Kasir sebelumnya pakai layout **dua kolom
berdampingan** (grid produk | keranjang) yang di HP portrait membuat kedua
kolom jadi sangat sempit — nama produk di keranjang gampang terpotong/susah
dibaca, bikin bingung saat merevisi pesanan.

Perbaikan:
- ✅ Grid produk sekarang **full-width** (tidak lagi berbagi lebar dengan panel keranjang).
- ✅ Setiap kartu produk yang sudah ada di keranjang menampilkan badge
  **"Di keranjang: Nx"** langsung di grid, jadi kasir langsung tahu status
  tanpa harus buka keranjang dulu.
- ✅ Keranjang jadi **bottom bar ringkas** yang selalu terlihat di bawah layar
  ("N item di keranjang • Total Rp X"), disentuh untuk membuka **bottom sheet
  full-width** berisi daftar lengkap: nama produk, harga, kontrol qty (+/-),
  dan subtotal — semuanya sekarang punya cukup ruang untuk terbaca jelas.
- ✅ Revisi pesanan (ubah qty, tambah/kurang) dilakukan langsung di bottom
  sheet ini dengan nama produk yang jelas di sebelah kontrolnya.
- ✅ Tombol BAYAR dipindah ke dalam bottom sheet (di bawah ringkasan
  subtotal/diskon/total), tidak ada perubahan logic pembayaran sama sekali.



- ✅ Nama aplikasi diganti dari "NADA KASIR CUSTOM" menjadi **"NADA POS"** di
  semua tempat (label launcher, halaman Login, fallback header Dashboard, test
  print). Package name (`com.nada.kasir`) TIDAK diubah — cuma nama tampilan.
- ✅ **Upload Logo Toko (khusus Custom ke atas)** — di Pengaturan Toko, tombol "Unggah Logo" membuka
  pemilih gambar (galeri), logo disalin ke storage internal aplikasi
  (`LogoStorageHelper`) supaya path-nya stabil.
- ✅ Logo asli sekarang tampil di **header Dashboard** (bulat, menggantikan
  ikon toko generik), pakai Coil `AsyncImage`.
- ✅ Logo asli juga **dicetak sebagai gambar sungguhan** di struk fisik (bukan
  cuma teks nama toko dalam kurung) via perintah ESC/POS raster `GS v 0`
  (`EscPosBuilder.image()`) — dikonversi otomatis ke hitam-putih murni karena
  printer thermal tidak mendukung grayscale.
- ✅ Preview struk (teks) menampilkan placeholder `[GAMBAR LOGO TOKO]` karena
  preview berbasis teks tidak bisa merender gambar asli - tampilan fisik di
  kertas akan menampilkan logo sungguhan.
- ✅ Ikon aplikasi (launcher) sudah diganti sebelumnya ke logo NG yang diunggah.

### Catatan
- Kalau printer Anda printer sangat murah/model lama yang tidak mendukung
  `GS v 0`, logo tidak akan tercetak (baris nama toko tetap tercetak sebagai
  fallback teks). Kebanyakan printer thermal Bluetooth 2015 ke atas mendukung ini.



- ✅ **Nomor antrian** — `NomorAntrianGenerator` menghitung ulang dari total
  transaksi hari ini (Calendar 00:00–23:59), otomatis **reset ke 1 setiap
  pergantian hari**. Terpisah dari nomor invoice (`INV-YYYYMMDD-XXXX`) yang
  tidak pernah reset. Dicetak besar & tebal di bagian atas struk, juga muncul
  di dialog "Transaksi Berhasil" dan daftar Riwayat Transaksi.
- ✅ **Nama pembeli** — kolom opsional di dialog pembayaran Kasir. Kalau diisi,
  tercetak sebagai baris "Pembeli: {nama}" di struk (baik cetak asli maupun
  preview); kalau dikosongkan, baris ini tidak muncul sama sekali.
- ✅ Kedua field ditambahkan ke `TransactionEntity` (versi database naik ke 2 -
  karena masih tahap development/testing, skema lama akan otomatis di-reset
  lewat `fallbackToDestructiveMigration`, **data lama akan terhapus** saat
  update ini pertama kali dijalankan - beri tahu penggunanya).
- ✅ Backup/Restore ikut menyimpan kedua field baru ini (`EntityJsonMapper`
  diperbarui, supaya tidak hilang saat restore).
- ✅ Unit test baru memverifikasi baris nomor antrian & nama pembeli tampil
  tepat saat diisi, dan tidak muncul sama sekali saat kosong.



Satu source code sekarang bisa melayani 3 tingkat paket komersial (poin 29 brief
awal), diaktifkan lewat kode lisensi di **Pengaturan Toko** (Admin) — tanpa perlu
build APK terpisah per paket.

| Fitur | Basic | Custom | Pro |
|---|---|---|---|
| Kasir, Barcode, Printer, Cetak Struk + Preview | ✅ | ✅ | ✅ |
| Produk, Stok, Riwayat Transaksi | ✅ | ✅ | ✅ |
| Custom Warna Aplikasi (Branding) | ❌ | ✅ | ✅ |
| Import/Export Excel | ❌ | ✅ | ✅ |
| Laporan (Harian/Bulanan/Stok) | ❌ | ✅ | ✅ |
| Backup & Restore Data | ❌ | ✅ | ✅ |
| Logo Toko (header aplikasi & struk) | ❌ | ✅ | ✅ |
| Manajemen Pengguna (Multi User Admin/Kasir) | ❌ | ❌ | ✅ |
| Supplier | ❌ | ❌ | ✅ |
| Outlet / Cabang | ❌ | ❌ | ✅ |
| Hutang / Piutang | ❌ | ❌ | ✅ |

- ✅ `PaketAplikasi` (enum BASIC/CUSTOM/PRO) + `PaketRepository` — disimpan di
  tabel `settings` yang sudah ada (mekanisme sama seperti feature flags poin 22).
- ✅ Pemilih paket di Pengaturan Toko, langsung menyembunyikan/menampilkan menu
  terkait di Dashboard, Produk, dan Pengaturan tanpa perlu restart aplikasi.
- ✅ Default paket = **BASIC** (model freemium, lihat `PaketRepository`). Paket naik hanya lewat
  kode lisensi yang valid; paket kembali ke Basic bila lisensi kedaluwarsa.
- ✅ **Pembatasan fitur per paket ada di satu tempat: `core/paket/FiturPaket.kt`.** Menu di tab
  Lainnya, penjaga rute di `NadaNavGraph` (`PaketRouteGuard`), dan tabel di Info Paket memakai
  definisi yang sama, jadi fitur yang menunya disembunyikan tidak bisa dibuka lewat rute. Paket
  yang belum mencakup fitur melihat layar "tersedia di paket ..." dengan tombol ke Info Paket.
  Untuk membatasi fitur baru: tambahkan satu entri di `FiturPaket`, pasang `PaketRouteGuard`
  pada rutenya, dan tambahkan barisnya di `InfoPaketScreen`.
- ✅ Supplier, Outlet/Cabang, dan Hutang/Piutang khusus **Pro**; Backup & Restore untuk **Custom**
  ke atas; Manajemen Pengguna khusus **Pro**. Data yang sudah dibuat tidak dihapus bila paket
  turun ke Basic — datanya tetap ada, hanya layarnya terkunci sampai paket naik lagi.
- ✅ **Pembelian ke Supplier** juga khusus **Pro** (menu Lainnya → Pembelian).
- ✅ **Logo toko** (unggah, tampil di header dan dicetak di struk) bagian dari *Custom Branding* bersama warna
  aplikasi, jadi **khusus Custom dan Pro** (`FiturPaket.LOGO_TOKO`). Paket Basic melihat kartu terkunci di
  Pengaturan Toko dan struknya mencetak nama toko biasa. Data logo tidak dihapus saat paket turun; ia hanya
  tidak ditampilkan dan tidak dicetak (`StoreEntity.sesuaiPaket`), sehingga muncul lagi setelah upgrade atau
  trial. Aturan ini dipakai di struk cetak, preview struk, cetak ulang di Riwayat, dan header Dashboard, serta
  dijaga ulang di `PengaturanTokoViewModel.gantiLogo`.
- ✅ Layar **Stok** (Lainnya → Stok) tersedia di **semua paket** untuk Admin, karena ini fitur inventaris dasar.
- ⚠️ Modul Phase 5 dikerjakan bertahap (lihat bagian Phase 5 di bawah). Pelabelan cabang (`outletId`) belum selesai.



Ditambahkan atas permintaan: struk ditampilkan dulu sebagai preview teks
sebelum benar-benar dikirim ke printer.

- ✅ `StrukFormatter.buatStrukPreviewText()` — versi teks biasa (bukan ESC/POS)
  dari struk, layout dibuat semirip mungkin dengan hasil cetak fisik (font
  monospace, lebar kolom mengikuti ukuran kertas 58mm/80mm).
- ✅ `StrukPreviewDialog` — dialog full-height menampilkan preview, tombol
  "Cetak Sekarang" (baru mengirim ke printer di sini) dan "Tutup" (batal cetak).
- ✅ Berlaku di DUA alur: setelah transaksi selesai di Kasir, dan "Cetak Ulang"
  dari Riwayat Transaksi.
- ✅ Kegagalan printer tetap tidak memengaruhi data transaksi (perilaku lama
  tidak berubah, hanya menambah langkah konfirmasi visual sebelum cetak).
- ✅ Unit test baru: `StrukFormatterPreviewTest` (memastikan preview memuat info
  yang benar dan tidak pernah menampilkan harga beli).



Redesign "Modern Minimalist POS Dashboard" untuk layar Home, TANPA mengubah
business logic/database/navigasi fungsional (hanya cara menyusun & menampilkan
data yang sudah ada):

- ✅ **Header baru** — logo/ikon toko, nama toko (dari Pengaturan Toko), sapaan
  "Selamat datang kembali, {nama pengguna}", tanggal hari ini, ikon notifikasi
  (placeholder, belum ada sistem notifikasi) dan menu (⋮) berisi "Keluar".
- ✅ **Summary cards** — 4 kartu dengan ikon, label, dan angka utama; tanpa data
  perbandingan palsu (sesuai instruksi, karena datanya memang belum tersedia).
- ✅ **Primary Action** — "Transaksi Baru" jadi kartu besar warna utama aplikasi,
  paling menonjol di layar.
- ✅ **Menu Utama** — grid 2 kolom dengan ikon (Produk, Riwayat, Laporan khusus Admin),
  menggantikan tumpukan 8 tombol outline.
- ✅ **Perhatian Stok** — otomatis menampilkan status Stok Habis (merah) →
  Stok Menipis (oranye) → Aman (hijau), sesuai prioritas.
- ✅ **Transaksi Terbaru** — 5 transaksi terakhir dengan preview produk, ada
  empty state profesional ("Belum ada transaksi..." + tombol) dan "Lihat Semua".
- ✅ **Bottom Navigation** — Home / Kasir / Produk / Transaksi / Pengaturan,
  selalu terlihat di layar utama. Menu administratif (Printer, Toko, Pengguna,
  Backup) dikelompokkan di tab **Pengaturan** (halaman baru `PengaturanHubScreen`),
  tetap dibatasi untuk Admin saja (role guard Phase 4 tidak berubah).
- ✅ Warna mengikuti **branding dinamis** (`ThemeConfig`) yang sudah dibangun di
  Phase 4 — bukan warna hijau hardcode, supaya kustomisasi per toko tetap jalan.
  Warna status (aman/menipis/habis) tetap konsisten terlepas dari warna branding.

### Catatan penting
- Redesign kali ini FOKUS di layar Home/Dashboard + Bottom Navigation shell,
  sesuai brief yang diberikan. Layar lain (Kasir, Produk, Riwayat, Laporan,
  Pengaturan Printer/Toko, Pengguna, Backup) masih tampilan lama - siap
  di-redesign berikutnya kalau diperlukan.
- Semua tombol & fitur lama tetap berfungsi sama persis, hanya berpindah posisi
  visual (mis. "Keluar" sekarang di menu ⋮ header ATAU di tab Pengaturan).



Sudah ditambahkan di atas Phase 1-3:

- ✅ **Login & Role Guard** — wajib login sebelum masuk aplikasi. **ADMIN** melihat
  semua menu; **KASIR** hanya melihat Transaksi, Lihat Produk (read-only), Riwayat
  (bisa cetak ulang, TIDAK BISA membatalkan transaksi) — sesuai poin 18.
  Akun pertama (`admin` / `admin123`) dibuat otomatis saat first-run, dengan
  pemberitahuan. Login dengan password bawaan **tidak membuka aplikasi**: pengguna wajib membuat
  password baru dulu (password bawaan juga ditolak sebagai password baru, di semua jalur ganti/reset/
  buat akun). Akun bawaan hanya dibuat saat tabel pengguna benar-benar kosong, jadi menonaktifkan
  akun `admin` tidak memunculkan akun bawaan baru.
- ✅ **Manajemen Pengguna** (khusus Admin) — tambah akun Kasir/Admin baru, password
  selalu di-hash (poin 26), lihat status aktif/nonaktif.
- ✅ **Pengaturan Toko** (poin 1, akhirnya dibuatkan UI-nya) — nama, alamat, WA,
  telepon, pemilik, slogan, footer struk, mata uang, ukuran kertas, TERMASUK
  toggle tampilan struk (poin 10). Harga beli tetap dipaksa `false` dari UI ini.
- ✅ **Custom Branding penuh** (poin 2 & 21) — `ThemeConfig` menghasilkan warna
  aplikasi Compose secara RUNTIME dari `StoreEntity.warnaUtama`. Ganti warna di
  Pengaturan Toko → seluruh aplikasi langsung berubah warna, tanpa rebuild.
- ✅ **Laporan** (poin 14) — tab Hari Ini / Bulanan / Stok: total penjualan, jumlah
  transaksi, produk terjual, diskon, estimasi keuntungan, produk terlaris,
  breakdown metode pembayaran, semua bisa di-export ke Excel.

### Catatan Pengujian Phase 4

- Login pertama kali pakai `admin` / `admin123` (muncul otomatis di dialog saat
  akun ini baru dibuat). Aplikasi langsung meminta password baru; setelah itu buat akun Kasir baru lewat Manajemen Pengguna,
  lalu coba login sebagai Kasir untuk memverifikasi menu yang disembunyikan.
- Ubah warna di Pengaturan Toko, lalu kembali ke Dashboard — tombol-tombol
  harus langsung berubah warna tanpa perlu restart aplikasi.
- Cek Laporan Hari Ini setelah melakukan beberapa transaksi kasir — angka
  "Estimasi Keuntungan" dan "Produk Terjual" harus sesuai perhitungan manual.



Sudah ditambahkan di atas Phase 1 & 2:

- ✅ **Export Excel** — `ExcelExporter` menghasilkan `DATA_PRODUK.xlsx`, `PENJUALAN.xlsx`,
  `STOK_MASUK.xlsx`, `STOK_KELUAR.xlsx` sesuai kolom yang ditentukan (poin 15).
  Tombol "Export Excel" ada di halaman Produk; hasilnya bisa langsung dibagikan (share).
- ✅ **Import Excel** — `ExcelImporter` + `ProdukRowValidator` memvalidasi header &
  setiap baris (kode/nama wajib, harga & stok harus angka), barcode duplikat
  otomatis DILEWATI (bukan menimpa), semua baris valid disimpan dalam satu
  DB transaction. Pesan error tetap berbahasa manusia ("File Excel tidak sesuai format.")
- ✅ **Backup Data (terenkripsi)** — `BackupManager` mengekspor seluruh data penting (Produk, Stok,
  Transaksi, Pengaturan Toko, Pengguna, Printer, Supplier, Outlet, Hutang/Piutang, Pembelian,
  Audit Log, foto) ke satu file `.nadabak`, bisa dibagikan. Format backup versi 2 membawa data
  Phase 5 tersebut; backup versi 1 tetap bisa dipulihkan (data Phase 5 di HP dikosongkan karena
  restore selalu menggantikan seluruh data). Foto produk/logo dari backup yang dibuat APK lain
  (mis. demo `com.nada.kasir.demo`) atau profil pengguna Android lain ikut dipulihkan.
  File **dienkripsi dengan password pilihan pemilik toko** (minimal 8 karakter): AES-256-GCM, kunci
  diturunkan dengan PBKDF2-HMAC-SHA256 (150.000 iterasi, salt dan IV acak per file), sehingga isi
  (omzet, transaksi, hash password akun) tidak terbaca oleh siapa pun yang memegang filenya, dan
  perubahan sekecil apa pun pada file terdeteksi. Isi zip ditulis langsung lewat stream enkripsi,
  jadi versi polosnya tidak pernah tersimpan di disk. **Password tidak disimpan di mana pun: jika
  hilang, backup tidak bisa dipulihkan oleh siapa pun** - ingatkan pelanggan untuk mencatatnya.
  Backup `.zip` lama (sebelum fitur ini) tetap bisa direstore tanpa password; file lama yang masih
  tersimpan di folder backup/Drive tidak ikut terenkripsi dan sebaiknya dihapus setelah diganti.
- ✅ **Simpan Backup ke Google Drive** — tombol "Simpan ke Google Drive" di layar
  Backup & Restore membuka file picker sistem Android (Storage Access Framework);
  Google Drive otomatis muncul sebagai salah satu lokasi kalau aplikasi Drive
  terpasang & pemilik toko sudah login ke akunnya sendiri di HP tersebut. Pemilihan
  akun & folder sepenuhnya di tangan pengguna - aplikasi ini TIDAK meminta akses
  login Google apa pun dan tidak butuh Google Drive API/OAuth credentials
  (pendekatan ini sengaja dipilih supaya tidak perlu setup Google Cloud Console
  yang rumit hanya untuk fitur backup opsional).
- ✅ **Restore Data** — validasi & parsing PENUH dilakukan dulu sebelum data lama
  disentuh sama sekali; penghapusan + penyisipan data baru dibungkus SATU
  `appDatabase.withTransaction{}` sehingga kalau ada error di tengah proses,
  Room otomatis rollback dan **data lama tetap utuh** (poin 17, tidak pernah ada
  kondisi "data lama sudah terhapus tapi data baru gagal masuk").
- ✅ Excel/backup HANYA untuk laporan & cadangan — database utama tetap Room/SQLite (poin 15).

### Catatan Pengujian Phase 3

- File export & backup disimpan di folder khusus aplikasi (tidak perlu izin
  penyimpanan tambahan), lalu dibagikan lewat Share Sheet Android (WhatsApp,
  Google Drive, email, dll) memakai `FileProvider`.
- Untuk uji Import Excel: buat file `.xlsx` dengan sheet bernama `DATA_PRODUK`
  dan header persis: `Kode Produk, Barcode, Nama Produk, Kategori, Satuan, Harga Beli, Harga Jual, Stok, Stok Minimum`
  (atau langsung pakai hasil Export Excel sebagai template, edit, lalu import lagi).
- Untuk uji Restore: coba restore file backup yang sengaja dirusak (hapus beberapa
  karakter) — aplikasi harus menampilkan "File Excel tidak sesuai format." dan
  **data yang ada saat ini tidak boleh hilang sama sekali**. Ini pengujian paling
  penting di Phase 3.



Sudah ditambahkan di atas Phase 1:

- ✅ **Barcode kamera** — CameraX + ML Kit, tombol scan di halaman Kasir, mendukung
  EAN-13/EAN-8/CODE-128/CODE-39/UPC-A/UPC-E/QR
- ✅ **Barcode scanner fisik (handheld)** — `HandheldScannerDetector` membedakan
  ketikan cepat dari alat scanner vs ketikan manual kasir di kolom pencarian yang sama,
  tanpa perlu mode/tombol terpisah
- ✅ **Printer thermal Bluetooth** — `BluetoothPrinterManager` (koneksi SPP ke
  perangkat yang sudah *paired*), `EscPosBuilder` (perintah ESC/POS generik,
  kompatibel mayoritas printer 58mm/80mm di pasaran)
- ✅ **Format & custom struk** — `StrukFormatter` mengikuti toggle tampilkan/sembunyikan
  logo, alamat, WA, diskon dari `StoreEntity` (harga beli TIDAK PERNAH ditampilkan)
- ✅ **Halaman Pengaturan Printer** — scan printer terpasang, pilih ukuran kertas,
  test print, simpan sebagai default
- ✅ **Cetak Struk** setelah bayar di Kasir, dan **Cetak Ulang** dari Riwayat Transaksi
- ✅ Kegagalan printer tidak pernah membatalkan transaksi yang sudah tersimpan

### Catatan Pengujian Phase 2

- Pasangkan (pair) printer thermal Bluetooth Anda dulu lewat **Pengaturan Bluetooth
  bawaan HP**, baru buka menu **Pengaturan Printer** di aplikasi → Scan → pilih
  ukuran kertas → Test Print → Jadikan Default.
- Karena kita hanya membaca daftar perangkat yang sudah *paired* (bukan discovery
  aktif), aplikasi tidak meminta izin lokasi — hanya izin Bluetooth (Android 12+).
- Scanner fisik (handheld) diuji dengan cara: buka Kasir, tap kolom pencarian
  (fokuskan kursor), lalu scan barcode dengan alat — kode otomatis masuk sebagai
  jika mengetik cepat + Enter, tanpa perlu tombol tambahan.



Sudah diimplementasikan sesuai brief:

- ✅ **Database** — Room, 11 tabel (stores, users, categories, products, transactions,
  transaction_items, payments, stock_movements, printers, settings, audit_logs)
- ✅ **Produk** — tambah/edit/hapus (soft delete), validasi barcode duplikat
- ✅ **Stok** — stok masuk/keluar dengan pencatatan mutasi, mencegah stok minus
- ✅ **Kasir** — cari produk, keranjang, hitung subtotal/diskon/total
- ✅ **Pembayaran** — tunai (validasi kembalian), QRIS/transfer/debit/kredit/lainnya
- ✅ **Transaksi atomik** — simpan transaksi + kurangi stok dibungkus satu
  `appDatabase.withTransaction { }` (lihat `TransactionRepository.kt`) sehingga
  tidak mungkin terjadi transaksi tersimpan tanpa stok berkurang, atau sebaliknya
- ✅ **Riwayat transaksi** — lihat, batalkan (dengan konfirmasi, stok dikembalikan,
  status → CANCELLED, tidak dihapus permanen)
- ✅ **Dashboard** — penjualan hari ini, jumlah transaksi, stok menipis/habis
- ✅ **Error handling** — semua pesan error dalam Bahasa Indonesia (`AppError.kt`),
  tidak ada exception teknis yang ditampilkan ke pengguna
- ✅ **Arsitektur custom-ready** — tidak ada nama toko/warna/logo yang di-hardcode;
  semua dari `StoreEntity` + `settings` (feature flags via `FeatureConfig.kt`);
  Gradle product flavors disiapkan untuk build APK berbeda per pelanggan

## Cara Menguji via GitHub (tanpa Android Studio)

Project ini sudah dilengkapi **GitHub Actions** (`.github/workflows/android-build.yml`)
yang otomatis: menjalankan unit test → build APK → upload APK sebagai artifact
yang bisa Anda download dan instal langsung ke HP.

### Langkah-langkah

1. **Buat repo baru** di GitHub (bisa private), kosongkan (jangan centang "Add README").
2. Extract ZIP ini, lalu dari terminal di folder `nada-kasir-custom/`:
   ```bash
   git init
   git add .
   git commit -m "Phase 1: kasir, produk, stok, transaksi, riwayat"
   git branch -M main
   git remote add origin https://github.com/USERNAME/NAMA-REPO.git
   git push -u origin main
   ```
3. Buka repo Anda di GitHub → tab **Actions**. Workflow "Build & Test NADA KASIR CUSTOM"
   akan berjalan otomatis (±3–5 menit untuk run pertama).
4. Kalau semua langkah hijau (✅), klik run tersebut → scroll ke bagian **Artifacts**
   → download **`nada-kasir-custom-demo-debug`** (file `.zip` berisi `.apk`).
5. Extract, pindahkan `app-demo-debug.apk` ke HP Android (via kabel USB, WhatsApp
   ke diri sendiri, Google Drive, dll).
6. Di HP: aktifkan "Izinkan instal dari sumber tidak dikenal" untuk aplikasi file
   manager/browser yang Anda pakai, lalu tap file APK-nya untuk instal.
7. Buka aplikasi, jalankan checklist pengujian di bawah.

Setiap kali Anda `git push` perubahan baru, workflow ini otomatis jalan lagi dan
menghasilkan APK terbaru — tidak perlu build manual.

### Kalau Anda tetap ingin pakai Android Studio nanti

Buka folder project seperti biasa — Gradle wrapper (`gradlew`) sudah disertakan
di ZIP ini, jadi tidak perlu setup tambahan.

> Catatan: mode DEMO otomatis (seed data dummy sesuai poin 23 brief) belum
> ditambahkan di Phase 1 — akan ditambahkan bersamaan dengan Phase 3/Backup,
> supaya tidak mengganggu pengujian data asli.

## Checklist Pengujian Otomatis (jalan di GitHub Actions)

- [x] `PembayaranCalculatorTest` — kembalian & validasi cukup/kurang bayar
- [x] `CurrencyFormatterTest` — format Rupiah dengan pemisah ribuan
- [x] `PasswordHasherTest` — password tidak pernah plain text
- [x] `BackupEncryptionTest` — round-trip, password salah, file diubah/terpotong, header berbahaya, zip lewat stream
- [x] `MutasiStokManualTest` — perubahan stok manual selalu tercatat (jumlah mutasi = stok akhir)
- [x] `PemetaanReferensiMutasiTest` — restore memetakan referensi transaksi pada mutasi stok ke id baru
- [x] `UserRepositoryTest` — seed akun pertama hanya saat kosong, password bawaan ditolak sebagai password baru
- [x] `LicenseKeyValidatorTest`, `PerangkatIdTest`, `WaktuLisensiTest` — lisensi terikat perangkat & pelindung jam

## Checklist Pengujian Manual di HP (sesuai poin 30 brief)

- [ ] Tambah produk baru, cek muncul di Kasir
- [ ] Tambah produk dengan barcode yang sudah ada → harus ditolak
- [ ] Transaksi kasir dengan 2+ produk, cek subtotal/diskon/total benar
- [ ] Bayar tunai dengan uang pas → kembalian Rp0
- [ ] Bayar tunai kurang dari total → muncul "Uang pembayaran belum mencukupi."
- [ ] Setelah bayar, cek stok produk berkurang sesuai qty
- [ ] Buka Riwayat, cek transaksi baru muncul
- [ ] Batalkan transaksi → konfirmasi muncul, stok kembali, status jadi CANCELLED
- [ ] Coba transaksi dengan qty > stok tersedia → ditolak dengan pesan "Stok tidak mencukupi."
- [ ] Matikan & nyalakan app (force close) → data tetap ada (Room persisten)

## Roadmap Selanjutnya

**Phase 2 (selesai)** — ~~Barcode scanner~~, ~~Printer Bluetooth~~, ~~format & custom struk~~, ~~logo toko (teks)~~.

**Phase 3 (selesai)** — ~~Import/export Excel~~, ~~Backup/Restore~~.

**Phase 4 (selesai)** — ~~Laporan~~, ~~User Admin/Kasir~~, ~~Custom Branding penuh~~.

**Phase 5 (bertahap, khusus paket Pro)** — dikerjakan dalam 4 tahap:

1. ✅ **Hutang/Piutang lengkap.** Layar punya tab Piutang dan Hutang, ringkasan total, penanda
   jatuh tempo/terlambat, detail dengan riwayat pembayaran, **cicilan** sampai lunas, edit, dan
   pembatalan catatan manual. Di **Kasir** ada metode **"Piutang (bayar nanti)"**: nama pembeli wajib,
   uang muka opsional, dan jatuh tempo (7/14/30 hari). Catatan piutang dibuat atomik bersama
   transaksinya, struk menampilkan uang muka dan sisa piutang, dan cicilan memperbarui
   `transactions.hutangSisa`. Membatalkan transaksi piutang yang belum dicicil ikut membatalkan
   piutangnya; yang sudah dicicil ditolak supaya uang cicilan tidak "menghilang".
   Database naik ke **versi 6** (tabel `hutang_piutang_pembayaran`, migrasi `MIGRATION_5_6`), format
   backup naik ke **versi 3**.
2. ✅ **Pembelian ke supplier dan detail Supplier.** Menu **Pembelian** (Pro): pilih supplier aktif dan
   barang (qty, harga beli, diskon baris, diskon supplier), bayar sekarang sebagian/penuh/belum sama
   sekali. Sisa otomatis menjadi **hutang ke supplier** (muncul di tab Hutang pada Hutang/Piutang,
   dengan jatuh tempo 7/14/30 hari) dan cicilannya memperbarui `purchases.hutangSisa`. Pembelian bisa
   langsung **diterima** (stok bertambah, mutasi stok `MASUK` tercatat, harga beli produk diperbarui ke
   harga setelah diskon baris) atau **dipesan dulu** lalu tombol *Terima Barang*. Semuanya atomik: pembelian,
   item, stok, mutasi, dan catatan hutang tersimpan bersamaan atau tidak sama sekali; barang yang hilang/dihapus
   membatalkan seluruh pembelian (tidak ada yang dilewati diam-diam). Nomor pembelian berurutan per hari
   (`PUR-YYYYMMDD-0001`, menggantikan angka acak). **Batalkan pembelian** ditolak bila hutangnya sudah dicicil
   atau stok sekarang lebih kecil dari yang diterima (sebagian sudah terjual); bila lolos, stok dikurangi
   lagi lewat mutasi `KELUAR`. Layar **Supplier** kini punya pencarian, edit, nonaktifkan/aktifkan, nama
   unik, serta detail berisi jumlah pembelian, hutang berjalan, dan 10 pembelian terakhir. Tidak ada
   perubahan skema database pada tahap ini.
3. ✅ **Layar Stok (masuk, keluar, opname).** Menu Lainnya → Stok (Admin, semua paket):
   - **Stok Masuk** manual (stok tambahan, hadiah, retur pelanggan) dengan harga beli opsional dan saklar
     "Perbarui harga beli produk". Belanja dari supplier tetap dicatat lewat menu Pembelian (Pro).
   - **Stok Keluar** dengan alasan (rusak, kadaluarsa, hilang, dipakai sendiri, retur ke supplier, lainnya;
     "Lainnya" wajib dicatat). Ditolak bila melebihi stok sistem.
   - **Opname:** isi stok hasil hitung fisik, selisih ditampilkan sebelum disimpan lalu dicatat sebagai mutasi
     (naik = MASUK, turun = KELUAR, sama seperti konvensi stok di seluruh aplikasi).
   - Riwayat mutasi dengan filter Manual / Semua / Masuk / Keluar (200 terbaru) dan nama produk yang tetap
     tampil walau produknya sudah dihapus.
   - Perbaikan `StockRepository`: jumlah <= 0 dan di atas 1.000.000 ditolak, produk dihapus ditolak, parameter
     `hargaBeli` yang dulu diabaikan kini dipakai, dan semua pengecekan dilakukan sebelum penulisan pertama.
     Tidak ada perubahan skema database.
4. 🔜 **Pelabelan cabang:** outlet aktif memberi label `outletId` pada transaksi, pembelian, dan mutasi
   stok; laporan bisa difilter per cabang; stok produk tetap satu pool.

Catatan perilaku Hutang/Piutang: omzet di Laporan menghitung penjualan piutang penuh pada tanggal
transaksinya (dasar akrual), bukan saat dibayar; metode ini tampil sebagai "Piutang (belum dibayar)" di
ringkasan metode pembayaran. Piutang dibuat dari Kasir (**piutang pelanggan**), sedangkan hutang ke
supplier dibuat otomatis dari menu Pembelian. Catatan yang terhubung ke transaksi/pembelian tidak bisa
dibatalkan manual dari layar Hutang/Piutang; batalkan lewat Riwayat atau menu Pembelian.

## Struktur Folder

Lihat dokumen arsitektur di pesan sebelumnya, atau jelajahi `app/src/main/java/com/nada/kasir/`:
- `core/` — data layer, repository, util (tidak boleh diubah besar-besaran per pelanggan)
- `feature/` — satu folder per halaman (kasir, produk, stok, riwayat, dashboard)
- `navigation/` — NavGraph
- `branding/` — (disiapkan untuk Phase 4) tema dinamis dari data toko
