<#
.SYNOPSIS
    Catches the local dev DB up after being away for a while: crawls fresh Vnstock prices,
    registers any newly-listed symbols, imports the new prices/fundamentals, and backfills the
    technical-indicator and valuation gaps -- all in one command.

.DESCRIPTION
    Runs the exact sequence documented in docs/runbooks/go-live-setup.md 3.7/6.3, automated:
      1. Crawl (export_all_symbols.py + the instrument-reference/equity-profile exporters).
         Fundamentals come from VCI statements (Feature 018 / ADR-0011); the first pass after the
         switch re-exports every symbol (~6 h at ~2.3 s per call, 6 calls per symbol).
      2. Restart the backend with instrument-reference import ON, wait for it to finish, stop it.
      3. Restart with equity-profile import ON, wait, stop. This is deliberately separate because
         ApplicationRunner ordering is not an implicit dependency guarantee.
      4. Restart with sector-reference import ON, after company profiles exist, wait, stop.
      5. Restart with market-overview index-history import ON, wait, stop.
      6. Restart with daily-bar + fundamentals import ON, wait, stop.
      7. Restart with market EOD breadth/regime reconciliation plus the technical-indicator
         and valuation warmups ON, wait, stop.
    Every import here is safe/idempotent (only adds missing rows or backfills gaps), so this is
    safe to run after a 3-day gap, a 7-day gap, or any length of time.

    The sector-reference snapshot changes rarely, but is still imported on every refresh. This
    makes a freshly-created local database complete in one command and is idempotent when the
    classification package has not changed. All overrides here are applied to THIS PowerShell
    process only, from finvera-be\.env.refresh (flags only, no secrets) layered on top
    of finvera-be\.env -- the real .env file on disk is never modified, so IntelliJ's own run
    configuration is unaffected.

    After this script finishes, start the backend normally (IntelliJ, or `.\mvnw.cmd
    spring-boot:run` in finvera-be) -- this script does not leave it running.

.PARAMETER SkipCrawl
    Skip the Python crawl step (use this if you already have fresh JSON files in
    tools/market-data/vnstock-export/output and just need to re-import them).

.PARAMETER FullRefresh
    Re-fetch the full configured history range for market indices and equity daily bars.
    Use this occasionally, for example monthly, to catch older provider corrections.

.PARAMETER LookbackDays
    Incremental refresh window. On normal runs, re-fetch this many days before the
    latest existing package/checkpoint and merge with older local files.

.PARAMETER Cleanup
    Run conservative retention cleanup after step 7. This removes old audit rows,
    stale live observations, and non-current derived revisions only; it does not
    delete current historical prices, index snapshots, instruments, profiles, sectors,
    or fundamentals.

.PARAMETER CleanupOnly
    Run only the conservative retention cleanup stage, without crawl/import/warmup.

.PARAMETER ForceWarmup
    Valuation warmup recomputes every instrument, even those already assessed today with
    unchanged inputs. Use once after a calculator/rule fix that kept its rule version
    (the warmup otherwise skips them). Combine with -WarmupOnly.

.PARAMETER WarmupOnly
    Run only step 7 (breadth/regime reconciliation + technical + valuation warmup) on the
    data already in the database -- e.g. after a rule-version change (valuation-v2) when
    nothing new has to be crawled or imported. ~20-40 minutes instead of ~1.5 hours.

.EXAMPLE
    .\refresh-data.ps1
#>
param(
    [switch]$SkipCrawl,
    [switch]$FullRefresh,
    [switch]$Cleanup,
    [switch]$CleanupOnly,
    [switch]$WarmupOnly,
    [switch]$ForceWarmup,
    [int]$LookbackDays = 90
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$beDir = Join-Path $root "finvera-be"
$exportDir = Join-Path $root "tools\market-data\vnstock-export"
$envFile = Join-Path $beDir ".env"
$envRefreshFile = Join-Path $beDir ".env.refresh"
# Q-37 (2026-08-30): 2023-01-01, not 2024-01-01 -- valuation-v1's own-history basis needs 500
# sessions with a visible report, and the provider's four fiscal years make FY2022 visible from
# ~Mar 2023, so this is the earliest start that adds usable history. The exporters detect the
# earlier start (package rangeStart) and re-fetch the whole range once.
$historyStartDate = "2023-01-01"
$historyEndDate = (Get-Date).ToString("yyyy-MM-dd")

$ManagedRuntimeFlags = @(
    "FINVERA_MARKET_IMPORT_ENABLED",
    "FINVERA_MARKET_EOD_RECONCILIATION_ENABLED",
    "FINVERA_MARKET_IMPORT_INSTRUMENT_REFERENCE_ENABLED",
    "FINVERA_STOCK_IMPORT_EQUITY_PROFILE_ENABLED",
    "FINVERA_STOCK_IMPORT_DAILY_BAR_ENABLED",
    "FINVERA_STOCK_IMPORT_FUNDAMENTALS_ENABLED",
    "FINVERA_STOCK_IMPORT_SECTOR_REFERENCE_ENABLED",
    "FINVERA_STOCK_TECHNICAL_WARMUP_ENABLED",
    "FINVERA_STOCK_VALUATION_WARMUP_ENABLED",
    "FINVERA_STOCK_VALUATION_WARMUP_FORCE",
    "FINVERA_STOCK_SECTOR_BASIS_ENABLED",
    "FINVERA_DATA_RETENTION_CLEANUP_ENABLED",
    "FINVERA_TCBS_LIVE_ENABLED",
    "FINVERA_STOCK_QUOTE_LIVE_ENABLED"
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

$WarmupStageFlags = @("FINVERA_MARKET_EOD_RECONCILIATION_ENABLED", "FINVERA_STOCK_TECHNICAL_WARMUP_ENABLED", "FINVERA_STOCK_VALUATION_WARMUP_ENABLED")
if ($ForceWarmup) { $WarmupStageFlags += "FINVERA_STOCK_VALUATION_WARMUP_FORCE" }

function Set-StageFlags([string[]]$enabledKeys) {
    foreach ($key in $ManagedRuntimeFlags) {
        $value = if ($enabledKeys -contains $key) { "true" } else { "false" }
        [Environment]::SetEnvironmentVariable($key, $value, "Process")
    }
}

function Assert-NativeSuccess([string]$operation) {
    if ($LASTEXITCODE -ne 0) {
        throw "$operation failed with exit code $LASTEXITCODE."
    }
}

function Get-MarketOverviewPackage([string]$outputDir) {
    $target = Join-Path $outputDir "market-overview.json"
    if (Test-Path -LiteralPath $target) { return $target }
    return $target
}

function Get-LegacyLatestMarketOverviewPackage([string]$outputDir, [string]$startDate) {
    $latest = Get-ChildItem -LiteralPath $outputDir -Filter "market-overview-$startDate-*.json" -File -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        Select-Object -First 1
    if ($latest) { return $latest.FullName }
    return $null
}

function Get-SectorReferencePackage([string]$configuredPath, [string]$outputDir) {
    if (-not [string]::IsNullOrWhiteSpace($configuredPath) -and (Test-Path -LiteralPath $configuredPath)) {
        return $configuredPath
    }

    $latest = Get-ChildItem -LiteralPath $outputDir -Filter "sector-reference-*.json" -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
    if ($latest) { return $latest.FullName }

    throw "Sector-reference package not found. Set FINVERA_STOCK_IMPORT_SECTOR_REFERENCE_PACKAGE_PATH in finvera-be\\.env or generate it with export_sector_reference.py before refresh."
}

function Invoke-BackendStage([string]$name, [string[]]$waitPatterns, [int]$timeoutSec) {
    Write-Host ""
    Write-Host "== $name ==" -ForegroundColor Cyan
    $stdoutLog = [System.IO.Path]::GetTempFileName()
    $stderrLog = "$stdoutLog.err"
    $proc = $null
    $succeeded = $false
    try {
        $proc = Start-Process -FilePath (Join-Path $beDir "mvnw.cmd") -ArgumentList "-q", "spring-boot:run" `
            -WorkingDirectory $beDir -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog `
            -PassThru -WindowStyle Hidden

        $deadline = (Get-Date).AddSeconds($timeoutSec)
        $seen = New-Object System.Collections.Generic.HashSet[string]
        while ((Get-Date) -lt $deadline -and -not $proc.HasExited) {
            Start-Sleep -Seconds 3
            $content = @(
                Get-Content $stdoutLog -ErrorAction SilentlyContinue
                Get-Content $stderrLog -ErrorAction SilentlyContinue
            )
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
            throw "$name did not produce every required completion marker within $timeoutSec seconds."
        }

        $succeeded = $true
    } finally {
        if ($null -ne $proc -and -not $proc.HasExited) {
            & taskkill /PID $proc.Id /T /F | Out-Null
            Start-Sleep -Seconds 2
        }
        if ($succeeded) {
            Remove-Item -LiteralPath $stdoutLog, $stderrLog -ErrorAction SilentlyContinue
        } else {
            Write-Host "  Stage failed. Logs retained:" -ForegroundColor Red
            Write-Host "  stdout: $stdoutLog"
            Write-Host "  stderr: $stderrLog"
        }
    }
}

Write-Host "=== Finvera data refresh ===" -ForegroundColor Green

$listener = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($listener) {
    throw "Port 8080 is already in use. Stop the normally running backend before refresh-data.ps1."
}

if ($WarmupOnly) {
    Import-EnvFile $envFile
    Import-EnvFile $envRefreshFile
    Set-StageFlags $WarmupStageFlags
    Invoke-BackendStage -Name "Warmup-only: Tinh breadth/regime + bu chi bao ky thuat + dinh gia" `
        -WaitPatterns @("market_eod_reconciliation status=", "technical_indicator_warmup total=", "valuation_warmup total=") `
        -TimeoutSec 7200
    Write-Host ""
    Write-Host "=== Xong warmup. Gio khoi dong backend binh thuong. ===" -ForegroundColor Green
    return
}

if ($CleanupOnly) {
    Import-EnvFile $envFile
    Import-EnvFile $envRefreshFile
    Set-StageFlags @("FINVERA_DATA_RETENTION_CLEANUP_ENABLED")
    Invoke-BackendStage -Name "Don dep retention: audit + revision cu khong con dung" `
        -WaitPatterns @("data_retention_cleanup total_deleted=") `
        -TimeoutSec 300
    Write-Host ""
    Write-Host "=== Xong cleanup. Gio khoi dong backend binh thuong (IntelliJ, hoac .\mvnw.cmd spring-boot:run trong finvera-be). ===" -ForegroundColor Green
    return
}

if (-not $SkipCrawl) {
    Write-Host ""
    Write-Host "== Buoc 1/7: Crawl gia + danh sach ma moi + index history tu Vnstock ==" -ForegroundColor Cyan
    Push-Location $exportDir
    try {
        uv run --project ../provider-poc python export_instrument_reference.py
        Assert-NativeSuccess "Instrument-reference export"
        $profileArgs = @("run", "--project", "../provider-poc", "python", "export_equity_profile.py")
        if ($FullRefresh) { $profileArgs += "--full-refresh" }
        & uv @profileArgs
        Assert-NativeSuccess "Equity-profile export"
        $marketOverviewArgs = @(
            "run", "--project", "../provider-poc", "python", "export_history.py",
            "--market-overview", "--start", $historyStartDate, "--end", $historyEndDate,
            "--lookback-days", "$LookbackDays"
        )
        if ($FullRefresh) { $marketOverviewArgs += "--full-refresh" }
        & uv @marketOverviewArgs
        Assert-NativeSuccess "Market-overview index export"
        $allSymbolsArgs = @(
            "run", "--project", "../provider-poc", "python", "export_all_symbols.py",
            "--start", $historyStartDate, "--lookback-days", "$LookbackDays"
        )
        if ($FullRefresh) { $allSymbolsArgs += "--full-refresh" }
        & uv @allSymbolsArgs
        Assert-NativeSuccess "Daily-bar/fundamentals export"
    } finally {
        Pop-Location
    }
} else {
    Write-Host "Bo qua buoc crawl (-SkipCrawl)." -ForegroundColor Yellow
}

$marketOverviewPackage = Get-MarketOverviewPackage (Join-Path $exportDir "output")
if (-not (Test-Path -LiteralPath $marketOverviewPackage)) {
    $legacyMarketOverviewPackage = Get-LegacyLatestMarketOverviewPackage (Join-Path $exportDir "output") $historyStartDate
    if ($legacyMarketOverviewPackage) {
        $marketOverviewPackage = $legacyMarketOverviewPackage
    } else {
        throw "Market-overview package not found: $marketOverviewPackage. Run without -SkipCrawl or regenerate it with export_history.py --market-overview."
    }
}

Import-EnvFile $envFile
Import-EnvFile $envRefreshFile
[Environment]::SetEnvironmentVariable("FINVERA_MARKET_PROVIDER_MODE", "vnstock-package-private", "Process")
[Environment]::SetEnvironmentVariable("FINVERA_MARKET_FIXTURE_BOOTSTRAP_ENABLED", "false", "Process")
[Environment]::SetEnvironmentVariable("FINVERA_MARKET_IMPORT_PACKAGE_PATH", $marketOverviewPackage, "Process")
$sectorReferencePackage = Get-SectorReferencePackage `
    $env:FINVERA_STOCK_IMPORT_SECTOR_REFERENCE_PACKAGE_PATH `
    (Join-Path $exportDir "output")
[Environment]::SetEnvironmentVariable("FINVERA_STOCK_IMPORT_SECTOR_REFERENCE_PACKAGE_PATH", $sectorReferencePackage, "Process")

Set-StageFlags @("FINVERA_MARKET_IMPORT_INSTRUMENT_REFERENCE_ENABLED")
Invoke-BackendStage -Name "Buoc 2/7: Dang ky ma moi" `
    -WaitPatterns @("instrument_reference_import status=") `
    -TimeoutSec 180

Set-StageFlags @("FINVERA_STOCK_IMPORT_EQUITY_PROFILE_ENABLED")
Invoke-BackendStage -Name "Buoc 3/7: Nap ho so cong ty" `
    -WaitPatterns @("stock_import dataset=equity-profile total=") `
    -TimeoutSec 300

Set-StageFlags @("FINVERA_STOCK_IMPORT_SECTOR_REFERENCE_ENABLED")
Invoke-BackendStage -Name "Buoc 4/7: Nap phan loai nganh" `
    -WaitPatterns @("stock_import dataset=sector-reference total=") `
    -TimeoutSec 300

Set-StageFlags @("FINVERA_MARKET_IMPORT_ENABLED")
Invoke-BackendStage -Name "Buoc 5/7: Nap lich su chi so thi truong" `
    -WaitPatterns @("market_import status=") `
    -TimeoutSec 300

Set-StageFlags @("FINVERA_STOCK_IMPORT_DAILY_BAR_ENABLED", "FINVERA_STOCK_IMPORT_FUNDAMENTALS_ENABLED")
Invoke-BackendStage -Name "Buoc 6/7: Nap gia + bao cao tai chinh moi" `
    -WaitPatterns @("stock_import dataset=daily-bar total=", "stock_import dataset=fundamentals total=", "fundamental_source_retirement source=") `
    -TimeoutSec 7200

Set-StageFlags $WarmupStageFlags
# Sector-basis valuation is disabled during bulk warmup (ManagedRuntimeFlags sets it to false)
# so ~1600 symbols don't each re-query ~83 peers. Sector percentiles are evaluated on-demand on page view.
Invoke-BackendStage -Name "Buoc 7/7: Tinh breadth/regime + bu chi bao ky thuat + dinh gia" `
    -WaitPatterns @("market_eod_reconciliation status=", "technical_indicator_warmup total=", "valuation_warmup total=") `
    -TimeoutSec 7200

if ($Cleanup) {
    Set-StageFlags @("FINVERA_DATA_RETENTION_CLEANUP_ENABLED")
    Invoke-BackendStage -Name "Don dep retention: audit + revision cu khong con dung" `
        -WaitPatterns @("data_retention_cleanup total_deleted=") `
        -TimeoutSec 300
}

Write-Host ""
Write-Host "=== Xong. Gio khoi dong backend binh thuong (IntelliJ, hoac .\mvnw.cmd spring-boot:run trong finvera-be). ===" -ForegroundColor Green
