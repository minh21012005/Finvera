param(
    [int]$BatchSize = 50,
    [int]$RequestsPerMinute = 50,
    [string]$Start = "2024-01-01",
    [string]$End = "2026-08-17",
    [int]$MaxBatches = 200,
    [int]$MaxConsecutiveFailures = 5,
    [int]$RetryBackoffSeconds = 5,
    [switch]$FailOnUniverseMismatch
)

$ErrorActionPreference = "Stop"
$summaryPath = Join-Path $PSScriptRoot "poc-output\vnstock-capability-summary.json"
$checkpointPath = Join-Path $PSScriptRoot "poc-output\vnstock-full-universe-checkpoint.json"
$archiveDir = Join-Path $PSScriptRoot "poc-output\checkpoint-archive"
$universeResetCount = 0
$consecutiveFailures = 0
$lastProcessedCount = -1

Write-Host "=== Starting Vnstock Full Universe Auto-Runner ==="
Write-Host "Batch size: $BatchSize | Pace: $RequestsPerMinute req/min | Max batches: $MaxBatches"

for ($batch = 1; $batch -le $MaxBatches; $batch++) {
    Write-Host "`n--------------------------------------------------"
    Write-Host "Starting batch $batch/$MaxBatches..." -ForegroundColor Cyan

    & uv run --project . python poc_vnstock.py --full-universe --max-symbols $BatchSize `
        --requests-per-minute $RequestsPerMinute --resume --skip-representative `
        --start $Start --end $End
    $exitCode = $LASTEXITCODE

    if (-not (Test-Path -LiteralPath $summaryPath)) {
        throw "Sanitized summary was not written."
    }

    $summaryJson = Get-Content -LiteralPath $summaryPath -Raw | ConvertFrom-Json
    $full = $summaryJson.full_universe

    if ($exitCode -ne 0 -and $full.status -eq "FAIL" -and $full.reason -eq "CHECKPOINT_MISMATCH") {
        if ($FailOnUniverseMismatch) {
            throw "Universe fingerprint changed; checkpoint preserved and automatic reset was disabled."
        }
        if ($universeResetCount -ge 1) {
            throw "Universe fingerprint changed again after automatic reset; stopping for review."
        }
        if (Test-Path -LiteralPath $checkpointPath) {
            New-Item -ItemType Directory -Path $archiveDir -Force | Out-Null
            $archivePath = Join-Path $archiveDir ("vnstock-full-universe-checkpoint-{0}.json" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
            Move-Item -LiteralPath $checkpointPath -Destination $archivePath
            Write-Host "Universe changed; archived old checkpoint to $archivePath" -ForegroundColor Yellow
        }
        $universeResetCount++
        continue
    }

    $currentProcessed = [int]$full.processed_count
    $remaining = [int]$full.remaining_count
    $failedAttempts = [int]$full.failed_attempt_count

    Write-Host ("Status: Processed {0}/{1} ({2}%); Remaining: {3}; Available: {4}; Insufficient: {5}" -f `
        $full.processed_count, $full.candidate_count, `
        [math]::Round(($currentProcessed / [int]$full.candidate_count) * 100, 1), `
        $remaining, $full.successful_count, $full.unavailable_history_count) -ForegroundColor Green

    if ($remaining -eq 0) {
        Write-Host "`n==================================================" -ForegroundColor Green
        Write-Host "CHECKPOINT 100% COMPLETE! All symbols processed." -ForegroundColor Green
        Write-Host "==================================================" -ForegroundColor Green
        exit 0
    }

    # If this batch had failed requests (exit code 2 or failed attempts)
    if ($exitCode -ne 0 -or $failedAttempts -gt 0) {
        Write-Host ("Batch completed with {0} transient provider failures (ExitCode: {1})." -f $failedAttempts, $exitCode) -ForegroundColor Yellow
        if ($currentProcessed -eq $lastProcessedCount) {
            $consecutiveFailures++
            Write-Host "No new symbols processed in this attempt (Consecutive unprogressed: $consecutiveFailures/$MaxConsecutiveFailures)." -ForegroundColor Yellow
            if ($consecutiveFailures -ge $MaxConsecutiveFailures) {
                throw "Encountered $MaxConsecutiveFailures consecutive failures without progress. Provider may be rate-limiting or offline. Stopping."
            }
        } else {
            $consecutiveFailures = 0
        }
        Write-Host "Waiting $RetryBackoffSeconds seconds before auto-resuming next batch..." -ForegroundColor Gray
        Start-Sleep -Seconds $RetryBackoffSeconds
    } else {
        $consecutiveFailures = 0
    }

    $lastProcessedCount = $currentProcessed
}

throw "Reached MaxBatches=$MaxBatches before checkpoint completion. Run again to resume."
