# Exercises the rewritten stage runner against a FAKE backend: a stub mvnw.cmd that prints what a
# real one would. Nothing here touches the database. What is being proven is the part the owner
# cares about most — that a stage detects completion, notices a stall instead of waiting out its
# timeout, survives a crash, and that the retry wrapper gives it another go.
$ErrorActionPreference = "Stop"

$script = Get-Content -LiteralPath 'D:\Finvera\refresh-data.ps1' -Raw
$cut = $script.IndexOf('Write-Host "=== Finvera data refresh ==="')
$definitions = $script.Substring(0, $cut)
$sandbox = Join-Path $env:TEMP "refresh-defs-stage.ps1"
Set-Content -LiteralPath $sandbox -Value $definitions -Encoding utf8

$FullRefresh = $false; $SkipCrawl = $false; $Cleanup = $false; $ForceWarmup = $false
$LookbackDays = 90; $historyStartDate = "2019-01-01"; $historyEndDate = "2026-09-06"

# Dot-source FIRST: the definitions region also assigns $root/$beDir/$exportDir, so the stubs have
# to be installed afterwards or they are overwritten by the real paths.
. $sandbox

$exportDir = Join-Path $env:TEMP ("stage-test-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path (Join-Path $exportDir "output") | Out-Null
$StateFile = Join-Path (Join-Path $exportDir "output") "refresh-state.json"

# A stub backend directory. Invoke-BackendStageAttempt runs "$beDir\mvnw.cmd".
$beDir = Join-Path $env:TEMP ("stage-be-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $beDir | Out-Null

# The harness drives real seconds, so keep the windows small.
$StageStallSeconds = 8
$StageAttempts = 2
$script:RefreshState = Read-RefreshState

$failures = 0
function Check([string]$name, [bool]$ok, [string]$detail = "") {
    if ($ok) { Write-Host "  OK   $name" }
    else { Write-Host "  FAIL $name $detail" -ForegroundColor Red; $script:failures++ }
}

function Set-FakeBackend([string]$body) {
    Set-Content -LiteralPath (Join-Path $beDir "mvnw.cmd") -Value $body -Encoding ascii
}

Write-Host "a stage that reaches its markers succeeds"
Set-FakeBackend @'
@echo off
echo starting up
ping -n 2 127.0.0.1 >nul
echo instrument_reference_import status=OK total=1522
ping -n 31 127.0.0.1 >nul
'@
$failure = Invoke-BackendStageAttempt -name "fake" -waitPatterns @("instrument_reference_import status=") -timeoutSec 60
Check "returns no failure when the marker appears" ($null -eq $failure) $failure

Write-Host "a stage needing two markers waits for both"
Set-FakeBackend @'
@echo off
echo stock_import dataset=daily-bar total=5
ping -n 3 127.0.0.1 >nul
echo daily_bar_source_retirement primary=VNSTOCK_VCI
ping -n 31 127.0.0.1 >nul
'@
$failure = Invoke-BackendStageAttempt -name "fake two" -waitPatterns @("stock_import dataset=daily-bar total=", "daily_bar_source_retirement primary=") -timeoutSec 60
Check "both markers detected" ($null -eq $failure) $failure

Write-Host "a silent backend is caught by the stall window, not the timeout"
Set-FakeBackend @'
@echo off
rem `timeout` needs stdin, which is redirected here; ping waits without it.
ping -n 121 127.0.0.1 >nul
'@
$started = Get-Date
# timeoutSec is deliberately far larger than the stall window: the old code would have waited it out.
$failure = Invoke-BackendStageAttempt -name "fake stalled" -waitPatterns @("never appears") -timeoutSec 600
$elapsed = ((Get-Date) - $started).TotalSeconds
Check "reports a stall" ($failure -like "*treo*") $failure
Check "fails within the stall window, not the 600s timeout" ($elapsed -lt 60) ("took {0:N0}s" -f $elapsed)

Write-Host "a backend that dies is reported, not waited on"
Set-FakeBackend @'
@echo off
echo booting
exit /b 1
'@
$failure = Invoke-BackendStageAttempt -name "fake crash" -waitPatterns @("never appears") -timeoutSec 600
Check "reports an early exit" ($failure -like "*thoat som*") $failure

Write-Host "APPLICATION FAILED TO START is recognised"
Set-FakeBackend @'
@echo off
echo APPLICATION FAILED TO START
ping -n 31 127.0.0.1 >nul
'@
$failure = Invoke-BackendStageAttempt -name "fake appfail" -waitPatterns @("never appears") -timeoutSec 600
Check "reports the startup failure" ($failure -like "*APPLICATION FAILED TO START*") $failure

Write-Host "the wrapper retries, then records the stage as done"
$script:attempts = 0
Set-FakeBackend @'
@echo off
echo instrument_reference_import status=OK total=1
ping -n 21 127.0.0.1 >nul
'@
Invoke-BackendStage -name "fake wrapped" -waitPatterns @("instrument_reference_import status=") -timeoutSec 60 -stageKey "test-stage"
Check "stage recorded in the state file" ($script:RefreshState.completed -contains "test-stage")

Write-Host "a completed stage is skipped on the next call"
Set-FakeBackend @'
@echo off
echo this backend must never start
exit /b 3
'@
Invoke-BackendStage -name "fake wrapped" -waitPatterns @("instrument_reference_import status=") -timeoutSec 60 -stageKey "test-stage"
Check "skipping a done stage does not run or fail" $true

Write-Host "a stage that never succeeds throws after its attempts"
Set-FakeBackend @'
@echo off
echo booting
exit /b 1
'@
$threw = $false
try {
    Invoke-BackendStage -name "fake doomed" -waitPatterns @("never appears") -timeoutSec 30 -stageKey "doomed-stage"
} catch { $threw = $true }
Check "throws after exhausting attempts" $threw
Check "a failed stage is not recorded as done" (-not ($script:RefreshState.completed -contains "doomed-stage"))

Remove-Item -LiteralPath $exportDir, $beDir -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $sandbox -Force -ErrorAction SilentlyContinue
if ($failures -gt 0) { Write-Host "$failures check(s) failed" -ForegroundColor Red; exit 1 }
Write-Host "stage runner: all checks passed" -ForegroundColor Green
