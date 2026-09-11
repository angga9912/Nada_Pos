#!/usr/bin/env bash
# Membuat keystore untuk menandatangani (sign) APK release NADA POS (poin perbaikan #2).
#
# PENTING - baca dulu sebelum jalan:
# - Jalankan script ini di KOMPUTER ANDA SENDIRI (bukan di lingkungan sementara/AI
#   sandbox/CI publik), karena file keystore yang dihasilkan adalah kunci kriptografi
#   yang harus disimpan AMAN & PERMANEN.
# - JANGAN PERNAH kehilangan file keystore ini atau lupa passwordnya. Kalau hilang,
#   Anda TIDAK BISA lagi merilis update untuk APK yang sudah terlanjur didistribusikan
#   dengan keystore ini (khususnya kalau sudah publish ke Google Play) - harus mulai
#   dari aplikasi "baru" dengan identitas signing berbeda.
# - Backup file .keystore/.jks hasil script ini ke tempat aman (password manager,
#   hard drive terpisah, dll), TERPISAH dari backup kode/Git.
#
# Cara pakai:
#   chmod +x tools/generate_keystore.sh
#   ./tools/generate_keystore.sh

set -euo pipefail

OUTPUT_FILE="release.keystore"
ALIAS="nada-pos-release"

if [ -f "$OUTPUT_FILE" ]; then
    echo "File $OUTPUT_FILE sudah ada di folder ini - hapus/pindahkan dulu kalau mau generate ulang."
    exit 1
fi

echo "=== Membuat keystore signing untuk NADA POS ==="
echo "Anda akan diminta membuat password keystore & mengisi info identitas (nama, organisasi, dll)."
echo "Info identitas TIDAK rahasia (boleh diisi nama toko/perusahaan Anda apa saja),"
echo "tapi PASSWORD harus rahasia dan diingat/disimpan dengan aman."
echo ""

keytool -genkeypair \
    -v \
    -keystore "$OUTPUT_FILE" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000

echo ""
echo "=== Selesai ==="
echo "File keystore dibuat di: $(pwd)/$OUTPUT_FILE"
echo ""
echo "Langkah selanjutnya:"
echo "1. Copy keystore.properties.example jadi keystore.properties di root project."
echo "2. Isi storeFile=$OUTPUT_FILE, keyAlias=$ALIAS, dan password yang baru saja Anda buat."
echo "3. Backup $OUTPUT_FILE ke tempat aman TERPISAH dari repo Git Anda."
echo "4. Jalankan: ./gradlew assembleDemoRelease  (APK hasil akan sudah ditandatangani)"
