# ZkrWatch - one-step setup for Windows (PowerShell).
# Installs the app on your Wear OS watch and imports YOUR keys (from keys.txt).
# Only dependency: adb - auto-downloaded here if you don't have it.
$ErrorActionPreference = "Stop"

$dir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$apk  = Join-Path $dir "app-release.apk"
$keys = Join-Path $dir "keys.txt"

# --- 1. Find or fetch adb (portable, no install) ---
$adb = $null
if (Get-Command adb -ErrorAction SilentlyContinue) {
    $adb = "adb"
} elseif (Test-Path (Join-Path $dir "platform-tools\adb.exe")) {
    $adb = Join-Path $dir "platform-tools\adb.exe"
} else {
    Write-Host "Downloading Android platform-tools (adb)..."
    $url = "https://dl.google.com/android/repository/platform-tools-latest-windows.zip"
    $zip = Join-Path $dir "platform-tools.zip"
    Invoke-WebRequest -Uri $url -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $dir -Force
    $adb = Join-Path $dir "platform-tools\adb.exe"
}

# --- 2. Sanity checks ---
if (-not (Test-Path $apk))  { throw "Missing app-release.apk (download it from the Releases page)." }
if (-not (Test-Path $keys)) {
    Write-Host "Missing keys.txt."
    Write-Host "  Copy keys.txt.example to keys.txt, fill in your keys, then re-run."
    exit 1
}

# --- 3. Connect to the watch (Wi-Fi debugging) ---
Write-Host ""
Write-Host "On the watch: Settings > Developer options > Wireless debugging = ON."
$pair = Read-Host "First time? Enter pairing 'IP:PORT' (blank to skip)"
if ($pair) {
    $code = Read-Host "  Pairing code shown on the watch"
    & $adb pair $pair $code
}
$hostport = Read-Host "Watch 'IP:PORT' to connect (blank if already connected)"
if ($hostport) { & $adb connect $hostport }

& $adb wait-for-device

# --- 4. Install + configure ---
Write-Host "Installing app..."
& $adb install -r $apk

Write-Host "Importing your keys..."
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keys))
& $adb shell am start -n com.zkrwatch/com.zkrwatch.setup.ConfigActivity --es cfg $b64 | Out-Null

Write-Host ""
Write-Host "Done. Open 'ZkrWatch' on your watch."
