# Produit projet.zip à la racine du dépôt sous Windows (OPS-02, ADR 0004).
# NON TESTÉ : l'équipe ne dispose pas d'environnement Windows. Transposition de package.sh.
# L'archive contient exactement chat/README.md, chat/presentation.pdf et chat/src/ (fichiers suivis
# par Git, plus src/docs/, gardé hors de Git et pris tel quel sur le disque).
# Usage, depuis src\ : powershell -ExecutionPolicy Bypass -File scripts\package.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Set-Location $root

if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'git est introuvable' }
if (-not (Test-Path README.md)) { throw 'README.md manquant à la racine du dépôt' }
if (-not (Test-Path presentation.pdf)) { throw 'presentation.pdf manquant à la racine du dépôt' }

$status = git status --porcelain --untracked-files=all -- src README.md
if ($status) {
    Write-Warning 'Des fichiers de src/ ou README.md ne sont pas commités : les fichiers suivis sont archivés dans leur état actuel, les fichiers non suivis (hors src/docs/) sont ignorés.'
}

# Chemins relatifs à la racine, toujours avec des barres obliques dans l'archive.
$files = New-Object System.Collections.Generic.List[string]
foreach ($file in (git -c core.quotepath=off ls-files -- src)) {
    if (Test-Path -LiteralPath $file -PathType Leaf) { $files.Add($file) }
}
if (Test-Path src/docs) {
    foreach ($item in Get-ChildItem -Recurse -File -LiteralPath src/docs) {
        $files.Add(($item.FullName.Substring($root.Length + 1)) -replace '\\', '/')
    }
}
$files.Add('README.md')
$files.Add('presentation.pdf')

$zipPath = Join-Path $root 'projet.zip'
if (Test-Path $zipPath) { Remove-Item $zipPath }
$zip = [System.IO.Compression.ZipFile]::Open($zipPath, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in ($files | Sort-Object -Unique)) {
        $source = Join-Path $root ($file -replace '/', '\')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $source, "chat/$file") | Out-Null
    }
} finally {
    $zip.Dispose()
}

# Vérification de l'archive avant livraison.
$zip = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
try {
    $entries = $zip.Entries | ForEach-Object { $_.FullName }
} finally {
    $zip.Dispose()
}
$unexpected = $entries | Where-Object { $_ -notmatch '^chat/(README\.md|presentation\.pdf|src/.*)$' }
if ($unexpected) { throw "Entrées inattendues dans l'archive : $unexpected" }
$forbidden = $entries | Where-Object { $_ -match '(^|/)(node_modules|target)/|^chat/src/(certs|data)/|(^|/)\.env$' }
if ($forbidden) { throw "Fichiers interdits dans l'archive : $forbidden" }
foreach ($required in 'chat/README.md', 'chat/presentation.pdf', 'chat/src/client/package.json', 'chat/src/server/pom.xml') {
    if ($entries -notcontains $required) { throw "$required manque dans l'archive" }
}

Write-Output "projet.zip créé : $($entries.Count) fichiers sous chat/"
