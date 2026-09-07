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

    Feature 026 (unattended running): every provider call in step 1 retries a dropped connection
    instead of ending the run; each backend stage is retried up to 3 times and fails within 15
    minutes of going silent rather than waiting out its full timeout; and completed stages are
    recorded in tools/market-data/vnstock-export/output/refresh-state.json, so re-running the same
    command continues where it stopped. That state is only reused when the parameters match and it
    is under 12 hours old -- otherwise the run starts clean and says so. It is deleted on success.

    The sector-reference package (VCI ICB level 3, Feature 020) is re-exported and imported on every refresh. This
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
# Feature 022 (2026-08-31): 2019-01-01 -- the own-history valuation percentile needs a full
# market cycle (2020 crash, 2021 bubble, 2022 bear) to mean anything; VCI serves ~8 rolling years
# in the same single call, so the deeper window costs no extra requests. 2019 (not the 2018 window
# edge) so there is ~4 months of headroom before the rolling window passes this start; after
# that a re-crawl just begins at the window edge and previously imported rows remain.
$historyStartDate = "2019-01-01"
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
    # Feature 020 / ADR-0012: the sector scheme is VCI ICB level 3. The package step 1 just exported
    # is the default; an explicit override is honoured only when it is itself a VCI package, so a
    # stale pin to the retired KBS file cannot silently keep 820 UPCoM instruments without a sector.
    $vci = Get-ChildItem -LiteralPath $outputDir -Filter "sector-reference-vci-*.json" -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
    if (-not [string]::IsNullOrWhiteSpace($configuredPath) -and (Test-Path -LiteralPath $configuredPath)) {
        if ((Split-Path -Leaf $configuredPath) -like "sector-reference-vci-*") { return $configuredPath }
        if ($vci) {
            Write-Warning "FINVERA_STOCK_IMPORT_SECTOR_REFERENCE_PACKAGE_PATH points to '$configuredPath' (retired KBS scheme); importing the VCI ICB package '$($vci.FullName)' instead. Remove the override from finvera-be\.env to silence this."
            return $vci.FullName
        }
        return $configuredPath
    }
    if ($vci) { return $vci.FullName }

    $latest = Get-ChildItem -LiteralPath $outputDir -Filter "sector-reference-*.json" -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
    if ($latest) { return $latest.FullName }

    throw "Sector-reference package not found. Set FINVERA_STOCK_IMPORT_SECTOR_REFERENCE_PACKAGE_PATH in finvera-be\\.env or generate it with export_sector_reference_vci.py before refresh."
}

# ── Stage state, so a broken run picks itself up instead of starting over (Feature 026) ──────
# The import stages are idempotent, so re-running one is only ever slow, never harmful. The real
# hazard is resuming into STALE state: skipping "already done" stages a week later would serve old
# data as new. Resume therefore requires the same parameters and a state file younger than
# $ResumeWindowHours, and every skip (and every discard) is printed rather than implied.
$StateFile = Join-Path (Join-Path $exportDir "output") "refresh-state.json"
$ResumeWindowHours = 12
$StageAttempts = 3
$StageStallSeconds = 900   # 15 minutes with no new backend output means stuck, not slow

function Get-RunKey() {
    $parts = @(
        "full=$([bool]$FullRefresh)", "skipCrawl=$([bool]$SkipCrawl)", "cleanup=$([bool]$Cleanup)",
        "forceWarmup=$([bool]$ForceWarmup)", "lookback=$LookbackDays",
        "start=$historyStartDate", "end=$historyEndDate"
    )
    return ($parts -join ";")
}

function Read-RefreshState() {
    $fresh = [pscustomobject]@{ runKey = (Get-RunKey); updatedAt = (Get-Date).ToString("o"); completed = @() }
    if (-not (Test-Path -LiteralPath $StateFile)) { return $fresh }
    try {
        $state = Get-Content -LiteralPath $StateFile -Raw | ConvertFrom-Json
    } catch {
        Write-Host "  Trang thai refresh khong doc duoc; bat dau lai tu dau." -ForegroundColor Yellow
        return $fresh
    }
    if ($state.runKey -ne (Get-RunKey)) {
        Write-Host "  Tham so lan chay khac lan truoc; bo trang thai cu, chay lai tu dau." -ForegroundColor Yellow
        return $fresh
    }
    $age = (Get-Date) - [datetime]::Parse($state.updatedAt)
    if ($age.TotalHours -gt $ResumeWindowHours) {
        Write-Host ("  Trang thai cu {0:N1} gio (> {1}h); bo qua, chay lai tu dau." -f $age.TotalHours, $ResumeWindowHours) -ForegroundColor Yellow
        return $fresh
    }
    Write-Host ("  Tiep tuc lan chay truoc ({0:N1} gio truoc); da xong: {1}" -f $age.TotalHours, ($state.completed -join ", ")) -ForegroundColor Yellow
    return $state
}

function Save-RefreshState($state) {
    $state.updatedAt = (Get-Date).ToString("o")
    $dir = Split-Path -Parent $StateFile
    if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    $state | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $StateFile -Encoding utf8
}

function Complete-Stage($state, [string]$key) {
    if ($state.completed -notcontains $key) {
        $state.completed = @($state.completed) + $key
    }
    Save-RefreshState $state
}

function Wait-PortFree([int]$port, [int]$timeoutSec = 60) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (-not (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)) { return $true }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Invoke-BackendStage([string]$name, [string[]]$waitPatterns, [int]$timeoutSec, [string]$stageKey) {
    if ($stageKey -and $script:RefreshState.completed -contains $stageKey) {
        Write-Host ""
        Write-Host "== $name == (bo qua: da xong o lan chay truoc)" -ForegroundColor DarkGray
        return
    }

    for ($attempt = 1; $attempt -le $StageAttempts; $attempt++) {
        Write-Host ""
        if ($attempt -eq 1) {
            Write-Host "== $name ==" -ForegroundColor Cyan
        } else {
            Write-Host "== $name == (thu lai lan $attempt/$StageAttempts)" -ForegroundColor Yellow
        }

        $failure = Invoke-BackendStageAttempt -name $name -waitPatterns $waitPatterns -timeoutSec $timeoutSec
        if (-not $failure) {
            if ($stageKey) { Complete-Stage $script:RefreshState $stageKey }
            return
        }

        Write-Host "  $failure" -ForegroundColor Red
        if ($attempt -eq $StageAttempts) {
            throw "$name failed after $StageAttempts attempts. $failure"
        }
        if (-not (Wait-PortFree 8080 90)) {
            throw "${name}: cong 8080 van bi giu sau khi dung stage; khong the thu lai an toan."
        }
        Start-Sleep -Seconds 5
    }
}

function Invoke-BackendStageAttempt([string]$name, [string[]]$waitPatterns, [int]$timeoutSec) {
    # Returns $null on success, or a message describing the failure. Never throws for an expected
    # failure, so the caller can retry.
    $stdoutLog = [System.IO.Path]::GetTempFileName()
    $stderrLog = "$stdoutLog.err"
    New-Item -ItemType File -Path $stderrLog -Force | Out-Null
    $proc = $null
    $readers = @()
    $failure = $null
    try {
        $proc = Start-Process -FilePath (Join-Path $beDir "mvnw.cmd") -ArgumentList "-q", "spring-boot:run" `
            -WorkingDirectory $beDir -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog `
            -PassThru -WindowStyle Hidden

        # NFR-001: follow from a byte offset. The previous version re-read the whole log every three
        # seconds, which over a six-hour stage costs time quadratic in the log size and loads the
        # machine exactly when the heaviest import is running.
        foreach ($path in @($stdoutLog, $stderrLog)) {
            $stream = [System.IO.File]::Open($path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
            $readers += (New-Object System.IO.StreamReader($stream))
        }

        $deadline = (Get-Date).AddSeconds($timeoutSec)
        $lastOutput = Get-Date
        $seen = New-Object System.Collections.Generic.HashSet[string]
        $appFailed = $false

        while ((Get-Date) -lt $deadline -and -not $proc.HasExited) {
            Start-Sleep -Seconds 3
            $sawSomething = $false
            foreach ($reader in $readers) {
                while ($true) {
                    $line = $reader.ReadLine()
                    if ($null -eq $line) { break }
                    $sawSomething = $true
                    if ($line -like "*APPLICATION FAILED TO START*") { $appFailed = $true }
                    foreach ($pattern in $waitPatterns) {
                        if ($seen.Contains($pattern)) { continue }
                        if ($line -like "*$pattern*") {
                            Write-Host "  $($line.Trim())"
                            $seen.Add($pattern) | Out-Null
                        }
                    }
                }
            }
            if ($sawSomething) { $lastOutput = Get-Date }
            if ($seen.Count -eq $waitPatterns.Count) { break }
            if ($appFailed) { break }
            # FR-005: a backend that is alive but silent is stuck. Failing here costs minutes; the
            # old code waited out the full timeout, which for stage 6a is six hours of nothing.
            if (((Get-Date) - $lastOutput).TotalSeconds -gt $StageStallSeconds) {
                $failure = "khong co output nao trong $([int]($StageStallSeconds / 60)) phut - stage bi treo."
                break
            }
        }

        if (-not $failure) {
            if ($appFailed) {
                $failure = "backend bao APPLICATION FAILED TO START."
            } elseif ($proc.HasExited -and $seen.Count -lt $waitPatterns.Count) {
                # A Process from Start-Process -PassThru does not always surface ExitCode; say so
                # rather than printing "exit ()", which reads like a bug in this script.
                $code = "khong ro"
                try {
                    $proc.WaitForExit()
                    if ($null -ne $proc.ExitCode) { $code = $proc.ExitCode }
                } catch { }
                $failure = "backend thoat som (exit $code) truoc khi xong; xem log ben duoi."
            } elseif ($seen.Count -lt $waitPatterns.Count) {
                $failure = "khong thay du marker hoan thanh trong $timeoutSec giay."
            }
        }
    } catch {
        $failure = "loi khi chay stage: $($_.Exception.Message)"
    } finally {
        foreach ($reader in $readers) { $reader.Dispose() }
        if ($null -ne $proc -and -not $proc.HasExited) {
            & taskkill /PID $proc.Id /T /F | Out-Null
            Start-Sleep -Seconds 2
        }
        if (-not $failure) {
            Remove-Item -LiteralPath $stdoutLog, $stderrLog -ErrorAction SilentlyContinue
        } else {
            Write-Host "  Logs giu lai de xem:" -ForegroundColor Red
            Write-Host "  stdout: $stdoutLog"
            Write-Host "  stderr: $stderrLog"
        }
    }
    return $failure
}

Write-Host "=== Finvera data refresh ===" -ForegroundColor Green

$listener = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($listener) {
    throw "Port 8080 is already in use. Stop the normally running backend before refresh-data.ps1. (Neu mot lan refresh truoc bi Ctrl+C, tien trinh mvnw co the con song: dong no roi chay lai - trang thai da xong se duoc bo qua.)"
}

$script:RefreshState = Read-RefreshState

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

if ((-not $SkipCrawl) -and ($script:RefreshState.completed -contains "1-crawl")) {
    Write-Host ""
    Write-Host "== Buoc 1/7: Crawl == (bo qua: da xong o lan chay truoc)" -ForegroundColor DarkGray
} elseif (-not $SkipCrawl) {
    Write-Host ""
    Write-Host "== Buoc 1/7: Crawl gia + danh sach ma moi + index history tu Vnstock ==" -ForegroundColor Cyan
    Push-Location $exportDir
    try {
        uv run --project ../provider-poc python export_instrument_reference.py
        Assert-NativeSuccess "Instrument-reference export"
        # Feature 020 / ADR-0012: sector reference from VCI ICB level 3 (all exchanges, 3 calls).
        uv run --project ../provider-poc python export_sector_reference_vci.py
        Assert-NativeSuccess "Sector-reference (VCI ICB) export"
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
        Complete-Stage $script:RefreshState "1-crawl"
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
    -TimeoutSec 180 `
    -StageKey "2-instrument-reference"

Set-StageFlags @("FINVERA_STOCK_IMPORT_EQUITY_PROFILE_ENABLED")
Invoke-BackendStage -Name "Buoc 3/7: Nap ho so cong ty" `
    -WaitPatterns @("stock_import dataset=equity-profile total=") `
    -TimeoutSec 300 `
    -StageKey "3-equity-profile"

Set-StageFlags @("FINVERA_STOCK_IMPORT_SECTOR_REFERENCE_ENABLED")
Invoke-BackendStage -Name "Buoc 4/7: Nap phan loai nganh" `
    -WaitPatterns @("stock_import dataset=sector-reference total=") `
    -TimeoutSec 300 `
    -StageKey "4-sector-reference"

Set-StageFlags @("FINVERA_MARKET_IMPORT_ENABLED")
# Feature 022 deepened index history to 2019-01-01. The first VCI import now
# persists roughly 7.6k index snapshots and can run a little over 5 minutes
# on a local PostgreSQL instance before emitting its completion marker.
Invoke-BackendStage -Name "Buoc 5/7: Nap lich su chi so thi truong" `
    -WaitPatterns @("market_import status=") `
    -TimeoutSec 1800 `
    -StageKey "5-market-index"

Set-StageFlags @("FINVERA_STOCK_IMPORT_DAILY_BAR_ENABLED")
Invoke-BackendStage -Name "Buoc 6a/7: Nap gia moi" `
    -WaitPatterns @("stock_import dataset=daily-bar total=", "daily_bar_source_retirement primary=") `
    -TimeoutSec 21600 `
    -StageKey "6a-daily-bars"

Set-StageFlags @("FINVERA_STOCK_IMPORT_FUNDAMENTALS_ENABLED")
Invoke-BackendStage -Name "Buoc 6b/7: Nap bao cao tai chinh moi" `
    -WaitPatterns @("stock_import dataset=fundamentals total=", "fundamental_source_retirement source=") `
    -TimeoutSec 7200 `
    -StageKey "6b-fundamentals"

Set-StageFlags $WarmupStageFlags
# Sector-basis valuation is disabled during bulk warmup (ManagedRuntimeFlags sets it to false)
# so ~1600 symbols don't each re-query ~83 peers. Sector percentiles are evaluated on-demand on page view.
Invoke-BackendStage -Name "Buoc 7/7: Tinh breadth/regime + bu chi bao ky thuat + dinh gia" `
    -WaitPatterns @("market_eod_reconciliation status=", "technical_indicator_warmup total=", "valuation_warmup total=") `
    -TimeoutSec 7200 `
    -StageKey "7-warmup"

if ($Cleanup) {
    Set-StageFlags @("FINVERA_DATA_RETENTION_CLEANUP_ENABLED")
    Invoke-BackendStage -Name "Don dep retention: audit + revision cu khong con dung" `
        -WaitPatterns @("data_retention_cleanup total_deleted=") `
        -TimeoutSec 300
}

Remove-Item -LiteralPath $StateFile -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "=== Xong. Gio khoi dong backend binh thuong (IntelliJ, hoac .\mvnw.cmd spring-boot:run trong finvera-be). ===" -ForegroundColor Green
