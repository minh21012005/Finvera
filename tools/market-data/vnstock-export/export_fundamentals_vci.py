"""Export fundamental statements from VCI (vnstock >= 4.0.7) into the Finvera fundamentals package.

Contract: specs/018-vci-fundamentals/contracts/vci-fundamentals-v1.md (ADR-0011, Q-57).

Why VCI: KBS statement pages carry another period's content (yearly mirrored, quarterly permuted,
20/20 symbols). VCI's labels were verified against audited figures and against the data's own
identities (FY2025 = sum of its four quarters to the VND for VNM and MBB; balance-sheet share capital
/ 10,000 = the profile's shares outstanding). The VCI *ratio* frame is malformed in vnstock 4.0.7 and
is never consumed: every ratio Finvera needs is derived here under a published rule id.

Package shape is the existing `vnstock-fundamentals-v1` (one file per symbol and period), so the
backend importer is unchanged apart from the source-supersession rule (contract I-1).

Usage:
    uv run --project ../provider-poc python export_fundamentals_vci.py --symbol VNM --period quarter --output ./out
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import warnings
from datetime import UTC, datetime
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path
from typing import Any

from export_fundamentals import CONTRACT_VERSION, canonical_json, period_bounds, output_filename

TOOL_VERSION = "1.0.0"
SOURCE = "VNSTOCK_VCI"
PAR_VALUE_VND = Decimal("10000")
SIX = Decimal("0.000001")
HUNDRED = Decimal("100")

# ── rule ids (quality_reason on derived facts) ──────────────────────────────────────────────
RULE_TRAILING_EPS = "vci-trailing-eps-parent-profit-over-shares-v1"
RULE_BVPS = "vci-bvps-parent-equity-over-shares-v1"
RULE_ROE = "vci-roe-parent-profit-over-average-equity-v1"
RULE_ROA = "vci-roa-net-profit-over-average-assets-v1"
RULE_MARGIN = "vci-margin-v1"
RULE_DEBT_TO_EQUITY = "vci-debt-to-equity-v1"
RULE_FCF = "vci-fcf-ocf-plus-capex-v1"
RULE_EBITDA = "vci-ebitda-operating-profit-plus-da-v1"
END_SUFFIX = "-end"   # average-balance denominators fall back to the period-end balance, disclosed

COMPANY_TYPES = ("BANK", "INSURER", "BROKER", "NON_FINANCIAL")
DETECTORS = (("BANK", "net_interest_income"), ("INSURER", "net_sales_from_insurance_business"), ("BROKER", "operating_sales"))

IS, BS, CF = "INCOME_STATEMENT", "BALANCE_SHEET", "CASH_FLOW"

# Statement facts: metric -> (report, spec) where spec is an item id, ("sum", ids) or ("diff", a, b).
STATEMENT_FACTS: dict[str, dict[str, tuple]] = {
    "NON_FINANCIAL": {
        "REVENUE": (IS, "net_sales"), "GROSS_PROFIT": (IS, "gross_profit"), "OPERATING_PROFIT": (IS, "operating_profit_loss"),
        "NET_PROFIT": (IS, "net_profit_loss_after_tax"), "EPS": (IS, "eps_basic_vnd"),
        "EQUITY_ATTRIBUTABLE_TO_PARENT": (BS, ("diff", "owners_equity", "minority_interests")),
        "TOTAL_DEBT": (BS, ("sum", "short_term_borrowings", "long_term_borrowings")),
        "CASH_AND_EQUIVALENTS": (BS, "cash_and_cash_equivalents"),
    },
    "BANK": {
        "REVENUE": (IS, "total_operating_income"), "OPERATING_PROFIT": (IS, "net_operating_profit_before_allowance_for_credit_losses"),
        "NET_PROFIT": (IS, "net_profit_loss_after_tax"), "EPS": (IS, "eps_basic_vnd"),
        "EQUITY_ATTRIBUTABLE_TO_PARENT": (BS, ("diff", "owners_equity", "minority_interest")),
    },
    "INSURER": {
        "REVENUE": (IS, "net_sales_from_insurance_business"), "GROSS_PROFIT": (IS, "gross_insurance_operating_profit"),
        "NET_PROFIT": (IS, "profit_after_tax"), "EPS": (IS, "eps_basic_vnd"),
        "EQUITY_ATTRIBUTABLE_TO_PARENT": (BS, "owners_equity"),
        "CASH_AND_EQUIVALENTS": (BS, "cash_and_cash_equivalents"),
    },
    "BROKER": {
        "REVENUE": (IS, "net_sales"), "GROSS_PROFIT": (IS, "gross_profit"), "OPERATING_PROFIT": (IS, "operating_profit_loss"),
        "NET_PROFIT": (IS, "net_profit_loss_after_tax"), "EPS": (IS, "eps_basic_vnd"),
        "EQUITY_ATTRIBUTABLE_TO_PARENT": (BS, "owners_equity"),
        "TOTAL_DEBT": (BS, ("sum", "short_term_borrowings", "long_term_borrowings")),
        "CASH_AND_EQUIVALENTS": (BS, "cash_and_cash_equivalents"),
    },
}

# Derivation inputs (never emitted on their own).
INPUTS: dict[str, dict[str, tuple]] = {
    "NON_FINANCIAL": {
        "parent_profit": (IS, "attributable_to_parent_company"), "total_assets": (BS, "total_assets"),
        "share_capital": (BS, ("first", "paid_in_capital", "common_shares")), "treasury": (BS, "treasury_shares"),
        "ocf": (CF, "net_cash_inflows_outflows_from_operating_activities"), "capex": (CF, "purchases_of_fixed_assets_and_other_long_term_assets"),
        "da": (CF, "depreciation_and_amortization"),
    },
    "BANK": {
        "parent_profit": (IS, "attributable_to_parent_company"), "total_assets": (BS, "total_assets"),
        "share_capital": (BS, "charter_capital"), "treasury": (BS, "treasury_shares"),
        "ocf": (CF, "net_cash_from_operating_activities"), "capex": (CF, "purchases_of_fixed_assets_and_other_long_term_assets"),
    },
    "INSURER": {
        "parent_profit": (IS, ("first", "net_profit_attributable_to_shareholders_of_the_group", "net_profit_attributable_to_shareholders_of_the_parent")),
        "total_assets": (BS, "total_assets"),
        "share_capital": (BS, ("first", "paid_in_capital", "common_shares")), "treasury": (BS, "treasury_shares"),
        "ocf": (CF, "net_cash_inflows_outflows_from_operating_activities"), "capex": (CF, "purchases_of_fixed_assets_and_other_long_term_assets"),
    },
    "BROKER": {
        "parent_profit": (IS, "net_profit_loss_after_tax"), "total_assets": (BS, "total_assets"),
        "share_capital": (BS, "paid_in_capital"), "treasury": (BS, "treasury_shares"),
        "ocf": (CF, "net_cash_inflows_outflows_from_operating_activities"), "capex": (CF, "purchases_of_fixed_assets_and_other_long_term_assets"),
    },
}

PERIOD_COLUMN = re.compile(r"^(\d{4})(?:-Q([1-4]))?$")


def parse_period(column: str) -> tuple[str, int, int | None] | None:
    m = PERIOD_COLUMN.match(str(column).strip())
    if not m:
        return None
    return ("QUARTER", int(m.group(1)), int(m.group(2))) if m.group(2) else ("ANNUAL", int(m.group(1)), None)


class Table:
    """A wide statement frame reduced to {item_id: {period column: Decimal}}; first occurrence of an id wins."""

    def __init__(self, columns: list[str], rows: list[dict[str, Any]]):
        self.periods = [c for c in columns if parse_period(c)]
        self.values: dict[str, dict[str, Decimal]] = {}
        for row in rows:
            item_id = str(row.get("item_id"))
            if item_id in self.values:
                continue  # contract: first occurrence wins (ids repeat in a few VCI frames)
            per = {}
            for column in self.periods:
                value = row.get(column)
                if value is None:
                    continue
                try:
                    if isinstance(value, float) and value != value:
                        continue
                    per[column] = Decimal(str(value))
                except Exception:  # noqa: BLE001 - non-numeric cell
                    continue
            self.values[item_id] = per

    @classmethod
    def from_frame(cls, frame) -> "Table":
        if frame is None or not hasattr(frame, "columns"):
            return cls([], [])
        columns = [str(c) for c in frame.columns]
        rows = [{str(k): v for k, v in r.items()} for _, r in frame.iterrows()]
        return cls(columns, rows)

    @classmethod
    def from_fixture(cls, data: dict[str, Any]) -> "Table":
        return cls([str(c) for c in data.get("columns", [])], data.get("rows", []))

    def has(self, item_id: str) -> bool:
        return item_id in self.values

    def get(self, item_id: str, period: str) -> Decimal | None:
        return self.values.get(item_id, {}).get(period)


Frames = dict[str, Table]  # keys: INCOME_STATEMENT, BALANCE_SHEET, CASH_FLOW


def detect_company_type(income: Table) -> str:
    for company_type, marker in DETECTORS:
        if income.has(marker):
            return company_type
    return "NON_FINANCIAL"


def resolve(frames: Frames, report: str, spec, period: str) -> Decimal | None:
    table = frames.get(report)
    if table is None:
        return None
    if isinstance(spec, str):
        return table.get(spec, period)
    kind = spec[0]
    if kind == "first":
        for item_id in spec[1:]:
            value = table.get(item_id, period)
            if value is not None:
                return value
        return None
    if kind == "sum":
        parts = [table.get(i, period) for i in spec[1:]]
        return None if any(p is None for p in parts) else sum(parts, Decimal(0))
    if kind == "diff":
        a, b = table.get(spec[1], period), table.get(spec[2], period)
        if a is None:
            return None
        return a - (b or Decimal(0))  # a missing minority row means none reported, not "unknown equity"
    raise ValueError(f"unknown spec {spec}")


def q6(value: Decimal) -> str:
    return format(value.quantize(SIX, rounding=ROUND_HALF_UP), "f")


def period_key(column: str) -> int:
    kind, year, quarter = parse_period(column)
    return year * 4 + (quarter - 1) if kind == "QUARTER" else year


def all_periods(frames: Frames) -> list[str]:
    seen: dict[str, None] = {}
    for table in frames.values():
        for c in table.periods:
            seen.setdefault(c, None)
    return sorted(seen, key=period_key)


def record(metric: str, column: str, value: Decimal, source_report: str, company_type: str, derivation: str | None = None) -> dict[str, Any]:
    period_type, year, quarter = parse_period(column)
    start, end = period_bounds(period_type, year, quarter)
    rec = {"metricCode": metric, "periodType": period_type, "fiscalYear": year, "fiscalQuarter": quarter,
           "periodStart": start, "periodEnd": end, "value": q6(value), "sourceReport": source_report,
           "companyType": company_type}
    if derivation:
        rec["derivation"] = derivation
    return rec


def shares_by_period(frames: Frames, company_type: str) -> dict[str, Decimal]:
    """shares(period) = (share capital + treasury shares) / par 10,000; treasury is stored negative."""
    inputs = INPUTS[company_type]
    out = {}
    for column in all_periods(frames):
        capital = resolve(frames, *inputs["share_capital"], column)
        if capital is None or capital <= 0:
            continue
        treasury = resolve(frames, *inputs["treasury"], column) or Decimal(0)
        shares = (capital + treasury) / PAR_VALUE_VND
        if shares > 0:
            out[column] = shares
    return out


def window(columns: list[str], column: str, size: int) -> list[str] | None:
    """The `size` consecutive quarter columns ending at `column`, or None when a quarter is missing."""
    keys = {period_key(c): c for c in columns}
    end = period_key(column)
    seq = [keys.get(end - i) for i in range(size - 1, -1, -1)]
    return None if any(c is None for c in seq) else seq


def average(values: list[Decimal]) -> Decimal:
    return sum(values, Decimal(0)) / Decimal(len(values))


def build_metric_records(symbol: str, frames: Frames, period: str) -> list[dict[str, Any]]:
    income = frames.get(IS) or Table([], [])
    company_type = detect_company_type(income)
    facts = STATEMENT_FACTS[company_type]
    inputs = INPUTS[company_type]
    periods = [c for c in all_periods(frames) if parse_period(c)[0] == ("QUARTER" if period == "quarter" else "ANNUAL")]
    records: list[dict[str, Any]] = []

    # 1. statement facts
    for metric, (report, spec) in facts.items():
        for column in periods:
            value = resolve(frames, report, spec, column)
            if value is None:
                continue
            if metric == "EPS" and value == 0:
                continue  # VCI writes 0.0 where the period's EPS is not reported (banks, HPG quarters)
            records.append(record(metric, column, value, report, company_type))

    # 2. derivations
    shares = shares_by_period(frames, company_type)
    by = {(r["metricCode"], r["periodType"], r["fiscalYear"], r["fiscalQuarter"]): Decimal(r["value"]) for r in records}

    def fact(metric: str, column: str) -> Decimal | None:
        kind, year, quarter = parse_period(column)
        return by.get((metric, kind, year, quarter))

    quarter_columns = [c for c in periods if parse_period(c)[0] == "QUARTER"]
    for column in periods:
        kind = parse_period(column)[0]
        parent = resolve(frames, *inputs["parent_profit"], column)
        equity = fact("EQUITY_ATTRIBUTABLE_TO_PARENT", column)
        assets = resolve(frames, *inputs["total_assets"], column)
        net_profit = fact("NET_PROFIT", column)
        revenue = fact("REVENUE", column)
        sh = shares.get(column)

        # BVPS
        if equity is not None and sh:
            records.append(record("BVPS", column, equity / sh, BS, company_type, RULE_BVPS))

        if kind == "ANNUAL":
            if parent is not None and sh:
                records.append(record("TRAILING_EPS", column, parent / sh, IS, company_type, RULE_TRAILING_EPS))
            prior_col = str(parse_period(column)[1] - 1)
            for code, numerator, balance_metric, rule in (("ROE", parent, "equity", RULE_ROE), ("ROA", net_profit, "assets", RULE_ROA)):
                end_bal = equity if balance_metric == "equity" else assets
                if numerator is None or end_bal is None or end_bal <= 0:
                    continue
                prior_bal = (fact("EQUITY_ATTRIBUTABLE_TO_PARENT", prior_col) if balance_metric == "equity"
                             else resolve(frames, *inputs["total_assets"], prior_col)) if parse_period(prior_col) else None
                if prior_bal is not None and prior_bal > 0:
                    records.append(record(code, column, numerator / average([prior_bal, end_bal]) * HUNDRED, IS, company_type, rule))
                else:
                    records.append(record(code, column, numerator / end_bal * HUNDRED, IS, company_type, rule + END_SUFFIX))
        else:
            win = window(quarter_columns, column, 4)
            if win:
                parents = [resolve(frames, *inputs["parent_profit"], c) for c in win]
                nets = [fact("NET_PROFIT", c) for c in win]
                if all(p is not None for p in parents) and sh:
                    ttm_parent = sum(parents, Decimal(0))
                    records.append(record("TRAILING_EPS", column, ttm_parent / sh, IS, company_type, RULE_TRAILING_EPS))
                    five = window(quarter_columns, column, 5)
                    eq_bals = [fact("EQUITY_ATTRIBUTABLE_TO_PARENT", c) for c in five] if five else None
                    if eq_bals and all(b is not None and b > 0 for b in eq_bals):
                        roe = ttm_parent / average(eq_bals) * HUNDRED
                        suffix = ""
                    elif equity is not None and equity > 0:
                        roe = ttm_parent / equity * HUNDRED
                        suffix = END_SUFFIX
                    else:
                        roe = None
                    if roe is not None:
                        records.append(record("ROE_TTM", column, roe, IS, company_type, RULE_ROE + suffix))
                        records.append(record("ROE", column, roe, IS, company_type, RULE_ROE + suffix))
                if all(n is not None for n in nets) and assets is not None and assets > 0:
                    ttm_net = sum(nets, Decimal(0))
                    five = window(quarter_columns, column, 5)
                    as_bals = [resolve(frames, *inputs["total_assets"], c) for c in five] if five else None
                    if as_bals and all(b is not None and b > 0 for b in as_bals):
                        roa, suffix = ttm_net / average(as_bals) * HUNDRED, ""
                    else:
                        roa, suffix = ttm_net / assets * HUNDRED, END_SUFFIX
                    records.append(record("ROA_TTM", column, roa, IS, company_type, RULE_ROA + suffix))
                    records.append(record("ROA", column, roa, IS, company_type, RULE_ROA + suffix))

        # margins
        if revenue is not None and revenue > 0:
            for code, metric in (("GROSS_MARGIN", "GROSS_PROFIT"), ("OPERATING_MARGIN", "OPERATING_PROFIT"), ("NET_MARGIN", "NET_PROFIT")):
                numerator = fact(metric, column)
                if numerator is not None:
                    records.append(record(code, column, numerator / revenue * HUNDRED, IS, company_type, RULE_MARGIN))
        # leverage
        debt = fact("TOTAL_DEBT", column)
        if debt is not None and equity is not None and equity > 0:
            records.append(record("DEBT_TO_EQUITY", column, debt / equity, BS, company_type, RULE_DEBT_TO_EQUITY))
        # cash flow
        ocf = resolve(frames, *inputs["ocf"], column)
        capex = resolve(frames, *inputs["capex"], column)
        if ocf is not None and capex is not None:
            records.append(record("FREE_CASH_FLOW", column, ocf + capex, CF, company_type, RULE_FCF))
        # EBITDA (non-financials only)
        if "da" in inputs:
            op = fact("OPERATING_PROFIT", column)
            da = resolve(frames, *inputs["da"], column)
            if op is not None and da is not None:
                records.append(record("EBITDA", column, op + da, IS, company_type, RULE_EBITDA))

    for rec in records:
        rec["symbol"] = symbol.upper()
        rec["canonicalRecord"] = ""
        rec["canonicalRecord"] = canonical_json({k: v for k, v in rec.items() if k != "canonicalRecord"})
    return records


class NoStatementsAvailable(ValueError):
    """The provider returned no statement rows for this symbol and period (e.g. A32 has annual
    statements on VCI but no quarterly ones). Recorded by the crawl as a settled, named failure —
    never written as an empty package, never guessed."""


def build_package(records: list[dict[str, Any]], symbol: str, company_type: str, unit_scale: int = 1) -> dict[str, Any]:
    if not records:
        raise NoStatementsAvailable(f"{symbol}: provider returned no mappable statement facts for this period")
    records = sorted(records, key=lambda r: (r["periodType"], r["fiscalYear"], r["fiscalQuarter"] or 0, r["metricCode"]))
    payload = {"records": records}
    payload_json = canonical_json(payload)
    return {
        "contractVersion": CONTRACT_VERSION, "toolName": "finvera-vnstock-exporter", "toolVersion": TOOL_VERSION,
        "upstreamSource": SOURCE, "symbol": symbol.upper(), "companyType": company_type,
        "reportKind": "UNKNOWN", "auditStatus": "UNKNOWN", "currency": "VND", "unitScale": unit_scale,
        "generatedAt": datetime.now(UTC).isoformat().replace("+00:00", "Z"),
        "packageSha256": hashlib.sha256(payload_json.encode()).hexdigest(),
        "canonicalPayload": payload_json, "records": records,
    }


def fetch_frames(symbol: str, period: str) -> Frames:
    from vnstock import Finance

    finance = Finance(symbol=symbol, source="vci")
    with warnings.catch_warnings():
        # vnstock's own pandas fillna/ffill usage emits a FutureWarning per call; it is not ours to fix
        # and would drown the crawl log (one line per statement per symbol).
        warnings.simplefilter("ignore", FutureWarning)
        return {
            IS: Table.from_frame(finance.income_statement(period=period)),
            BS: Table.from_frame(finance.balance_sheet(period=period)),
            CF: Table.from_frame(finance.cash_flow(period=period)),
        }


def frames_from_fixture(data: dict[str, Any], period: str) -> Frames:
    ds = data["datasets"]
    return {IS: Table.from_fixture(ds[f"income_statement:{period}"]), BS: Table.from_fixture(ds[f"balance_sheet:{period}"]),
            CF: Table.from_fixture(ds[f"cash_flow:{period}"])}


def export_symbol(symbol: str, period: str, output: Path, unit_scale: int = 1) -> Path:
    frames = fetch_frames(symbol, period)
    records = build_metric_records(symbol, frames, period)
    company_type = detect_company_type(frames[IS])
    package = build_package(records, symbol, company_type, unit_scale)
    output.mkdir(parents=True, exist_ok=True)
    path = output / output_filename(symbol, period)
    path.write_text(json.dumps(package, ensure_ascii=False, indent=1), encoding="utf-8")
    return path


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--symbol", required=True)
    parser.add_argument("--period", choices=["year", "quarter"], default="quarter")
    parser.add_argument("--output", type=Path, default=Path("out"))
    parser.add_argument("--unit-scale", type=int, default=1)
    args = parser.parse_args()
    path = export_symbol(args.symbol, args.period, args.output, args.unit_scale)
    package = json.loads(path.read_text(encoding="utf-8"))
    print(f"wrote {path} ({package['companyType']}, {len(package['records'])} records, sha256 {package['packageSha256'][:12]})")


if __name__ == "__main__":
    main()
