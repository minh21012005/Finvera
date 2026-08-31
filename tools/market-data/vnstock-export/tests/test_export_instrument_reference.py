"""Feature 021 (ADR-0013) — instrument reference from the VCI listing."""
import importlib.util
from pathlib import Path

MODULE_PATH = Path(__file__).parents[1] / "export_instrument_reference.py"
SPEC = importlib.util.spec_from_file_location("export_instrument_reference", MODULE_PATH)
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


def test_vci_exchange_labels_map_to_finvera_venues():
    # VCI lists HOSE as HSX; market_instrument.venue must stay HOSE or every instrument would fork.
    frame = FakeFrame([
        {"symbol": "VNM", "type": "STOCK", "exchange": "HSX"},
        {"symbol": "SHS", "type": "STOCK", "exchange": "HNX"},
        {"symbol": "A32", "type": "STOCK", "exchange": "UPCOM"},
    ])
    records = {r["symbol"]: r for r in mod.build_records(frame, "2026-08-31")}
    assert records["VNM"]["venue"] == "HOSE"
    assert records["SHS"]["venue"] == "HNX"
    assert records["A32"]["venue"] == "UPCOM"


def test_package_identity_is_vci():
    frame = FakeFrame([{"symbol": "VNM", "type": "STOCK", "exchange": "HSX"}])
    package = mod.build_package(mod.build_records(frame, "2026-08-31"), "1.0.0")
    assert package["upstreamSource"] == "VNSTOCK_VCI"
    assert package["records"][0]["venue"] == "HOSE"
    assert mod.SOURCE == "VNSTOCK_VCI"
