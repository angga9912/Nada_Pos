# Bot WhatsApp: kode trial otomatis Nada POS

Pembeli menekan tombol **"Coba trial 14 hari …"** di aplikasi (atau mengirim ID Perangkat sendiri) ke nomor
WhatsApp Nada POS → bot membalas **kode trial** untuk paket yang diminta (Custom/Pro, 14 hari) dalam hitungan detik, tanpa kamu membuka Termux.

```
Pembeli ──pesan──▶ WhatsApp Cloud API ──webhook──▶ Vercel (api/webhook.js) ──balasan──▶ Pembeli
                                                      │
                                                      └─ Upstash Redis: 1 trial/ID, batas per nomor & per hari
```

## Kenapa kodenya dibuat di sini, bukan lewat GitHub Actions

Workflow "Generate Kode Lisensi" memakai kunci lisensi utama. Token GitHub yang dipakai server untuk memicunya
bisa membaca log semua workflow di repo itu, jadi kebocoran token = bisa membuat lisensi apa pun. Karena itu bot
memakai **kunci trial yang terpisah**:

- Aplikasi hanya menerima kode dari kunci trial bila **terikat ke ID Perangkat**, **bukan LIFETIME**, dan
  **berlaku paling lama 16 hari dari sekarang** (`LicenseKeyValidator.validasiTrial`).
- Jadi kalau server ini bocor, penyerang paling banyak membuat kode trial pendek. Lisensi berbayar tidak tersentuh.
- GitHub tetap dipakai untuk **deploy otomatis**: hubungkan repo bot ini ke Vercel, setiap push ikut ter-deploy.
- Aplikasi menolak kode trial bila toko sudah punya paket aktif, supaya lisensi berbayar tidak tertimpa.

## Langkah setup

### 1. Buat kunci trial (sekali saja)
```bash
python3 tools/generate_license.py --init --private-file license.trial.private --public-file license.trial.public
```
- Upload **`license.trial.public`** ke root repo aplikasi (aman, isinya kunci publik), lalu **build ulang APK**.
  Hanya APK yang dibuild setelah file ini ada yang menerima kode trial.
- Isi **`license.trial.private`** (64 karakter hex) hanya dipakai sebagai environment variable di Vercel.
  Jangan di-commit (sudah ada di `.gitignore`). Simpan cadangan di password manager.

### 2. Upstash Redis (penyimpanan batas trial)
Di dashboard Vercel: **Storage / Marketplace → Upstash Redis → buat database**, sambungkan ke project. Variable
`KV_REST_API_URL` dan `KV_REST_API_TOKEN` (atau `UPSTASH_REDIS_REST_URL`/`_TOKEN`) terisi otomatis.

### 3. Deploy ke Vercel
1. Taruh folder `whatsapp-trial-bot/` sebagai repo GitHub sendiri (boleh private), atau pakai repo yang ada dan
   isi *Root Directory* = `whatsapp-trial-bot`.
2. Import ke Vercel, lalu isi Environment Variables:

| Variable | Isi |
|---|---|
| `WHATSAPP_VERIFY_TOKEN` | kata acak buatanmu (dipakai saat mendaftarkan webhook) |
| `WHATSAPP_APP_SECRET` | App Secret dari Meta (App settings → Basic) |
| `WHATSAPP_ACCESS_TOKEN` | token permanen (System User) dengan izin `whatsapp_business_messaging` |
| `WHATSAPP_PHONE_NUMBER_ID` | ID nomor dari WhatsApp → API Setup |
| `TRIAL_PRIVATE_KEY_HEX` | isi `license.trial.private` |
| `TRIAL_PUBLIC_KEY` | isi `license.trial.public` |
| `APK_URL` | link unduh APK (lihat catatan di bawah) |
| `TRIAL_TIER` *(opsional)* | paket bawaan bila pesan tidak menyebut paket: `PRO` (bawaan) atau `CUSTOM` |
| `TRIAL_DAYS` *(opsional)* | 14 (bawaan, sama dengan label tombol di aplikasi), maksimal 14 |
| `TRIAL_MAX_PER_DAY` *(opsional)* | 30 (bawaan): batas trial per hari seluruhnya |
| `TRIAL_MAX_PER_NUMBER` *(opsional)* | 3 (bawaan): batas per nomor WhatsApp per 30 hari |
| `WHATSAPP_API_VERSION` *(opsional)* | `v23.0` (bawaan); cek versi terbaru di dokumentasi Meta |

### 4. Meta (WhatsApp Cloud API)
Tampilan dashboard Meta sering berubah, ikuti dokumentasi resminya bila langkah berikut berbeda.
1. developers.facebook.com → buat App (tipe Business) → tambahkan produk **WhatsApp**.
2. Catat **Phone number ID**; buat **System User** di Business Settings dan generate token permanen.
3. WhatsApp → **Configuration → Webhook**: Callback URL `https://<project-kamu>.vercel.app/api/webhook`,
   Verify token = `WHATSAPP_VERIFY_TOKEN`, lalu **subscribe ke field `messages`**.
4. Uji dulu dengan nomor uji dari Meta (penerimanya harus didaftarkan di allow-list). Untuk pembeli sungguhan,
   daftarkan nomor produksi dan ikuti syarat Meta (verifikasi bisnis, persetujuan display name). Cek juga
   biaya dan kebijakan pesan di halaman resmi Meta; balasan di dalam 24 jam setelah pembeli menulis adalah
   percakapan yang dimulai pembeli.

### 5. Uji
- Kirim "halo" → bot membalas cara mencoba + link unduh.
- Kirim ID Perangkat (mis. `A1B2-C3D4-E5F6`) → bot membalas kode. Tempel di aplikasi: Lainnya → Pengaturan Toko.
- Atau tekan tombol trial di layar Info Paket aplikasi; pesannya ("…TRIAL 14 hari Nada POS paket Custom… ID Perangkat saya: …")
  dibaca bot: paket diambil dari teks, durasi selalu ditentukan server (pembeli tidak bisa meminta lebih lama).

## Aturan pembatasan
- **Satu trial per ID Perangkat**, selamanya (nomor lain tidak bisa meminta untuk ID yang sama).
- **Maksimal 3 permintaan per nomor per 30 hari** (memberi ruang bila pembeli salah ketik ID).
- **Maksimal 30 trial per hari** secara total, supaya penyalahgunaan tidak meluas.
- Bila balasan gagal terkirim, semua pembatasan dikembalikan dan Meta akan mencoba lagi.

## Catatan dan batasan
- **Link unduh APK:** repo aplikasi yang private tidak bisa dipakai pembeli untuk mengunduh. Unggah APK rilis
  yang sudah ditandatangani (`NadaPOS.apk` dari job `build-release`) ke tempat publik (mis. repo publik khusus
  rilis atau penyimpanan file), lalu isi `APK_URL`. **Jangan** bagikan APK demo debug dari CI.
- **ID yang seluruhnya angka** (±0,35% perangkat) tidak dikenali bot karena disamakan dengan nomor telepon.
  Pembeli seperti itu perlu dibuatkan kode manual lewat `tools/generate_license.py`.
- **Kunci trial bocor?** Buat pasangan kunci trial baru, ganti `license.trial.public` dan env Vercel, lalu build ulang
  APK. Kode trial lama berhenti berlaku; lisensi berbayar tidak terpengaruh.
- Kode trial yang masih berlaku tetap bisa dipakai ulang di perangkat yang sama (mis. setelah hapus data) sampai
  tanggalnya habis.
- Server ini tidak menyimpan isi pesan; yang disimpan hanya nomor pengirim dan ID Perangkat sebagai pembatas.

## Tes
```bash
cd whatsapp-trial-bot && npm test      # butuh Node.js 20+, tanpa dependensi tambahan
```
