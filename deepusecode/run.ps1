# Start backend using environment variables from .env
#
# Usage:  .\run.ps1
#         .\run.ps1 -Profile dev     (H2 demo mode, no MySQL needed)
#
# NOTE: keep this file ASCII-only. Windows PowerShell reads .ps1 without BOM
# using the system ANSI codepage, and non-ASCII characters break the parser.

param(
    [string]$Profile = ''
)

# Deliberately not 'Stop': java writes its log to stderr and that would abort
# the script immediately.
$ErrorActionPreference = 'Continue'
$root = $PSScriptRoot
$envFile = Join-Path $root '.env'

if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith('#')) { return }
        $idx = $line.IndexOf('=')
        if ($idx -lt 1) { return }
        $key = $line.Substring(0, $idx).Trim()
        $value = $line.Substring($idx + 1).Trim()
        Set-Item -Path "env:$key" -Value $value
    }
    Write-Host 'Loaded environment variables from .env' -ForegroundColor DarkGray
} else {
    Write-Warning 'No .env found. Copy .env.example to .env and fill in real values.'
}

if (-not $env:JWT_SECRET) {
    throw 'JWT_SECRET is missing; the application refuses to start without it. Set it in .env.'
}

$jar = Join-Path $root 'target\deepusecode-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path $jar)) {
    throw "Missing $jar . Run mvn package -DskipTests (or build.ps1) first."
}

# Prefer JAVA_HOME: on some machines the first 'java' on PATH is a broken
# Oracle javapath stub that exits with STATUS_STACK_BUFFER_OVERRUN.
$javaExe = 'java'
if ($env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (Test-Path $candidate) {
        $javaExe = $candidate
    }
} else {
    Write-Warning 'JAVA_HOME not set; falling back to java on PATH.'
}

$jarArgs = @('-jar', $jar)
if ($Profile) { $jarArgs += "--spring.profiles.active=$Profile" }

Write-Host "Starting: $javaExe $($jarArgs -join ' ')" -ForegroundColor Cyan
& $javaExe @jarArgs
