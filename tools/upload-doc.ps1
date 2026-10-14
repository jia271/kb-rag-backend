# Upload one document to the running service and report the result.
# Usage: .\tools\upload-doc.ps1 .\samples\产品需求说明.md
# The service must be running already: .\tools\run-local.ps1
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string] $Path,
    [string] $Endpoint = 'http://localhost:8080/api/documents'
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

if (-not (Test-Path -LiteralPath $Path)) {
    throw "File not found: $Path"
}
$file = (Resolve-Path -LiteralPath $Path).Path

$response = curl.exe -s --max-time 180 -F "file=@$file" $Endpoint
if (-not $response) {
    throw "No response from $Endpoint. Start the service first: .\tools\run-local.ps1"
}

$document = $response | ConvertFrom-Json
Write-Host ("Uploaded : {0}" -f $document.fileName)
Write-Host ("Id       : {0}" -f $document.id)

# 解析是异步的，这里等它结束，免得看到的就是一个 PENDING
$deadline = (Get-Date).AddSeconds(60)
while ((Get-Date) -lt $deadline) {
    $current = curl.exe -s --max-time 30 ("http://localhost:8080/api/documents/{0}" -f $document.id) | ConvertFrom-Json
    if ($current.status -eq 'READY' -or $current.status -eq 'FAILED') {
        $document = $current
        break
    }
    Start-Sleep -Milliseconds 300
}

Write-Host ("Status   : {0}" -f $document.status)
Write-Host ("Chunks   : {0}" -f $document.chunkCount)
if ($document.failureReason) {
    Write-Host ("Failed   : {0}" -f $document.failureReason)
}
Write-Host ''
Write-Host 'Open in browser:'
Write-Host ("  http://localhost:8080/api/documents/{0}/chunks?size=50" -f $document.id)
