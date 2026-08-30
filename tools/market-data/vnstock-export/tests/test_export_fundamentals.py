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
    assert "DIVIDEND_YIELD" not in by_code  # quarter columns are unit-inconsistent (Feature 011 R-003)
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
    assert fcf[("ANNUAL", 2025, None)]["derivation"] == "kbs-fcf-ocf-plus-capex-v2"
    assert fcf[("ANNUAL", 2025, None)]["periodEnd"] == "2025-12-31"


def test_bank_without_capex_row_yields_no_fcf():
    cash_flow = FakeFrame([
        {"item_id": "operating_cash_flow", "item": "x", "2025-Năm": "1876329000000.0"},
    ])
    records = export_fundamentals.build_metric_records("MBB", FakeFrame([]), FakeFrame([]), cash_flow)
    assert not [r for r in records if r["metricCode"] == "FREE_CASH_FLOW"]


def test_provider_ratios_are_mapped_and_growth_rows_resolved_by_label():
    ratio = FakeFrame([
        {"item_id": "gross_margin", "item": "Tỷ suất lợi nhuận gộp biên", "2026-Q2": "41.8", "2025-Năm": "41.18"},
        {"item_id": "beta", "item": "Beta", "2026-Q2": "0.52", "2025-Năm": "0.52"},
        {"item_id": "total_assets", "item": "Tăng trưởng tổng tài sản", "2026-Q2": "-3.47", "2025-Năm": "-3.15"},
        {"item_id": "ev_ebitda", "item": "EV/EBITDA", "2026-Q2": "26.02", "2025-Năm": "9.89"},
        {"item_id": "cash_return_on_equity", "item": "Dòng tiền từ HĐKD trên VCSH", "2026-Q2": "0.0", "2025-Năm": "25.14"},
    ])
    records = export_fundamentals.pivot_wide_table(ratio, export_fundamentals.RATIO_MAP, "RATIO")
    by_code = {(r["metricCode"], r["periodType"]): r["value"] for r in records}
    assert by_code[("GROSS_MARGIN", "QUARTER")] == "41.8"
    assert by_code[("BETA", "QUARTER")] == "0.52"
    assert ("TOTAL_ASSETS_GROWTH_PERCENT", "QUARTER") not in by_code   # growth rows: annual columns only (v2)
    assert by_code[("TOTAL_ASSETS_GROWTH_PERCENT", "ANNUAL")] == "-3.15"
    assert not [k for k in by_code if k[0] == "EV_EBITDA"]              # provider valuation ratios are never imported
    assert not [k for k in by_code if k[0] == "CASH_RETURN_ON_EQUITY"]  # zero-only cash-flow family is not mapped


def test_dividend_yield_is_taken_as_reported_from_annual_columns_only():
    # VNM probe 2026-08-30: quarter 0.04 / annual 7.92 -- quarter columns mix units, annual is percent.
    ratio = FakeFrame([{"item_id": "dividend_yield", "item": "Tỷ suất cổ tức", "2026-Q2": "0.04", "2025-Năm": "7.92"}])
    records = export_fundamentals.pivot_wide_table(ratio, export_fundamentals.RATIO_MAP, "RATIO")
    assert [(r["periodType"], r["value"]) for r in records] == [("ANNUAL", "7.92")]
    assert "derivation" not in records[0]


def test_ratio_period_scope_contract_v2_vectors():
    quarter = FakeFrame([
        {"item_id": "roe", "item": "ROEA", "2026-Q2": "6.86"},
        {"item_id": "roe_trailling", "item": "ROE 4 quý", "2026-Q2": "26.37"},
        {"item_id": "net_interest_margin_nim", "item": "NIM", "2026-Q2": "1.0"},
        {"item_id": "ps_ratio", "item": "P/S", "2026-Q2": "7.4"},
        {"item_id": "gross_margin", "item": "Biên gộp", "2026-Q2": "41.8"},
    ])
    q = export_fundamentals.pivot_wide_table(quarter, export_fundamentals.RATIO_MAP, "RATIO")
    by_code = {r["metricCode"]: r for r in q}
    assert by_code["ROE"]["value"] == "26.37" and by_code["ROE"]["derivation"] == "kbs-trailing-ratio-as-annualized-v1"
    assert by_code["ROE_TTM"]["value"] == "26.37" and "derivation" not in by_code["ROE_TTM"]
    assert "NIM" not in by_code and "PS" not in by_code          # single-quarter values never emitted
    assert by_code["GROSS_MARGIN"]["value"] == "41.8"
    annual = FakeFrame([
        {"item_id": "roe", "item": "ROEA", "2025-Năm": "26.64"},
        {"item_id": "roe_trailling", "item": "ROE 4 quý", "2025-Năm": "0.0"},
        {"item_id": "net_interest_margin_nim", "item": "NIM", "2025-Năm": "3.89"},
    ])
    a = {r["metricCode"]: r for r in export_fundamentals.pivot_wide_table(annual, export_fundamentals.RATIO_MAP, "RATIO")}
    assert a["ROE"]["value"] == "26.64" and "derivation" not in a["ROE"]
    assert "ROE_TTM" not in a                                     # 0.0 placeholder is not a fact
    assert a["NIM"]["value"] == "3.89"


def test_insurance_and_securities_statement_ids_are_mapped_and_deduped():
    bvh = FakeFrame([
        {"item_id": "total_net_revenue_from_insurance_business", "item": "5. Doanh thu thuần HĐKD BH", "2026-Q2": "9749591088000.0"},
        {"item_id": "profit_after_tax", "item": "29. Lợi nhuận sau thuế", "2026-Q2": "797764930000.0"},
        {"item_id": "revenue", "item": "Doanh thu", "2026-Q2": None},
    ])
    b = {r["metricCode"]: r["value"] for r in export_fundamentals.pivot_wide_table(bvh, export_fundamentals.INCOME_STATEMENT_MAP, "INCOME_STATEMENT")}
    assert b["REVENUE"] == "9749591088000.0" and b["NET_PROFIT"] == "797764930000.0"
    ssi = FakeFrame([
        {"item_id": "revenue_from_securities_business_01_11", "item": "Cộng doanh thu hoạt động", "2025-Năm": "6335823058000.0"},
        {"item_id": "net_profit_from_securities_business_20_50_40_60_61_62", "item": "VII. KẾT QUẢ HOẠT ĐỘNG", "2025-Năm": "2099656023000.0"},
        {"item_id": "net_profit", "item": "XIII. Lợi nhuận sau thuế", "2025-Năm": "1697693169000.0"},
        {"item_id": "profit_after_tax", "item": "duplicate concept", "2025-Năm": "1.0"},
    ])
    r = {x["metricCode"]: x["value"] for x in export_fundamentals.pivot_wide_table(ssi, export_fundamentals.INCOME_STATEMENT_MAP, "INCOME_STATEMENT")}
    assert r["REVENUE"] == "6335823058000.0" and r["OPERATING_PROFIT"] == "2099656023000.0"
    assert r["NET_PROFIT"] == "1697693169000.0"                  # first row wins; never two facts


def test_free_cash_flow_v2_covers_securities_and_insurance_ids_but_not_banks():
    ssi = FakeFrame([
        {"item_id": "net_cash_flows_from_securities_trading_activities", "item": "LCTT HĐKD CK", "2025-Năm": "-7148593105000.0"},
        {"item_id": "payment_for_fixed_assets_constructions_and_other_long_term_assets", "item": "capex", "2025-Năm": "-100000000.0"},
    ])
    fcf = _by(export_fundamentals.build_metric_records("SSI", FakeFrame([]), FakeFrame([]), ssi), "FREE_CASH_FLOW")
    assert fcf[("ANNUAL", 2025, None)]["value"] == "-7148693105000.000000"
    bvh = FakeFrame([
        {"item_id": "operating_cash_flow", "item": "ocf", "2025-Năm": "1000.0"},
        {"item_id": "n_1_payment_for_fixed_assets_constructions_and_other_long_term_assets", "item": "capex", "2025-Năm": "-163378387000.0"},
    ])
    fcf = _by(export_fundamentals.build_metric_records("BVH", FakeFrame([]), FakeFrame([]), bvh), "FREE_CASH_FLOW")
    assert fcf[("ANNUAL", 2025, None)]["value"] == "-163378386000.000000"
    mbb = FakeFrame([
        {"item_id": "operating_cash_flow", "item": "ocf", "2025-Năm": "1000.0"},
        {"item_id": "purchase_of_fixed_assets", "item": "bank capex", "2025-Năm": "-1748106000000.0"},
    ])
    assert not [r for r in export_fundamentals.build_metric_records("MBB", FakeFrame([]), FakeFrame([]), mbb) if r["metricCode"] == "FREE_CASH_FLOW"]


def test_bank_eps_item_id_is_mapped_and_normalized_like_non_bank_eps():
    frame = FakeFrame([{"item_id": "earning_per_share_vnd", "item": "Lãi cơ bản trên cổ phiếu", "2026-Q2": "4050730.0"}])
    records = export_fundamentals.pivot_wide_table(frame, export_fundamentals.INCOME_STATEMENT_MAP, "INCOME_STATEMENT")
    assert records[0]["metricCode"] == "EPS"
    assert records[0]["value"] == "4050.73"
