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
    assert by_code["DIVIDEND_YIELD"] == "0.04"
    assert by_code["EV_EBITDA"] == "26.02"


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

