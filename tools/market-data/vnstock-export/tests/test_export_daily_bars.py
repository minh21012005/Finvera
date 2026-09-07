import importlib.util
import json
import sys
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "export_daily_bars.py"
SPEC = importlib.util.spec_from_file_location("export_daily_bars", MODULE_PATH)
export_daily_bars = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(export_daily_bars)

sys.path.insert(0, str(Path(__file__).parents[1]))
ALL_SYMBOLS_PATH = Path(__file__).parents[1] / "export_all_symbols.py"
ALL_SYMBOLS_SPEC = importlib.util.spec_from_file_location("export_all_symbols", ALL_SYMBOLS_PATH)
export_all_symbols = importlib.util.module_from_spec(ALL_SYMBOLS_SPEC)
assert ALL_SYMBOLS_SPEC.loader is not None
ALL_SYMBOLS_SPEC.loader.exec_module(export_all_symbols)


def test_no_trade_filler_bars_are_dropped_but_real_odd_lot_trades_are_kept():
    # Feature 021 R-006: A32 2025 -- VCI fills non-traded sessions with a flat zero-volume bar.
    def flat(day, volume):
        return {"time": f"{day} 00:00:00", "open": "31.62", "high": "31.62", "low": "31.62",
                "close": "31.62", "volume": volume}

    records = export_daily_bars.package_records(
        [flat("2025-01-06", "0"), flat("2025-01-07", None), flat("2025-01-08", "1"), flat("2025-01-09", "225")],
        "A32")

    assert [r["tradingDate"] for r in records] == ["2025-01-08", "2025-01-09"]  # no-trade fillers dropped
    assert records[0]["volume"] == "1.000000"                                    # a 1-share trade is a real trade


def test_records_carry_the_provider_adjusted_label():
    # ADR-0013: VCI serves corporate-action-adjusted series; the label must say so, not RAW.
    rows = [{"time": "2026-08-24 00:00:00", "open": "62.5", "high": "63.0", "low": "62.0", "close": "62.3", "volume": "1000"}]
    assert export_daily_bars.package_records(rows, "VNM")[0]["adjustmentStatus"] == "PROVIDER_ADJUSTED"


def test_current_board_unit_ohlcv_schema_normalizes_prices_without_exporting_reference_price():
    rows = [{
        "time": "2026-08-24 00:00:00",
        "open": "208.0",
        "high": "214.9",
        "low": "208.0",
        "close": "214.5",
        "volume": "3586200",
    }]

    record = export_daily_bars.package_records(rows, "VIC")[0]

    assert record["open"] == "208000.000000"
    assert record["high"] == "214900.000000"
    assert record["low"] == "208000.000000"
    assert record["close"] == "214500.000000"
    assert "referencePrice" not in record
    assert record["valueVnd"] == "769239900000.000000"


def test_reference_like_columns_are_ignored_for_historical_vnstock_bars():
    rows = [{
        "time": "2026-08-24 00:00:00",
        "open": "208.0",
        "high": "214.9",
        "low": "208.0",
        "close": "214.5",
        "reference_price": "212.0",
        "volume": "3586200",
    }]

    record = export_daily_bars.package_records(rows, "VIC")[0]

    assert "referencePrice" not in record


def test_daily_bar_tool_version_changes_when_canonical_price_unit_changes():
    # 1.0.0: VCI source under ADR-0013 -- a version change forces the full universe re-export.
    assert export_daily_bars.TOOL_VERSION == "1.0.0"
    assert export_daily_bars.SOURCE == "VNSTOCK_VCI"


def test_full_universe_checkpoint_does_not_skip_old_daily_bar_tool_version(tmp_path):
    symbol = "VIC"
    path = tmp_path / export_daily_bars.output_filename(symbol)
    path.write_text(json.dumps({
        "toolVersion": "0.1.0",
        "records": [],
    }), encoding="utf-8")

    class Args:
        start = "2024-01-01"
        end = "2026-08-24"
        full_refresh = False
        output = tmp_path

    entry = {
        "daily_bars": "done",
        "daily_bars_range": ["2024-01-01", "2026-08-24"],
    }

    assert not export_all_symbols.daily_bars_current(symbol, entry, Args())


def test_a_symbol_that_did_not_trade_on_the_end_date_still_counts_as_fetched(tmp_path):
    """Feature 026 R-009 (Q-62): coverage is what was asked for, not whether the symbol traded.

    This replaces `..._does_not_skip_file_missing_requested_end_date`, which required a bar dated
    `--end`. A delisted or illiquid symbol can never produce one: 603 of 1,522 symbols held a
    complete package whose newest session predates `--end` (ART 2022-11-18), so they were never
    finished and were re-fetched on every re-run of the same command.
    """
    def write(symbol, range_end, latest):
        path = tmp_path / export_daily_bars.output_filename(symbol)
        path.write_text(json.dumps({
            "toolVersion": export_daily_bars.TOOL_VERSION,
            "rangeStart": "2024-01-01", "rangeEnd": range_end,
            "records": [{"tradingDate": latest}],
        }), encoding="utf-8")

    class Args:
        start = "2024-01-01"
        end = "2026-08-25"
        full_refresh = False
        output = tmp_path

    def entry(range_end):
        return {"daily_bars": "done", "daily_bars_range": ["2024-01-01", range_end]}

    # Asked through 2026-08-25; the provider's newest session is older. That is its answer.
    write("ADG", "2026-08-25", "2026-08-24")
    assert export_all_symbols.daily_bars_current("ADG", entry("2026-08-25"), Args()) is True

    # A symbol that stopped trading in 2022 settles for this --end instead of looping forever.
    write("ART", "2026-08-25", "2022-11-18")
    assert export_all_symbols.daily_bars_current("ART", entry("2026-08-25"), Args()) is True

    # Freshness is unchanged: when --end moves on, the entry goes stale and is re-fetched.
    write("ADG", "2026-08-24", "2026-08-24")
    assert export_all_symbols.daily_bars_current("ADG", entry("2026-08-24"), Args()) is False

    # The file, not the checkpoint, is the evidence: a checkpoint claiming a window the package on
    # disk does not carry is not current, and neither is a package with no sessions at all.
    write("ADG", "2026-08-24", "2026-08-24")
    assert export_all_symbols.daily_bars_current("ADG", entry("2026-08-25"), Args()) is False
    (tmp_path / export_daily_bars.output_filename("EMP")).write_text(json.dumps({
        "toolVersion": export_daily_bars.TOOL_VERSION,
        "rangeStart": "2024-01-01", "rangeEnd": "2026-08-25", "records": [],
    }), encoding="utf-8")
    assert export_all_symbols.daily_bars_current("EMP", entry("2026-08-25"), Args()) is False


def test_full_universe_reexport_drops_records_from_old_daily_bar_tool_version(tmp_path, monkeypatch):
    symbol = "VIC"
    path = tmp_path / export_daily_bars.output_filename(symbol)
    path.write_text(json.dumps({
        "toolVersion": "0.1.0",
        "records": [{
            "adjustmentStatus": "RAW",
            "canonicalRecord": "{}",
            "close": "214.500000",
            "high": "214.900000",
            "low": "208.000000",
            "observedAt": "2026-08-23T08:00:00Z",
            "open": "208.000000",
            "symbol": symbol,
            "tradingDate": "2026-08-23",
            "valueVnd": "769239900.000000",
            "volume": "3586200.000000",
        }],
    }), encoding="utf-8")

    def fake_fetch_rows(symbol_arg, start, end):
        assert symbol_arg == symbol
        assert start == "2026-08-01"
        assert end == "2026-08-24"
        return [{
            "time": f"2026-08-{day:02d} 00:00:00",
            "open": "208.0",
            "high": "214.9",
            "low": "208.0",
            "close": "214.5",
            "volume": "3586200",
        } for day in range(1, 21)]

    monkeypatch.setattr(export_all_symbols.export_daily_bars, "fetch_rows", fake_fetch_rows)

    export_all_symbols.export_daily_bars_for(
        symbol=symbol,
        start="2026-08-01",
        end="2026-08-24",
        output=tmp_path,
        lookback_days=90,
        full_refresh=False,
    )

    package = json.loads(path.read_text(encoding="utf-8"))

    assert package["toolVersion"] == export_daily_bars.TOOL_VERSION
    assert package["records"][0]["tradingDate"] == "2026-08-01"
    assert package["records"][-1]["tradingDate"] == "2026-08-20"
    assert package["records"][0]["close"] == "214500.000000"
    assert "referencePrice" not in package["records"][0]


class _FakeOhlcvFrame:
    def __init__(self, rows):
        self._rows = rows
        self.columns = ["time", "open", "high", "low", "close", "volume"]

    @property
    def loc(self):
        return self

    def __getitem__(self, _key):
        return self

    def to_dict(self, _kind):
        return list(self._rows)


def test_fetch_rows_pads_the_end_and_clamps_both_ends_of_the_vci_response(monkeypatch):
    """Feature 011 R-001 (end padding) + Feature 021 R-002: VCI also returns a buffer of sessions
    BEFORE the requested start, which must never leak into the package."""
    import types
    seen = {}

    def bar(day):
        return {"time": f"{day} 07:00:00", "open": 62.5, "high": 63.0, "low": 62.0, "close": 62.3, "volume": 1000}

    class _Quote:
        def __init__(self, symbol, source):
            seen["source"] = source

        def history(self, start, end, interval):
            seen["end"] = end
            return _FakeOhlcvFrame([bar("2026-08-20"), bar("2026-08-27"), bar("2026-08-28"), bar("2026-09-01")])

    monkeypatch.setitem(sys.modules, "vnstock", types.SimpleNamespace(Quote=_Quote))
    rows = export_daily_bars.fetch_rows("VNM", "2026-08-24", "2026-08-30")
    assert seen["source"] == "vci"
    assert seen["end"] == "2026-09-02"                      # 3-day padding
    # 08-20 (VCI pre-start buffer) and 09-01 (after end) both dropped
    assert [r["time"][:10] for r in rows] == ["2026-08-27", "2026-08-28"]


def test_earlier_start_than_existing_file_triggers_a_full_range_refetch(tmp_path, monkeypatch):
    """Q-37: moving --start from 2024-01-01 back to 2023-01-01 must not be swallowed by the lookback window."""
    def bars(prefix, n):
        return [{"time": f"{prefix}-{d:02d} 00:00:00", "open": "1", "high": "1", "low": "1", "close": "1", "volume": "1"} for d in range(1, n + 1)]
    existing = export_daily_bars.build_package(export_daily_bars.package_records(bars("2024-01", 25), "VNM"),
                                               "VNM", "2024-01-01", "2026-08-20", export_daily_bars.TOOL_VERSION)
    (tmp_path / export_daily_bars.output_filename("VNM")).write_text(json.dumps(existing), encoding="utf-8")
    seen = {}

    def fake_fetch(symbol, start, end):
        seen["start"] = start
        return bars("2023-01", 25)

    monkeypatch.setattr(export_all_symbols.export_daily_bars, "fetch_rows", fake_fetch)
    export_all_symbols.export_daily_bars_for("VNM", "2023-01-01", "2026-08-30", tmp_path, 90, False)
    assert seen["start"] == "2023-01-01"                      # whole range, not the lookback window
    package = json.loads((tmp_path / export_daily_bars.output_filename("VNM")).read_text(encoding="utf-8"))
    assert package["rangeStart"] == "2023-01-01"
    assert all(r["tradingDate"].startswith("2023-01") for r in package["records"])  # old 2024 rows not kept blindly


def test_too_few_sessions_raises_its_own_recheckable_failure_class():
    """Feature 026 R-008 (Q-61): a newly listed symbol is not a symbol the provider cannot serve.

    Measured 2026-09-07: LPS and DMX both failed the MIN_RECORDS guard with a bare ValueError on
    2026-09-01 and were settled permanently; DMX had crossed the threshold by 2026-09-07 (20
    sessions, exportable) but the checkpoint would never have asked again.
    """
    import pytest

    def bars(n):
        return [{"time": f"2026-08-{d:02d} 00:00:00", "open": "1", "high": "1", "low": "1",
                 "close": "1", "volume": "1"} for d in range(1, n + 1)]

    short = export_daily_bars.package_records(bars(export_daily_bars.MIN_RECORDS - 1), "LPS")
    with pytest.raises(export_daily_bars.InsufficientSessions) as raised:
        export_daily_bars.build_package(short, "LPS", "2019-01-01", "2026-09-07", export_daily_bars.TOOL_VERSION)
    assert "19 completed sessions" in str(raised.value)          # says how far short it is, not just "required"
    assert isinstance(raised.value, ValueError)                  # existing callers catching ValueError still catch it

    exact = export_daily_bars.package_records(bars(export_daily_bars.MIN_RECORDS), "DMX")
    package = export_daily_bars.build_package(exact, "DMX", "2019-01-01", "2026-09-07",
                                              export_daily_bars.TOOL_VERSION)
    assert len(package["records"]) == export_daily_bars.MIN_RECORDS   # the boundary itself exports

    # The classification the checkpoint records must be the new name, not a bare ValueError.
    assert export_all_symbols.classify_failure(
        export_daily_bars.InsufficientSessions("LPS: 19 completed sessions available, at least 20 are required")
    ) == "InsufficientSessions"
