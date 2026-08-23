<#
.SYNOPSIS
    Catches the local dev DB up after being away for a while: crawls fresh Vnstock prices,
    registers any newly-listed symbols, imports the new prices/fundamentals, and backfills the
    technical-indicator gap -- all in one command.

.DESCRIPTION
    Runs the exact sequence documented in docs/runbooks/go-live-setup.md 3.7/6.3, automated:
      1. Crawl (export_all_symbols.py + the instrument-reference/equity-profile exporters).
      2. Restart the backend with instrument-reference + equity-profile import ON, wait for it
         to finish, stop it.
      3. Restart with daily-bar + fundamentals import ON, wait, stop.
      4. Restart with the technical-indicator warmup ON, wait, stop.
    Every import here is safe/idempotent (only adds missing rows or backfills gaps), so this is
    safe to run after a 3-day gap, a 7-day gap, or any length of time.

    Sector reference is NOT part of this script -- industries change rarely enough that it stays
    a separate, occasional manual step (see the runbook). All overrides here are applied to THIS
    PowerShell process only, from finvera-be\.env.refresh (flags only, no secrets) layered on top
    of finvera-be\.env -- the real .env file on disk is never modified, so IntelliJ's own run
    configuration is unaffected.

    After this script finishes, start the backend normally (IntelliJ, or `.\mvnw.cmd
    spring-boot:run` in finvera-be) -- this script does not leave it running.

.PARAMETER SkipCrawl
    Skip the Python crawl step (use this if you already have fresh JSON files in
    tools/market-data/vnstock-export/output and just need to re-import them).

.EXAMPLE
    .\refresh-data.ps1
#>
param(
    [switch]$SkipCrawl
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$beDir = Join-Path $root "finvera-be"
$exportDir = Join-Path $root "tools\market-data\vnstock-export"
$envFile = Join-Path $beDir ".env"
$envRefreshFile = Join-Path $beDir ".env.refresh"

$AllImportFlags = @(
    "FINVERA_MARKET_IMPORT_INSTRUMENT_REFERENCE_ENABLED",
    "FINVERA_STOCK_IMPORT_EQUITY_PROFILE_ENABLED",
    "FINVERA_STOCK_IMPORT_DAILY_BAR_ENABLED",
    "FINVERA_STOCK_IMPORT_FUNDAMENTALS_ENABLED",
    "FINVERA_STOCK_TECHNICAL_WARMUP_ENABLED"
)

function Import-EnvFile([string]$path) {
    if (-not (Test-Path $path)) { throw "Env file not found: $path" }
    foreach ($line in Get-Content $path) {
        $trimmed = $line.Trim()
        if ($trimmed -eq "" -or $trimmed.StartsWith("#")) { continue }
        $idx = $trimmed.IndexOf("=")
        if ($idx -lt 0) { continue }
        $key = $trimmed.Substring(0, $idx).Trim()
        $value = $trimmed.Substring($idx + 1)
        [Environment]::SetEnvironmentVariable($key, $value, "Process")
    }
}

function Set-StageFlags([string[]]$enabledKeys) {
    foreach ($key in $AllImportFlags) {
        $value = if ($enabledKeys -contains $key) { "true" } else { "false" }
        [Environment]::SetEnvironmentVariable($key, $value, "Process")
    }
}

function Invoke-BackendStage([string]$name, [string[]]$waitPatterns, [int]$timeoutSec) {
    Write-Host ""
    Write-Host "== $name ==" -ForegroundColor Cyan
    $logFile = [System.IO.Path]::GetTempFileName()
    $proc = Start-Process -FilePath (Join-Path $beDir "mvnw.cmd") -ArgumentList "-q", "spring-boot:run" `
        -WorkingDirectory $beDir -RedirectStandardOutput $logFile -RedirectStandardError "$logFile.err" `
        -PassThru -WindowStyle Hidden

    $deadline = (Get-Date).AddSeconds($timeoutSec)
    $seen = New-Object System.Collections.Generic.HashSet[string]
    while ((Get-Date) -lt $deadline -and -not $proc.HasExited) {
        Start-Sleep -Seconds 3
        $content = Get-Content $logFile -ErrorAction SilentlyContinue
        foreach ($pattern in $waitPatterns) {
            if ($seen.Contains($pattern)) { continue }
            $match = $content | Select-String -Pattern $pattern -SimpleMatch | Select-Object -Last 1
            if ($match) {
                Write-Host "  $($match.Line.Trim())"
                $seen.Add($pattern) | Out-Null
            }
        }
        if ($seen.Count -eq $waitPatterns.Count) { break }
        if ($content -match "APPLICATION FAILED TO START") { break }
    }

    if ($seen.Count -lt $waitPatterns.Count) {
        Write-Host "  CANH BAO: chua thay het cac dong ket qua mong doi trong $timeoutSec giay." -ForegroundColor Yellow
        Write-Host "  Xem log day du tai: $logFile"
    }

    if (-not $proc.HasExited) {
        & taskkill /PID $proc.Id /T /F | Out-Null
        Start-Sleep -Seconds 2
    }
    Remove-Item $logFile, "$logFile.err" -ErrorAction SilentlyContinue
}

Write-Host "=== Finvera data refresh ===" -ForegroundColor Green

if (-not $SkipCrawl) {
    Write-Host ""
    Write-Host "== Buoc 1/4: Crawl gia + danh sach ma moi tu Vnstock ==" -ForegroundColor Cyan
    Push-Location $exportDir
    try {
        uv run --project ../provider-poc python export_instrument_reference.py
        uv run --project ../provider-poc python export_equity_profile.py
        uv run --project ../provider-poc python export_all_symbols.py --start 2024-01-01
    } finally {
        Pop-Location
    }
} else {
    Write-Host "Bo qua buoc crawl (-SkipCrawl)." -ForegroundColor Yellow
}

Import-EnvFile $envFile
Import-EnvFile $envRefreshFile

Set-StageFlags @("FINVERA_MARKET_IMPORT_INSTRUMENT_REFERENCE_ENABLED", "FINVERA_STOCK_IMPORT_EQUITY_PROFILE_ENABLED")
Invoke-BackendStage -Name "Buoc 2/4: Dang ky ma moi + ho so cong ty" `
    -WaitPatterns @("instrument_reference_import status=", "stock_import dataset=equity-profile total=") `
    -TimeoutSec 180

Set-StageFlags @("FINVERA_STOCK_IMPORT_DAILY_BAR_ENABLED", "FINVERA_STOCK_IMPORT_FUNDAMENTALS_ENABLED")
Invoke-BackendStage -Name "Buoc 3/4: Nap gia + bao cao tai chinh moi" `
    -WaitPatterns @("stock_import dataset=daily-bar total=", "stock_import dataset=fundamentals total=") `
    -TimeoutSec 1800

Set-StageFlags @("FINVERA_STOCK_TECHNICAL_WARMUP_ENABLED")
Invoke-BackendStage -Name "Buoc 4/4: Tinh bu chi bao ky thuat (MA/RSI/MACD...)" `
    -WaitPatterns @("technical_indicator_warmup total=") `
    -TimeoutSec 900

Write-Host ""
Write-Host "=== Xong. Gio khoi dong backend binh thuong (IntelliJ, hoac .\mvnw.cmd spring-boot:run trong finvera-be). ===" -ForegroundColor Green
