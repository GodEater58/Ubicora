# Read-only check. Does not install or uninstall anything.
# Requires Android SDK platform-tools (adb), build-tools (aapt, apksigner),
# a USB-connected phone with USB debugging and authorization.
param(
    [Parameter(Mandatory = $true)][string]$CandidateApk,
    [string]$PackageId = "com.ubicora.app",
    [string]$SdkPath = "$env:LOCALAPPDATA\Android\Sdk"
)
$ErrorActionPreference = "Stop"
if (-not (Test-Path $CandidateApk)) { throw "APK da verificare non trovato: $CandidateApk" }
$CandidateApk = (Resolve-Path $CandidateApk).Path
$adb = Join-Path $SdkPath "platform-tools\adb.exe"
$buildTools = Get-ChildItem (Join-Path $SdkPath "build-tools") -Directory |
    Sort-Object { try { [version]$_.Name } catch { [version]"0.0" } } -Descending |
    Select-Object -First 1
if (-not $buildTools) { throw "Android SDK build-tools non trovati." }
$aapt = Join-Path $buildTools.FullName "aapt.exe"
$apksigner = Join-Path $buildTools.FullName "apksigner.bat"
foreach ($tool in @($adb, $aapt, $apksigner)) {
    if (-not (Test-Path $tool)) { throw "Strumento SDK mancante: $tool" }
}
function Get-ApkIdentity([string]$apk) {
    $details = & $aapt dump badging $apk
    if ($LASTEXITCODE -ne 0) { throw "Impossibile leggere il manifest: $apk" }
    $first = [string]($details | Select-Object -First 1)
    if ($first -notmatch "package: name='([^']+)' versionCode='([0-9]+)'") {
        throw "Identificativo/versionCode non leggibile nel file APK"
    }
    $id = $Matches[1]; $code = [long]$Matches[2]
    $signature = & $apksigner verify --print-certs $apk
    if ($LASTEXITCODE -ne 0) { throw "Firma APK non valida: $apk" }
    $line = [string]($signature | Where-Object { $_ -match "Signer #1 certificate SHA-256 digest:" } | Select-Object -First 1)
    if ($line -notmatch "SHA-256 digest: ([a-fA-F0-9:]+)") { throw "Fingerprint firma non trovata" }
    return [PSCustomObject]@{ Id=$id; Code=$code; Cert=$Matches[1].Replace(":","").ToLowerInvariant() }
}
$devices = & $adb devices
if ($LASTEXITCODE -ne 0) { throw "ADB non risponde" }
$connected = @($devices | Where-Object { $_ -match "\sdevice$" })
if ($connected.Count -ne 1) {
    throw "Collega e autorizza esattamente un telefono. ADB rileva $($connected.Count) dispositivi pronti."
}
$remote = @(& $adb shell pm path $PackageId | Where-Object { $_ -match '^package:.*/base\.apk\s*$' } | Select-Object -First 1)
if (-not $remote -or $remote.Count -eq 0) {
    throw "Ubicora non installata con ID $PackageId, oppure il pacchetto non e' leggibile."
}
$apkOnDevice = ([string]$remote[0]).Trim().Substring("package:".Length)
$temp = Join-Path ([IO.Path]::GetTempPath()) ("ubicora-update-check-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $temp | Out-Null
try {
    $installedApk = Join-Path $temp "currently-installed.apk"
    & $adb pull $apkOnDevice $installedApk | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Impossibile estrarre temporaneamente l'APK installato" }
    $current = Get-ApkIdentity $installedApk
    $candidate = Get-ApkIdentity $CandidateApk
    Write-Host "ATTUALE: $($current.Id) versionCode=$($current.Code) cert=$($current.Cert)"
    Write-Host "NUOVO:   $($candidate.Id) versionCode=$($candidate.Code) cert=$($candidate.Cert)"
    $idOk = $current.Id -eq $candidate.Id
    $signOk = $current.Cert -eq $candidate.Cert
    $versionOk = $candidate.Code -gt $current.Code
    if ($idOk -and $signOk -and $versionOk) {
        Write-Host "COMPATIBILE: aggiornamento Android consentito dalle verifiche ID, firma e versione."
        Write-Host "IMPORTANTE: fai comunque backup; questa verifica non certifica migrazioni del database."
        exit 0
    }
    if (-not $idOk) { Write-Host "BLOCCO: applicationId differente." }
    if (-not $signOk) { Write-Host "BLOCCO: certificato di firma differente." }
    if (-not $versionOk) { Write-Host "BLOCCO: versionCode nuovo non superiore." }
    Write-Host "NON disinstallare Ubicora per superare un blocco: prima esporta e verifica un backup."
    exit 1
} finally {
    Remove-Item $temp -Recurse -Force -ErrorAction SilentlyContinue
}
