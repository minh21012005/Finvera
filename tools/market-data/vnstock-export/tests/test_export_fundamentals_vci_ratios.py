"""Feature 019 — derived fundamental ratios (contract vci-derived-ratios-v1) against the
2026-08-31 fixtures. Expected values are recomputed here with independent Decimal arithmetic
straight from the fixture statement lines (not through the exporter's helpers), so a formula
regression on either side surfaces as a mismatch."""
import json
from decimal import Decimal
from pathlib import Path

import pytest

import export_fundamentals_vci as vci

FIXTURES = Path(__file__).parent / "fixtures" / "vci"


def fixture(symbol: str):
    return json.loads((FIXTURES / f"{symbol}.json").read_text(encoding="utf-8"))


def line(symbol: str, dataset: str, item_id: str, column: str) -> Decimal:
    ds = fixture(symbol)["datasets"][dataset]
    for row in ds["rows"]:
        if row.get("item_id") == item_id and row.get(column) is not None:
            return Decimal(str(row[column]))
    raise AssertionError(f"{symbol} {dataset} {item_id} {column} missing")


def recs(symbol: str, period: str):
    frames = vci.frames_from_fixture(fixture(symbol), period)
    return vci.build_metric_records(symbol, frames, period)


def get(records, code, year, quarter=None):
    matches = [r for r in records if r["metricCode"] == code and r["fiscalYear"] == year and r["fiscalQuarter"] == quarter]
    assert len(matches) <= 1
    return matches[0] if matches else None


def val(record):
    return Decimal(record["value"])


Q6 = Decimal("0.000001")


def test_vnm_annual_liquidity_leverage_and_coverage_match_fixture_arithmetic():
    r = recs("VNM", "year")
    ca = line("VNM", "balance_sheet:year", "current_assets", "2025")
    cl = line("VNM", "balance_sheet:year", "current_liabilities", "2025")
    inv = line("VNM", "balance_sheet:year", "inventories_net", "2025")
    cash = line("VNM", "balance_sheet:year", "cash_and_cash_equivalents", "2025")
    liab = line("VNM", "balance_sheet:year", "liabilities", "2025")
    eq = line("VNM", "balance_sheet:year", "owners_equity", "2025")
    assets = line("VNM", "balance_sheet:year", "total_assets", "2025")
    current = get(r, "CURRENT_RATIO", 2025)
    assert val(current) == (ca / cl).quantize(Q6)
    assert str(val(current)).startswith("1.9579")  # research R-004: 36,261/18,520
    assert current["derivation"] == "vci-current-ratio-v1"
    assert val(get(r, "QUICK_RATIO", 2025)) == ((ca - inv) / cl).quantize(Q6)
    assert val(get(r, "CASH_RATIO", 2025)) == (cash / cl).quantize(Q6)
    assert val(get(r, "LIABILITIES_TO_EQUITY", 2025)) == (liab / eq * 100).quantize(Q6)
    assert val(get(r, "EQUITY_TO_ASSETS", 2025)) == (eq / assets * 100).quantize(Q6)
    pre_tax = line("VNM", "income_statement:year", "net_accounting_profit_loss_before_tax", "2025")
    interest = abs(line("VNM", "income_statement:year", "interest_expenses", "2025"))
    coverage = get(r, "INTEREST_COVERAGE", 2025)
    assert val(coverage) == ((pre_tax + interest) / interest).quantize(Q6)
    assert coverage["derivation"] == "vci-interest-coverage-v1"


def test_vnm_annual_flow_ratios_use_two_point_average_balances():
    r = recs("VNM", "year")
    revenue = line("VNM", "income_statement:year", "net_sales", "2025")
    cogs = abs(line("VNM", "income_statement:year", "cost_of_sales", "2025"))
    for code, num, item in (("TOTAL_ASSET_TURNOVER", revenue, "total_assets"),
                            ("INVENTORY_TURNOVER", cogs, "inventories_net"),
                            ("RECEIVABLES_TURNOVER", revenue, "trade_accounts_receivable")):
        end = line("VNM", "balance_sheet:year", item, "2025")
        prior = line("VNM", "balance_sheet:year", item, "2024")
        rec = get(r, code, 2025)
        assert val(rec) == (num / ((prior + end) / 2)).quantize(Q6), code
        assert not rec["derivation"].endswith("-end"), code
    # ROCE: EBIT over average capital employed, percent
    pre_tax = line("VNM", "income_statement:year", "net_accounting_profit_loss_before_tax", "2025")
    interest = abs(line("VNM", "income_statement:year", "interest_expenses", "2025"))
    cap = {y: line("VNM", "balance_sheet:year", "total_assets", y) - line("VNM", "balance_sheet:year", "current_liabilities", y)
           for y in ("2024", "2025")}
    roce = get(r, "ROCE", 2025)
    assert val(roce) == ((pre_tax + interest) / ((cap["2024"] + cap["2025"]) / 2) * 100).quantize(Q6)
    assert roce["derivation"] == "vci-roce-v1"
    # growth against the prior year-end
    assets = {y: line("VNM", "balance_sheet:year", "total_assets", y) for y in ("2024", "2025")}
    growth = get(r, "TOTAL_ASSETS_GROWTH_PERCENT", 2025)
    assert val(growth) == ((assets["2025"] - assets["2024"]) / assets["2024"] * 100).quantize(Q6)
    assert growth["derivation"] == "vci-balance-growth-yoy-v1"


def test_mbb_bank_ratios_match_fixture_arithmetic_and_magnitudes():
    r = recs("MBB", "year")
    oi = line("MBB", "income_statement:year", "total_operating_income", "2025")
    admin = abs(line("MBB", "income_statement:year", "general_and_admin_expenses", "2025"))
    loans = line("MBB", "balance_sheet:year", "loans_and_advances_to_customers", "2025")
    deposits = line("MBB", "balance_sheet:year", "deposits_from_customers", "2025")
    cir = get(r, "COST_INCOME_RATIO", 2025)
    assert val(cir) == (admin / oi * 100).quantize(Q6)
    assert Decimal("25") < val(cir) < Decimal("45")  # research R-004: 29.07 %
    ldr = get(r, "LOAN_TO_DEPOSIT", 2025)
    assert val(ldr) == (loans / deposits * 100).quantize(Q6)
    assert Decimal("60") < val(ldr) < Decimal("130")  # research R-004: 117.65 %
    nii = line("MBB", "income_statement:year", "net_interest_income", "2025")
    ea = {}
    for year in ("2024", "2025"):
        ea[year] = sum(line("MBB", "balance_sheet:year", item, year) for item in (
            "balances_with_other_credit_institutions", "placements_with_and_loans_to_other_credit_institutions",
            "trading_securities_net", "investment_securities", "loans_and_advances_to_customers_net"))
    nim = get(r, "NIM", 2025)
    assert val(nim) == (nii / ((ea["2024"] + ea["2025"]) / 2) * 100).quantize(Q6)
    assert Decimal("2") < val(nim) < Decimal("6")
    assert nim["derivation"] == "vci-nim-earning-assets-v1"
    # banks: no liquidity ratios, no coverage, no inventory/receivables turnover, no ROCE (FR-005)
    for code in ("CURRENT_RATIO", "QUICK_RATIO", "CASH_RATIO", "INTEREST_COVERAGE",
                 "INVENTORY_TURNOVER", "RECEIVABLES_TURNOVER", "ROCE", "DEBT_TO_ASSETS"):
        assert get(r, code, 2025) is None, code
    assert val(get(r, "LIABILITIES_TO_EQUITY", 2025)) == (
        line("MBB", "balance_sheet:year", "total_liabilities", "2025")
        / line("MBB", "balance_sheet:year", "owners_equity", "2025") * 100).quantize(Q6)


def test_broker_and_insurer_presence_matrix():
    ssi = recs("SSI", "year")
    current = get(ssi, "CURRENT_RATIO", 2025)
    assert str(val(current)).startswith("1.44")  # research R-004
    assert get(ssi, "DEBT_TO_ASSETS", 2025) is not None  # brokers carry borrowings
    for code in ("INVENTORY_TURNOVER", "RECEIVABLES_TURNOVER", "ROCE", "INTEREST_COVERAGE",
                 "NIM", "COST_INCOME_RATIO", "LOAN_TO_DEPOSIT", "QUICK_RATIO"):
        assert get(ssi, code, 2025) is None, code
    bvh = recs("BVH", "year")
    assert get(bvh, "CURRENT_RATIO", 2025) is not None
    assert get(bvh, "QUICK_RATIO", 2025) is not None  # insurer inventory id resolves
    assert get(bvh, "LIABILITIES_TO_EQUITY", 2025) is not None
    for code in ("NIM", "COST_INCOME_RATIO", "LOAN_TO_DEPOSIT", "ROCE", "INTEREST_COVERAGE", "DEBT_TO_ASSETS"):
        assert get(bvh, code, 2025) is None, code


def test_quarterly_flow_ratios_are_ttm_with_five_point_averages():
    r = recs("VNM", "quarter")
    quarters = ["2025-Q3", "2025-Q4", "2026-Q1", "2026-Q2"]
    revenue_ttm = sum(line("VNM", "income_statement:quarter", "net_sales", c) for c in quarters)
    balance_cols = ["2025-Q2"] + quarters
    avg_assets = sum(line("VNM", "balance_sheet:quarter", "total_assets", c) for c in balance_cols) / 5
    turnover = get(r, "TOTAL_ASSET_TURNOVER", 2026, 2)
    assert val(turnover) == (revenue_ttm / avg_assets).quantize(Q6)
    assert turnover["derivation"] == "vci-asset-turnover-v1"  # 5-point average, no -end suffix
    # liquidity is period-end on the quarter column
    ca = line("VNM", "balance_sheet:quarter", "current_assets", "2026-Q2")
    cl = line("VNM", "balance_sheet:quarter", "current_liabilities", "2026-Q2")
    assert val(get(r, "CURRENT_RATIO", 2026, 2)) == (ca / cl).quantize(Q6)
    # growth is annual-only
    assert get(r, "TOTAL_ASSETS_GROWTH_PERCENT", 2026, 2) is None
    # the oldest quarter has no complete TTM window -> flow ratios absent there (missing, never zero)
    oldest = min((r_["fiscalYear"], r_["fiscalQuarter"]) for r_ in r if r_["periodType"] == "QUARTER")
    assert get(r, "TOTAL_ASSET_TURNOVER", oldest[0], oldest[1]) is None


def test_every_derived_ratio_carries_a_rule_id_and_catalog_unit_convention():
    percent_codes = {"ROCE", "DEBT_TO_ASSETS", "LIABILITIES_TO_EQUITY", "EQUITY_TO_ASSETS",
                     "TOTAL_ASSETS_GROWTH_PERCENT", "EQUITY_GROWTH_PERCENT", "NIM",
                     "COST_INCOME_RATIO", "LOAN_TO_DEPOSIT"}
    ratio_codes = {"CURRENT_RATIO", "QUICK_RATIO", "CASH_RATIO", "INTEREST_COVERAGE",
                   "TOTAL_ASSET_TURNOVER", "INVENTORY_TURNOVER", "RECEIVABLES_TURNOVER"}
    seen = set()
    for symbol in ("VNM", "MBB", "BVH", "SSI"):
        for rec in recs(symbol, "year"):
            code = rec["metricCode"]
            if code in percent_codes | ratio_codes:
                assert rec.get("derivation", "").startswith("vci-"), (symbol, code)
                seen.add(code)
                if code in percent_codes:
                    assert abs(Decimal(rec["value"])) < Decimal("10000"), (symbol, code, rec["value"])
    assert "PS" not in seen and "DIVIDEND_YIELD" not in seen and "BETA" not in seen
    # the full contract table is exercised across the four company types
    assert seen == percent_codes | ratio_codes


def test_dividend_per_share_from_cash_dividends_for_every_company_type():
    # Feature 022: DPS = |dividends_paid| / shares feeds the summary DIVIDEND_PER_SHARE_TTM and the
    # valuation DIVIDEND_YIELD chain that went dark when the KBS ratio frame was retired.
    for symbol in ("VNM", "MBB", "BVH", "SSI"):
        r = recs(symbol, "year")
        raw = None
        for row in fixture(symbol)["datasets"]["cash_flow:year"]["rows"]:
            if row.get("item_id") == "dividends_paid" and row.get("2025") is not None:
                raw = Decimal(str(row["2025"]))
                break
        dps = get(r, "DIVIDEND_PER_SHARE", 2025)
        if raw is None:
            assert dps is None, symbol            # absent line stays absent -- never fabricated as 0
            continue
        assert dps is not None, symbol
        assert dps["derivation"] == "vci-dps-cash-dividends-over-shares-v1"
        assert Decimal("0") <= val(dps) < Decimal("20000"), (symbol, dps["value"])  # VND per share
    vnm = recs("VNM", "year")
    paid = abs(line("VNM", "cash_flow:year", "dividends_paid", "2025"))
    shares = (line("VNM", "balance_sheet:year", "paid_in_capital", "2025")
              + line("VNM", "balance_sheet:year", "treasury_shares", "2025")) / Decimal(10000)
    assert val(get(vnm, "DIVIDEND_PER_SHARE", 2025)) == (paid / shares).quantize(Q6)
