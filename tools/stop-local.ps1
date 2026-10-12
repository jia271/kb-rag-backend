# Stop the locally running kb-rag-backend service.
# Only processes whose command line points at this project are touched.
$ErrorActionPreference = 'Stop'

$port = 8080
$listeners = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue

if (-not $listeners) {
    Write-Host "Nothing is listening on port $port"
    exit 0
}

foreach ($procId in ($listeners | Select-Object -ExpandProperty OwningProcess -Unique)) {
    $proc = Get-CimInstance Win32_Process -Filter "ProcessId=$procId" -ErrorAction SilentlyContinue
    if ($proc -and $proc.CommandLine -like '*kb-rag-backend*') {
        Stop-Process -Id $procId -Force
        Write-Host "Stopped kb-rag-backend (PID $procId)"
    } else {
        Write-Host "Skipped PID $procId (not this project)"
    }
}
