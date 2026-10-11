import test from 'node:test';
import assert from 'node:assert/strict';
import { createPublicKey, verify } from 'node:crypto';
import {
  base32, buatKodeTrial, cariDeviceId, cariTierDiminta, hitungExpiry, kunciPrivatDari, normalisasiDeviceId, tampilDeviceId,
} from '../lib/license.js';
import { buatKunciUji } from './helpers.js';

test('normalisasiDeviceId menerima huruf kecil, tanda hubung, dan spasi', () => {
  assert.equal(normalisasiDeviceId('a1b2-c3d4-e5f6'), 'A1B2C3D4E5F6');
  assert.equal(normalisasiDeviceId(' A1B2 C3D4 E5F6 '), 'A1B2C3D4E5F6');
  assert.equal(normalisasiDeviceId('A1B2C3D4E5F'), null);
  assert.equal(normalisasiDeviceId('G1B2C3D4E5F6'), null);
});

test('cariDeviceId menemukan ID di tengah kalimat', () => {
  assert.equal(cariDeviceId('halo kak, ID saya A1B2-C3D4-E5F6 ya'), 'A1B2C3D4E5F6');
  assert.equal(cariDeviceId('id: a1b2c3d4e5f6'), 'A1B2C3D4E5F6');
  assert.equal(cariDeviceId('A1B2 C3D4 E5F6'), 'A1B2C3D4E5F6');
  assert.equal(cariDeviceId('A1B2\u2011C3D4\u2011E5F6'), 'A1B2C3D4E5F6'); // tanda hubung tak-putus hasil salin-tempel
});

test('cariDeviceId tidak salah mengira nomor telepon atau teks biasa sebagai ID', () => {
  assert.equal(cariDeviceId('0812-3456-7890'), null);
  assert.equal(cariDeviceId('081234567890'), null);
  assert.equal(cariDeviceId('mau coba dong'), null);
  assert.equal(cariDeviceId('A1B2-C3D4-E5F6-AAAA'), null); // terlalu panjang
  assert.equal(cariDeviceId('xA1B2C3D4E5F6'), null);        // menempel pada huruf lain
  assert.equal(cariDeviceId('AAAA-A1B2-C3D4-E5F6'), null); // 4 kelompok di depan
  assert.equal(cariDeviceId('A1B2-C3D4-E5F6 ya kak'), 'A1B2C3D4E5F6');
});

test('tampilDeviceId mengelompokkan per 4 karakter', () => {
  assert.equal(tampilDeviceId('A1B2C3D4E5F6'), 'A1B2-C3D4-E5F6');
});

test('base32 cocok dengan nilai referensi RFC 4648', () => {
  assert.equal(base32(Buffer.from('f')), 'MY');
  assert.equal(base32(Buffer.from('fo')), 'MZXQ');
  assert.equal(base32(Buffer.from('foobar')), 'MZXW6YTBOI');
});

test('hitungExpiry memakai tanggal UTC dan melewati pergantian bulan/tahun', () => {
  assert.deepEqual(hitungExpiry(new Date('2026-10-07T20:00:00Z'), 7), { expiry: '20261014', label: '14 Oktober 2026' });
  assert.equal(hitungExpiry(new Date('2026-12-28T00:00:00Z'), 7).expiry, '20270104');
});

test('buatKodeTrial: format benar dan tanda tangan terverifikasi dengan kunci publik', () => {
  const { hex, publicBase64, publicKey } = buatKunciUji();
  const privateKey = kunciPrivatDari(hex, publicBase64);
  const { kode, expiry } = buatKodeTrial({
    privateKey, tier: 'PRO', deviceId: 'a1b2-c3d4-e5f6', sekarang: new Date('2026-10-07T10:00:00Z'), hari: 7,
  });
  assert.match(kode, /^NADA-PRO-20261014-[A-Z2-7]{8}(-[A-Z2-7]{1,8})+$/);
  assert.equal(expiry, '20261014');

  // Decode seperti LicenseKeyValidator: gabungkan bagian ke-4 dst, Base32 -> 64 byte.
  const b32 = kode.split('-').slice(3).join('');
  const alfabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let buffer = 0, bit = 0; const out = [];
  for (const c of b32) { buffer = (buffer << 5) | alfabet.indexOf(c); bit += 5; if (bit >= 8) { bit -= 8; out.push((buffer >> bit) & 255); buffer &= (1 << bit) - 1; } }
  assert.equal(buffer, 0); // sisa bit nol, syarat decodeBase32 di Kotlin
  const tandaTangan = Buffer.from(out);
  assert.equal(tandaTangan.length, 64);

  const pesan = Buffer.from('NADA-LIC-V2:PRO:20261014:A1B2C3D4E5F6');
  assert.equal(verify('sha256', pesan, { key: publicKey, dsaEncoding: 'ieee-p1363' }, tandaTangan), true);
  // ID perangkat lain TIDAK boleh cocok
  const lain = Buffer.from('NADA-LIC-V2:PRO:20261014:FFFFFFFFFFFF');
  assert.equal(verify('sha256', lain, { key: publicKey, dsaEncoding: 'ieee-p1363' }, tandaTangan), false);
});

test('kunciPrivatDari menolak masukan rusak', () => {
  const { hex, publicBase64 } = buatKunciUji();
  assert.throws(() => kunciPrivatDari('zz', publicBase64));
  assert.throws(() => kunciPrivatDari(hex, 'bukan-kunci'));
});

test('buatKodeTrial menolak tier dan ID yang tidak valid', () => {
  const { hex, publicBase64 } = buatKunciUji();
  const privateKey = kunciPrivatDari(hex, publicBase64);
  const dasar = { privateKey, sekarang: new Date(), hari: 7 };
  assert.throws(() => buatKodeTrial({ ...dasar, tier: 'BASIC', deviceId: 'A1B2C3D4E5F6' }));
  assert.throws(() => buatKodeTrial({ ...dasar, tier: 'PRO', deviceId: 'xyz' }));
});

test('kunci publik hasil createPublicKey cocok dengan kunci privat yang dibangun', () => {
  const { hex, publicBase64 } = buatKunciUji();
  const privateKey = kunciPrivatDari(hex, publicBase64);
  const dariPrivat = createPublicKey(privateKey).export({ format: 'der', type: 'spki' }).toString('base64');
  assert.equal(dariPrivat, publicBase64);
});

test('cariTierDiminta membaca paket dari pesan tombol aplikasi', () => {
  assert.equal(cariTierDiminta('coba TRIAL 14 hari Nada POS paket Custom. Boleh minta'), 'CUSTOM');
  assert.equal(cariTierDiminta('coba TRIAL 14 hari Nada POS paket Pro. Boleh minta'), 'PRO');
  assert.equal(cariTierDiminta('mau coba Pro'), 'PRO');
  assert.equal(cariTierDiminta('mau coba custom dong'), 'CUSTOM');
  assert.equal(cariTierDiminta('tolong trial'), null);
  assert.equal(cariTierDiminta('prospek produk'), null);              // bukan kata "pro"
  assert.equal(cariTierDiminta('antara Custom atau Pro'), null);      // ambigu
  assert.equal(cariTierDiminta('bukan Pro, paket Custom'), 'CUSTOM'); // "paket" menentukan
});
