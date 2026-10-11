// Adapter tipis untuk Vercel (Node.js runtime). Seluruh logika ada di lib/handler.js.
import { handleWebhook, bacaKonfigurasi } from '../lib/handler.js';
import { ambilRuntime } from '../lib/runtime.js';

// Tanda tangan Meta dihitung atas isi MENTAH, jadi body tidak boleh di-parse otomatis.
export const config = { api: { bodyParser: false } };

async function bacaMentah(req) {
  if (Buffer.isBuffer(req.rawBody)) return req.rawBody;
  const potongan = [];
  for await (const p of req) potongan.push(typeof p === 'string' ? Buffer.from(p) : p);
  return Buffer.concat(potongan);
}

export default async function handler(req, res) {
  let hasil;
  try {
    const url = new URL(req.url, 'http://localhost');
    const query = Object.fromEntries(url.searchParams);
    const rawBody = req.method === 'POST' ? await bacaMentah(req) : Buffer.alloc(0);
    // GET (verifikasi webhook) hanya butuh token verifikasi; POST butuh kunci + penyimpanan lengkap.
    const rt = req.method === 'POST' ? ambilRuntime() : { config: bacaKonfigurasi(process.env) };
    hasil = await handleWebhook({ method: req.method, query, rawBody, headers: req.headers, ...rt });
  } catch (e) {
    console.error(`Webhook error: ${e.message}`);
    hasil = { status: 500, body: 'Internal Server Error', contentType: 'text/plain; charset=utf-8' };
  }
  res.statusCode = hasil.status;
  res.setHeader('Content-Type', hasil.contentType);
  res.end(hasil.body);
}
