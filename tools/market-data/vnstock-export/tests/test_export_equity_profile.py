import importlib.util
from pathlib import Path

MODULE_PATH = Path(__file__).parents[1] / "export_equity_profile.py"
SPEC = importlib.util.spec_from_file_location("export_equity_profile", MODULE_PATH)
mod = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(mod)


class FakeFrame:
    def __init__(self, rows):
        self._rows = rows
        self.columns = list(rows[0].keys()) if rows else []

    def iterrows(self):
        for i, r in enumerate(self._rows):
            yield i, r


def universe():
    return FakeFrame([
        {"symbol": "VNM", "type": "stock", "exchange": "HOSE", "organ_name": "Vinamilk", "en_organ_name": "Vinamilk JSC"},
        {"symbol": "MBB", "type": "stock", "exchange": "HOSE", "organ_name": "MB Bank", "en_organ_name": None},
    ])


def test_outstanding_shares_from_overview_clear_the_quality_reason():
    lookup = {"VNM": {"outstanding_shares": 2089955445, "free_float_percentage": 35.5},
              "MBB": None}
    records = {r["symbol"]: r for r in mod.build_records(universe(), "2026-08-30", lookup.get)}
    assert records["VNM"]["sharesOutstanding"] == 2089955445
    assert records["VNM"]["freeFloatRatio"] == "35.500000"
    assert records["VNM"]["qualityReason"] is None
    # overview unavailable -> no fabricated count, reason retained
    assert records["MBB"]["sharesOutstanding"] is None
    assert records["MBB"]["qualityReason"] == "SHARES_OUTSTANDING_UNAVAILABLE"
    assert '"sharesOutstanding":2089955445' in records["VNM"]["canonicalRecord"]


def test_non_positive_shares_are_treated_as_unavailable():
    records = mod.build_records(universe(), "2026-08-30", lambda s: {"outstanding_shares": 0})
    assert all(r["sharesOutstanding"] is None for r in records)
