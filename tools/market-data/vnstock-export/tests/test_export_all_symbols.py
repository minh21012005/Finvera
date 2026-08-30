import importlib.util
import sys
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[1]))  # export_all_symbols imports sibling exporters
MODULE_PATH = Path(__file__).parents[1] / "export_all_symbols.py"
SPEC = importlib.util.spec_from_file_location("export_all_symbols", MODULE_PATH)
mod = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(mod)


def pkg(*period_ends):
    return {"records": [{"periodEnd": e} for e in period_ends]}


def test_quarter_package_is_current_until_next_quarter_disclosure_deadline():
    p = pkg("2026-03-31", "2026-06-30")
    assert mod.fundamentals_package_stale(p, date(2026, 8, 30), "quarter") is False   # Q3 not due yet
    assert mod.fundamentals_package_stale(p, date(2026, 11, 20), "quarter") is True   # Q3 due ~Nov 14


def test_annual_package_is_current_until_next_audited_deadline():
    p = pkg("2024-12-31", "2025-12-31")
    assert mod.fundamentals_package_stale(p, date(2026, 8, 30), "year") is False
    assert mod.fundamentals_package_stale(p, date(2027, 4, 15), "year") is True


def test_empty_package_is_always_stale():
    assert mod.fundamentals_package_stale({"records": []}, date(2026, 8, 30), "quarter") is True
