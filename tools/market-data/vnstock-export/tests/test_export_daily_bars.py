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


def test_kbs_board_price_is_normalized_to_base_vnd_per_share():
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
    assert record["valueVnd"] == "769239900000.000000"


def test_daily_bar_tool_version_changes_when_canonical_price_unit_changes():
    assert export_daily_bars.TOOL_VERSION == "0.2.0"


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
