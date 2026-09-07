# Exercises the resume/state helpers of refresh-data.ps1 without running a refresh: everything above
# the "=== Finvera data refresh ===" banner is definitions, so it can be dot-sourced on its own.
$ErrorActionPreference = "Stop"

$script = Get-Content -LiteralPath 'D:\Finvera\refresh-data.ps1' -Raw
$cut = $script.IndexOf('Write-Host "=== Finvera data refresh ==="')
if ($cut -lt 0) { throw "banner not found" }
$definitions = $script.Substring(0, $cut)

$sandbox = Join-Path $env:TEMP "refresh-defs.ps1"
Set-Content -LiteralPath $sandbox -Value $definitions -Encoding utf8

# The parameters the run key is built from.
$FullRefresh = $false; $SkipCrawl = $false; $Cleanup = $false; $ForceWarmup = $false
$LookbackDays = 90; $historyStartDate = "2019-01-01"; $historyEndDate = "2026-09-06"
$root = "D:\Finvera"; $beDir = "D:\Finvera\finvera-be"
$exportDir = Join-Path $env:TEMP ("refresh-state-test-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path (Join-Path $exportDir "output") | Out-Null

. $sandbox

$failures = 0
function Check([string]$name, [bool]$ok) {
    if ($ok) { Write-Host "  OK   $name" } else { Write-Host "  FAIL $name" -ForegroundColor Red; $script:failures++ }
}

Write-Host "state round-trip"
$state = Read-RefreshState
Check "starts empty" ($state.completed.Count -eq 0)

Complete-Stage $state "2-instrument-reference"
Complete-Stage $state "3-equity-profile"
Check "records two stages" ($state.completed.Count -eq 2)
Check "state file written" (Test-Path -LiteralPath $StateFile)

$reloaded = Read-RefreshState
Check "resumes the same run" ($reloaded.completed -contains "3-equity-profile")

Write-Host "a different parameter set must not resume"
$FullRefresh = $true
$afterParamChange = Read-RefreshState
Check "discards state when parameters differ" ($afterParamChange.completed.Count -eq 0)
$FullRefresh = $false

Write-Host "stale state must not resume"
$state = Read-RefreshState
Complete-Stage $state "6a-daily-bars"
$raw = Get-Content -LiteralPath $StateFile -Raw | ConvertFrom-Json
$raw.updatedAt = (Get-Date).AddHours(-13).ToString("o")
$raw | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $StateFile -Encoding utf8
$afterAge = Read-RefreshState
Check "discards state older than the window" ($afterAge.completed.Count -eq 0)

Write-Host "corrupt state must not stop the run"
Set-Content -LiteralPath $StateFile -Value "{ not json" -Encoding utf8
$afterCorrupt = Read-RefreshState
Check "starts fresh on unreadable state" ($afterCorrupt.completed.Count -eq 0)

Write-Host "port helper"
Check "reports a free port" (Wait-PortFree 59999 2)

Remove-Item -LiteralPath $exportDir -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $sandbox -Force -ErrorAction SilentlyContinue
if ($failures -gt 0) { Write-Host "$failures check(s) failed" -ForegroundColor Red; exit 1 }
Write-Host "all state checks passed" -ForegroundColor Green
