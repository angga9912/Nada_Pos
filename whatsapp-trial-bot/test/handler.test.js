import test from 'node:test';
import assert from 'node:assert/strict';
import { createHmac } from 'node:crypto';
import { bacaKonfigurasi, handleWebhook } from '../lib/handler.js';
import { kunciPrivatDari } from '../lib/license.js';
import { buatMemoryStore } from '../lib/store.js';
import { buatKunciUji } from './helpers.js';

const SECRET = 'rahasia-app';
const ID = 'A1B2-C3D4-E5F6';

function siapkan(envTambahan = {}, opsi = {}) {
  const { hex, publicBase64 } = buatKunciUji();
  const config = bacaKonfigurasi({
    WHATSAPP_VERIFY_TOKEN: 'verif', WHATSAPP_APP_SECRET: SECRET, WHATSAPP_ACCESS_TOKEN: 'tok',
    WHATSAPP_PHONE_NUMBER_ID: '123', APK_URL: 'https://contoh.id/NadaPOS.apk', ...envTambahan,
  });
  const terkirim = [];
  let gagalKirim = opsi.gagalKirim ?? 0;
  const fetchFn = async (url, init) => {
    if (gagalKirim > 0) { gagalKirim--; return { ok: false, status: 500, text: async () => 'boom' }; }
    terkirim.push({ url, ...JSON.parse(init.body) });
    return { ok: true, status: 200, text: async () => '' };
  };
  return {
    config, terkirim, store: buatMemoryStore(), privateKey: kunciPrivatDari(hex, publicBase64), fetchFn,
    sekarang: () => new Date('2026-10-07T10:00:00Z'),
  };
}

function pesanMasuk(teks, { id = 'wamid.1', from = '6281111', type = 'text' } = {}) {
  const payload = { entry: [{ changes: [{ value: { messages: [{ id, from, type, text: { body: teks } }] } }] }] };
  const rawBody = Buffer.from(JSON.stringify(payload));
  const sig = 'sha256=' + createHmac('sha256', SECRET).update(rawBody).digest('hex');
  return { method: 'POST', rawBody, headers: { 'x-hub-signature-256': sig } };
}

const kirimPesan = (s, ...args) => handleWebhook({ ...s, ...pesanMasuk(...args) });

test('GET verifikasi: token benar mengembalikan challenge, salah ditolak', async () => {
  const s = siapkan();
  const ok = await handleWebhook({ ...s, method: 'GET', query: { 'hub.mode': 'subscribe', 'hub.verify_token': 'verif', 'hub.challenge': '777' } });
  assert.equal(ok.status, 200);
  assert.equal(ok.body, '777');
  const salah = await handleWebhook({ ...s, method: 'GET', query: { 'hub.mode': 'subscribe', 'hub.verify_token': 'x', 'hub.challenge': '777' } });
  assert.equal(salah.status, 403);
});

test('GET verifikasi ditolak bila verify token belum dikonfigurasi', async () => {
  const s = siapkan({ WHATSAPP_VERIFY_TOKEN: '' });
  const r = await handleWebhook({ ...s, method: 'GET', query: { 'hub.mode': 'subscribe', 'hub.verify_token': '', 'hub.challenge': '1' } });
  assert.equal(r.status, 403);
});

test('POST tanpa/ dengan tanda tangan salah ditolak dan tidak mengirim apa pun', async () => {
  const s = siapkan();
  const p = pesanMasuk(ID);
  const r1 = await handleWebhook({ ...s, ...p, headers: {} });
  const r2 = await handleWebhook({ ...s, ...p, headers: { 'x-hub-signature-256': 'sha256=' + '0'.repeat(64) } });
  assert.equal(r1.status, 401);
  assert.equal(r2.status, 401);
  assert.equal(s.terkirim.length, 0);
});

test('POST ditolak bila App Secret belum dikonfigurasi', async () => {
  const s = siapkan({ WHATSAPP_APP_SECRET: '' });
  const r = await kirimPesan(s, ID);
  assert.equal(r.status, 500);
  assert.equal(s.terkirim.length, 0);
});

test('pesan persis dari tombol trial di aplikasi: paket Custom, 14 hari, ID 4133-646D-15C4', async () => {
  const s = siapkan({}, {});
  const teks = 'Halo, saya ingin coba TRIAL 14 hari Nada POS paket Custom. Boleh minta kode aktivasi trial-nya?\n\nID Perangkat saya: 4133-646D-15C4\n(Mohon kode aktivasi dibuat untuk ID ini.)';
  const r = await kirimPesan(s, teks);
  assert.equal(r.status, 200);
  assert.equal(s.terkirim.length, 1);
  const isi = s.terkirim[0].text.body;
  assert.match(isi, /NADA-CUSTOM-20261021-[A-Z2-7-]+/); // 7 Okt 2026 + 14 hari
  assert.match(isi, /paket CUSTOM \(14 hari\)/);
  assert.match(isi, /4133-646D-15C4/);
  assert.match(isi, /21 Oktober 2026/);
});

test('paket Pro diminta -> kode PRO; paket tidak disebut -> paket bawaan server', async () => {
  const s = siapkan();
  await kirimPesan(s, 'coba trial Nada POS paket Pro. ID Perangkat saya: A1B2-C3D4-E5F6', { id: 'p1', from: '6281' });
  await kirimPesan(s, 'minta trial ID saya A1B2-C3D4-E5F7', { id: 'p2', from: '6282' });
  assert.match(s.terkirim[0].text.body, /NADA-PRO-/);
  assert.match(s.terkirim[1].text.body, /NADA-PRO-/); // bawaan TRIAL_TIER=PRO
});

test('pembeli tidak bisa meminta durasi lebih lama dari yang ditentukan server', async () => {
  const s = siapkan();
  await kirimPesan(s, 'mau trial 365 hari paket Custom ID A1B2-C3D4-E5F6');
  assert.match(s.terkirim[0].text.body, /NADA-CUSTOM-20261021-/); // tetap 14 hari
});

test('pesan berisi ID Perangkat dibalas kode trial untuk nomor pengirim', async () => {
  const s = siapkan();
  const r = await kirimPesan(s, `ini ID saya ${ID}`);
  assert.equal(r.status, 200);
  assert.equal(s.terkirim.length, 1);
  const kirim = s.terkirim[0];
  assert.equal(kirim.to, '6281111');
  assert.match(kirim.text.body, /NADA-PRO-20261021-[A-Z2-7-]+/);
  assert.match(kirim.text.body, /A1B2-C3D4-E5F6/);
  assert.match(kirim.text.body, /21 Oktober 2026/);
  assert.match(kirim.url, /\/123\/messages$/);
});

test('pesan tanpa ID dibalas sambutan + link unduhan, tanpa kode', async () => {
  const s = siapkan();
  await kirimPesan(s, 'mau coba nada pos');
  assert.equal(s.terkirim.length, 1);
  assert.match(s.terkirim[0].text.body, /https:\/\/contoh\.id\/NadaPOS\.apk/);
  assert.doesNotMatch(s.terkirim[0].text.body, /NADA-PRO-/);
});

test('pesan bukan teks dibalas permintaan kirim teks', async () => {
  const s = siapkan();
  await kirimPesan(s, '', { type: 'image' });
  assert.match(s.terkirim[0].text.body, /pesan teks/);
});

test('satu ID Perangkat hanya mendapat satu trial, bahkan dari nomor lain', async () => {
  const s = siapkan();
  await kirimPesan(s, ID, { id: 'm1', from: '6281111' });
  await kirimPesan(s, ID, { id: 'm2', from: '6282222' });
  assert.equal(s.terkirim.length, 2);
  assert.match(s.terkirim[0].text.body, /NADA-PRO-/);
  assert.doesNotMatch(s.terkirim[1].text.body, /NADA-PRO-/);
  assert.match(s.terkirim[1].text.body, /sudah pernah dipakai/);
});

test('pesan yang sama dikirim ulang oleh Meta tidak diproses dua kali', async () => {
  const s = siapkan();
  await kirimPesan(s, ID, { id: 'sama' });
  await kirimPesan(s, ID, { id: 'sama' });
  assert.equal(s.terkirim.length, 1);
});

test('batas per nomor: ID ke-4 dari nomor yang sama ditolak', async () => {
  const s = siapkan({ TRIAL_MAX_PER_NUMBER: '3' });
  const ids = ['A1B2-C3D4-E5F6', 'A1B2-C3D4-E5F7', 'A1B2-C3D4-E5F8', 'A1B2-C3D4-E5F9'];
  for (let i = 0; i < ids.length; i++) await kirimPesan(s, ids[i], { id: `m${i}` });
  assert.equal(s.terkirim.length, 4);
  assert.match(s.terkirim[2].text.body, /NADA-PRO-/);
  assert.match(s.terkirim[3].text.body, /batas permintaan trial/);
});

test('batas harian global: permintaan melebihi batas ditolak dan ID-nya tidak terpakai', async () => {
  const s = siapkan({ TRIAL_MAX_PER_DAY: '1' });
  await kirimPesan(s, 'A1B2-C3D4-E5F1', { id: 'a', from: '6281' });
  await kirimPesan(s, 'A1B2-C3D4-E5F2', { id: 'b', from: '6282' });
  assert.match(s.terkirim[1].text.body, /sedang penuh/);
  // ID kedua dikembalikan: kalau batas dinaikkan/besok, ia masih bisa dipakai
  assert.equal(await s.store.get('trial:device:A1B2C3D4E5F2'), null);
});

test('pengiriman balasan gagal: respons 500 agar Meta mencoba lagi, lalu percobaan ulang berhasil', async () => {
  const s = siapkan({}, { gagalKirim: 1 });
  const r1 = await kirimPesan(s, ID, { id: 'retry' });
  assert.equal(r1.status, 500);
  assert.equal(s.terkirim.length, 0);
  assert.equal(await s.store.get('trial:device:A1B2C3D4E5F6'), null); // ID dibebaskan lagi
  const r2 = await kirimPesan(s, ID, { id: 'retry' }); // Meta mengirim ulang pesan yang sama
  assert.equal(r2.status, 200);
  assert.match(s.terkirim[0].text.body, /NADA-PRO-/);
});

test('konfigurasi dibatasi: TRIAL_DAYS maksimal 14 dan tier hanya PRO/CUSTOM', () => {
  assert.equal(bacaKonfigurasi({ TRIAL_DAYS: '365' }).hari, 14);
  assert.equal(bacaKonfigurasi({ TRIAL_DAYS: '0' }).hari, 1);
  assert.equal(bacaKonfigurasi({ TRIAL_DAYS: 'abc' }).hari, 14);
  assert.equal(bacaKonfigurasi({ TRIAL_TIER: 'LIFETIME' }).tier, 'PRO');
  assert.equal(bacaKonfigurasi({ TRIAL_TIER: 'CUSTOM' }).tier, 'CUSTOM');
});
