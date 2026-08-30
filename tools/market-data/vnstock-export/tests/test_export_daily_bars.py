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


def test_current_kbs_ohlcv_schema_normalizes_prices_without_exporting_reference_price():
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
    assert export_daily_bars.TOOL_VERSION == "0.4.0"


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


def test_full_universe_checkpoint_does_not_skip_file_missing_requested_end_date(tmp_path):
    symbol = "ADG"
    path = tmp_path / export_daily_bars.output_filename(symbol)
    path.write_text(json.dumps({
        "toolVersion": export_daily_bars.TOOL_VERSION,
        "rangeStart": "2024-01-01",
        "rangeEnd": "2026-08-25",
        "records": [{
            "tradingDate": "2026-08-24",
        }],
    }), encoding="utf-8")

    class Args:
        start = "2024-01-01"
        end = "2026-08-25"
        full_refresh = False
        output = tmp_path

    entry = {
        "daily_bars": "done",
        "daily_bars_range": ["2024-01-01", "2026-08-25"],
    }

    assert not export_all_symbols.daily_bars_current(symbol, entry, Args())


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

    assert package["toolVersion"] == "0.4.0"
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


def test_fetch_rows_pads_the_provider_end_date_and_cuts_back_to_the_requested_end(monkeypatch):
    """Feature 011 R-001: KBS treats `end` as exclusive-ish (end=2026-08-29 -> last bar 08-27)."""
    import types
    seen = {}

    def bar(day):
        return {"time": f"{day} 07:00:00", "open": 62.5, "high": 63.0, "low": 62.0, "close": 62.3, "volume": 1000}

    class _Equity:
        def ohlcv(self, start, end, interval, count, source):
            seen["end"] = end
            return _FakeOhlcvFrame([bar("2026-08-27"), bar("2026-08-28"), bar("2026-09-01")])

    class _Market:
        def equity(self, _symbol):
            return _Equity()

    monkeypatch.setitem(sys.modules, "vnstock", types.SimpleNamespace(Market=_Market))
    rows = export_daily_bars.fetch_rows("VNM", "2026-08-24", "2026-08-30")
    assert seen["end"] == "2026-09-02"                      # 3-day padding
    assert [r["time"][:10] for r in rows] == ["2026-08-27", "2026-08-28"]  # 09-01 (after end) dropped


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
