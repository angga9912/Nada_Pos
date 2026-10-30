# Pantau Cabang (Firebase) - Tahap 1

Admin memantau rekap penjualan harian semua cabang dari jarak jauh. Tiap cabang = satu HP.
Hanya paket **Pro** (`FiturPaket.PANTAU_CABANG`). Aplikasi tetap offline-first: kasir tidak pernah menunggu internet.

## Alur data
HP cabang -> menghitung rekap hari ini (+2 hari sebelumnya) dari data lokal -> menulis ke Firestore
`toko/{tokoId}/rekap/{yyyy-MM-dd}_{kodeCabang}` (timpa dokumen yang sama, aman dikirim berulang)
-> admin membaca semua rekap dari HP admin.

Isi rekap: jumlah transaksi, total penjualan, total diskon, produk terjual, estimasi keuntungan,
omzet per metode bayar, 10 produk terlaris. Tidak ada data pelanggan, password, atau harga beli per produk.

## Setup Firebase (sekali per project)
1. Firebase Console -> buat project.
2. Tambahkan 2 aplikasi Android: `com.nada.kasir` dan `com.nada.kasir.demo`.
3. Unduh `google-services.json` -> taruh di `app/google-services.json` (boleh dicommit; bukan rahasia).
4. Build -> Authentication -> Sign-in method -> aktifkan **Email/Password**.
5. Build -> Firestore Database -> buat database (mode production).
6. Firestore -> Rules -> tempel isi `firebase/firestore.rules` -> Publish. Uji di Rules Playground.

Tanpa langkah 3 aplikasi tetap bisa dibuild; fitur ini menampilkan "Firebase belum dikonfigurasi".

## Setup per toko (manual dulu; Tahap 2 bisa dibuatkan layar undangan)
1. Authentication -> Users -> Add user: satu akun **admin** dan satu akun per **cabang** (email + password).
2. **Kode Toko = UID akun admin** (disalin dari kolom User UID).
3. Firestore -> koleksi `toko` -> dokumen `{UID admin}` -> subkoleksi `anggota` -> tambah dokumen untuk tiap cabang:
   - Document ID = UID akun cabang
   - `peran` (string) = `CABANG`
   - `kodeDokumen` (string) = kode outlet cabang itu setelah dibersihkan (huruf besar, mis. `CBG-01`; spasi/titik jadi `_`)
4. Akun admin (UID = Kode Toko) otomatis berhak membaca semua rekap; tidak perlu dokumen anggota.

## Batasan yang perlu diketahui
- Kode cabang di rekap = **kode outlet aktif di HP itu**. Bila outlet aktif diganti, rekap berikutnya memakai kode baru.
- Mengirim butuh internet. Bila offline, tulisan ditahan antrean Firestore dan terkirim sendiri saat online.
- Plan gratis Firebase (Spark) cukup untuk puluhan cabang dengan rekap harian; pantau kuota bila pelanggan banyak.
- Aturan keamanan belum diuji di emulator.
