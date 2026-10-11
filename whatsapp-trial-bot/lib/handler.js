// Logika inti webhook, murni (tanpa bergantung pada Vercel) supaya bisa diuji.
import { buatKodeTrial, cariDeviceId, cariTierDiminta, tampilDeviceId } from './license.js';
import { ambilPesan, kirimTeks, samaAman, tandaTanganWebhookValid } from './whatsapp.js';
import {
  TEKS_BATAS_NOMOR, TEKS_BUKAN_TEKS, TEKS_PENUH_HARI_INI, TEKS_SUDAH_PERNAH, teksKode, teksSambutan,
} from './teks.js';

const HARI = 24 * 60 * 60;

/** Baca konfigurasi dari environment variable, dengan batas aman. */
export function bacaKonfigurasi(env) {
  const angka = (nilai, bawaan, min, max) => {
    const n = Number.parseInt(nilai ?? '', 10);
    return Number.isFinite(n) ? Math.min(max, Math.max(min, n)) : bawaan;
  };
  return {
    verifyToken: env.WHATSAPP_VERIFY_TOKEN ?? '',
    appSecret: env.WHATSAPP_APP_SECRET ?? '',
    accessToken: env.WHATSAPP_ACCESS_TOKEN ?? '',
    phoneNumberId: env.WHATSAPP_PHONE_NUMBER_ID ?? '',
    apiVersion: env.WHATSAPP_API_VERSION || 'v23.0',
    tier: env.TRIAL_TIER === 'CUSTOM' ? 'CUSTOM' : 'PRO',
    // Aplikasi menolak kode trial yang berlaku lebih dari ~15 hari, jadi 14 adalah batas atas yang masuk akal.
    // Bawaan 14 hari = angka di tombol "Coba trial 14 hari" pada aplikasi.
    hari: angka(env.TRIAL_DAYS, 14, 1, 14),
    maksPerHari: angka(env.TRIAL_MAX_PER_DAY, 30, 1, 1000),
    maksPerNomor: angka(env.TRIAL_MAX_PER_NUMBER, 3, 1, 20),
    apkUrl: env.APK_URL ?? '',
  };
}

function tanggalKunci(sekarang) {
  return sekarang.toISOString().slice(0, 10).replaceAll('-', '');
}

/** Proses satu pesan masuk. Melempar error bila balasan gagal terkirim (semua pembatasan dikembalikan). */
async function tanganiPesan(pesan, { config, store, privateKey, kirim, sekarang }) {
  const kunciPesan = `msg:${pesan.id}`;
  if (!(await store.setNX(kunciPesan, '1', 2 * HARI))) return; // sudah diproses (Meta mengirim ulang)

  const batal = [];
  // Idempoten: setiap langkah pembatalan hanya dijalankan sekali, walau dipanggil lagi dari catch di bawah.
  const kembalikan = async () => {
    while (batal.length > 0) {
      const fn = batal.pop();
      try { await fn(); } catch { /* upaya terbaik */ }
    }
  };

  try {
    if (pesan.type !== 'text') {
      await kirim(pesan.from, TEKS_BUKAN_TEKS);
      return;
    }
    const id = cariDeviceId(pesan.teks);
    if (!id) {
      await kirim(pesan.from, teksSambutan({ apkUrl: config.apkUrl, hari: config.hari, tier: config.tier }));
      return;
    }

    // 1) batas per nomor (bulanan)
    const kunciNomor = `trial:wa:${pesan.from}`;
    const percobaan = await store.incr(kunciNomor, 30 * HARI);
    batal.push(() => store.decr(kunciNomor));
    if (percobaan > config.maksPerNomor) {
      await kembalikan();
      await kirim(pesan.from, TEKS_BATAS_NOMOR);
      return;
    }

    // 2) satu trial per ID Perangkat (selamanya, ~13 bulan di penyimpanan)
    const kunciDevice = `trial:device:${id}`;
    if (!(await store.setNX(kunciDevice, JSON.stringify({ wa: pesan.from, tgl: sekarang.toISOString() }), 400 * HARI))) {
      await kembalikan();
      await kirim(pesan.from, TEKS_SUDAH_PERNAH);
      return;
    }
    batal.push(() => store.del(kunciDevice));

    // 3) batas harian global
    const kunciHari = `trial:hari:${tanggalKunci(sekarang)}`;
    const hariIni = await store.incr(kunciHari, 2 * HARI);
    batal.push(() => store.decr(kunciHari));
    if (hariIni > config.maksPerHari) {
      await kembalikan();
      await kirim(pesan.from, TEKS_PENUH_HARI_INI);
      return;
    }

    // 4) buat kode dan kirim; bila pengiriman gagal, semua pembatasan dikembalikan agar pembeli bisa mencoba lagi
    // Paket mengikuti permintaan pembeli (Custom/Pro); durasi selalu ditentukan server, bukan pembeli.
    const tier = cariTierDiminta(pesan.teks) ?? config.tier;
    const { kode, label } = buatKodeTrial({ privateKey, tier, deviceId: id, sekarang, hari: config.hari });
    await kirim(
      pesan.from,
      teksKode({ kode, tier, hari: config.hari, idTampil: tampilDeviceId(id), labelExpiry: label }),
    );
  } catch (e) {
    await kembalikan();
    await store.del(kunciPesan); // izinkan percobaan ulang dari Meta
    throw e;
  }
}

/**
 * @returns {{status:number, body:string, contentType:string}}
 */
export async function handleWebhook({ method, query = {}, rawBody, headers = {}, config, store, privateKey, fetchFn = fetch, sekarang = () => new Date() }) {
  const teks = (status, body) => ({ status, body, contentType: 'text/plain; charset=utf-8' });

  if (method === 'GET') {
    // Verifikasi webhook saat didaftarkan di dashboard Meta.
    const ok = query['hub.mode'] === 'subscribe' && config.verifyToken && samaAman(query['hub.verify_token'], config.verifyToken);
    return ok ? teks(200, String(query['hub.challenge'] ?? '')) : teks(403, 'Forbidden');
  }
  if (method !== 'POST') return teks(405, 'Method Not Allowed');

  if (!config.appSecret) return teks(500, 'Server belum dikonfigurasi (WHATSAPP_APP_SECRET)');
  if (!tandaTanganWebhookValid(rawBody, headers['x-hub-signature-256'], config.appSecret)) return teks(401, 'Invalid signature');

  let payload;
  try {
    payload = JSON.parse(Buffer.from(rawBody).toString('utf8'));
  } catch {
    return teks(400, 'Bad Request');
  }

  const kirim = (to, body) => kirimTeks({
    fetchFn, token: config.accessToken, phoneNumberId: config.phoneNumberId, versi: config.apiVersion, to, body,
  });

  let gagal = false;
  for (const pesan of ambilPesan(payload)) {
    try {
      await tanganiPesan(pesan, { config, store, privateKey, kirim, sekarang: sekarang() });
    } catch (e) {
      gagal = true;
      console.error(`Gagal memproses pesan ${pesan.id}: ${e.message}`); // tanpa isi pesan/kode
    }
  }
  // 500 membuat Meta mengirim ulang; pesan yang sudah sukses dilindungi dari duplikat oleh kunci msg:*
  return gagal ? teks(500, 'Retry') : teks(200, 'OK');
}
