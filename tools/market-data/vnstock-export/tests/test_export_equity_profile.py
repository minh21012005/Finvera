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
    # Real VNM overview values (VCI, 2026-08-31): issue_share cross-checked vs market_cap / price.
    lookup = {"VNM": {"issue_share": 2089955445, "market_cap": 130204224223500.0, "current_price": 62300.0},
              "MBB": None}
    records = {r["symbol"]: r for r in mod.build_records(universe(), "2026-08-30", lookup.get)}
    assert records["VNM"]["sharesOutstanding"] == 2089955445
    assert "freeFloatRatio" not in records["VNM"]          # provider has no free float (R-002)
    assert records["VNM"]["qualityReason"] is None
    # overview unavailable -> no fabricated count, reason retained
    assert records["MBB"]["sharesOutstanding"] is None
    assert records["MBB"]["qualityReason"] == "SHARES_OUTSTANDING_UNAVAILABLE"
    assert '"sharesOutstanding":2089955445' in records["VNM"]["canonicalRecord"]


def test_non_positive_shares_are_treated_as_unavailable():
    records = mod.build_records(universe(), "2026-08-30", lambda s: {"issue_share": 0})
    assert all(r["sharesOutstanding"] is None for r in records)


def test_share_count_inconsistent_with_market_cap_is_flagged_not_dropped():
    # VCI cross-check (ADR-0013): market_cap / current_price implies the count; > 1 % gap flags it.
    inconsistent = {"issue_share": 3_000_000_000, "market_cap": 130204224223500.0, "current_price": 62300.0}
    shares, reason = mod.share_fields(inconsistent)
    assert shares == 3_000_000_000
    assert reason == "SHARES_OUTSTANDING_UNVERIFIED"
    consistent = {"issue_share": 2089955445, "market_cap": 130204224223500.0, "current_price": 62300.0}
    assert mod.share_fields(consistent) == (2089955445, None)
    # no market_cap/price on the page -> count kept, nothing to verify against, no false flag
    assert mod.share_fields({"issue_share": 2089955445}) == (2089955445, None)


def test_recent_package_is_reused_and_only_new_symbols_are_fetched(tmp_path):
    import json
    from datetime import UTC, datetime
    existing = mod.build_package([{"symbol": "VNM", "sharesOutstanding": 2089955445, "qualityReason": None,
                                   "canonicalRecord": "", "companyNameVi": "x", "companyNameEn": None,
                                   "effectiveFrom": "2026-08-01", "listingStatus": "LISTED"}], mod.TOOL_VERSION)
    (tmp_path / "equity-profile.json").write_text(json.dumps(existing), encoding="utf-8")
    reusable = mod.reusable_share_facts(tmp_path, 30, full_refresh=False)
    assert reusable == {"VNM": (2089955445, None)}
    assert mod.reusable_share_facts(tmp_path, 30, full_refresh=True) == {}
    calls = []

    def lookup(symbol):
        calls.append(symbol)
        return {"issue_share": 8054999909, "market_cap": 8054999909 * 21050.0, "current_price": 21050.0}

    records = {r["symbol"]: r for r in mod.build_records(universe(), "2026-08-30", lookup, share_lookup=reusable.get)}
    assert calls == ["MBB"]                                   # VNM served from the package, MBB fetched
    assert records["VNM"]["sharesOutstanding"] == 2089955445
    assert records["MBB"]["sharesOutstanding"] == 8054999909
    stale = dict(existing, generatedAt=datetime(2026, 1, 1, tzinfo=UTC).isoformat().replace("+00:00", "Z"))
    (tmp_path / "equity-profile.json").write_text(json.dumps(stale), encoding="utf-8")
    assert mod.reusable_share_facts(tmp_path, 30, full_refresh=False) == {}   # older than max age -> refetch
