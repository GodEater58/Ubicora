# Run locally on YOUR Windows PC. The private keystore never enters Git history or ChatGPT.
# Requires Java keytool and authenticated GitHub CLI (gh auth login).
param(
    [Parameter(Mandatory=$true)][string]$KeystorePath,
    [Parameter(Mandatory=$true)][string]$Alias,
    [string]$Repo = "GodEater58/OfficinaStock-Android"
)
$ErrorActionPreference = "Stop"
$resolved = Resolve-Path -LiteralPath $KeystorePath -ErrorAction Stop
$KeystorePath = $resolved.Path
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "GitHub CLI mancante: installa gh e fai gh auth login sul tuo PC."
}
if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
    throw "Java keytool mancante: installa un JDK sul tuo PC."
}
& gh auth status
if ($LASTEXITCODE -ne 0) { throw "Accesso GitHub mancante: esegui gh auth login." }
Write-Host "IMPORTANTE: utilizzare la CHIAVE ORIGINALE se vuoi aggiornare la versione gia' installata."
Write-Host "Configurazione per $Repo, alias $Alias; nessuna password verra' stampata."
$storeSecure = Read-Host "Password keystore" -AsSecureString
$keySecure = Read-Host "Password chiave/alias" -AsSecureString
function Reveal([System.Security.SecureString]$s) {
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($s)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}
$storePassword = Reveal $storeSecure
$keyPassword = Reveal $keySecure
try {
    # keytool accepts the password through an environment variable, not through process arguments.
    $env:UBICORA_KEYTOOL_STOREPASSWORD_TEMP = $storePassword
    $keyInfo = & keytool -list -v -keystore $KeystorePath -alias $Alias -storepass:env UBICORA_KEYTOOL_STOREPASSWORD_TEMP 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Impossibile aprire la chiave / alias. Controlla password, file e alias." }
    $certLine = $keyInfo | Where-Object { $_ -match 'SHA256:\s*([A-Fa-f0-9:]{64,95})' } | Select-Object -First 1
    $certificateMatch = [regex]::Match([string]$certLine, 'SHA256:\s*([A-Fa-f0-9:]{64,95})')
    if (-not $certificateMatch.Success) {
        throw "Impossibile ricavare l'impronta SHA256 del certificato."
    }
    $cert = $certificateMatch.Groups[1].Value.Replace(":","").ToLowerInvariant()
    if ($cert -notmatch '^[a-f0-9]{64}$') { throw "Fingerprint SHA256 non valida." }

    # gh secret set reads sensitive input from stdin, not visible as command-line arguments.
    [Convert]::ToBase64String([IO.File]::ReadAllBytes($KeystorePath)) |
        & gh secret set UBICORA_KEYSTORE_BASE64 -R $Repo
    if ($LASTEXITCODE -ne 0) { throw "Caricamento keystore GitHub fallito." }
    $storePassword | & gh secret set UBICORA_STORE_PASSWORD -R $Repo
    if ($LASTEXITCODE -ne 0) { throw "Caricamento password keystore fallito." }
    $Alias | & gh secret set UBICORA_KEY_ALIAS -R $Repo
    if ($LASTEXITCODE -ne 0) { throw "Caricamento alias fallito." }
    $keyPassword | & gh secret set UBICORA_KEY_PASSWORD -R $Repo
    if ($LASTEXITCODE -ne 0) { throw "Caricamento password chiave fallito." }
    & gh variable set UBICORA_SIGNING_CERT_SHA256 --body $cert -R $Repo
    if ($LASTEXITCODE -ne 0) { throw "Caricamento fingerprint pubblica fallito." }

    Write-Host "Firma permanente configurata nei GitHub Secrets del repository."
    Write-Host "Fingerprint SHA256 pubblica: $cert"
    Write-Host "Conserva offline il keystore, la password e l'alias: la perdita impedisce i futuri aggiornamenti."
    Write-Host "Esegui il workflow 'Ubicora Stable Signed Update' su main dopo il merge della PR."
} finally {
    Remove-Item Env:UBICORA_KEYTOOL_STOREPASSWORD_TEMP -ErrorAction SilentlyContinue
    $storePassword = $null
    $keyPassword = $null
    $storeSecure.Dispose()
    $keySecure.Dispose()
}
