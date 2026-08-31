"""Feature 020 -- VCI ICB sector-reference exporter, anchored on a trimmed 2026-08-31 capture
(tests/fixtures/vci/listing_*.json) of vnstock 4.0.7 `Listing(source="vci")` frames."""
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

import pandas as pd
import pytest

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import export_sector_reference_vci as mod  # noqa: E402

FIXTURES = Path(__file__).parent / "fixtures" / "vci"


def frame(name: str) -> pd.DataFrame:
    return pd.DataFrame(json.loads((FIXTURES / f"listing_{name}.json").read_text(encoding="utf-8")))


@pytest.fixture
def frames():
    return frame("symbols_by_industries"), frame("industries_icb"), frame("symbols_by_exchange")


def by_symbol(records):
    return {r["symbol"]: r for r in records}


def test_one_level3_record_per_listed_equity_with_dictionary_names(frames):
    records, audit = mod.build_records(*frames)
    got = by_symbol(records)
    # UPCoM equities are classified (the whole point of Feature 020: KBS_INDUSTRY covered HOSE/HNX only)
    assert got["A32"]["sectorCode"] == "3760"
    assert got["A32"]["displayNameVi"] == "Hàng cá nhân"
    assert got["A32"]["displayNameEn"] == "Personal Goods"
    assert got["YTC"]["sectorCode"] == "4530"
    assert got["ACV"]["sectorCode"] == "2770"
    # HOSE anchors
    assert got["MBB"]["sectorCode"] == "8350" and got["MBB"]["displayNameEn"] == "Banks"
    assert got["VNM"]["sectorCode"] == "3570" and got["VNM"]["displayNameEn"] == "Food Producers"
    assert got["HPG"]["sectorCode"] == "1750"
    # not equities Finvera compares peers across
    assert "A+ FUND" not in got and "A+ Fund" not in got  # open-ended fund (com_type QU, not in the exchange list)
    assert "FUEVN50G" not in got  # ETF
    assert "XDC" not in got  # delisted
    assert audit["icbLevel"] == 3
    assert audit["byExchange"]["UPCOM"]["classified"] == audit["byExchange"]["UPCOM"]["equities"] == 4
    assert audit["byExchange"]["HSX"] == {"equities": 3, "classified": 3}
    assert audit["unclassifiedEquities"] == []
    assert audit["codesWithoutDictionaryName"] == []


def test_duplicate_level3_codes_keep_the_lowest_code_and_are_reported(frames):
    # VNH (a food producer) also appears under 8980 "Quỹ đầu tư" at the provider -- rule S-3.
    records, audit = mod.build_records(*frames)
    assert by_symbol(records)["VNH"]["sectorCode"] == "3570"
    assert audit["duplicateClassifications"] == {"VNH": ["3570", "8980"]}


def test_canonical_record_and_package_checksum_follow_the_v1_contract(frames):
    records, audit = mod.build_records(*frames)
    package = mod.build_package(records, audit, "4.0.7")
    for record in package["records"]:
        expected = {k: v for k, v in record.items() if k != "canonicalRecord"}
        assert record["canonicalRecord"] == json.dumps(expected, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    assert package["packageSha256"] == hashlib.sha256(package["canonicalPayload"].encode()).hexdigest()
    assert json.loads(package["canonicalPayload"]) == {"records": package["records"]}
    assert package["contractVersion"] == "vnstock-sector-reference-v1"
    assert package["scheme"] == "VCI_ICB_L3" and package["schemeVersion"] == "4.0.7"
    assert package["upstreamSource"] == "VNSTOCK_VCI"
    assert package["records"] == sorted(package["records"], key=lambda r: (r["sectorCode"], r["symbol"]))
    assert package["sectorConstituentCounts"]["3570"] == 2  # VNM + VNH
    # every fixture sector is below the 8-constituent floor -- reported, never hidden
    assert set(package["sectorsBelowComparabilityFloor"]) == set(package["sectorConstituentCounts"])


def test_half_empty_provider_response_is_refused(frames):
    by_industries, industries, by_exchange = frames
    only_two = by_industries[by_industries["symbol"].isin(["VNM", "MBB"])]
    with pytest.raises(mod.SchemaMismatch, match="refusing to write a package"):
        mod.build_records(only_two, industries, by_exchange)


def test_schema_drift_is_refused(frames):
    by_industries, industries, by_exchange = frames
    with pytest.raises(mod.SchemaMismatch, match="icb_level"):
        mod.build_records(by_industries.drop(columns=["icb_level"]), industries, by_exchange)
    with pytest.raises(mod.SchemaMismatch, match="level-3"):
        mod.build_records(by_industries, industries[industries["level"] != 3], by_exchange)
