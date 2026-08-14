# ZkrWatch — Setup (no build, no coding)

Get the app running on your Samsung/Wear OS watch with **your own** keys.
You don't compile anything — you install a prebuilt app and import your keys.

**Only requirement:** a Windows or Mac/Linux PC and your watch. The script downloads
`adb` for you automatically; nothing else to install.

---

## 1. Get your keys (once)

The app needs the same keys as the Home Assistant `zeekr_ev` integration. Extract them with
**[zeekr_key_extractor](https://github.com/Wysie/zeekr_key_extractor)** — you get:
`HMAC_ACCESS_KEY`, `HMAC_SECRET_KEY`, `PASSWORD_PUBLIC_KEY`, `PROD_SECRET`, `VIN_KEY`, `VIN_IV`.

Use a **dedicated account** with the car shared to it — logging in from the watch boots
any other active session (your phone app / Home Assistant).

## 2. Download

From this repo's **Releases** page, download `app-release.apk`, and from the `setup/` folder:
`setup.ps1` (Windows) or `setup.sh` (Mac/Linux), plus `keys.txt.example`. Put them in one folder.

## 3. Fill in your keys

Copy `keys.txt.example` → `keys.txt` and paste your values (keys + account email/password).
`keys.txt` never leaves your PC.

## 4. Turn on watch debugging

On the watch: **Settings → About → tap Build number 7×**, then
**Settings → Developer options → Wireless debugging = ON**. Keep that screen handy — it shows
an **IP:PORT** (and a pairing code the first time).

## 5. Run the setup

- **Windows:** right-click `setup.ps1` → *Run with PowerShell*
  (or in PowerShell: `./setup.ps1`)
- **Mac/Linux:** `chmod +x setup.sh && ./setup.sh`

Enter the pairing code / IP:PORT when asked. The script installs the app and imports your keys.
Open **ZkrWatch** on the watch — you should see your battery, range, and Lock/Unlock/Trunk.

---

## How it stays safe

- The published APK contains **no keys** — it's the same app for everyone.
- Your `keys.txt` stays on your PC (git-ignored) and is imported into the watch's
  **hardware-Keystore-encrypted** storage. It is never stored in plaintext on the watch.
- Re-run the script anytime to update keys or reconfigure.

## Troubleshooting

- **"device not found"** — re-run; `adb connect IP:PORT` sometimes needs a second try after the
  watch sleeps. Make sure the watch and PC are on the **same Wi-Fi** (client isolation on
  guest/corporate networks blocks it; a phone hotspot both join works).
- **"Not set up" on the watch** — the keys import didn't run; re-run the script and confirm
  `keys.txt` has every field filled.
- **Login error on the watch** — check the account/keys, and that you're using the dedicated
  shared account for your region (`COUNTRY_CODE`).

---

## Credits

Built on **[Fryyyy](https://github.com/Fryyyyy)**'s
**[Home Assistant integration](https://github.com/Fryyyyy/zeekr_homeassistant)** and
**[zeekr_ev_api](https://github.com/Fryyyyy/zeekr_ev_api)** — the foundation this whole app is
ported from. Thank you. 🙏
