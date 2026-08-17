import importlib.util
from pathlib import Path

import pytest


MODULE_PATH = Path(__file__).parents[1] / "export_history.py"
SPEC = importlib.util.spec_from_file_location("export_history", MODULE_PATH)
export_history = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(export_history)


def rows(count: int = 271):
    return [{"time": f"2025-01-{(index % 28) + 1:02d} 00:00:00", "close": "101.5"} for index in range(count)]


def test_builds_checksum_bound_canonical_package_with_exact_decimal_strings():
    records = export_history.package_records(rows(), "HOSE", "VNM", "2025-01-01")
    package = export_history.build_package(records, "2025-01-01", "2026-01-01", "0.1.0")

    assert package["contractVersion"] == export_history.CONTRACT_VERSION
    assert package["records"][0]["closePrice"] == "101.500000"
    assert package["packageSha256"] == export_history.hashlib.sha256(
        package["canonicalPayload"].encode()
    ).hexdigest()


def test_rejects_insufficient_history_and_invalid_decimal():
    with pytest.raises(ValueError, match="271"):
        export_history.build_package([], "2025-01-01", "2026-01-01", "0.1.0")
    with pytest.raises(ValueError, match="finite"):
        export_history.decimal_string("-1")
