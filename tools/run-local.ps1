# Start kb-rag-backend locally, injecting secrets from .env.local.
# Usage: .\tools\run-local.ps1
# NOTE: keep this file ASCII-only. Windows PowerShell 5.1 reads .ps1 files using
# the system ANSI codepage, so non-ASCII text here can break parsing.
$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot '.env.local'

if (Test-Path -LiteralPath $envFile) {
    # Read via .NET with an explicit encoding, then split manually.
    # Windows PowerShell 5.1 decodes BOM-less UTF-8 files using the ANSI codepage,
    # which corrupts the content and swallows newlines, so no setting would match.
    $raw = [System.IO.File]::ReadAllText($envFile, [System.Text.Encoding]::UTF8)
    $loaded = 0
    foreach ($line in ($raw -split "`r`n|`r|`n")) {
        if ($line -match '^\s*[^#\s][^=]*=') {
            $name, $value = $line -split '=', 2
            [System.Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim())
            $loaded++
        }
    }
    Write-Host ("Loaded {0} setting(s) from .env.local" -f $loaded)
    if ($loaded -eq 0) {
        Write-Warning 'No settings found in .env.local; model calls will fail'
    }
} else {
    Write-Warning '.env.local not found; model calls will fail without an API key'
}

$javaHome = 'C:\Program Files\Java\jdk-17.0.18'
$javaExe = Join-Path $javaHome 'bin\java.exe'
if (-not (Test-Path -LiteralPath $javaExe)) {
    throw "JDK not found: $javaHome"
}
$env:JAVA_HOME = $javaHome

$jar = Join-Path $projectRoot 'target\kb-rag-backend-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jar)) {
    throw 'Build artifact not found. Run first: .\mvnw.cmd -DskipTests package'
}

# Fail early with a readable message instead of a Spring stack trace.
$port = 8080
$listeners = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
if ($listeners) {
    $owners = $listeners | Select-Object -ExpandProperty OwningProcess -Unique
    Write-Warning "Port $port is already in use by PID(s): $($owners -join ', ')"
    Write-Warning 'If that is this project, run .\tools\stop-local.ps1 first.'
    exit 1
}

Write-Host "Starting $jar"
Write-Host 'Health check:   http://localhost:8080/actuator/health'
Write-Host 'Model selftest: http://localhost:8080/api/llm/ping'
Write-Host 'Press Ctrl+C to stop.'
& $javaExe -jar $jar
