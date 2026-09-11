#!/usr/bin/env python3
"""
Generator kode aktivasi lisensi NADA POS.

SECRET dibaca dari file "license.secret" di root project - file YANG SAMA PERSIS
juga dibaca oleh app/build.gradle.kts saat build APK (lihat license.secret.example
untuk cara setup). Karena keduanya membaca satu file yang sama, SECRET di app dan
di generator ini TIDAK BISA lagi tidak-sinkron.

JANGAN commit file "license.secret" ke repository PUBLIK (sudah otomatis
di-.gitignore). Kalau repo Anda publik, tetap jaga file ini di luar Git.

Cara pakai:
    python3 generate_license.py --tier PRO --expiry 20271231
    python3 generate_license.py --tier CUSTOM --expiry LIFETIME
    python3 generate_license.py --tier PRO --bulan 1        # otomatis hitung expiry = hari ini + 1 bulan

Override lokasi/isi secret (opsional):
    --secret-file /path/lain/ke/license.secret
    atau set environment variable NADA_LICENSE_SECRET (dipakai duluan kalau ada,
    berguna untuk CI/CD tanpa perlu menulis file ke disk).
"""
import argparse
import hashlib
import hmac
import os
import sys
from datetime import date, timedelta
from pathlib import Path

DEFAULT_SECRET_FILE = Path(__file__).resolve().parent.parent / "license.secret"


def load_secret(secret_file_arg: str | None) -> str:
    env_secret = os.environ.get("NADA_LICENSE_SECRET")
    if env_secret:
        return env_secret.strip()

    path = Path(secret_file_arg) if secret_file_arg else DEFAULT_SECRET_FILE
    if not path.exists():
        print(f"SECRET tidak ditemukan di {path}.", file=sys.stderr)
        print("Copy license.secret.example jadi license.secret lalu isi dengan secret rahasia Anda,", file=sys.stderr)
        print("atau set environment variable NADA_LICENSE_SECRET.", file=sys.stderr)
        sys.exit(1)
    return path.read_text().strip()


def generate(secret: str, tier: str, expiry: str) -> str:
    message = f"{tier}:{expiry}"
    checksum = hmac.new(secret.encode(), message.encode(), hashlib.sha256).hexdigest().upper()[:8]
    return f"NADA-{tier}-{expiry}-{checksum}"


def main():
    parser = argparse.ArgumentParser(description="Generate kode aktivasi lisensi NADA POS")
    parser.add_argument("--tier", choices=["CUSTOM", "PRO"], required=True, help="Tingkat paket yang dibeli")
    parser.add_argument("--secret-file", help="Lokasi custom file license.secret (default: root project)")
    grup_expiry = parser.add_mutually_exclusive_group(required=True)
    grup_expiry.add_argument("--expiry", help="Tanggal kadaluarsa format YYYYMMDD, atau 'LIFETIME' untuk sekali bayar")
    grup_expiry.add_argument("--bulan", type=int, help="Jumlah bulan langganan dari hari ini (otomatis hitung expiry)")

    args = parser.parse_args()
    secret = load_secret(args.secret_file)

    if args.bulan:
        # Perkiraan sederhana: 1 bulan = 30 hari (cukup akurat untuk siklus langganan bulanan)
        tanggal_expiry = date.today() + timedelta(days=30 * args.bulan)
        expiry = tanggal_expiry.strftime("%Y%m%d")
    else:
        expiry = args.expiry.upper()
        if expiry != "LIFETIME":
            try:
                date.fromisoformat(f"{expiry[0:4]}-{expiry[4:6]}-{expiry[6:8]}")
            except ValueError:
                print(f"Format tanggal salah: {expiry}. Gunakan YYYYMMDD, contoh: 20271231", file=sys.stderr)
                sys.exit(1)

    kode = generate(secret, args.tier, expiry)
    print(f"\nKode aktivasi untuk paket {args.tier} (berlaku sampai: {expiry}):\n")
    print(f"    {kode}\n")
    print("Kirim kode ini ke pelanggan lewat WhatsApp setelah pembayaran diterima.")


if __name__ == "__main__":
    main()
