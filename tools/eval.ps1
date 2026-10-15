# Run the retrieval evaluation against the current index.
# Usage: .\tools\eval.ps1              (all three modes)
#        .\tools\eval.ps1 -Mode hybrid
# The service must be running: .\tools\run-local.ps1
# NOTE: keep this file ASCII-only. Windows PowerShell 5.1 reads .ps1 files using
# the system ANSI codepage, and a non-ASCII comment can swallow the next line.
param(
    [ValidateSet('keyword', 'vector', 'hybrid', 'all')]
    [string] $Mode = 'all',
    [int] $TopK = 5,
    [string] $Endpoint = 'http://localhost:8080/api/eval/run'
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$modes = if ($Mode -eq 'all') { 'keyword,vector,hybrid' } else { $Mode }
$url = "$Endpoint`?modes=$modes&topK=$TopK"

$json = curl.exe -s --max-time 300 -X POST $url
if (-not $json) {
    throw "No response from $Endpoint. Start the service first: .\tools\run-local.ps1"
}
$report = $json | ConvertFrom-Json

Write-Host ("Questions: {0} ({1} answerable)   Index: {2}   topK: {3}" -f `
        $report.questions, $report.answerableQuestions, $report.index, $report.topK)
Write-Host ''

$header = '{0,-9} {1,9} {2,8} {3,10} {4,16} {5,18}'
Write-Host ($header -f 'mode', 'hitRate', 'MRR', 'latencyMs', 'answerTopScore', 'noAnswerTopScore')
foreach ($m in $report.modes) {
    Write-Host ($header -f `
            $m.mode, $m.hitRate.ToString('P1'), $m.mrr.ToString('N3'), $m.averageLatencyMs.ToString('N1'), `
            $m.answerableTopScore.ToString('N5'), $m.unanswerableTopScore.ToString('N5'))
}

$misses = @()
foreach ($m in $report.modes) {
    foreach ($d in $m.details) {
        if ($d.answerable -and $d.rank -eq 0) {
            $misses += ('  [{0}] {1} - {2}' -f $m.mode, $d.id, $d.question)
        }
    }
}
if ($misses.Count -gt 0) {
    Write-Host ''
    Write-Host 'Not found in topK:'
    $misses | ForEach-Object { Write-Host $_ }
}
