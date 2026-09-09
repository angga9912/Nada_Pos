#!/usr/bin/env python3
"""
Generator kode aktivasi lisensi NADA POS.

PENTING: nilai SECRET di bawah ini HARUS SAMA PERSIS dengan yang ada di
app/src/main/java/com/nada/kasir/core/lisensi/LicenseKeyValidator.kt
Kalau Anda ganti salah satu, ganti juga yang satunya.

JANGAN commit file ini (dengan SECRET asli) ke repository PUBLIK. Kalau repo
Anda publik, pindahkan SECRET ke file terpisah yang di-.gitignore, atau simpan
di password manager dan isi manual tiap generate.

Cara pakai:
    python3 generate_license.py --tier PRO --expiry 20271231
    python3 generate_license.py --tier CUSTOM --expiry LIFETIME
    python3 generate_license.py --tier PRO --bulan 1        # otomatis hitung expiry = hari ini + 1 bulan
"""
import argparse
import hashlib
import hmac
import sys
from datetime import date, timedelta

# HARUS SAMA dengan SECRET di LicenseKeyValidator.kt
SECRET = "GANTI_DENGAN_SECRET_RAHASIA_ANDA_SEBELUM_RILIS"


def generate(tier: str, expiry: str) -> str:
    message = f"{tier}:{expiry}"
    checksum = hmac.new(SECRET.encode(), message.encode(), hashlib.sha256).hexdigest().upper()[:8]
    return f"NADA-{tier}-{expiry}-{checksum}"


def main():
    parser = argparse.ArgumentParser(description="Generate kode aktivasi lisensi NADA POS")
    parser.add_argument("--tier", choices=["CUSTOM", "PRO"], required=True, help="Tingkat paket yang dibeli")
    grup_expiry = parser.add_mutually_exclusive_group(required=True)
    grup_expiry.add_argument("--expiry", help="Tanggal kadaluarsa format YYYYMMDD, atau 'LIFETIME' untuk sekali bayar")
    grup_expiry.add_argument("--bulan", type=int, help="Jumlah bulan langganan dari hari ini (otomatis hitung expiry)")

    args = parser.parse_args()

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

    kode = generate(args.tier, expiry)
    print(f"\nKode aktivasi untuk paket {args.tier} (berlaku sampai: {expiry}):\n")
    print(f"    {kode}\n")
    print("Kirim kode ini ke pelanggan lewat WhatsApp setelah pembayaran diterima.")


if __name__ == "__main__":
    main()
