# Ask a question against the indexed documents.
# Usage: .\tools\ask.ps1 "<question>"                 (waits for the full answer)
#        .\tools\ask.ps1 "<question>" -Stream        (prints the SSE stream as it arrives)
#        .\tools\ask.ps1 "<follow-up>" -Session demo (same session keeps the history)
# The service must be running: .\tools\run-local.ps1
# NOTE: keep this file ASCII-only. Windows PowerShell 5.1 reads .ps1 files using
# the system ANSI codepage, and a non-ASCII comment can swallow the next line.
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string] $Question,
    [string] $Session,
    [ValidateSet('keyword', 'vector', 'hybrid')]
    [string] $Mode = 'hybrid',
    [int] $TopK = 5,
    [switch] $Stream,
    [string] $Endpoint = 'http://localhost:8080/api/chat'
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$body = @{ question = $Question; topK = $TopK; mode = $Mode }
if ($Session) { $body.sessionId = $Session }

# The JSON must reach the server as UTF-8, so write it to a temp file instead of
# passing it on the command line (the console codepage would corrupt it).
$payload = $body | ConvertTo-Json -Compress
$tempFile = Join-Path ([System.IO.Path]::GetTempPath()) ("kb-ask-{0}.json" -f ([guid]::NewGuid()))
[System.IO.File]::WriteAllText($tempFile, $payload, (New-Object System.Text.UTF8Encoding($false)))

try {
    if ($Stream) {
        $url = "$Endpoint/stream"
        curl.exe -N -s --max-time 300 -X POST -H 'Content-Type: application/json' -d "@$tempFile" $url
        exit 0
    }

    $json = curl.exe -s --max-time 300 -X POST -H 'Content-Type: application/json' -d "@$tempFile" $Endpoint
    if (-not $json) {
        throw "No response from $Endpoint. Start the service first: .\tools\run-local.ps1"
    }
    $response = $json | ConvertFrom-Json

    Write-Host ''
    Write-Host $response.answer
    Write-Host ''
    if ($response.citations -and $response.citations.Count -gt 0) {
        Write-Host ('Sources ({0}), took {1} ms, tokens {2}+{3}:' -f `
                $response.citations.Count, $response.tookMillis,
                $response.promptTokens, $response.completionTokens)
        foreach ($citation in $response.citations) {
            $location = $citation.fileName
            if ($citation.heading) { $location += ' / ' + $citation.heading }
            if ($citation.pageNumber) { $location += ' / page ' + $citation.pageNumber }
            Write-Host ('  [{0}] {1}' -f $citation.index, $location)
            Write-Host ('      {0}' -f $citation.snippet)
        }
    } else {
        Write-Host ('No sources. Took {0} ms.' -f $response.tookMillis)
    }
} finally {
    Remove-Item -LiteralPath $tempFile -Force -ErrorAction SilentlyContinue
}
