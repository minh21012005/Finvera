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
    assert mod.reusable_share_facts(tmp_path, 30, full_refresh=False) == {}
    mod.save_share_cache(tmp_path, existing["records"], {}, {})
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
    # Hồ sơ thiếu dữ liệu phải được thử lại ngay ở đợt refresh tiếp theo.
    existing["records"].append({"symbol": "MBB", "sharesOutstanding": None,
                                "qualityReason": "SHARES_OUTSTANDING_UNAVAILABLE"})
    (tmp_path / "equity-profile.json").write_text(json.dumps(existing), encoding="utf-8")
    assert mod.reusable_share_facts(tmp_path, 30, full_refresh=False) == {"VNM": (2089955445, None)}
    cache = mod.load_share_cache(tmp_path)
    cache["VNM"]["fetchedAt"] = datetime(2026, 1, 1, tzinfo=UTC).isoformat()
    mod.save_share_cache(tmp_path, existing["records"], reusable, cache)
    assert mod.reusable_share_facts(tmp_path, 30, full_refresh=False) == {}   # older than max age -> refetch


def test_repackaging_does_not_renew_share_age(tmp_path, monkeypatch):
    import json
    from datetime import datetime, UTC, timedelta
    class Clock(datetime):
        instant = datetime(2026, 1, 1, tzinfo=UTC)
        @classmethod
        def now(cls, tz=None):
            return cls.instant
    monkeypatch.setattr(mod, "datetime", Clock)
    records = [{"symbol": "ACB", "sharesOutstanding": 100, "qualityReason": None}]
    def write():
        (tmp_path / "equity-profile.json").write_text(json.dumps(mod.build_package(records, mod.TOOL_VERSION)))
    write()
    mod.save_share_cache(tmp_path, records, {}, {})
    original = mod.load_share_cache(tmp_path)
    Clock.instant += timedelta(days=20)
    reusable = mod.reusable_share_facts(tmp_path, 30, False)
    assert reusable == {"ACB": (100, None)}
    write()
    mod.save_share_cache(tmp_path, records, reusable, original)
    assert mod.load_share_cache(tmp_path) == original
    Clock.instant += timedelta(days=20)
    assert mod.reusable_share_facts(tmp_path, 30, False) == {}
    # Sidecar không bị importer nhầm là package dữ liệu.
    assert list(tmp_path.glob("equity-profile*.json")) == [tmp_path / "equity-profile.json"]


def test_cache_requires_matching_value_and_valid_fetch_time(tmp_path):
    import json
    records = [{"symbol": "ACB", "sharesOutstanding": 100, "qualityReason": None}]
    (tmp_path / "equity-profile.json").write_text(json.dumps(mod.build_package(records, mod.TOOL_VERSION)))
    mod.save_share_cache(tmp_path, records, {}, {})
    cache = mod.load_share_cache(tmp_path)
    cache["ACB"]["sharesOutstanding"] = 200
    mod.save_share_cache(tmp_path, records, {"ACB": (100, None)}, cache)
    assert mod.reusable_share_facts(tmp_path, 30, False) == {}
    cache["ACB"]["sharesOutstanding"] = 100
    for timestamp in ("invalid", "2026-01-01T00:00:00", "2999-01-01T00:00:00Z"):
        cache["ACB"]["fetchedAt"] = timestamp
        mod.save_share_cache(tmp_path, records, {"ACB": (100, None)}, cache)
        assert mod.reusable_share_facts(tmp_path, 30, False) == {}


def test_profile_main_uses_shared_pacing_and_preserves_cache(tmp_path, monkeypatch):
    import sys
    calls, installed = [], []
    monkeypatch.setattr(sys, "argv", ["export_equity_profile.py", "--output", str(tmp_path),
                                     "--requests-per-minute", "24", "--workers", "2"])
    monkeypatch.setattr(mod.provider_retry, "install", installed.append)
    monkeypatch.setattr(mod, "fetch_universe", universe)
    monkeypatch.setattr(mod, "fetch_delisted", lambda: FakeFrame([]))
    def lookup(symbol):
        calls.append(symbol)
        return {"issue_share": 100}
    monkeypatch.setattr(mod, "fetch_overview", lookup)
    real_collect = mod.provider_retry.collect_available
    def collect(subjects, action, **kwargs):
        assert kwargs["workers"] == 2
        return real_collect(subjects, action, **kwargs)
    monkeypatch.setattr(mod.provider_retry, "collect_available", collect)
    mod.main()
    assert installed == [24]
    assert sorted(calls) == ["MBB", "VNM"]
    cache = mod.load_share_cache(tmp_path)
    mod.main()
    assert sorted(calls) == ["MBB", "VNM"]
    assert mod.load_share_cache(tmp_path) == cache


def test_provider_delisted_symbols_become_delisted_records_without_overview_calls():
    # ADR-0013 / specs/021 R-007 (DAN/DVT): a dead symbol must not stay LISTED in the product.
    frame = FakeFrame([
        {"symbol": "DAN", "type": "STOCK", "exchange": "DELISTED", "organ_name": "Nha Da Nang"},
        {"symbol": "A+ Fund", "type": "STOCK", "exchange": "DELISTED", "organ_name": "Quy dau tu"},
    ])
    records = mod.build_delisted_records(frame, "2026-08-31")
    assert [r["symbol"] for r in records] == ["DAN"]        # import-layer symbol shape guarded
    assert records[0]["listingStatus"] == "DELISTED"
    assert records[0]["sharesOutstanding"] is None          # unknown; the importer keeps the last known count
    assert records[0]["companyNameVi"] == "Nha Da Nang"
