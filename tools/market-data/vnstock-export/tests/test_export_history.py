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


def test_builds_market_package_with_index_records_and_derived_reference():
    index_rows = [
        {"time": "2026-08-21 00:00:00", "close": "1710.0", "volume": "1000"},
        {"time": "2026-08-24 00:00:00", "close": "1728.0", "volume": "1200"},
    ]

    records = export_history.index_records(index_rows, "VN_INDEX", "VNINDEX")
    package = export_history.build_market_package([], records, "2026-08-21", "2026-08-24", "0.2.0")

    assert package["contractVersion"] == export_history.MARKET_PACKAGE_CONTRACT_VERSION
    assert package["records"] == []
    assert package["indexRecords"][0]["code"] == "VN_INDEX"
    assert package["indexRecords"][0]["level"] == "1728.000000"
    assert package["indexRecords"][0]["referenceLevel"] == "1710.000000"
    assert package["indexRecords"][0]["matchedVolume"] == "1200"
    assert "VNSTOCK_DAILY_CLOSE_REFERENCE_DERIVED" in package["indexRecords"][0]["reasonCodes"]


def test_market_overview_incremental_merges_existing_records(monkeypatch, tmp_path):
    existing_records = [
        {
            "canonicalRecord": "old",
            "code": "VN_INDEX",
            "dataStatus": "CURRENT",
            "level": "100.000000",
            "matchedValueVnd": None,
            "matchedVolume": None,
            "observedAt": "2026-08-20T08:00:00Z",
            "providerSymbol": "VNINDEX",
            "reasonCodes": ["VNSTOCK_DAILY_CLOSE_REFERENCE_DERIVED"],
            "referenceLevel": "99.000000",
            "sessionState": "CLOSED",
            "tradingDate": "2026-08-20",
        }
    ]
    package = export_history.build_market_package([], existing_records, "2024-01-01", "2026-08-20", "0.2.0")
    (tmp_path / export_history.market_overview_filename("2024-01-01", "2026-08-20")).write_text(
        export_history.json.dumps(package, ensure_ascii=False), encoding="utf-8"
    )
    calls = []

    def fake_fetch(symbol, start, end):
        calls.append((symbol, start, end))
        return [
            {"time": "2026-05-12 00:00:00", "close": "101"},
            {"time": "2026-08-24 00:00:00", "close": "102"},
        ]

    monkeypatch.setattr(export_history, "fetch_index_rows", fake_fetch)

    records = export_history.incremental_market_index_records(
        "2024-01-01", "2026-08-24", tmp_path, lookback_days=90, full_refresh=False
    )

    assert calls
    assert all(call[1] != "2024-01-01" for call in calls)
    assert ("VN_INDEX", "2026-08-20") in {(record["code"], record["tradingDate"]) for record in records}
    assert ("VN_INDEX", "2026-08-24") in {(record["code"], record["tradingDate"]) for record in records}


def test_rejects_insufficient_history_and_invalid_decimal():
    with pytest.raises(ValueError, match="271"):
        export_history.build_package([], "2025-01-01", "2026-01-01", "0.1.0")
    with pytest.raises(ValueError, match="finite"):
        export_history.decimal_string("-1")
