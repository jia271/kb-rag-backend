# Search the indexed documents.
# Usage: .\tools\search.ps1 "<query>" -Mode hybrid -TopK 5
# Mode can be keyword, vector or hybrid. The service must be running.
# NOTE: keep this file ASCII-only. Windows PowerShell 5.1 reads .ps1 files using
# the system ANSI codepage, and a non-ASCII comment can swallow the next line.
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string] $Query,
    [ValidateSet('keyword', 'vector', 'hybrid')]
    [string] $Mode = 'hybrid',
    [int] $TopK = 5,
    [string] $Endpoint = 'http://localhost:8080/api/search'
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# The query must be URL-encoded, otherwise the request line is invalid.
$encoded = [uri]::EscapeDataString($Query)
$url = "$Endpoint`?q=$encoded&mode=$Mode&topK=$TopK"

$json = curl.exe -s --max-time 60 $url
if (-not $json) {
    throw "No response from $Endpoint. Start the service first: .\tools\run-local.ps1"
}
$response = $json | ConvertFrom-Json

Write-Host ("Query   : {0}" -f $response.query)
Write-Host ("Mode    : {0}   Embedding: {1} ({2}d)   Took: {3} ms   Indexed: {4} chunks" -f `
        $response.mode, $response.embeddingProvider, $response.embeddingDimensions, `
        $response.tookMillis, $response.indexedChunks)
Write-Host ''

if (-not $response.hits -or $response.hits.Count -eq 0) {
    Write-Host 'No hits. Upload documents first: .\tools\upload-doc.ps1 <path>'
    exit 0
}

$rank = 0
foreach ($hit in $response.hits) {
    $rank++
    $flat = ($hit.content -replace "`r?`n", ' ')
    $preview = if ($flat.Length -gt 70) { $flat.Substring(0, 70) } else { $flat }
    Write-Host ("{0}. kw={1} vec={2} score={3:N5} | {4}" -f `
            $rank, $hit.keywordRank, $hit.vectorRank, $hit.score, $hit.fileName)
    Write-Host ("   {0}" -f $preview)
}
