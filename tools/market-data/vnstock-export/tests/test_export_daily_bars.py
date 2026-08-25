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
