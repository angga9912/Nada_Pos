// Utilitas WhatsApp Cloud API: verifikasi tanda tangan webhook, membaca pesan masuk, mengirim balasan.
import { createHmac, timingSafeEqual } from 'node:crypto';

/** Bandingkan dua string dengan waktu konstan. */
export function samaAman(a, b) {
  const x = Buffer.from(String(a ?? ''), 'utf8');
  const y = Buffer.from(String(b ?? ''), 'utf8');
  if (x.length !== y.length) return false;
  return timingSafeEqual(x, y);
}

/**
 * Meta menandatangani isi mentah webhook: header "X-Hub-Signature-256: sha256=<hmac-sha256 hex>"
 * dengan App Secret sebagai kunci. Isi harus byte-per-byte sama dengan yang diterima (jangan parse dulu).
 */
export function tandaTanganWebhookValid(rawBody, headerTandaTangan, appSecret) {
  if (!appSecret || !headerTandaTangan) return false;
  const diharapkan = 'sha256=' + createHmac('sha256', appSecret).update(rawBody).digest('hex');
  return samaAman(headerTandaTangan, diharapkan);
}

/** Ambil pesan masuk dari payload webhook; status pengiriman dan event lain diabaikan. */
export function ambilPesan(payload) {
  const hasil = [];
  for (const entry of payload?.entry ?? []) {
    for (const change of entry?.changes ?? []) {
      for (const m of change?.value?.messages ?? []) {
        if (!m?.id || !m?.from) continue;
        hasil.push({ id: String(m.id), from: String(m.from), type: String(m.type ?? ''), teks: m.text?.body ?? '' });
      }
    }
  }
  return hasil;
}

export async function kirimTeks({ fetchFn = fetch, token, phoneNumberId, versi = 'v23.0', to, body }) {
  const res = await fetchFn(`https://graph.facebook.com/${versi}/${phoneNumberId}/messages`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ messaging_product: 'whatsapp', to, type: 'text', text: { preview_url: false, body } }),
  });
  if (!res.ok) {
    const detail = (await res.text().catch(() => '')).slice(0, 300);
    throw new Error(`Kirim WhatsApp gagal (${res.status}): ${detail}`);
  }
}
