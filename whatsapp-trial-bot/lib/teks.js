// Semua teks balasan di satu tempat supaya mudah diubah tanpa menyentuh logika.

export function teksSambutan({ apkUrl, hari, tier }) {
  const unduh = apkUrl
    ? `1. Unduh & pasang aplikasi Nada POS:\n${apkUrl}\n`
    : '1. Pasang aplikasi Nada POS (minta link unduhan ke kami bila belum punya).\n';
  return (
    `Halo! 👋 Mau coba Nada POS gratis ${hari} hari? Tulis juga paket yang ingin dicoba (Custom atau Pro); kalau tidak disebut kami pakai paket ${tier}.\n\n` +
    unduh +
    '2. Buka aplikasi → tab Lainnya → Pengaturan Toko, salin "ID Perangkat" (bentuknya A1B2-C3D4-E5F6).\n' +
    '3. Kirim ID itu ke chat ini. Kode trial akan kami balas otomatis.\n\n' +
    'Satu ID Perangkat hanya bisa mendapat satu kali trial.'
  );
}

export const TEKS_BUKAN_TEKS =
  'Maaf, kami hanya bisa membaca pesan teks. Kirim ID Perangkat (contoh: A1B2-C3D4-E5F6) dalam bentuk teks ya.';

export function teksKode({ kode, tier, hari, idTampil, labelExpiry }) {
  return (
    `✅ Kode trial Nada POS paket ${tier} (${hari} hari)\n` +
    `Untuk ID Perangkat: ${idTampil}\n\n` +
    `${kode}\n\n` +
    'Cara memakai:\n' +
    '1. Buka Nada POS → tab Lainnya → Pengaturan Toko.\n' +
    '2. Tempel kode di kolom "Kode Aktivasi", lalu tekan Aktivasi.\n\n' +
    `Berlaku sampai ${labelExpiry}. Setelah itu aplikasi kembali ke paket Basic dan data tetap aman.\n` +
    'Kalau kode ditolak, pastikan ID Perangkat di aplikasi sama dengan ID di atas.'
  );
}

export const TEKS_SUDAH_PERNAH =
  'ID Perangkat ini sudah pernah dipakai untuk trial. Kalau ingin lanjut memakai paket Pro/Custom, balas chat ini dan kami bantu berlangganan. 🙏';

export const TEKS_BATAS_NOMOR =
  'Nomor ini sudah mencapai batas permintaan trial bulan ini. Kalau ada kendala (mis. salah kirim ID), balas chat ini dan kami bantu secara manual.';

export const TEKS_PENUH_HARI_INI =
  'Permintaan trial hari ini sedang penuh. Silakan kirim ulang ID Perangkat besok ya. 🙏';
