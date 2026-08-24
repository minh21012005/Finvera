# Local Vnstock history exporter

This is a manual, local-only tool under the owner-approved exception in the
feature contract. It never writes to PostgreSQL. Do not commit its `output/`.

## Market overview package

Generate the four-index market package used by Feature 001:

```powershell
uv run --project ..\provider-poc python .\export_history.py `
  --market-overview `
  --start 2024-01-01 `
  --end 2026-08-24 `
  --output .\output
```

The output contract is `vnstock-market-private-package-v1`. Spring imports it
through `FINVERA_MARKET_IMPORT_PACKAGE_PATH`; the browser never calls Vnstock
directly.

## Single-equity history package

```powershell
uv run --project ..\provider-poc python .\export_history.py --symbol VNM --venue HOSE --start 2025-01-01 --end 2026-08-14
```

The exporter refuses packages with fewer than 271 daily rows. Review the output
locally before enabling the Spring internal import boundary. It is not approved
for public, remote, multi-user, scheduled, or redistributed use.
