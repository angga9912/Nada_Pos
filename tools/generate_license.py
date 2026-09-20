#!/usr/bin/env python3
"""
Generator kode aktivasi lisensi NADA POS - versi TANDA TANGAN DIGITAL (ECDSA P-256).

Beda dengan versi lama (HMAC + secret bersama):
  - APK HANYA membawa kunci PUBLIK. Kunci publik tidak bisa dipakai untuk membuat kode,
    jadi walaupun APK dibongkar habis, orang tetap TIDAK bisa membuat kode lisensi palsu.
  - Kunci PRIVAT (untuk membuat kode) cuma ada pada Anda. Jaga baik-baik.

Script ini murni Python 3.8+ tanpa library tambahan (aman dijalankan di Termux).

LANGKAH SEKALI SAJA - buat pasangan kunci:
    python3 generate_license.py --init
  -> membuat "license.private" (RAHASIA, jangan pernah di-upload ke GitHub!)
     dan "license.public"  (aman; upload ke root repo GitHub).
  Simpan isi license.private di 2 tempat aman: (1) GitHub Secret bernama
  NADA_LICENSE_PRIVATE_KEY (untuk workflow "Generate Kode Lisensi"), (2) cadangan offline.
  Kalau license.private hilang, Anda tidak bisa membuat kode baru untuk APK yang sudah beredar.

PAKAI SEHARI-HARI:
    python3 generate_license.py --tier PRO --expiry 20271231
    python3 generate_license.py --tier CUSTOM --expiry LIFETIME
    python3 generate_license.py --tier PRO --bulan 1        # expiry = hari ini + 1 bulan (30 hari)

CEK SEBUAH KODE (memakai license.public):
    python3 generate_license.py --cek NADA-PRO-LIFETIME-XXXXXXXX-...

Kunci privat dibaca dari environment variable NADA_LICENSE_PRIVATE_KEY (dipakai duluan,
untuk CI/CD) atau dari file license.private (bisa diganti lewat --private-file).
"""
import argparse
import base64
import hashlib
import hmac
import os
import secrets
import stat
import sys
from datetime import date, timedelta
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
# Di repo, script ada di folder "tools/" -> kunci disimpan di root repo. Kalau script disalin
# sendirian (mis. ke Termux), kunci disimpan di folder yang sama dengan script.
ROOT = SCRIPT_DIR.parent if SCRIPT_DIR.name == "tools" else SCRIPT_DIR
DEFAULT_PRIVATE_FILE = ROOT / "license.private"
DEFAULT_PUBLIC_FILE = ROOT / "license.public"

# Awalan pesan yang ditandatangani. HARUS sama persis dengan LicenseKeyValidator.kt di aplikasi.
DOMAIN = "NADA-LIC-V1"

# --- Parameter kurva NIST P-256 (secp256r1) ---
P = 0xFFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF
A = P - 3
N = 0xFFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551
G = (
    0x6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296,
    0x4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5,
)
# Header ASN.1 DER untuk "SubjectPublicKeyInfo" kunci publik EC P-256 (format X.509 yang dibaca Android)
SPKI_PREFIX = bytes.fromhex("3059301306072a8648ce3d020106082a8648ce3d030107034200")


# ---------------------------------------------------------------------------
# Aritmetika kurva eliptik P-256 (sederhana, cukup untuk membuat kode lisensi sesekali)
# ---------------------------------------------------------------------------
def _inv(x, m):
    return pow(x, -1, m)


def _add(p1, p2):
    if p1 is None:
        return p2
    if p2 is None:
        return p1
    x1, y1 = p1
    x2, y2 = p2
    if x1 == x2 and (y1 + y2) % P == 0:
        return None
    if p1 == p2:
        lam = (3 * x1 * x1 + A) * _inv(2 * y1, P) % P
    else:
        lam = (y2 - y1) * _inv(x2 - x1, P) % P
    x3 = (lam * lam - x1 - x2) % P
    y3 = (lam * (x1 - x3) - y1) % P
    return (x3, y3)


def _mul(k, point):
    hasil = None
    tambah = point
    while k:
        if k & 1:
            hasil = _add(hasil, tambah)
        tambah = _add(tambah, tambah)
        k >>= 1
    return hasil


def _hmac(key, data):
    return hmac.new(key, data, hashlib.sha256).digest()


def _rfc6979_k(d, h1):
    """Nonce deterministik (RFC 6979) - tidak bergantung pada kualitas RNG perangkat."""
    x = d.to_bytes(32, "big")
    h2 = (int.from_bytes(h1, "big") % N).to_bytes(32, "big")
    v = b"\x01" * 32
    k = b"\x00" * 32
    k = _hmac(k, v + b"\x00" + x + h2)
    v = _hmac(k, v)
    k = _hmac(k, v + b"\x01" + x + h2)
    v = _hmac(k, v)
    while True:
        v = _hmac(k, v)
        kandidat = int.from_bytes(v, "big")
        if 1 <= kandidat < N:
            return kandidat
        k = _hmac(k, v + b"\x00")
        v = _hmac(k, v)


def sign(d, pesan):
    """Tanda tangan ECDSA-SHA256, hasil format mentah r||s (64 byte)."""
    h = hashlib.sha256(pesan).digest()
    z = int.from_bytes(h, "big")
    k = _rfc6979_k(d, h)
    r = _mul(k, G)[0] % N
    s = _inv(k, N) * (z + r * d) % N
    if r == 0 or s == 0:
        raise RuntimeError("Nonce tidak valid, coba lagi.")
    return r.to_bytes(32, "big") + s.to_bytes(32, "big")


def verify(titik_publik, pesan, tanda_tangan):
    if len(tanda_tangan) != 64:
        return False
    r = int.from_bytes(tanda_tangan[:32], "big")
    s = int.from_bytes(tanda_tangan[32:], "big")
    if not (1 <= r < N and 1 <= s < N):
        return False
    z = int.from_bytes(hashlib.sha256(pesan).digest(), "big")
    w = _inv(s, N)
    titik = _add(_mul(z * w % N, G), _mul(r * w % N, titik_publik))
    return titik is not None and titik[0] % N == r


# ---------------------------------------------------------------------------
# Kunci
# ---------------------------------------------------------------------------
def public_dari_private(d):
    return _mul(d, G)


def encode_public(titik):
    return base64.b64encode(SPKI_PREFIX + b"\x04" + titik[0].to_bytes(32, "big") + titik[1].to_bytes(32, "big")).decode()


def decode_public(teks_base64):
    der = base64.b64decode(teks_base64.strip())
    if len(der) != len(SPKI_PREFIX) + 65 or not der.startswith(SPKI_PREFIX + b"\x04"):
        raise ValueError("Format license.public tidak dikenali.")
    isi = der[len(SPKI_PREFIX) + 1:]
    return (int.from_bytes(isi[:32], "big"), int.from_bytes(isi[32:], "big"))


def load_private(private_file_arg):
    env = os.environ.get("NADA_LICENSE_PRIVATE_KEY")
    if env and env.strip():
        teks = env.strip()
    else:
        path = Path(private_file_arg) if private_file_arg else DEFAULT_PRIVATE_FILE
        if not path.exists():
            print(f"Kunci privat tidak ditemukan di {path}.", file=sys.stderr)
            print("Buat dulu dengan: python3 generate_license.py --init", file=sys.stderr)
            print("atau set environment variable NADA_LICENSE_PRIVATE_KEY.", file=sys.stderr)
            sys.exit(1)
        teks = path.read_text().strip()
    try:
        d = int("".join(teks.split()), 16)
    except ValueError:
        d = 0
    if not (1 <= d < N):
        print("Isi kunci privat tidak valid (harus 64 karakter hex).", file=sys.stderr)
        sys.exit(1)
    return d


def cmd_init(private_file, public_file, paksa):
    if private_file.exists() and not paksa:
        print(f"GAGAL: {private_file} sudah ada. Membuat kunci baru akan MEMBUAT SEMUA KODE LAMA", file=sys.stderr)
        print("TIDAK VALID. Kalau memang itu yang Anda mau, tambahkan --force.", file=sys.stderr)
        sys.exit(1)
    d = secrets.randbelow(N - 1) + 1
    private_file.write_text(f"{d:064x}\n")
    try:
        os.chmod(private_file, stat.S_IRUSR | stat.S_IWUSR)
    except OSError:
        pass
    public_file.write_text(encode_public(public_dari_private(d)) + "\n")
    print("Pasangan kunci berhasil dibuat:\n")
    print(f"  RAHASIA : {private_file}")
    print(f"  PUBLIK  : {public_file}\n")
    print("Langkah berikutnya:")
    print("  1. Upload 'license.public' ke root repo GitHub Anda (aman, isinya kunci publik).")
    print("  2. Buka 'license.private', salin isinya (64 karakter) ke GitHub Secret bernama")
    print("     NADA_LICENSE_PRIVATE_KEY, dan simpan cadangan offline (mis. password manager).")
    print("  3. JANGAN upload 'license.private' ke GitHub atau kirim ke siapa pun.")
    print("  4. Build ulang APK - hanya APK yang dibuild dengan license.public ini yang menerima kode buatan Anda.")


# ---------------------------------------------------------------------------
# Kode aktivasi
# ---------------------------------------------------------------------------
def pesan_untuk(tier, expiry):
    return f"{DOMAIN}:{tier}:{expiry}".encode()


def buat_kode(d, tier, expiry):
    tanda_tangan = sign(d, pesan_untuk(tier, expiry))
    # Pastikan tanda tangan benar-benar cocok dengan kunci publik sebelum dipakai
    if not verify(public_dari_private(d), pesan_untuk(tier, expiry), tanda_tangan):
        raise RuntimeError("Verifikasi internal gagal - kode tidak dikeluarkan.")
    b32 = base64.b32encode(tanda_tangan).decode().rstrip("=")
    kelompok = "-".join(b32[i:i + 8] for i in range(0, len(b32), 8))
    return f"NADA-{tier}-{expiry}-{kelompok}"


def cek_kode(kode, public_file):
    if not public_file.exists():
        print(f"{public_file} tidak ditemukan.", file=sys.stderr)
        sys.exit(1)
    titik = decode_public(public_file.read_text())
    bersih = "".join(kode.split()).upper()
    bagian = bersih.split("-")
    if len(bagian) < 4 or bagian[0] != "NADA":
        return False
    b32 = "".join(bagian[3:])
    b32 += "=" * (-len(b32) % 8)
    try:
        tanda_tangan = base64.b32decode(b32)
    except Exception:
        return False
    return verify(titik, pesan_untuk(bagian[1], bagian[2]), tanda_tangan)


def main():
    parser = argparse.ArgumentParser(description="Generator kode aktivasi lisensi NADA POS (ECDSA)")
    parser.add_argument("--init", action="store_true", help="Buat pasangan kunci baru (sekali saja)")
    parser.add_argument("--force", action="store_true", help="Izinkan --init menimpa kunci lama (kode lama jadi tidak valid!)")
    parser.add_argument("--cek", metavar="KODE", help="Cek apakah sebuah kode valid terhadap license.public")
    parser.add_argument("--tier", choices=["CUSTOM", "PRO"], help="Tingkat paket yang dibeli")
    parser.add_argument("--private-file", help="Lokasi custom file license.private")
    parser.add_argument("--public-file", help="Lokasi custom file license.public")
    parser.add_argument("--expiry", help="Tanggal kadaluarsa YYYYMMDD, atau 'LIFETIME' untuk sekali bayar")
    parser.add_argument("--bulan", type=int, help="Jumlah bulan langganan dari hari ini")
    args = parser.parse_args()

    private_file = Path(args.private_file) if args.private_file else DEFAULT_PRIVATE_FILE
    public_file = Path(args.public_file) if args.public_file else DEFAULT_PUBLIC_FILE

    if args.init:
        cmd_init(private_file, public_file, args.force)
        return

    if args.cek:
        if cek_kode(args.cek, public_file):
            print("KODE VALID (tanda tangan cocok dengan license.public).")
        else:
            print("KODE TIDAK VALID.")
            sys.exit(2)
        return

    if not args.tier or (args.expiry is None) == (args.bulan is None):
        parser.error("Isi --tier dan salah satu dari --expiry atau --bulan (atau pakai --init / --cek).")

    if args.bulan is not None:
        if args.bulan <= 0:
            parser.error("--bulan harus lebih dari 0.")
        # Perkiraan sederhana: 1 bulan = 30 hari
        expiry = (date.today() + timedelta(days=30 * args.bulan)).strftime("%Y%m%d")
    else:
        expiry = args.expiry.upper()
        if expiry != "LIFETIME":
            try:
                date.fromisoformat(f"{expiry[0:4]}-{expiry[4:6]}-{expiry[6:8]}")
                if len(expiry) != 8 or not expiry.isdigit():
                    raise ValueError
            except ValueError:
                print(f"Format tanggal salah: {expiry}. Gunakan YYYYMMDD, contoh: 20271231", file=sys.stderr)
                sys.exit(1)

    d = load_private(args.private_file)
    kode = buat_kode(d, args.tier, expiry)
    print(f"\nKode aktivasi untuk paket {args.tier} (berlaku sampai: {expiry}):\n")
    print(f"    {kode}\n")
    print("Kirim kode ini ke pelanggan lewat WhatsApp setelah pembayaran diterima.")
    print("(Kode memang panjang - cukup salin-tempel utuh; huruf besar/kecil dan spasi tidak masalah.)")


if __name__ == "__main__":
    main()
