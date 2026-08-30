import importlib.util
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "export_fundamentals.py"
SPEC = importlib.util.spec_from_file_location("export_fundamentals", MODULE_PATH)
export_fundamentals = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(export_fundamentals)


class FakeFrame:
    def __init__(self, rows):
        self._rows = rows
        self.columns = list(rows[0].keys()) if rows else []

    def iterrows(self):
        for index, row in enumerate(self._rows):
            yield index, row


def test_kbs_eps_is_normalized_to_vnd_per_share_before_packaging():
    frame = FakeFrame([
        {
            "item_id": "earnings_per_share_vnd",
            "item": "EPS",
            "2026-Q2": "1036000.0",
        }
    ])

    records = export_fundamentals.pivot_wide_table(
        frame,
        export_fundamentals.INCOME_STATEMENT_MAP,
        "INCOME_STATEMENT",
    )

    assert records[0]["metricCode"] == "EPS"
    assert records[0]["value"] == "1036.0"


def test_kbs_statement_level_values_are_not_per_share_normalized():
    frame = FakeFrame([
        {
            "item_id": "net_profit",
            "item": "Net profit",
            "2026-Q2": "11055657918.0",
        }
    ])

    records = export_fundamentals.pivot_wide_table(
        frame,
        export_fundamentals.INCOME_STATEMENT_MAP,
        "INCOME_STATEMENT",
    )

    assert records[0]["metricCode"] == "NET_PROFIT"
    assert records[0]["value"] == "11055657918.0"


def test_kbs_ratio_bvps_and_trailing_eps_are_mapped_unscaled():
    frame = FakeFrame([
        {
            "item_id": "book_value_per_share_bvps",
            "item": "BVPS",
            "2026-Q2": "18160.0",
        },
        {
            "item_id": "trailing_eps",
            "item": "Trailing EPS",
            "2026-Q2": "4159.65",
        },
        {
            "item_id": "dividend_yield",
            "item": "Dividend yield",
            "2026-Q2": "0.04",
        },
        {
            "item_id": "ev_ebitda",
            "item": "EV/EBITDA",
            "2026-Q2": "26.02",
        },
    ])

    records = export_fundamentals.pivot_wide_table(
        frame,
        export_fundamentals.RATIO_MAP,
        "RATIO",
    )

    by_code = {r["metricCode"]: r["value"] for r in records}
    assert by_code["BVPS"] == "18160.0"
    assert by_code["TRAILING_EPS"] == "4159.65"
    assert by_code["DIVIDEND_YIELD"] == "4.000000"  # KBS fraction -> percent (Feature 009 R-004.1)
    assert "EV_EBITDA" not in by_code  # provider valuation ratios are never imported (Feature 009 R-003)


def test_kbs_income_statement_revenue_and_operating_profit_are_mapped():
    frame = FakeFrame([
        {
            "item_id": "revenue",
            "item": "Revenue",
            "2026-Q2": "16953231538000.0",
        },
        {
            "item_id": "operating_profit",
            "item": "Operating profit",
            "2026-Q2": "3157752824000.0",
        },
    ])

    records = export_fundamentals.pivot_wide_table(
        frame,
        export_fundamentals.INCOME_STATEMENT_MAP,
        "INCOME_STATEMENT",
    )

    by_code = {r["metricCode"]: r["value"] for r in records}
    assert by_code["REVENUE"] == "16953231538000.0"
    assert by_code["OPERATING_PROFIT"] == "3157752824000.0"


def _by(records, code):
    return {(r["periodType"], r["fiscalYear"], r["fiscalQuarter"]): r for r in records if r["metricCode"] == code}


def test_only_net_revenue_row_is_mapped_to_revenue():
    income = FakeFrame([
        {"item_id": "revenue", "item": "1. Doanh thu bán hàng và cung cấp dịch vụ", "2026-Q2": "16953231538000.0"},
        {"item_id": "revenue", "item": "3. Doanh thu thuần về bán hàng và cung cấp dịch vụ", "2026-Q2": "16968084098000.0"},
    ])
    records = export_fundamentals.build_metric_records("VNM", income, FakeFrame([]), FakeFrame([]))
    revenue = [r for r in records if r["metricCode"] == "REVENUE"]
    assert len(revenue) == 1
    assert revenue[0]["value"] == "16968084098000.0"


def test_ebitda_is_derived_from_margin_times_net_revenue_only_where_both_exist():
    income = FakeFrame([
        {"item_id": "revenue", "item": "3. Doanh thu thuần", "2026-Q2": "16968084098000.0", "2026-Q1": "17045421379000.0"},
    ])
    ratio = FakeFrame([
        {"item_id": "ebitda_net_revenue", "item": "Tỷ lệ lãi EBITDA", "2026-Q2": "21.73", "2025-Q4": "19.62"},
    ])
    records = export_fundamentals.build_metric_records("VNM", income, ratio, FakeFrame([]))
    ebitda = _by(records, "EBITDA")
    assert list(ebitda) == [("QUARTER", 2026, 2)]  # 2026-Q1 has no margin, 2025-Q4 has no revenue
    rec = ebitda[("QUARTER", 2026, 2)]
    assert rec["value"] == "3687164674495.400000"  # 21.73/100 * 16,968,084,098,000
    assert rec["derivation"] == "kbs-ebitda-margin-x-net-revenue-v1"
    assert "kbs-ebitda" in rec["canonicalRecord"]


def test_free_cash_flow_is_ocf_plus_signed_capex_and_accepts_annual_nam_columns():
    cash_flow = FakeFrame([
        {"item_id": "operating_cash_flow", "item": "Lưu chuyển tiền thuần từ HĐKD", "2025-Năm": "8827273177000.0", "2024-Năm": "7887423562000.0"},
        {"item_id": "payment_for_fixed_assets_constructions_and_other_long_term_assets", "item": "1. Tiền chi...", "2025-Năm": "-1456914053000.0"},
    ])
    records = export_fundamentals.build_metric_records("VNM", FakeFrame([]), FakeFrame([]), cash_flow)
    fcf = _by(records, "FREE_CASH_FLOW")
    assert list(fcf) == [("ANNUAL", 2025, None)]  # 2024 has no capex row value -> not derived
    assert fcf[("ANNUAL", 2025, None)]["value"] == "7370359124000.000000"
    assert fcf[("ANNUAL", 2025, None)]["derivation"] == "kbs-fcf-ocf-plus-capex-v1"
    assert fcf[("ANNUAL", 2025, None)]["periodEnd"] == "2025-12-31"


def test_bank_without_capex_row_yields_no_fcf():
    cash_flow = FakeFrame([
        {"item_id": "operating_cash_flow", "item": "x", "2025-Năm": "1876329000000.0"},
    ])
    records = export_fundamentals.build_metric_records("MBB", FakeFrame([]), FakeFrame([]), cash_flow)
    assert not [r for r in records if r["metricCode"] == "FREE_CASH_FLOW"]


def test_provider_ratios_are_mapped_and_growth_rows_resolved_by_label():
    ratio = FakeFrame([
        {"item_id": "gross_margin", "item": "Tỷ suất lợi nhuận gộp biên", "2026-Q2": "41.8"},
        {"item_id": "beta", "item": "Beta", "2026-Q2": "0.52"},
        {"item_id": "total_assets", "item": "Tăng trưởng tổng tài sản", "2026-Q2": "-3.47"},
        {"item_id": "ev_ebitda", "item": "EV/EBITDA", "2026-Q2": "26.02"},
        {"item_id": "cash_return_on_equity", "item": "Dòng tiền từ HĐKD trên VCSH", "2026-Q2": "0.0"},
    ])
    records = export_fundamentals.pivot_wide_table(ratio, export_fundamentals.RATIO_MAP, "RATIO")
    by_code = {r["metricCode"]: r["value"] for r in records}
    assert by_code["GROSS_MARGIN"] == "41.8"
    assert by_code["BETA"] == "0.52"
    assert by_code["TOTAL_ASSETS_GROWTH_PERCENT"] == "-3.47"
    assert "EV_EBITDA" not in by_code          # provider valuation ratios are never imported
    assert "CASH_RETURN_ON_EQUITY" not in by_code  # zero-only cash-flow family is not mapped


def test_dividend_yield_fraction_becomes_percent_with_derivation():
    ratio = FakeFrame([{"item_id": "dividend_yield", "item": "Tỷ suất cổ tức", "2026-Q2": "0.04"}])
    records = export_fundamentals.pivot_wide_table(ratio, export_fundamentals.RATIO_MAP, "RATIO")
    assert records[0]["metricCode"] == "DIVIDEND_YIELD"
    assert records[0]["value"] == "4.000000"
    assert records[0]["derivation"] == "kbs-dividend-yield-fraction-to-percent"


def test_bank_eps_item_id_is_mapped_and_normalized_like_non_bank_eps():
    frame = FakeFrame([{"item_id": "earning_per_share_vnd", "item": "Lãi cơ bản trên cổ phiếu", "2026-Q2": "4050730.0"}])
    records = export_fundamentals.pivot_wide_table(frame, export_fundamentals.INCOME_STATEMENT_MAP, "INCOME_STATEMENT")
    assert records[0]["metricCode"] == "EPS"
    assert records[0]["value"] == "4050.73"
