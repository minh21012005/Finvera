param(
    [int]$BatchSize = 25,
    [int]$RequestsPerMinute = 30,
    [string]$Start = "2024-01-01",
    [string]$End = "2026-08-17",
    [int]$MaxBatches = 100,
    [switch]$FailOnUniverseMismatch
)

$ErrorActionPreference = "Stop"
$summaryPath = Join-Path $PSScriptRoot "poc-output\vnstock-capability-summary.json"
$checkpointPath = Join-Path $PSScriptRoot "poc-output\vnstock-full-universe-checkpoint.json"
$archiveDir = Join-Path $PSScriptRoot "poc-output\checkpoint-archive"
$universeResetCount = 0

for ($batch = 1; $batch -le $MaxBatches; $batch++) {
    Write-Host "Starting Vnstock batch $batch/$MaxBatches..."
    & uv run --project . python poc_vnstock.py --full-universe --max-symbols $BatchSize `
        --requests-per-minute $RequestsPerMinute --resume --skip-representative `
        --start $Start --end $End
    if (-not (Test-Path -LiteralPath $summaryPath)) { throw "Sanitized summary was not written." }

    $full = (Get-Content -LiteralPath $summaryPath -Raw | ConvertFrom-Json).full_universe
    if ($LASTEXITCODE -ne 0 -and $full.status -eq "FAIL" -and $full.reason -eq "CHECKPOINT_MISMATCH") {
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
            Write-Host "Universe changed; archived old checkpoint to $archivePath"
        }
        $universeResetCount++
        continue
    }
    if ($LASTEXITCODE -ne 0) { throw "Vnstock batch failed with exit code $LASTEXITCODE." }
    if ($full.status -ne "PASS") { throw "Full-universe probe status is $($full.status)." }
    if ([int]$full.failed_attempt_count -gt 0) { throw "Provider failures detected; stopping automatic retry." }
    Write-Host ("Processed {0}/{1}; remaining {2}; available {3}; insufficient {4}" -f `
        $full.processed_count, $full.candidate_count, $full.remaining_count,
        $full.successful_count, $full.unavailable_history_count)
    if ([int]$full.remaining_count -eq 0) { Write-Host "Checkpoint complete."; exit 0 }
}
throw "Reached MaxBatches=$MaxBatches before checkpoint completion."
