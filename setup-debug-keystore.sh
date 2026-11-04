#!/usr/bin/env bash
set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
KEYSTORE="$DIR/debug.keystore"
BASE64_FILE="$DIR/debug.keystore.base64"

if [ -f "$KEYSTORE" ]; then
    echo "[INFO] debug.keystore already exists at: $KEYSTORE"
    exit 0
fi

echo "[INFO] debug.keystore not found. Preparing debug keystore..."

if [ -f "$BASE64_FILE" ]; then
    echo "[INFO] Decoding debug.keystore from debug.keystore.base64..."
    if command -v base64 >/dev/null 2>&1; then
        base64 -d "$BASE64_FILE" > "$KEYSTORE" 2>/dev/null || base64 -D "$BASE64_FILE" > "$KEYSTORE" 2>/dev/null || true
    fi
fi

if [ ! -f "$KEYSTORE" ] || [ ! -s "$KEYSTORE" ]; then
    echo "[INFO] Generating new debug.keystore via keytool..."
    keytool -genkeypair -v \
      -keystore "$KEYSTORE" \
      -storepass android \
      -alias androiddebugkey \
      -keypass android \
      -keyalg RSA \
      -keysize 2048 \
      -validity 10000 \
      -dname "CN=Android Debug,O=Android,C=US"
fi

echo "[SUCCESS] debug.keystore is ready at $KEYSTORE"
