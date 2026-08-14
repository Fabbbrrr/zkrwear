#!/usr/bin/env bash
# ZkrWatch — one-step setup for macOS / Linux.
# Installs the app on your Wear OS watch and imports YOUR keys (from keys.txt).
# Only dependency: adb — auto-downloaded here if you don't have it.
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
APK="${APK:-$DIR/app-release.apk}"
KEYS="${KEYS:-$DIR/keys.txt}"

# --- 1. Find or fetch adb (portable, no install) ---
if command -v adb >/dev/null 2>&1; then
  ADB="adb"
elif [ -x "$DIR/platform-tools/adb" ]; then
  ADB="$DIR/platform-tools/adb"
else
  echo "Downloading Android platform-tools (adb)…"
  case "$(uname -s)" in
    Darwin) URL="https://dl.google.com/android/repository/platform-tools-latest-darwin.zip" ;;
    Linux)  URL="https://dl.google.com/android/repository/platform-tools-latest-linux.zip" ;;
    *) echo "Unsupported OS. Please install adb (Android platform-tools) and re-run."; exit 1 ;;
  esac
  curl -fL "$URL" -o "$DIR/platform-tools.zip"
  unzip -oq "$DIR/platform-tools.zip" -d "$DIR"
  ADB="$DIR/platform-tools/adb"
fi

# --- 2. Sanity checks ---
[ -f "$APK" ]  || { echo "Missing $APK (download it from the Releases page)."; exit 1; }
if [ ! -f "$KEYS" ]; then
  echo "Missing keys.txt."
  echo "  cp \"$DIR/keys.txt.example\" \"$KEYS\"  and fill in your keys, then re-run."
  exit 1
fi

# --- 3. Connect to the watch (Wi-Fi debugging) ---
echo
echo "On the watch: Settings > Developer options > Wireless debugging = ON."
read -r -p "First time? Enter pairing 'IP:PORT' (blank to skip): " PAIR
if [ -n "$PAIR" ]; then
  read -r -p "  Pairing code shown on the watch: " CODE
  "$ADB" pair "$PAIR" "$CODE"
fi
read -r -p "Watch 'IP:PORT' to connect (blank if already connected): " HOSTPORT
[ -n "$HOSTPORT" ] && "$ADB" connect "$HOSTPORT"

"$ADB" wait-for-device

# --- 4. Install + configure ---
echo "Installing app…"
"$ADB" install -r "$APK"

echo "Importing your keys…"
B64="$(base64 < "$KEYS" | tr -d '\n')"
"$ADB" shell am start -n com.zkrwatch/com.zkrwatch.setup.ConfigActivity --es cfg "$B64" >/dev/null

echo
echo "✅ Done. Open 'ZkrWatch' on your watch."
