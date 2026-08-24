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
