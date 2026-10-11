// Pembuat kode trial. Format HARUS sama dengan tools/generate_license.py dan LicenseKeyValidator.kt:
//   NADA-{TIER}-{YYYYMMDD}-{BASE32(tanda tangan ECDSA P-256, 64 byte r||s) per 8 karakter}
//   pesan yang ditandatangani (V2, terikat perangkat): "NADA-LIC-V2:TIER:EXPIRY:ID_PERANGKAT"
import { createPrivateKey, sign } from 'node:crypto';

const ALFABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
const DOMAIN_V2 = 'NADA-LIC-V2';
const SPKI_PREFIX_PANJANG = 26; // header ASN.1 "SubjectPublicKeyInfo" EC P-256, lalu 0x04 + X(32) + Y(32)

const BULAN = [
  'Januari', 'Februari', 'Maret', 'April', 'Mei', 'Juni',
  'Juli', 'Agustus', 'September', 'Oktober', 'November', 'Desember',
];

/** RFC 4648 Base32 tanpa padding (sama dengan base64.b32encode(...).rstrip('=') di Python). */
export function base32(buf) {
  let hasil = '';
  let buffer = 0;
  let bit = 0;
  for (const b of buf) {
    buffer = (buffer << 8) | b;
    bit += 8;
    while (bit >= 5) {
      bit -= 5;
      hasil += ALFABET[(buffer >> bit) & 31];
    }
    buffer &= (1 << bit) - 1;
  }
  if (bit > 0) hasil += ALFABET[(buffer << (5 - bit)) & 31];
  return hasil;
}

/** "a1b2-c3d4-e5f6" -> "A1B2C3D4E5F6"; null bila bukan 12 karakter hex. */
export function normalisasiDeviceId(teks) {
  const bersih = String(teks ?? '').toUpperCase().replace(/[^0-9A-Z]/g, '');
  if (bersih.length !== 12 || /[^0-9A-F]/.test(bersih)) return null;
  return bersih;
}

const SEP = '[\\s\\-\\u2010-\\u2015]'; // spasi, tanda hubung, dan varian tanda hubung hasil salin-tempel
// Tidak boleh menempel pada huruf/angka lain, dan tidak boleh disambung "-hex" di depan/belakang (mis. 4 kelompok
// "A1B2-C3D4-E5F6-AAAA" bukan ID Perangkat; aplikasi selalu menampilkan 3 kelompok).
const AWAL = '(?<![0-9A-Za-z])(?<![0-9A-Fa-f]-)';
const AKHIR = '(?![0-9A-Za-z])(?!-[0-9A-Fa-f])';
const POLA_GRUP = new RegExp(`${AWAL}([0-9A-F]{4})${SEP}([0-9A-F]{4})${SEP}([0-9A-F]{4})${AKHIR}`, 'i');
const POLA_UTUH = new RegExp(`${AWAL}([0-9A-F]{12})${AKHIR}`, 'i');

/**
 * Cari ID Perangkat di teks pesan (bentuk "A1B2-C3D4-E5F6" atau 12 hex tanpa pemisah).
 * Wajib mengandung minimal satu huruf A-F supaya nomor telepon (12 digit) tidak salah dikira ID.
 * Konsekuensinya ID yang kebetulan terdiri dari angka saja (sekitar 0,35%) tidak dikenali bot.
 */
export function cariDeviceId(teks) {
  const t = String(teks ?? '');
  const cocok = t.match(POLA_GRUP) ?? t.match(POLA_UTUH);
  if (!cocok) return null;
  const id = normalisasiDeviceId(cocok.slice(1).join(''));
  if (!id || !/[A-F]/.test(id)) return null;
  return id;
}

/**
 * Paket yang diminta pembeli. Tombol trial di aplikasi menulis "... paket Custom." atau "... paket Pro.".
 * Bila tidak jelas (tidak disebut, atau keduanya disebut tanpa kata "paket"), kembalikan null -> pakai bawaan server.
 */
export function cariTierDiminta(teks) {
  const t = String(teks ?? '');
  const sesudahPaket = t.match(/paket\s+(custom|pro)(?![0-9A-Za-z])/i);
  if (sesudahPaket) return sesudahPaket[1].toUpperCase();
  const custom = /(?<![0-9A-Za-z])custom(?![0-9A-Za-z])/i.test(t);
  const pro = /(?<![0-9A-Za-z])pro(?![0-9A-Za-z])/i.test(t);
  if (custom !== pro) return custom ? 'CUSTOM' : 'PRO';
  return null;
}

export function tampilDeviceId(id) {
  return id.match(/.{1,4}/g).join('-');
}

const b64url = (buf) => Buffer.from(buf).toString('base64url');

/**
 * Bangun kunci privat dari format yang sama dengan tools/generate_license.py --init:
 *  - privateHex   : 64 karakter hex (isi file license.trial.private)
 *  - publicBase64 : isi file license.trial.public (X.509 SubjectPublicKeyInfo, base64)
 */
export function kunciPrivatDari(privateHex, publicBase64) {
  const hex = String(privateHex ?? '').replace(/\s+/g, '');
  if (!/^[0-9a-fA-F]{64}$/.test(hex)) throw new Error('TRIAL_PRIVATE_KEY_HEX harus 64 karakter hex');
  const der = Buffer.from(String(publicBase64 ?? '').trim(), 'base64');
  if (der.length !== SPKI_PREFIX_PANJANG + 65 || der[SPKI_PREFIX_PANJANG] !== 0x04) {
    throw new Error('TRIAL_PUBLIC_KEY bukan kunci publik EC P-256 (isi license.trial.public)');
  }
  const x = der.subarray(SPKI_PREFIX_PANJANG + 1, SPKI_PREFIX_PANJANG + 33);
  const y = der.subarray(SPKI_PREFIX_PANJANG + 33, SPKI_PREFIX_PANJANG + 65);
  return createPrivateKey({
    key: { kty: 'EC', crv: 'P-256', d: b64url(Buffer.from(hex, 'hex')), x: b64url(x), y: b64url(y) },
    format: 'jwk',
  });
}

/** Tanggal kadaluarsa (UTC) = hari ini + [hari]. Aplikasi memakainya sampai akhir hari itu (UTC). */
export function hitungExpiry(sekarang, hari) {
  const d = new Date(Date.UTC(sekarang.getUTCFullYear(), sekarang.getUTCMonth(), sekarang.getUTCDate() + hari));
  const yyyy = String(d.getUTCFullYear()).padStart(4, '0');
  const mm = String(d.getUTCMonth() + 1).padStart(2, '0');
  const dd = String(d.getUTCDate()).padStart(2, '0');
  return { expiry: `${yyyy}${mm}${dd}`, label: `${d.getUTCDate()} ${BULAN[d.getUTCMonth()]} ${d.getUTCFullYear()}` };
}

export function buatKodeTrial({ privateKey, tier, deviceId, sekarang, hari }) {
  const id = normalisasiDeviceId(deviceId);
  if (!id) throw new Error('ID Perangkat tidak valid');
  if (tier !== 'PRO' && tier !== 'CUSTOM') throw new Error('tier trial harus PRO atau CUSTOM');
  const { expiry, label } = hitungExpiry(sekarang, hari);
  const pesan = Buffer.from(`${DOMAIN_V2}:${tier}:${expiry}:${id}`, 'utf8');
  const tandaTangan = sign('sha256', pesan, { key: privateKey, dsaEncoding: 'ieee-p1363' }); // r||s, 64 byte
  const b32 = base32(tandaTangan);
  const kelompok = b32.match(/.{1,8}/g).join('-');
  return { kode: `NADA-${tier}-${expiry}-${kelompok}`, expiry, label };
}
