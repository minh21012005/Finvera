param(
    [string]$QuoteSymbols = "VNM,TCB,HPG",
    [ValidateSet("totp", "email-sms")]
    [string]$OtpMethod = "totp",
    [int]$MaxAttempts = 3
)

# Owner-run probe for research.md R-012 gate G-03 (specs/002-stock-detail-analysis).
# Wraps poc_tcbs.py so you only have to answer the API key / OTP prompts and watch
# the result - it does NOT and CANNOT script the OTP itself (by design: TCBS's own
# safety model requires a human to read and type a live code). Each retry means
# typing a fresh OTP again; that is expected, not a bug.

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not (Test-Path -LiteralPath ".venv")) {
    Write-Host "No .venv found - running 'uv sync' first..." -ForegroundColor Yellow
    uv sync
}

$summaryPath = Join-Path $PSScriptRoot "poc-output\tcbs-capability-summary.json"

Write-Host "=== TCBS G-03 per-stock quote probe ===" -ForegroundColor Cyan
Write-Host "Symbols: $QuoteSymbols | OTP method: $OtpMethod | Max attempts: $MaxAttempts"
Write-Host "You will be prompted for your TCBS API key and a fresh OTP on every attempt."
Write-Host ""

for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
    Write-Host "`n--- Attempt $attempt/$MaxAttempts ---" -ForegroundColor Cyan
    & uv run python poc_tcbs.py --otp-method $OtpMethod --quote-symbols $QuoteSymbols --rate-probe-requests 3
    $exitCode = $LASTEXITCODE

    # $exitCode reflects the ORIGINAL index/websocket gate too, which G-03 doesn't need - check
    # the quote-symbols result specifically so a WS timeout (e.g. outside trading hours) doesn't
    # cause an unnecessary retry when the part we actually need already succeeded.
    $quoteStatus = $null
    $summary = $null
    if (Test-Path -LiteralPath $summaryPath) {
        try {
            $summary = Get-Content -LiteralPath $summaryPath -Raw | ConvertFrom-Json
            $quoteStatus = $summary.rest.ticker_commons_quote_symbols.status
        } catch {
            Write-Host "(Could not parse the summary file; check it manually.)" -ForegroundColor Yellow
        }
    }

    if ($quoteStatus -eq "PASS") {
        Write-Host "`n=== SUCCESS (quote probe passed) ===" -ForegroundColor Green
        if ($exitCode -ne 0) {
            Write-Host "(Note: the unrelated index/websocket part of this script reported a" -ForegroundColor DarkGray
            Write-Host "non-zero exit code - that's fine, it isn't part of G-03.)" -ForegroundColor DarkGray
        }
        Write-Host "Sanitized summary written to: $summaryPath"
        Write-Host "Nothing secret is in that file (no key/OTP/token/raw price) - safe to hand off."
        Write-Host "Tell Claude the probe succeeded; it will read the summary itself and close gate G-03."
        exit 0
    }

    Write-Host "`nAttempt $attempt`: quote-symbols probe did not pass (status: $quoteStatus)." -ForegroundColor Yellow
    if ($summary -and $summary.authentication) {
        Write-Host "Reported reason: step=$($summary.authentication.step) reason=$($summary.authentication.reason)" -ForegroundColor Yellow
    }

    if ($attempt -lt $MaxAttempts) {
        $again = Read-Host "Retry with a fresh OTP? (Y/n)"
        if ($again -eq "n" -or $again -eq "N") {
            Write-Host "Stopped at your request." -ForegroundColor Yellow
            exit 1
        }
    }
}

Write-Host "`n=== Did not succeed after $MaxAttempts attempts ===" -ForegroundColor Red
Write-Host "Common causes: wrong/expired OTP, market closed (rate probe still runs but review timing),"
Write-Host "or no TCBS OpenAPI access on this account yet. See README.md for details, or share the"
Write-Host "summary file's non-secret 'authentication'/'rest' sections with Claude for help diagnosing."
exit 1
