# Releasing ZkrWatch

Every release **must be signed with the same key**, otherwise the in-app updater
can't install it over an existing install (Android rejects signature-mismatched
updates with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`). The build enforces this
automatically — read the signing section before your first release.

## Signing key (do not change)

Releases are signed by the `sideload` signing config in
[`app/build.gradle.kts`](app/build.gradle.kts), which uses the local Android
debug keystore:

- **Keystore:** `~/.android/debug.keystore`
- **Alias:** `androiddebugkey`  ·  **Store/key password:** `android`
- **Pinned certificate (all releases must match this):**
  - `SHA-256: E8:9F:4F:29:28:96:4E:81:8F:B2:75:26:27:D5:9E:0A:97:A8:80:25:94:EA:94:2E:E1:4B:B1:C4:5E:9E:44:BE`
  - `SHA-1:   3D:2F:82:E3:A8:CE:86:CD:63:4D:71:19:3B:F7:46:D0:75:D7:78:19`
  - Valid 2025-10-09 → 2055-10-02 · `SHA256withRSA`

The release build runs `verifyReleaseSigningCert`, which **fails the build** if
the signing certificate doesn't match the pinned SHA-256 above. So you can't
accidentally ship a differently-signed release.

> ### ⚠️ Back up the keystore
> `~/.android/debug.keystore` is **machine-specific and irreplaceable**. If you
> lose it (or build a release on a different machine), you can no longer sign an
> update that existing users can install — they'd have to uninstall and
> reinstall. **Copy it somewhere safe now** (password manager, encrypted backup):
> ```bash
> cp ~/.android/debug.keystore ~/zkrwatch-release.keystore.backup
> ```
> To release from another machine, restore that exact file to `~/.android/debug.keystore`
> first (the build guard will refuse otherwise).

Check the fingerprint any time:
```bash
keytool -list -v -keystore ~/.android/debug.keystore -storepass android -alias androiddebugkey
```

## Cutting a release

1. **Bump the version** in [`app/build.gradle.kts`](app/build.gradle.kts):
   - `versionCode` — increment by 1 (monotonic; the updater/OS compare this).
   - `versionName` — the human version, e.g. `1.3.0`. The GitHub tag is `v` + this.

2. **Build the keyless release** (the published APK must contain **no keys**):
   ```bash
   # bash
   mv keys.properties keys.properties.hold          # build without baked secrets
   ./gradlew clean :app:assembleRelease             # clean: avoids stale BuildConfig version
   mv keys.properties.hold keys.properties           # restore your local keys
   ```
   ```powershell
   # PowerShell
   Move-Item keys.properties keys.properties.hold
   .\gradlew.bat clean :app:assembleRelease
   Move-Item keys.properties.hold keys.properties
   ```
   The build prints `Release signing certificate OK (…)`. The APK is at
   `app/build/outputs/apk/release/app-release.apk`.

   > `clean` matters: `BuildConfig.VERSION_NAME` is an inlined compile-time
   > constant, and Gradle's incremental/cached compile can otherwise keep an old
   > version baked into the code even after you bump it.

3. **Sanity-check it's keyless** (optional but recommended) — none of your real
   key values should appear in the APK:
   ```bash
   for v in $(grep -vE '^\s*#|^\s*$|^COUNTRY_CODE=' keys.properties | cut -d= -f2-); do
     grep -qa -- "$v" app/build/outputs/apk/release/app-release.apk && echo "LEAK: $v" || true
   done; echo "done"
   ```

4. **Publish the GitHub release** with a tag `vX.Y.Z` and the APK attached:
   ```bash
   gh release create vX.Y.Z app/build/outputs/apk/release/app-release.apk \
     --title "ZkrWatch vX.Y.Z" --notes-file notes.md
   ```
   - The tag **must** be `v` + `versionName` (e.g. `v1.3.0`) — the updater strips
     the `v` and compares to the installed `versionName`.
   - The release **must have an `.apk` asset** — the in-app updater downloads the
     first asset whose name ends in `.apk`.

## How the in-app update finds it

On launch (when configured), the app GETs
`https://api.github.com/repos/Fabbbrrr/zkrwear/releases/latest`, compares the
release `tag_name` to `BuildConfig.VERSION_NAME`, and — if newer — shows the
"Update to X.Y.Z" button. This request is anonymous (no auth, no device id, no
account data); see `UpdateChecker`.

Users on the **official release APK** (installed via `setup/`) update seamlessly.
People who build and sign their **own** APK use a different key, so they can't
self-update to an official release — they update by rebuilding from source.
