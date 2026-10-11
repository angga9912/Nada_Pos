// Penyimpanan kecil untuk pembatasan: satu trial per perangkat, batas per nomor WhatsApp,
// batas harian, dan anti-duplikat pesan (Meta mengirim ulang webhook bila balasan lambat/gagal).
// Dua implementasi dengan antarmuka sama: memori (untuk tes) dan Upstash Redis lewat REST (produksi).

export function buatMemoryStore(jam = () => Date.now()) {
  const data = new Map(); // key -> { nilai, kadaluarsa }
  const ambil = (key) => {
    const e = data.get(key);
    if (!e) return undefined;
    if (e.kadaluarsa !== null && e.kadaluarsa <= jam()) {
      data.delete(key);
      return undefined;
    }
    return e;
  };
  return {
    async setNX(key, nilai, ttlDetik) {
      if (ambil(key)) return false;
      data.set(key, { nilai: String(nilai), kadaluarsa: jam() + ttlDetik * 1000 });
      return true;
    },
    async get(key) {
      return ambil(key)?.nilai ?? null;
    },
    async del(key) {
      data.delete(key);
    },
    async incr(key, ttlDetik) {
      const e = ambil(key);
      const n = (e ? Number(e.nilai) : 0) + 1;
      data.set(key, { nilai: String(n), kadaluarsa: e ? e.kadaluarsa : jam() + ttlDetik * 1000 });
      return n;
    },
    async decr(key) {
      const e = ambil(key);
      if (!e) return;
      data.set(key, { ...e, nilai: String(Math.max(0, Number(e.nilai) - 1)) });
    },
  };
}

/** Upstash Redis REST: POST ke URL dasar dengan isi berupa array perintah, mis. ["SET","k","v","NX","EX","60"]. */
export function buatUpstashStore({ url, token, fetchFn = fetch }) {
  if (!url || !token) throw new Error('Penyimpanan belum dikonfigurasi (KV_REST_API_URL / KV_REST_API_TOKEN)');
  async function perintah(args) {
    const res = await fetchFn(url, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(args),
    });
    const json = await res.json().catch(() => ({}));
    if (!res.ok || json.error) throw new Error(`Upstash ${args[0]} gagal (${res.status}): ${json.error ?? 'tanpa pesan'}`);
    return json.result;
  }
  return {
    async setNX(key, nilai, ttlDetik) {
      return (await perintah(['SET', key, String(nilai), 'NX', 'EX', String(ttlDetik)])) === 'OK';
    },
    async get(key) {
      return (await perintah(['GET', key])) ?? null;
    },
    async del(key) {
      await perintah(['DEL', key]);
    },
    async incr(key, ttlDetik) {
      const n = await perintah(['INCR', key]);
      if (n === 1) await perintah(['EXPIRE', key, String(ttlDetik)]);
      return n;
    },
    async decr(key) {
      await perintah(['DECR', key]);
    },
  };
}
