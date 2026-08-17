param(
    [int]$BatchSize = 25,
    [int]$RequestsPerMinute = 30,
    [string]$Start = "2024-01-01",
    [string]$End = "2026-08-17",
    [int]$MaxBatches = 100
)

$ErrorActionPreference = "Stop"
$summaryPath = Join-Path $PSScriptRoot "poc-output\vnstock-capability-summary.json"

for ($batch = 1; $batch -le $MaxBatches; $batch++) {
    Write-Host "Starting Vnstock batch $batch/$MaxBatches..."
    & uv run --project . python poc_vnstock.py --full-universe --max-symbols $BatchSize `
        --requests-per-minute $RequestsPerMinute --resume --skip-representative `
        --start $Start --end $End
    if ($LASTEXITCODE -ne 0) { throw "Vnstock batch failed with exit code $LASTEXITCODE." }
    if (-not (Test-Path -LiteralPath $summaryPath)) { throw "Sanitized summary was not written." }

    $full = (Get-Content -LiteralPath $summaryPath -Raw | ConvertFrom-Json).full_universe
    if ($full.status -ne "PASS") { throw "Full-universe probe status is $($full.status)." }
    if ([int]$full.failed_attempt_count -gt 0) { throw "Provider failures detected; stopping automatic retry." }
    Write-Host ("Processed {0}/{1}; remaining {2}; available {3}; insufficient {4}" -f `
        $full.processed_count, $full.candidate_count, $full.remaining_count,
        $full.successful_count, $full.unavailable_history_count)
    if ([int]$full.remaining_count -eq 0) { Write-Host "Checkpoint complete."; exit 0 }
}
throw "Reached MaxBatches=$MaxBatches before checkpoint completion."
