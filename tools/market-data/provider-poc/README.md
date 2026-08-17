# Finvera Market Provider POC

This directory contains non-production probes used to pass the provider gates
for `001-market-overview`. It is not a runtime service or production adapter.

## Safety

- Never pass a TCBS API key, iOTP, or token on the command line.
- The TCBS probe prompts with hidden input unless `TCBS_API_KEY` is already set
  in the local process environment. iOTP is always prompted and never read from
  an environment variable.
- Output contains field names, types, counts, dates, and capability results—not
  tokens, credentials, raw responses, or market values.
- The Vnstock probe suppresses the package's unrelated import-time generation
  of editor/AI-agent instruction files. The probe must not alter repository or
  user-profile agent configuration.
- `poc-output/` is gitignored and must not be committed or shared unchanged.

## Setup

```powershell
cd tools/market-data/provider-poc
uv sync
```

## Vnstock history probe

```powershell
uv run python poc_vnstock.py
```

The probe checks index history and representative HOSE/HNX/UPCOM equities. It
also inspects the reference universe when the pinned Vnstock API supports it.
Passing requires at least 271 completed VN-Index sessions and usable OHLCV
columns. Full-universe coverage is a separate gated POC; the bootstrap importer
remains blocked until T047 is fully approved.

### Bounded full-universe coverage probe

After the representative probe succeeds, use an explicit bounded batch first:

```powershell
uv run python poc_vnstock.py --full-universe --max-symbols 25 --requests-per-minute 30
```

Continue the same source/range/universe after reviewing the sanitized summary:

```powershell
uv run python poc_vnstock.py --full-universe --max-symbols 25 --requests-per-minute 30 --resume --skip-representative
```

Omit `--max-symbols` only when intentionally requesting every eligible common
equity. The tool never writes PostgreSQL or raw market values. Its checkpoint
and summary are gitignored; the checkpoint keeps only one-way symbol
fingerprints and aggregate status counts so interrupted batches can resume.
`--skip-representative` is allowed only on `--resume`; it avoids repeating the
eight sample calls and marks the summary as `NOT_RECHECKED`, never as fresh
representative-gate evidence.

## TCBS interactive probe

Run this yourself in a trusted terminal. The default `totp` flow accepts a
current iOTP/TOTP from the TCInvest app:

```powershell
.\.venv\Scripts\python.exe .\poc_tcbs.py
```

If TCBS has registered your OpenAPI access for Email/SMS OTP instead, explicitly
choose that flow:

```powershell
.\.venv\Scripts\python.exe .\poc_tcbs.py --otp-method email-sms
```

The Email/SMS flow invokes only TCBS's documented `request-otp` endpoint, then
prompts for the code delivered by TCBS. Both flows exchange the token, check
documented read-only market endpoints, and listen briefly for WebSocket index
channel 8. They never invoke account, cash, portfolio, trading, or order
operations. Error summaries retain only the HTTP status, safe provider error
code, and field schema; response text is suppressed.

Review `poc-output/*-summary.json`. Do not send credentials or raw provider
responses. A summary can be reviewed only after you confirm it contains no
private value you do not want to share.
