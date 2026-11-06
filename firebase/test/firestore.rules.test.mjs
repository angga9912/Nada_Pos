// Uji aturan keamanan Firestore (firebase/firestore.rules) memakai emulator.
// Jalan otomatis di GitHub Actions (workflow "Uji aturan Firestore"), atau lokal:
//   cd firebase/test && npm install && npx firebase emulators:exec --only firestore --project demo-nadapos "npm test"
import { readFileSync } from 'node:fs';
import { describe, it, before, after, beforeEach } from 'node:test';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import {
  doc, collection, query, where, getDoc, getDocs, setDoc, updateDoc, deleteDoc,
  writeBatch, serverTimestamp, Timestamp,
} from 'firebase/firestore';

const PEMILIK_A = 'pemilikA';
const PEMILIK_B = 'pemilikB';
const PEMILIK_LAMA = 'pemilikLama'; // toko setup manual Tahap 1-2: tanpa field "aktif"
const PEMILIK_MATI = 'pemilikMati'; // toko dinonaktifkan
const CABANG_MANUAL = 'cabangManual'; // anggota CABANG buatan manual (kodeDokumen CBG01)
const CABANG_1 = 'cabang1'; // anggota CABANG hasil undangan (kodeDokumen CBG02)
const CABANG_BARU = 'cabangBaru'; // akun anonim yang akan bergabung
const PENDAFTAR = 'pendaftarBaru';
const ORANG_ASING = 'orangAsing';

const JAM = 3600 * 1000;
const HARI = 24 * JAM;
const dalam = (ms) => Timestamp.fromMillis(Date.now() + ms);

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-nadapos',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});

after(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const toko = (id, extra) => setDoc(doc(db, 'toko', id), { nama: `Toko ${id}`, ...extra });
    await toko(PEMILIK_A, { aktif: true, batasCabang: 3, kodeAktivasi: 'LAMA000001', dibuatPada: Timestamp.now() });
    await toko(PEMILIK_B, { aktif: true, batasCabang: 3, kodeAktivasi: 'LAMA000002', dibuatPada: Timestamp.now() });
    await toko(PEMILIK_LAMA, {});
    await toko(PEMILIK_MATI, { aktif: false, batasCabang: 3, kodeAktivasi: 'LAMA000003', dibuatPada: Timestamp.now() });

    await setDoc(doc(db, 'toko', PEMILIK_A, 'anggota', CABANG_MANUAL), { peran: 'CABANG', kodeDokumen: 'CBG01' });
    await setDoc(doc(db, 'toko', PEMILIK_A, 'anggota', CABANG_1), {
      peran: 'CABANG', kodeDokumen: 'CBG02', undangan: 'UNDANG0001', namaCabang: 'Cabang Dua',
    });
    await setDoc(doc(db, 'toko', PEMILIK_LAMA, 'anggota', CABANG_MANUAL), { peran: 'CABANG', kodeDokumen: 'CBG01' });
    await setDoc(doc(db, 'toko', PEMILIK_MATI, 'anggota', CABANG_MANUAL), { peran: 'CABANG', kodeDokumen: 'CBG01' });

    await setDoc(doc(db, 'aktivasi', 'BARU000001'), { aktif: true, batasCabang: 2 });
    await setDoc(doc(db, 'aktivasi', 'BARU000002'), { aktif: true, batasCabang: 5 });
    await setDoc(doc(db, 'aktivasi', 'PAKAI00001'), { aktif: true, batasCabang: 2, dipakaiOleh: 'orangLain' });
    await setDoc(doc(db, 'aktivasi', 'MATI000001'), { aktif: false, batasCabang: 2 });

    const undangan = (kode, extra) => setDoc(doc(db, 'undangan', kode), {
      tokoId: PEMILIK_A, kodeDokumen: 'CBG03', namaCabang: 'Cabang Tiga',
      berlakuSampai: dalam(3 * HARI), dibuatPada: Timestamp.now(), ...extra,
    });
    await undangan('UNDANG0001', { kodeDokumen: 'CBG02', namaCabang: 'Cabang Dua', dipakaiOleh: CABANG_1 });
    await undangan('UNDANG0002', {});
    await undangan('UNDANG0003', { berlakuSampai: dalam(-1 * HARI) }); // kedaluwarsa
    await undangan('UNDANG0004', { dipakaiOleh: 'orangLain' }); // sudah dipakai
    await undangan('UNDANG0005', { tokoId: PEMILIK_B, kodeDokumen: 'BBB01', namaCabang: 'Cabang B' });
    await undangan('UNDANG0006', { tokoId: PEMILIK_MATI });

    await setDoc(doc(db, 'toko', PEMILIK_A, 'rekap', '2026-11-02_CBG02'), {
      kodeDokumen: 'CBG02', tanggal: '2026-11-02', totalPenjualan: 1000,
    });
  });
});

const dbOf = (uid) => env.authenticatedContext(uid).firestore();
const dbAnon = (uid) => env.authenticatedContext(uid, { firebase: { sign_in_provider: 'anonymous' } }).firestore();
const dbTamu = () => env.unauthenticatedContext().firestore();

// Pendaftaran toko: satu batch (toko + tandai aktivasi dipakai).
function daftar(db, uid, kode, { nama = 'Toko Baru', batas = 2, aktif = true, tulisToko = true, tandai = true } = {}) {
  const b = writeBatch(db);
  if (tulisToko) {
    b.set(doc(db, 'toko', uid), {
      nama, aktif, batasCabang: batas, kodeAktivasi: kode, dibuatPada: serverTimestamp(),
    });
  }
  if (tandai) b.update(doc(db, 'aktivasi', kode), { dipakaiOleh: uid, dipakaiPada: serverTimestamp() });
  return b.commit();
}

// Cabang bergabung: satu batch (anggota + tandai undangan dipakai).
function gabung(db, uid, tokoId, kode, { kodeDokumen = 'CBG03', namaCabang = 'Cabang Tiga', peran = 'CABANG', tulisAnggota = true, tandai = true } = {}) {
  const b = writeBatch(db);
  if (tulisAnggota) {
    b.set(doc(db, 'toko', tokoId, 'anggota', uid), {
      peran, kodeDokumen, undangan: kode, namaCabang, bergabungPada: serverTimestamp(),
    });
  }
  if (tandai) b.update(doc(db, 'undangan', kode), { dipakaiOleh: uid, dipakaiPada: serverTimestamp() });
  return b.commit();
}

const dataUndangan = (extra = {}) => ({
  tokoId: PEMILIK_A, kodeDokumen: 'CBG09', namaCabang: 'Cabang Sembilan',
  berlakuSampai: dalam(3 * HARI), dibuatPada: serverTimestamp(), ...extra,
});

describe('pendaftaran toko dengan kode aktivasi', () => {
  it('berhasil dengan kode sah', async () => {
    await assertSucceeds(daftar(dbOf(PENDAFTAR), PENDAFTAR, 'BARU000001'));
    // setelah terdaftar, pemilik bisa membaca dokumen tokonya
    await assertSucceeds(getDoc(doc(dbOf(PENDAFTAR), 'toko', PENDAFTAR)));
  });

  it('ditolak bila kode sudah dipakai', async () => {
    await assertFails(daftar(dbOf(PENDAFTAR), PENDAFTAR, 'PAKAI00001'));
  });

  it('ditolak bila kode tidak aktif', async () => {
    await assertFails(daftar(dbOf(PENDAFTAR), PENDAFTAR, 'MATI000001'));
  });

  it('ditolak bila kode tidak ada', async () => {
    await assertFails(daftar(dbOf(PENDAFTAR), PENDAFTAR, 'TIDAKADA01'));
  });

  it('ditolak bila batas cabang tidak sama dengan kode aktivasi', async () => {
    await assertFails(daftar(dbOf(PENDAFTAR), PENDAFTAR, 'BARU000001', { batas: 99 }));
  });

  it('ditolak bila toko dibuat nonaktif', async () => {
    await assertFails(daftar(dbOf(PENDAFTAR), PENDAFTAR, 'BARU000001', { aktif: false }));
  });

  it('ditolak bila hanya menulis toko tanpa menandai kode', async () => {
    await assertFails(daftar(dbOf(PENDAFTAR), PENDAFTAR, 'BARU000001', { tandai: false }));
  });

  it('ditolak bila hanya menandai kode tanpa membuat toko', async () => {
    await assertFails(daftar(dbOf(PENDAFTAR), PENDAFTAR, 'BARU000001', { tulisToko: false }));
  });

  it('ditolak bila membuat toko di path akun lain', async () => {
    const db = dbOf(PENDAFTAR);
    const b = writeBatch(db);
    b.set(doc(db, 'toko', ORANG_ASING), {
      nama: 'Curang', aktif: true, batasCabang: 2, kodeAktivasi: 'BARU000001', dibuatPada: serverTimestamp(),
    });
    b.update(doc(db, 'aktivasi', 'BARU000001'), { dipakaiOleh: PENDAFTAR, dipakaiPada: serverTimestamp() });
    await assertFails(b.commit());
  });

  it('ditolak bila menandai kode atas nama akun lain', async () => {
    const db = dbOf(PENDAFTAR);
    const b = writeBatch(db);
    b.set(doc(db, 'toko', PENDAFTAR), {
      nama: 'Toko Baru', aktif: true, batasCabang: 2, kodeAktivasi: 'BARU000001', dibuatPada: serverTimestamp(),
    });
    b.update(doc(db, 'aktivasi', 'BARU000001'), { dipakaiOleh: ORANG_ASING, dipakaiPada: serverTimestamp() });
    await assertFails(b.commit());
  });

  it('ditolak bila ada field tambahan di dokumen toko', async () => {
    const db = dbOf(PENDAFTAR);
    const b = writeBatch(db);
    b.set(doc(db, 'toko', PENDAFTAR), {
      nama: 'Toko Baru', aktif: true, batasCabang: 2, kodeAktivasi: 'BARU000001', dibuatPada: serverTimestamp(), admin: true,
    });
    b.update(doc(db, 'aktivasi', 'BARU000001'), { dipakaiOleh: PENDAFTAR, dipakaiPada: serverTimestamp() });
    await assertFails(b.commit());
  });

  it('kode yang sama tidak bisa dipakai dua pendaftar', async () => {
    await assertSucceeds(daftar(dbOf(PENDAFTAR), PENDAFTAR, 'BARU000001'));
    await assertFails(daftar(dbOf(ORANG_ASING), ORANG_ASING, 'BARU000001'));
  });

  it('ditolak tanpa login', async () => {
    await assertFails(daftar(dbTamu(), PENDAFTAR, 'BARU000001'));
  });

  it('kode aktivasi hanya bisa dibaca per kode; tidak bisa didaftar, dibuat, atau dihapus dari klien', async () => {
    const db = dbOf(PENDAFTAR);
    await assertSucceeds(getDoc(doc(db, 'aktivasi', 'BARU000001'))); // get per kode boleh (untuk membaca batasCabang)
    await assertFails(getDocs(collection(db, 'aktivasi')));
    await assertFails(setDoc(doc(db, 'aktivasi', 'BUATSENDIRI'), { aktif: true, batasCabang: 999 }));
    await assertFails(deleteDoc(doc(db, 'aktivasi', 'BARU000001')));
  });
});

describe('dokumen toko', () => {
  it('pemilik boleh mengganti nama toko', async () => {
    await assertSucceeds(updateDoc(doc(dbOf(PEMILIK_A), 'toko', PEMILIK_A), { nama: 'Nama Baru' }));
  });

  it('pemilik tidak boleh mengubah status aktif, batas cabang, atau kode aktivasi', async () => {
    const ref = doc(dbOf(PEMILIK_MATI), 'toko', PEMILIK_MATI);
    await assertFails(updateDoc(ref, { aktif: true }));
    await assertFails(updateDoc(doc(dbOf(PEMILIK_A), 'toko', PEMILIK_A), { batasCabang: 999 }));
    await assertFails(updateDoc(doc(dbOf(PEMILIK_A), 'toko', PEMILIK_A), { kodeAktivasi: 'LAINNYA' }));
  });

  it('toko tidak bisa dihapus dan tidak bisa didaftar', async () => {
    await assertFails(deleteDoc(doc(dbOf(PEMILIK_A), 'toko', PEMILIK_A)));
    await assertFails(getDocs(collection(dbOf(PEMILIK_A), 'toko')));
  });

  it('orang lain dan akun cabang tidak bisa membaca dokumen toko', async () => {
    await assertFails(getDoc(doc(dbOf(ORANG_ASING), 'toko', PEMILIK_A)));
    await assertFails(getDoc(doc(dbOf(CABANG_1), 'toko', PEMILIK_A)));
    await assertFails(getDoc(doc(dbTamu(), 'toko', PEMILIK_A)));
  });

  it('pemilik tidak bisa mengubah toko milik orang lain', async () => {
    await assertFails(updateDoc(doc(dbOf(PEMILIK_A), 'toko', PEMILIK_B), { nama: 'Dibajak' }));
  });
});

describe('undangan cabang: dibuat pemilik', () => {
  it('pemilik membuat undangan sah', async () => {
    await assertSucceeds(setDoc(doc(dbOf(PEMILIK_A), 'undangan', 'ABCDE12345'), dataUndangan()));
  });

  it('ditolak bila untuk toko orang lain', async () => {
    await assertFails(setDoc(doc(dbOf(PEMILIK_A), 'undangan', 'ABCDE12345'), dataUndangan({ tokoId: PEMILIK_B })));
  });

  it('ditolak bila berlaku lebih dari 7 hari', async () => {
    await assertFails(setDoc(doc(dbOf(PEMILIK_A), 'undangan', 'ABCDE12345'), dataUndangan({ berlakuSampai: dalam(8 * HARI) })));
  });

  it('ditolak bila sudah kedaluwarsa saat dibuat', async () => {
    await assertFails(setDoc(doc(dbOf(PEMILIK_A), 'undangan', 'ABCDE12345'), dataUndangan({ berlakuSampai: dalam(-1 * HARI) })));
  });

  it('ditolak bila kode cabang berhuruf kecil atau mengandung karakter aneh', async () => {
    await assertFails(setDoc(doc(dbOf(PEMILIK_A), 'undangan', 'ABCDE12345'), dataUndangan({ kodeDokumen: 'cbg09' })));
    await assertFails(setDoc(doc(dbOf(PEMILIK_A), 'undangan', 'ABCDE12345'), dataUndangan({ kodeDokumen: 'CBG.*' })));
  });

  it('ditolak bila kode undangan bukan 10 karakter huruf/angka', async () => {
    await assertFails(setDoc(doc(dbOf(PEMILIK_A), 'undangan', 'PENDEK'), dataUndangan()));
    await assertFails(setDoc(doc(dbOf(PEMILIK_A), 'undangan', 'ABCDE-12345'), dataUndangan()));
  });

  it('ditolak bila langsung ditandai sudah dipakai', async () => {
    await assertFails(setDoc(doc(dbOf(PEMILIK_A), 'undangan', 'ABCDE12345'), dataUndangan({ dipakaiOleh: PEMILIK_A })));
  });

  it('ditolak bila toko nonaktif', async () => {
    await assertFails(setDoc(doc(dbOf(PEMILIK_MATI), 'undangan', 'ABCDE12345'), dataUndangan({ tokoId: PEMILIK_MATI })));
  });

  it('toko lama tanpa field aktif tetap boleh membuat undangan', async () => {
    await assertSucceeds(setDoc(doc(dbOf(PEMILIK_LAMA), 'undangan', 'ABCDE12345'), dataUndangan({ tokoId: PEMILIK_LAMA })));
  });

  it('akun cabang tidak bisa membuat undangan', async () => {
    await assertFails(setDoc(doc(dbOf(CABANG_1), 'undangan', 'ABCDE12345'), dataUndangan()));
  });

  it('pemilik bisa membatalkan undangan, orang lain tidak', async () => {
    await assertFails(deleteDoc(doc(dbOf(PEMILIK_B), 'undangan', 'UNDANG0002')));
    await assertFails(deleteDoc(doc(dbOf(CABANG_1), 'undangan', 'UNDANG0002')));
    await assertSucceeds(deleteDoc(doc(dbOf(PEMILIK_A), 'undangan', 'UNDANG0002')));
  });
});

describe('undangan cabang: dibaca', () => {
  it('akun anonim bisa membaca satu undangan bila tahu kodenya', async () => {
    await assertSucceeds(getDoc(doc(dbAnon(CABANG_BARU), 'undangan', 'UNDANG0002')));
  });

  it('tamu tanpa login tidak bisa membaca', async () => {
    await assertFails(getDoc(doc(dbTamu(), 'undangan', 'UNDANG0002')));
  });

  it('akun anonim tidak bisa mendaftar semua undangan', async () => {
    await assertFails(getDocs(collection(dbAnon(CABANG_BARU), 'undangan')));
    await assertFails(getDocs(query(collection(dbAnon(CABANG_BARU), 'undangan'), where('tokoId', '==', PEMILIK_A))));
  });

  it('pemilik bisa mendaftar undangannya sendiri, tapi tidak tanpa filter', async () => {
    await assertSucceeds(getDocs(query(collection(dbOf(PEMILIK_A), 'undangan'), where('tokoId', '==', PEMILIK_A))));
    await assertFails(getDocs(collection(dbOf(PEMILIK_A), 'undangan')));
    await assertFails(getDocs(query(collection(dbOf(PEMILIK_A), 'undangan'), where('tokoId', '==', PEMILIK_B))));
  });
});

describe('cabang bergabung dengan undangan', () => {
  it('berhasil dengan undangan sah, lalu bisa membaca catatan anggotanya sendiri', async () => {
    const db = dbAnon(CABANG_BARU);
    await assertSucceeds(gabung(db, CABANG_BARU, PEMILIK_A, 'UNDANG0002'));
    await assertSucceeds(getDoc(doc(db, 'toko', PEMILIK_A, 'anggota', CABANG_BARU)));
  });

  it('ditolak bila undangan sudah dipakai', async () => {
    await assertFails(gabung(dbAnon(CABANG_BARU), CABANG_BARU, PEMILIK_A, 'UNDANG0004'));
  });

  it('undangan yang sama tidak bisa dipakai dua akun', async () => {
    await assertSucceeds(gabung(dbAnon(CABANG_BARU), CABANG_BARU, PEMILIK_A, 'UNDANG0002'));
    await assertFails(gabung(dbAnon(ORANG_ASING), ORANG_ASING, PEMILIK_A, 'UNDANG0002'));
  });

  it('ditolak bila undangan kedaluwarsa', async () => {
    await assertFails(gabung(dbAnon(CABANG_BARU), CABANG_BARU, PEMILIK_A, 'UNDANG0003'));
  });

  it('ditolak bila undangan tidak ada', async () => {
    await assertFails(gabung(dbAnon(CABANG_BARU), CABANG_BARU, PEMILIK_A, 'TIDAKADA01'));
  });

  it('ditolak bila undangan milik toko lain (tokoId tidak cocok)', async () => {
    await assertFails(gabung(dbAnon(CABANG_BARU), CABANG_BARU, PEMILIK_A, 'UNDANG0005', { kodeDokumen: 'BBB01', namaCabang: 'Cabang B' }));
  });

  it('ditolak bila kode cabang berbeda dari yang ada di undangan', async () => {
    await assertFails(gabung(dbAnon(CABANG_BARU), CABANG_BARU, PEMILIK_A, 'UNDANG0002', { kodeDokumen: 'CBG02' }));
  });

  it('ditolak bila nama cabang berbeda dari undangan', async () => {
    await assertFails(gabung(dbAnon(CABANG_BARU), CABANG_BARU, PEMILIK_A, 'UNDANG0002', { namaCabang: 'Nama Lain' }));
  });

  it('ditolak bila meminta peran ADMIN', async () => {
    await assertFails(gabung(dbAnon(CABANG_BARU), CABANG_BARU, PEMILIK_A, 'UNDANG0002', { peran: 'ADMIN' }));
  });

  it('ditolak bila hanya menulis anggota tanpa menandai undangan', async () => {
    await assertFails(gabung(dbAnon(CABANG_BARU), CABANG_BARU, PEMILIK_A, 'UNDANG0002', { tandai: false }));
  });

  it('ditolak bila hanya menandai undangan tanpa menulis anggota', async () => {
    await assertFails(gabung(dbAnon(CABANG_BARU), CABANG_BARU, PEMILIK_A, 'UNDANG0002', { tulisAnggota: false }));
  });

  it('ditolak bila mendaftarkan akun lain sebagai anggota', async () => {
    const db = dbAnon(CABANG_BARU);
    const b = writeBatch(db);
    b.set(doc(db, 'toko', PEMILIK_A, 'anggota', ORANG_ASING), {
      peran: 'CABANG', kodeDokumen: 'CBG03', undangan: 'UNDANG0002', namaCabang: 'Cabang Tiga', bergabungPada: serverTimestamp(),
    });
    b.update(doc(db, 'undangan', 'UNDANG0002'), { dipakaiOleh: CABANG_BARU, dipakaiPada: serverTimestamp() });
    await assertFails(b.commit());
  });

  it('ditolak bila toko nonaktif', async () => {
    await assertFails(gabung(dbAnon(CABANG_BARU), CABANG_BARU, PEMILIK_MATI, 'UNDANG0006'));
  });

  it('ditolak bila tanpa login', async () => {
    await assertFails(gabung(dbTamu(), CABANG_BARU, PEMILIK_A, 'UNDANG0002'));
  });

  it('anggota yang sudah ada tidak bisa mengubah dokumennya sendiri atau menghapusnya', async () => {
    const db = dbAnon(CABANG_1);
    await assertFails(updateDoc(doc(db, 'toko', PEMILIK_A, 'anggota', CABANG_1), { kodeDokumen: 'CBG01' }));
    await assertFails(updateDoc(doc(db, 'toko', PEMILIK_A, 'anggota', CABANG_1), { peran: 'ADMIN' }));
    await assertFails(deleteDoc(doc(db, 'toko', PEMILIK_A, 'anggota', CABANG_1)));
  });

  it('akun cabang tidak bisa menambah anggota lain secara langsung', async () => {
    await assertFails(setDoc(doc(dbOf(CABANG_1), 'toko', PEMILIK_A, 'anggota', ORANG_ASING), { peran: 'ADMIN', kodeDokumen: 'X' }));
  });
});

describe('kelola anggota oleh pemilik', () => {
  it('pemilik bisa melihat daftar anggota dan mencabut akses', async () => {
    const db = dbOf(PEMILIK_A);
    await assertSucceeds(getDocs(collection(db, 'toko', PEMILIK_A, 'anggota')));
    await assertSucceeds(deleteDoc(doc(db, 'toko', PEMILIK_A, 'anggota', CABANG_1)));
  });

  it('pemilik bisa menambah anggota manual (cara lama tetap jalan)', async () => {
    await assertSucceeds(setDoc(doc(dbOf(PEMILIK_A), 'toko', PEMILIK_A, 'anggota', 'akunBaru'), { peran: 'CABANG', kodeDokumen: 'CBG07' }));
  });

  it('orang lain tidak bisa melihat daftar anggota atau mencabut akses', async () => {
    await assertFails(getDocs(collection(dbOf(PEMILIK_B), 'toko', PEMILIK_A, 'anggota')));
    await assertFails(deleteDoc(doc(dbOf(PEMILIK_B), 'toko', PEMILIK_A, 'anggota', CABANG_1)));
    await assertFails(getDocs(collection(dbOf(CABANG_1), 'toko', PEMILIK_A, 'anggota')));
  });

  it('setelah dicabut, cabang tidak bisa lagi menulis rekap', async () => {
    await assertSucceeds(deleteDoc(doc(dbOf(PEMILIK_A), 'toko', PEMILIK_A, 'anggota', CABANG_1)));
    await assertFails(setDoc(doc(dbOf(CABANG_1), 'toko', PEMILIK_A, 'rekap', '2026-11-03_CBG02'), { kodeDokumen: 'CBG02', tanggal: '2026-11-03' }));
  });
});

describe('rekap harian', () => {
  const rekap = (kodeDokumen, tanggal = '2026-11-03') => ({ kodeDokumen, tanggal, totalPenjualan: 5000, jumlahTransaksi: 3 });

  it('cabang hasil undangan menulis rekap kodenya sendiri (baru dan menimpa)', async () => {
    const db = dbAnon(CABANG_1);
    await assertSucceeds(setDoc(doc(db, 'toko', PEMILIK_A, 'rekap', '2026-11-03_CBG02'), rekap('CBG02')));
    await assertSucceeds(setDoc(doc(db, 'toko', PEMILIK_A, 'rekap', '2026-11-03_CBG02'), rekap('CBG02')));
  });

  it('cabang buatan manual (cara lama) tetap bisa menulis rekap', async () => {
    await assertSucceeds(setDoc(doc(dbOf(CABANG_MANUAL), 'toko', PEMILIK_A, 'rekap', '2026-11-03_CBG01'), rekap('CBG01')));
  });

  it('cabang tidak bisa menulis rekap cabang lain', async () => {
    const db = dbAnon(CABANG_1);
    await assertFails(setDoc(doc(db, 'toko', PEMILIK_A, 'rekap', '2026-11-03_CBG01'), rekap('CBG01')));
    // id milik sendiri tapi isi mengaku cabang lain
    await assertFails(setDoc(doc(db, 'toko', PEMILIK_A, 'rekap', '2026-11-03_CBG02'), rekap('CBG01')));
  });

  it('cabang tidak bisa menulis id dengan format tanggal salah', async () => {
    await assertFails(setDoc(doc(dbAnon(CABANG_1), 'toko', PEMILIK_A, 'rekap', 'bebas_CBG02'), rekap('CBG02')));
  });

  it('cabang tidak bisa menulis tanpa field tanggal', async () => {
    await assertFails(setDoc(doc(dbAnon(CABANG_1), 'toko', PEMILIK_A, 'rekap', '2026-11-03_CBG02'), { kodeDokumen: 'CBG02' }));
  });

  it('cabang toko A tidak bisa menulis di toko B', async () => {
    await assertFails(setDoc(doc(dbAnon(CABANG_1), 'toko', PEMILIK_B, 'rekap', '2026-11-03_CBG02'), rekap('CBG02')));
  });

  it('cabang tidak bisa membaca atau menghapus rekap', async () => {
    const db = dbAnon(CABANG_1);
    await assertFails(getDoc(doc(db, 'toko', PEMILIK_A, 'rekap', '2026-11-02_CBG02')));
    await assertFails(getDocs(collection(db, 'toko', PEMILIK_A, 'rekap')));
    await assertFails(deleteDoc(doc(db, 'toko', PEMILIK_A, 'rekap', '2026-11-02_CBG02')));
  });

  it('pemilik membaca semua rekap tokonya, orang lain tidak', async () => {
    await assertSucceeds(getDocs(collection(dbOf(PEMILIK_A), 'toko', PEMILIK_A, 'rekap')));
    await assertSucceeds(getDoc(doc(dbOf(PEMILIK_A), 'toko', PEMILIK_A, 'rekap', '2026-11-02_CBG02')));
    await assertFails(getDocs(collection(dbOf(PEMILIK_B), 'toko', PEMILIK_A, 'rekap')));
    await assertFails(getDoc(doc(dbOf(ORANG_ASING), 'toko', PEMILIK_A, 'rekap', '2026-11-02_CBG02')));
  });

  it('tamu tanpa login ditolak', async () => {
    await assertFails(getDoc(doc(dbTamu(), 'toko', PEMILIK_A, 'rekap', '2026-11-02_CBG02')));
    await assertFails(setDoc(doc(dbTamu(), 'toko', PEMILIK_A, 'rekap', '2026-11-03_CBG02'), rekap('CBG02')));
  });

  it('toko lama tanpa field aktif tetap menerima rekap', async () => {
    await assertSucceeds(setDoc(doc(dbOf(CABANG_MANUAL), 'toko', PEMILIK_LAMA, 'rekap', '2026-11-03_CBG01'), rekap('CBG01')));
    await assertSucceeds(setDoc(doc(dbOf(PEMILIK_LAMA), 'toko', PEMILIK_LAMA, 'rekap', '2026-11-03_CBG99'), rekap('CBG99')));
  });

  it('toko nonaktif menolak semua tulisan rekap, termasuk dari pemilik', async () => {
    await assertFails(setDoc(doc(dbOf(CABANG_MANUAL), 'toko', PEMILIK_MATI, 'rekap', '2026-11-03_CBG01'), rekap('CBG01')));
    await assertFails(setDoc(doc(dbOf(PEMILIK_MATI), 'toko', PEMILIK_MATI, 'rekap', '2026-11-03_CBG01'), rekap('CBG01')));
  });

  it('akun tanpa toko terdaftar tidak bisa menulis apa pun di path-nya sendiri', async () => {
    const db = dbOf(ORANG_ASING);
    await assertFails(setDoc(doc(db, 'toko', ORANG_ASING, 'rekap', '2026-11-03_X'), rekap('X')));
    await assertFails(setDoc(doc(db, 'toko', ORANG_ASING, 'anggota', 'siapa'), { peran: 'ADMIN', kodeDokumen: 'X' }));
    await assertFails(setDoc(doc(db, 'undangan', 'ABCDE12345'), dataUndangan({ tokoId: ORANG_ASING })));
  });
});
