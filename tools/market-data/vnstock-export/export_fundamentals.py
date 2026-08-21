"""Manual, local-only Vnstock fundamental-report exporter for Feature 002
(specs/002-stock-detail-analysis, research.md R-012 gate G-01: owner-accepted
narrower scope).

Only maps the small set of `item_id` values confirmed present by the sanitized
G-01 evidence probe (income_statement/cash_flow/ratio, source="kbs") to
Finvera's FundamentalReportAcceptance.ALLOWED_METRIC_CODES. Everything else —
including the ambiguous two "revenue" rows and any item_id not on this
allowlist — is dropped and counted, never guessed into the nearest-looking
code, per FundamentalReportProvider's own contract and AGENTS.md's "never
guess provider schema" rule. Same safety posture as export_history.py: never
touches PostgreSQL, never logs credentials, output is gitignored and must be
reviewed before import.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from datetime import UTC, date, datetime
from decimal import Decimal
from pathlib import Path
from typing import Any

CONTRACT_VERSION = "vnstock-fundamentals-v1"
SOURCE = "VNSTOCK_KBS"

# item_id -> Finvera metric_code, confirmed by the G-01 sanitized evidence probe only.
# "revenue" is deliberately absent: two rows share that item_id (gross vs. net revenue,
# research.md R-012 G-01 point 5a) and there is no confirmed way to disambiguate them.
INCOME_STATEMENT_MAP = {
    "gross_profit": "GROSS_PROFIT",
    "net_profit": "NET_PROFIT",
    "earnings_per_share_vnd": "EPS",
}
RATIO_MAP = {
    "roe": "ROE",
    "roa": "ROA",
    "debt_to_equity": "DEBT_TO_EQUITY",
}
CASH_FLOW_MAP: dict[str, str] = {}  # no confirmed unambiguous item_id yet; nothing mapped

QUARTER_COLUMN = re.compile(r"^(\d{4})-Q([1-4])$")
YEAR_COLUMN = re.compile(r"^(\d{4})$")


def decimal_string(value: Any) -> str:
    decimal = Decimal(str(value))
    if decimal.is_nan() or decimal.is_infinite():
        raise ValueError("metric value must be a finite decimal")
    return format(decimal, "f")


def canonical_json(value: dict[str, Any]) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def period_bounds(period_type: str, year: int, quarter: int | None) -> tuple[str, str]:
    if period_type == "QUARTER":
        start_month = (quarter - 1) * 3 + 1
        end_month = start_month + 2
        start = date(year, start_month, 1)
        end_year, end_month_norm = (year, end_month) if end_month <= 12 else (year + 1, end_month - 12)
        next_month = date(end_year, end_month_norm, 1).replace(day=28) + __import__("datetime").timedelta(days=4)
        end = next_month - __import__("datetime").timedelta(days=next_month.day)
        return start.isoformat(), end.isoformat()
    return date(year, 1, 1).isoformat(), date(year, 12, 31).isoformat()


def parse_period_column(column: str) -> tuple[str, int, int | None]:
    quarter_match = QUARTER_COLUMN.match(column)
    if quarter_match:
        return "QUARTER", int(quarter_match.group(1)), int(quarter_match.group(2))
    year_match = YEAR_COLUMN.match(column)
    if year_match:
        return "ANNUAL", int(year_match.group(1)), None
    raise ValueError(f"unrecognized period column: {column!r}")


def pivot_wide_table(frame, item_id_map: dict[str, str], source_report: str) -> list[dict[str, Any]]:
    """One row per item_id, one column per period (confirmed shape, research.md R-012 G-01 point 4)."""
    records: list[dict[str, Any]] = []
    if "item_id" not in frame.columns:
        return records
    period_columns = [c for c in frame.columns if c not in ("item_id", "item")]
    for _, row in frame.iterrows():
        item_id = str(row["item_id"])
        metric_code = item_id_map.get(item_id)
        if metric_code is None:
            continue
        for column in period_columns:
            value = row[column]
            if value is None or (isinstance(value, float) and value != value):  # NaN
                continue
            try:
                period_type, year, quarter = parse_period_column(str(column))
            except ValueError:
                continue
            period_start, period_end = period_bounds(period_type, year, quarter)
            records.append({
                "metricCode": metric_code, "periodType": period_type, "fiscalYear": year,
                "fiscalQuarter": quarter, "periodStart": period_start, "periodEnd": period_end,
                "value": decimal_string(value), "sourceReport": source_report,
            })
    return records


def build_metric_records(symbol: str, income_statement, ratio, cash_flow) -> list[dict[str, Any]]:
    records = []
    records += pivot_wide_table(income_statement, INCOME_STATEMENT_MAP, "INCOME_STATEMENT")
    records += pivot_wide_table(ratio, RATIO_MAP, "RATIO")
    if CASH_FLOW_MAP:
        records += pivot_wide_table(cash_flow, CASH_FLOW_MAP, "CASH_FLOW")
    for record in records:
        record["symbol"] = symbol.upper()
        record["canonicalRecord"] = ""
        record["canonicalRecord"] = canonical_json({k: v for k, v in record.items() if k != "canonicalRecord"})
    return records


def build_package(records: list[dict[str, Any]], symbol: str, tool_version: str, unit_scale: int) -> dict[str, Any]:
    if not records:
        raise ValueError("no confirmed-mappable metrics were found for this symbol")
    records = sorted(records, key=lambda r: (r["periodType"], r["fiscalYear"], r["fiscalQuarter"] or 0, r["metricCode"]))
    payload = {"records": records}
    payload_json = canonical_json(payload)
    return {
        "contractVersion": CONTRACT_VERSION, "toolName": "finvera-vnstock-exporter",
        "toolVersion": tool_version, "upstreamSource": SOURCE, "symbol": symbol.upper(),
        # reportKind/auditStatus are not confirmed by the G-01 evidence (consolidated vs.
        # separate, audited vs. unaudited was never established) -- recorded honestly as
        # UNKNOWN rather than guessed, matching export_history.py's own UNKNOWN convention.
        "reportKind": "UNKNOWN", "auditStatus": "UNKNOWN", "currency": "VND",
        # Not confirmed by evidence either -- review the printed sample values against this
        # symbol's known real financials before importing, and override with --unit-scale
        # if they look like thousands/millions rather than raw VND.
        "unitScale": unit_scale,
        "generatedAt": datetime.now(UTC).isoformat().replace("+00:00", "Z"),
        "packageSha256": hashlib.sha256(payload_json.encode()).hexdigest(),
        "canonicalPayload": payload_json, "records": records,
    }


def fetch_tables(symbol: str, period: str):
    from vnstock import Finance

    finance = Finance(symbol=symbol, source="kbs")
    income_statement = finance.income_statement(period=period)
    ratio = finance.ratio(period=period)
    cash_flow = finance.cash_flow(period=period)
    return income_statement, ratio, cash_flow


def main() -> None:
    parser = argparse.ArgumentParser(description="Create a local-only canonical Vnstock fundamentals package")
    parser.add_argument("--symbol", required=True)
    parser.add_argument("--period", choices=("year", "quarter"), default="quarter")
    parser.add_argument("--unit-scale", type=int, default=1,
                         help="Multiply every value by this before import. Inspect the printed "
                              "sample against this symbol's known real financials first; default "
                              "1 assumes raw VND, not thousands/millions.")
    parser.add_argument("--output", type=Path, default=Path("output"))
    args = parser.parse_args()
    income_statement, ratio, cash_flow = fetch_tables(args.symbol, args.period)
    records = build_metric_records(args.symbol, income_statement, ratio, cash_flow)
    package = build_package(records, args.symbol, "0.1.0", args.unit_scale)
    args.output.mkdir(parents=True, exist_ok=True)
    path = args.output / f"fundamentals-{args.symbol.lower()}-{args.period}.json"
    path.write_text(json.dumps(package, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote canonical package: {path} ({len(records)} metric-period records)")
    print(f"Package SHA-256: {package['packageSha256']}")
    print("Sample values (review before import; unit-scale currently "
          f"{args.unit_scale}x):")
    for record in records[:5]:
        print(f"  {record['metricCode']} {record['periodType']} {record['fiscalYear']}"
              f"{'-Q' + str(record['fiscalQuarter']) if record['fiscalQuarter'] else ''}: {record['value']}")


if __name__ == "__main__":
    main()
