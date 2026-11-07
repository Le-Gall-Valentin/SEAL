# Prépare SEAL pour un lancement local sous Windows (section 17 du CDC, ADR 0004).
# NON TESTÉ : l'équipe ne dispose pas d'environnement Windows. Transposition de gen-certs.sh :
#   1. installe l'autorité locale de mkcert et crée un certificat pour localhost, 127.0.0.1 et ::1 ;
#   2. crée .env à partir de .env.example s'il n'existe pas, puis remplit les secrets vides.
# Usage, depuis src\ : powershell -ExecutionPolicy Bypass -File scripts\gen-certs.ps1
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

if (-not (Get-Command mkcert -ErrorAction SilentlyContinue)) {
    throw 'mkcert est introuvable : https://github.com/FiloSottile/mkcert#installation'
}

New-Item -ItemType Directory -Force -Path certs | Out-Null
mkcert -install
if ($LASTEXITCODE -ne 0) { throw 'mkcert -install a échoué' }
mkcert -cert-file certs/localhost.pem -key-file certs/localhost-key.pem localhost 127.0.0.1 ::1
if ($LASTEXITCODE -ne 0) { throw 'La création du certificat a échoué' }

if (-not (Test-Path .env)) {
    Copy-Item .env.example .env
    Write-Output '.env créé à partir de .env.example'
}

function New-RandomBase64([int] $bytes) {
    $buffer = New-Object byte[] $bytes
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($buffer) } finally { $generator.Dispose() }
    return [Convert]::ToBase64String($buffer)
}

$secrets = [ordered]@{
    # SEC-22 : 32 octets aléatoires en base64.
    'SEAL_DATA_KEY'    = New-RandomBase64 32
    # Sans + ni / ni =, pour rester sûr dans une URL JDBC ou une ligne de commande.
    'SEAL_DB_PASSWORD' = (New-RandomBase64 32) -replace '[+/=]', ''
}

$path = Join-Path (Get-Location) '.env'
$lines = [System.IO.File]::ReadAllLines($path)
for ($i = 0; $i -lt $lines.Length; $i++) {
    foreach ($name in $secrets.Keys) {
        if ($lines[$i] -eq "$name=") {
            $lines[$i] = "$name=$($secrets[$name])"
            Write-Output "$name généré"
        }
    }
}
# UTF-8 sans BOM et fins de ligne LF, comme le lit docker compose.
[System.IO.File]::WriteAllText($path, (($lines -join "`n") + "`n"), (New-Object System.Text.UTF8Encoding $false))

Write-Output 'Prêt : lancez docker compose up --build, puis ouvrez https://localhost:8443'
