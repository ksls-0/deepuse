# One-shot build: frontend -> backend static/ -> single executable jar
#
#   1. build frontend into hello/dist
#   2. copy dist into deepusecode/src/main/resources/static
#   3. mvn package
#
# Usage:  .\build.ps1
#         .\build.ps1 -SkipTests
#
# NOTE: keep this file ASCII-only. Windows PowerShell reads .ps1 without BOM
# using the system ANSI codepage, and non-ASCII characters break the parser.

param(
    [switch]$SkipTests
)

# Deliberately not 'Stop': npm/mvn write progress to stderr, which PowerShell
# surfaces as ErrorRecords and would abort the script on the first line of
# normal output. We check $LASTEXITCODE explicitly instead.
$ErrorActionPreference = 'Continue'
$failed = $false
$root = $PSScriptRoot
$frontend = Join-Path $root 'hello'
$backend = Join-Path $root 'deepusecode'
$staticDir = Join-Path $backend 'src\main\resources\static'

function Write-Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Cyan }

# ---------- 1. frontend ----------
Write-Step 'Build frontend'
Push-Location $frontend
try {
    $env:BROWSERSLIST_IGNORE_OLD_DATA = 'true'
    & npm run build
    if ($LASTEXITCODE -ne 0) {
        Pop-Location
        throw "Frontend build failed (exit $LASTEXITCODE)"
    }
} finally {
    Pop-Location
}

$dist = Join-Path $frontend 'dist'
if (-not (Test-Path (Join-Path $dist 'index.html'))) {
    throw "Missing $dist\index.html, frontend output is incomplete"
}

# ---------- 2. sync into static ----------
Write-Step 'Sync frontend output into backend static/'
if (Test-Path $staticDir) {
    Get-ChildItem $staticDir -Force |
        Where-Object { $_.Name -ne '.gitkeep' } |
        Remove-Item -Recurse -Force
} else {
    New-Item $staticDir -ItemType Directory -Force | Out-Null
}
Copy-Item (Join-Path $dist '*') $staticDir -Recurse -Force
$fileCount = (Get-ChildItem $staticDir -Recurse -File | Measure-Object).Count
Write-Host "Copied $fileCount files into src\main\resources\static" -ForegroundColor DarkGray

# ---------- 3. backend package ----------
Write-Step 'Package backend'
$mvn = (Get-Command mvn -ErrorAction SilentlyContinue).Source
if (-not $mvn) {
    $mvn = 'D:\software\maven\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin\mvn.cmd'
}
if (-not (Test-Path $mvn)) {
    throw 'Maven not found. Put mvn on PATH or edit the default path in this script.'
}

$mvnArgs = @('-B', 'package')
# The machine-wide Maven settings point at a non-existent repo; prefer the
# workspace-local settings file when it exists.
$settings = Join-Path $root '.build\settings.xml'
if (Test-Path $settings) {
    $mvnArgs += @('-s', $settings)
    Write-Host "Using workspace Maven settings: $settings" -ForegroundColor DarkGray
}
if ($SkipTests) { $mvnArgs += '-DskipTests' }
$mvnArgs += '-Dfile.encoding=UTF-8'

Push-Location $backend
try {
    & $mvn @mvnArgs
    if ($LASTEXITCODE -ne 0) { throw "Backend package failed (exit $LASTEXITCODE)" }
} finally {
    Pop-Location
}

$jar = Join-Path $backend 'target\deepusecode-0.0.1-SNAPSHOT.jar'
$size = [math]::Round((Get-Item $jar).Length / 1MB, 1)
Write-Step "Build OK: $jar ($size MB)"
Write-Host 'Run it with:  deepusecode\run.ps1   (loads env vars from .env)' -ForegroundColor Yellow
