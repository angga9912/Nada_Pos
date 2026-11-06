# Pantau Cabang (Firebase) - Tahap 1, 2, 3A, dan 3B

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
- Aturan keamanan diuji otomatis di emulator lewat GitHub Actions (workflow "Uji aturan Firestore", folder `firebase/test`).

## Tahap 2 - sisi cabang (aplikasi)
- Menu **Pengaturan > Pantau Cabang** (admin, paket Pro): login akun cabang Firebase, isi Kode Toko, nyalakan
  "Kirim rekap otomatis", tombol "Kirim rekap sekarang", dan status terakhir terkirim/gagal.
- Pengiriman otomatis lewat WorkManager tiap 3 jam, hanya saat ada internet. Jadwal disamakan dengan setelan
  setiap aplikasi dibuka (aman setelah restore backup). Gagal sementara diulang maksimal 3 kali per putaran.
- Sebelum menyalakan: pilih **cabang aktif** di menu Outlet / Cabang, karena kode outlet itulah identitas cabang di rekap.
- Dashboard web untuk admin (Nada Pantau) ada di repo terpisah dan di-deploy ke Vercel.

## Tahap 3A - pendaftaran toko sendiri dan undangan cabang (aturan Firestore)
Tanpa Cloud Functions (tetap paket gratis Spark). Semua dijaga `firebase/firestore.rules`.

### Peta data
| Dokumen | Dibuat oleh | Isi |
|---|---|---|
| `aktivasi/{KODE}` | **kamu** (pengembang), lewat Firebase Console | `aktif` (boolean), `batasCabang` (number); `dipakaiOleh` dan `dipakaiPada` diisi otomatis saat dipakai |
| `toko/{uid pemilik}` | pemilik, saat mendaftar di dashboard | `nama`, `aktif`, `batasCabang`, `kodeAktivasi`, `dibuatPada` |
| `undangan/{KODE10}` | pemilik, di dashboard | `tokoId`, `kodeDokumen`, `namaCabang`, `berlakuSampai`, `dibuatPada`; `dipakaiOleh`, `dipakaiPada` diisi saat dipakai |
| `toko/{id}/anggota/{uid}` | cabang saat bergabung, atau pemilik manual | `peran`, `kodeDokumen`, (`undangan`, `namaCabang`, `bergabungPada`) |
| `toko/{id}/rekap/{tanggal}_{kodeDokumen}` | HP cabang | seperti sebelumnya |

### Membuat Kode Aktivasi (satu per toko yang kamu jual)
Firebase Console -> Firestore -> **Start collection** `aktivasi` (huruf kecil) -> Document ID = kode, **HURUF BESAR tanpa spasi**, 10 karakter acak (mis. `K7M2QX9PTR`) -> field:
- `aktif` = boolean `true`
- `batasCabang` = **number** (mis. `3`), bukan string

Jangan isi `dipakaiOleh`. Kode hanya bisa dipakai sekali. Beri kode itu ke pelanggan setelah mereka membayar.

### Menonaktifkan toko yang berhenti berlangganan
Firestore -> `toko` -> dokumen toko itu -> ubah `aktif` menjadi boolean `false`. Sejak itu tidak ada rekap baru yang bisa masuk, dan undangan baru ditolak. Data lama tetap ada dan tetap bisa dibaca pemilik. Untuk mengaktifkan lagi, ubah kembali ke `true`.

### Toko yang sudah dibuat manual (Tahap 1-2)
Tetap jalan tanpa perubahan, **asal dokumen `toko/{UID admin}` ada** (sekarang dibutuhkan; sebelumnya tidak). Field `aktif` yang tidak ada dianggap aktif.

### Hal yang sengaja belum ditegakkan
- `batasCabang` hanya ditegakkan di tampilan dashboard, belum di aturan Firestore.
- Akun anonim cabang hilang bila aplikasi dihapus atau datanya dibersihkan. Pemilik membuat undangan baru dengan kode cabang yang sama, lalu mencabut akun lama; riwayat rekap tetap menyambung karena kode cabang sama.

### Menguji aturan
Otomatis: setiap push yang mengubah folder `firebase/` menjalankan workflow **Uji aturan Firestore** (tab Actions). Hijau berarti semua skenario lolos.
Lokal: `cd firebase/test && npm install && npx firebase emulators:exec --only firestore --project demo-nadapos "npm test"` (butuh Java 21).

## Tahap 3B - HP cabang bergabung lewat kode undangan (aplikasi)
Cara baru yang menggantikan pembuatan akun email per cabang di Firebase Console (cara lama tetap bisa dipakai).

1. Pemilik membuka dashboard Nada Pantau > **Kelola cabang** > isi nama dan kode cabang > **Buat undangan**, lalu memberikan kode 10 karakter ke pengelola cabang (berlaku 7 hari, sekali pakai).
2. Di HP cabang: **Pengaturan > Pantau Cabang > Gabung dengan kode undangan** > masukkan kode > **Gabung**. HP masuk sebagai akun anonim, membuat dokumen anggota CABANG, dan menandai undangan terpakai dalam satu batch. Kirim rekap otomatis langsung menyala.
3. Identitas cabang di rekap (`kodeDokumen`, `namaCabang`) diambil dari undangan, **bukan** dari outlet aktif HP. Aturan Firestore mewajibkan `kodeDokumen` rekap sama dengan dokumen anggota.
4. **Lepas HP dari toko** menghapus identitas cabang dan keluar dari akun anonim. Akun anonim yang sudah keluar tidak bisa dipulihkan; untuk menyambungkan HP lagi, pemilik cukup membuat undangan baru dengan kode cabang yang sama (cabut akses yang lama dulu di Kelola cabang).
5. HP yang diganti atau data aplikasi yang terhapus kehilangan akun anonimnya. Pemilik mencabut akses lama di Kelola cabang, lalu membuat undangan baru.

### Syarat sekali jalan
- Firebase Console > Authentication > Sign-in method > aktifkan **Anonymous**.
- Aturan Firestore terbaru (`firebase/firestore.rules`) sudah dipublikasikan.
