import test from 'node:test';
import assert from 'node:assert/strict';
import { buatMemoryStore, buatUpstashStore } from '../lib/store.js';
import { samaAman, tandaTanganWebhookValid, ambilPesan } from '../lib/whatsapp.js';
import { createHmac } from 'node:crypto';

test('memory store: setNX, TTL, incr/decr', async () => {
  let t = 1000;
  const s = buatMemoryStore(() => t);
  assert.equal(await s.setNX('k', 'v', 10), true);
  assert.equal(await s.setNX('k', 'v2', 10), false);
  t += 11_000;
  assert.equal(await s.setNX('k', 'v3', 10), true); // sudah kadaluarsa
  assert.equal(await s.incr('n', 60), 1);
  assert.equal(await s.incr('n', 60), 2);
  await s.decr('n');
  assert.equal(await s.get('n'), '1');
});

test('upstash store mengirim perintah yang benar dan memberi TTL hanya pada INCR pertama', async () => {
  const log = [];
  const balasan = { SET: 'OK', INCR: 1, EXPIRE: 1, DEL: 1, GET: null, DECR: 0 };
  const fetchFn = async (url, init) => {
    const args = JSON.parse(init.body);
    log.push({ url, auth: init.headers.Authorization, args });
    return { ok: true, status: 200, json: async () => ({ result: balasan[args[0]] }) };
  };
  const s = buatUpstashStore({ url: 'https://u.example', token: 'T', fetchFn });
  assert.equal(await s.setNX('a', 'b', 5), true);
  await s.incr('c', 99);
  balasan.INCR = 2;
  await s.incr('c', 99);
  assert.deepEqual(log[0].args, ['SET', 'a', 'b', 'NX', 'EX', '5']);
  assert.equal(log[0].auth, 'Bearer T');
  assert.equal(log.filter((l) => l.args[0] === 'EXPIRE').length, 1);
});

test('upstash store melempar error saat Redis menjawab error, dan menolak konfigurasi kosong', async () => {
  const fetchFn = async () => ({ ok: false, status: 401, json: async () => ({ error: 'WRONGPASS' }) });
  const s = buatUpstashStore({ url: 'https://u.example', token: 'T', fetchFn });
  await assert.rejects(() => s.get('x'), /WRONGPASS/);
  assert.throws(() => buatUpstashStore({ url: '', token: '' }));
});

test('tanda tangan webhook: benar diterima; isi diubah satu byte atau secret salah ditolak', () => {
  const body = Buffer.from('{"a":1}');
  const sig = 'sha256=' + createHmac('sha256', 's').update(body).digest('hex');
  assert.equal(tandaTanganWebhookValid(body, sig, 's'), true);
  assert.equal(tandaTanganWebhookValid(Buffer.from('{"a":2}'), sig, 's'), false);
  assert.equal(tandaTanganWebhookValid(body, sig, 'lain'), false);
  assert.equal(tandaTanganWebhookValid(body, undefined, 's'), false);
  assert.equal(tandaTanganWebhookValid(body, sig, ''), false);
  assert.equal(samaAman('abc', 'abcd'), false);
});

test('ambilPesan mengabaikan status pengiriman dan payload kosong', () => {
  assert.deepEqual(ambilPesan({}), []);
  assert.deepEqual(ambilPesan({ entry: [{ changes: [{ value: { statuses: [{ id: 'x' }] } }] }] }), []);
});
