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
TOOL_VERSION = "0.7.0"  # Feature 011: provider-ratio-facts-v2, statement ids per company type, FCF v2
SOURCE = "VNSTOCK_KBS"

# item_id -> Finvera metric_code
INCOME_STATEMENT_MAP = {
    "gross_profit": "GROSS_PROFIT",
    "net_profit": "NET_PROFIT",
    "earnings_per_share_vnd": "EPS",
    "earning_per_share_vnd": "EPS",  # banks (MBB) use the singular id -- Feature 010 R-004
    "operating_profit": "OPERATING_PROFIT",
    "revenue": "REVENUE",
    # Feature 011 research R-006: insurance and securities statements use their own ids.
    "profit_after_tax": "NET_PROFIT",                                   # insurance
    "total_net_revenue_from_insurance_business": "REVENUE",             # insurance
    "revenue_from_securities_business_01_11": "REVENUE",                # securities
    "net_profit_from_securities_business_20_50_40_60_61_62": "OPERATING_PROFIT",  # securities
}
RATIO_MAP = {
    "roe": "ROE",
    "roa": "ROA",
    "debt_to_equity": "DEBT_TO_EQUITY",
    "book_value_per_share_bvps": "BVPS",
    "trailing_eps": "TRAILING_EPS",
    "dividend_yield": "DIVIDEND_YIELD",
    "ebit_margin": "OPERATING_MARGIN",
    # Feature 009 (contract provider-ratio-facts-v1 U-2). ev_ebitda / pe_ratio / pb_ratio are
    # deliberately NOT mapped: Finvera computes those under valuation-v1 at its own price.
    "gross_margin": "GROSS_MARGIN",
    "net_margin": "NET_MARGIN",
    "roe_trailling": "ROE_TTM",
    "roa_trailling": "ROA_TTM",
    "return_on_capital_employed_roce": "ROCE",
    "short_term_ratio": "CURRENT_RATIO",
    "quick_ratio": "QUICK_RATIO",
    "cash_ratio": "CASH_RATIO",
    "interest_coverage": "INTEREST_COVERAGE",
    "total_asset_turnover": "TOTAL_ASSET_TURNOVER",
    "inventory_turnover": "INVENTORY_TURNOVER",
    "receivables_turnover": "RECEIVABLES_TURNOVER",
    "debt_to_assets": "DEBT_TO_ASSETS",
    "liabilities_to_equity": "LIABILITIES_TO_EQUITY",
    "equity_to_assets": "EQUITY_TO_ASSETS",
    "beta": "BETA",
    "ps_ratio": "PS",
    "net_interest_margin_nim": "NIM",
    "cost_income_ratio_cir": "COST_INCOME_RATIO",
    "outstanding_loans_customer_deposits": "LOAN_TO_DEPOSIT",
}
# The KBS ratio dataset reuses statement item_ids for its growth rows; they are mapped only when
# the row label reads "Tăng trưởng ..." (see pivot_wide_table).
RATIO_GROWTH_MAP = {
    "total_assets": "TOTAL_ASSETS_GROWTH_PERCENT",
    "owners_equity": "EQUITY_GROWTH_PERCENT",
}
# Contract provider-ratio-facts-v2 (Feature 011 research R-003): KBS quarter columns hold
# single-quarter values for flow-over-stock ratios, and the annual dataset carries 0.0
# placeholders for the trailing ratios. Each id is emitted only from the columns whose
# period matches the catalog meaning.
RATIO_ANNUAL_ONLY_IDS = {
    "roe", "roa", "return_on_capital_employed_roce", "net_interest_margin_nim",
    "total_asset_turnover", "inventory_turnover", "receivables_turnover",
    "dividend_yield", "ps_ratio", "total_assets", "owners_equity",
}
RATIO_QUARTER_ONLY_IDS = {"roe_trailling", "roa_trailling"}
# In quarter packages the trailing ratios are the annualized ROE/ROA (rule id on the row).
TRAILING_AS_ANNUALIZED = {"roe_trailling": "ROE", "roa_trailling": "ROA"}
TRAILING_DERIVATION = "kbs-trailing-ratio-as-annualized-v1"
CASH_FLOW_MAP: dict[str, str] = {}  # no confirmed unambiguous item_id yet; nothing mapped
KBS_PER_SHARE_DIVISOR = Decimal("1000")
KBS_PER_SHARE_METRIC_CODES = {"EPS"}

QUARTER_COLUMN = re.compile(r"^(\d{4})-Q([1-4])$")
# KBS labels annual columns "YYYY-Năm" (Feature 008 research R-004); bare "YYYY" kept for fixtures.
YEAR_COLUMN = re.compile(r"^(\d{4})(?:-Năm)?$")

# Q-57 (Feature 011 research R-009, contract kbs-yearly-statement-orientation-v1): for the KBS
# *statement* endpoints (income statement, cash flow) with period="year", vnstock 4.0.6 pairs
# Value1..Value4 with the Head entries sorted by ID, and for yearly statements that order is the
# mirror image of the labels: the column labelled "2025-Năm" holds FY2022, "2022-Năm" holds
# FY2025 (VNM net revenue 59,956 bn under "2025-Năm" is the audited FY2022 figure; HPG parent
# profit 12,021 bn under "2023-Năm" is FY2024; MBB EPS 3,856 under "2025-Năm" is FY2022; the
# database-wide signature: 737 of 838 instruments had "FY2022" >= Q3+Q4 of 2025). The yearly
# RATIO endpoint is correctly labelled (FPT P/E 26.7 under "2024-Năm" is the 2024 peak) and is
# left alone, as are all quarterly frames. The relabelled records carry the rule id so the
# provenance of every annual statement fact is visible.
YEARLY_STATEMENT_ORIENTATION = "kbs-yearly-statement-labels-mirrored-v1"

# Feature 008 research R-003/R-002: versioned derivation rule ids carried on each derived record.
EBITDA_DERIVATION = "kbs-ebitda-margin-x-net-revenue-v1"
FCF_DERIVATION = "kbs-fcf-ocf-plus-capex-v2"
# v2 (Feature 011 R-006): first present id wins; securities firms report OCF under the
# trading-activities id, insurers prefix the capex id. Banks are deliberately not covered.
FCF_OCF_IDS = ("operating_cash_flow", "net_cash_flows_from_securities_trading_activities")
FCF_CAPEX_IDS = ("payment_for_fixed_assets_constructions_and_other_long_term_assets",
                 "n_1_payment_for_fixed_assets_constructions_and_other_long_term_assets")
NET_REVENUE_LABEL_TOKEN = "thuần"


def decimal_string(value: Any) -> str:
    decimal = Decimal(str(value))
    if decimal.is_nan() or decimal.is_infinite():
        raise ValueError("metric value must be a finite decimal")
    return format(decimal, "f")


def normalize_metric_value(metric_code: str, value: Any) -> str:
    decimal = Decimal(str(value))
    if decimal.is_nan() or decimal.is_infinite():
        raise ValueError("metric value must be a finite decimal")
    if metric_code in KBS_PER_SHARE_METRIC_CODES:
        # KBS/vnstock 4.0.6 exposes per-share fields such as
        # earnings_per_share_vnd in thousandths of VND/share in the observed
        # package shape (e.g. 1,036,000 for an EPS that should feed valuation
        # as 1,036 VND/share). Normalize before the canonical package so Java
        # import stores a true per-share value and unitScale remains reserved
        # for statement-level money values.
        decimal = decimal / KBS_PER_SHARE_DIVISOR
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


def yearly_columns(frame) -> list[str]:
    """Annual period columns of a wide frame, in the provider's order."""
    if frame is None or "item_id" not in getattr(frame, "columns", []):
        return []
    out = []
    for column in frame.columns:
        if column in ("item_id", "item"):
            continue
        if YEAR_COLUMN.match(str(column)):
            out.append(str(column))
    return out


def mirror_yearly_statement_labels(frame):
    """Q-57: relabel a KBS yearly *statement* frame so the column named for year Y really holds
    FY Y. The provider's labels are the mirror image of its values (see
    YEARLY_STATEMENT_ORIENTATION); mirroring the label sequence restores the truth without
    touching a single value. Frames with no or one annual column are returned unchanged."""
    columns = yearly_columns(frame)
    if len(columns) < 2:
        return frame
    years = [int(YEAR_COLUMN.match(c).group(1)) for c in columns]
    mirrored = {old: old.replace(str(year), str(new_year), 1)
                for old, year, new_year in zip(columns, years, reversed(years))}
    return frame.rename(columns=mirrored)


def orient_statement_frames(income_statement, cash_flow, period: str):
    """Apply the Q-57 relabelling to the two statement frames when they were fetched yearly."""
    if period != "year":
        return income_statement, cash_flow
    return mirror_yearly_statement_labels(income_statement), mirror_yearly_statement_labels(cash_flow)


def pivot_wide_table(frame, item_id_map: dict[str, str], source_report: str) -> list[dict[str, Any]]:
    """One row per item_id, one column per period (confirmed shape, research.md R-012 G-01 point 4)."""
    records: list[dict[str, Any]] = []
    if "item_id" not in frame.columns:
        return records
    period_columns = [c for c in frame.columns if c not in ("item_id", "item")]
    seen: set[tuple[str, str]] = set()  # (metricCode, column): first mapped row wins (R-006)
    for _, row in frame.iterrows():
        item_id = str(row["item_id"])
        metric_code = item_id_map.get(item_id)
        if source_report == "RATIO" and item_id in RATIO_GROWTH_MAP and "tăng trưởng" in str(cell(row, "item") or "").lower():
            metric_code = RATIO_GROWTH_MAP[item_id]
        if metric_code is None:
            continue
        if item_id == "revenue" and has_net_revenue_row(frame) and not is_net_revenue_row(row):
            # Two rows share item_id "revenue" (gross "1. Doanh thu bán hàng" and net
            # "3. Doanh thu thuần"); when both exist only net revenue is REVENUE (research R-003).
            continue
        for column in period_columns:
            value = row[column]
            if value is None or (isinstance(value, float) and value != value):  # NaN
                continue
            try:
                period_type, year, quarter = parse_period_column(str(column))
            except ValueError:
                continue
            if source_report == "RATIO":
                if period_type == "QUARTER" and item_id in RATIO_ANNUAL_ONLY_IDS:
                    continue  # single-quarter value; catalog meaning is annualized (contract v2 U-2)
                if period_type == "ANNUAL" and item_id in RATIO_QUARTER_ONLY_IDS:
                    continue  # provider writes 0.0 here, not a fact
            if (metric_code, str(column)) in seen:
                continue
            seen.add((metric_code, str(column)))
            period_start, period_end = period_bounds(period_type, year, quarter)
            record = {
                "metricCode": metric_code, "periodType": period_type, "fiscalYear": year,
                "fiscalQuarter": quarter, "periodStart": period_start, "periodEnd": period_end,
                "value": normalize_metric_value(metric_code, value), "sourceReport": source_report,
            }
            if period_type == "ANNUAL" and source_report in ("INCOME_STATEMENT", "CASH_FLOW"):
                record["derivation"] = YEARLY_STATEMENT_ORIENTATION  # Q-57 provenance
            records.append(record)
            if source_report == "RATIO" and period_type == "QUARTER" and item_id in TRAILING_AS_ANNUALIZED:
                annualized = TRAILING_AS_ANNUALIZED[item_id]
                if (annualized, str(column)) not in seen:
                    seen.add((annualized, str(column)))
                    records.append({**record, "metricCode": annualized, "derivation": TRAILING_DERIVATION})
    return records


def cell(row: Any, column: str) -> Any:
    try:
        return row[column]
    except (KeyError, IndexError):
        return None


def is_net_revenue_row(row: Any) -> bool:
    return NET_REVENUE_LABEL_TOKEN in str(cell(row, "item") or "").lower()


def has_net_revenue_row(frame: Any) -> bool:
    return any(str(r["item_id"]) == "revenue" and is_net_revenue_row(r) for _, r in frame.iterrows())


def revenue_basis_predicate(frame: Any):
    """Net revenue when the frame distinguishes it; otherwise the sole revenue row."""
    strict = has_net_revenue_row(frame)
    return (lambda row: is_net_revenue_row(row)) if strict else (lambda row: True)


def period_values(frame, item_id: str, label_predicate=None) -> dict[str, Decimal]:
    """{period column: Decimal} for one item_id (optionally filtered by label), skipping NaN/None."""
    values: dict[str, Decimal] = {}
    if frame is None or "item_id" not in getattr(frame, "columns", []):
        return values
    period_columns = [c for c in frame.columns if c not in ("item_id", "item")]
    for _, row in frame.iterrows():
        if str(row["item_id"]) != item_id:
            continue
        if label_predicate is not None and not label_predicate(row):
            continue
        for column in period_columns:
            value = cell(row, column)
            if value is None or (isinstance(value, float) and value != value):
                continue
            try:
                parse_period_column(str(column))
            except ValueError:
                continue
            values[str(column)] = Decimal(str(value))
    return values


def first_present(frame, item_ids) -> dict[str, Decimal]:
    for item_id in item_ids:
        values = period_values(frame, item_id)
        if values:
            return values
    return {}


def derived_record(metric_code: str, column: str, value: Decimal, derivation: str, source_report: str) -> dict[str, Any]:
    period_type, year, quarter = parse_period_column(column)
    period_start, period_end = period_bounds(period_type, year, quarter)
    return {
        "metricCode": metric_code, "periodType": period_type, "fiscalYear": year,
        "fiscalQuarter": quarter, "periodStart": period_start, "periodEnd": period_end,
        "value": format(value.quantize(Decimal("0.000001")), "f"), "sourceReport": source_report,
        "derivation": derivation,
    }


def derive_ebitda(income_statement, ratio) -> list[dict[str, Any]]:
    """EBITDA(period) = ebitda_net_revenue% / 100 x net revenue(period) -- research R-003.
    Emitted only where both inputs exist for the same period column; never interpolated."""
    margins = period_values(ratio, "ebitda_net_revenue")
    revenue = period_values(income_statement, "revenue", revenue_basis_predicate(income_statement))
    out = []
    for column, margin in margins.items():
        if column in revenue:
            out.append(derived_record("EBITDA", column, margin / Decimal("100") * revenue[column],
                                      EBITDA_DERIVATION, "RATIO+INCOME_STATEMENT"))
    return out


def derive_free_cash_flow(cash_flow) -> list[dict[str, Any]]:
    """FCF = operating cash flow + capex (provider signs capex negative) -- research R-002."""
    ocf = first_present(cash_flow, FCF_OCF_IDS)
    capex = first_present(cash_flow, FCF_CAPEX_IDS)
    return [derived_record("FREE_CASH_FLOW", column, ocf[column] + capex[column], FCF_DERIVATION, "CASH_FLOW")
            for column in ocf if column in capex]


def build_metric_records(symbol: str, income_statement, ratio, cash_flow) -> list[dict[str, Any]]:
    records = []
    records += pivot_wide_table(income_statement, INCOME_STATEMENT_MAP, "INCOME_STATEMENT")
    records += pivot_wide_table(ratio, RATIO_MAP, "RATIO")
    if CASH_FLOW_MAP:
        records += pivot_wide_table(cash_flow, CASH_FLOW_MAP, "CASH_FLOW")
    records += derive_ebitda(income_statement, ratio)
    records += derive_free_cash_flow(cash_flow)
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
    income_statement, cash_flow = orient_statement_frames(income_statement, cash_flow, period)
    return income_statement, ratio, cash_flow


def output_filename(symbol: str, period: str) -> str:
    """Stable per (symbol, period) -- re-running overwrites the same file with the latest figures."""
    return f"fundamentals-{symbol.lower()}-{period}.json"


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
    package = build_package(records, args.symbol, TOOL_VERSION, args.unit_scale)
    args.output.mkdir(parents=True, exist_ok=True)
    path = args.output / output_filename(args.symbol, args.period)
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
