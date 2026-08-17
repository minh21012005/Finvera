from __future__ import annotations

import argparse
import importlib.metadata
import sys
import types
from datetime import date
from pathlib import Path
from typing import Any, Callable

from poc_common import utc_now, write_summary


MIN_COMPLETED_SESSIONS = 271
REQUIRED_PRICE_COLUMNS = {"open", "high", "low", "close", "volume"}


def suppress_vnstock_agent_bootstrap() -> None:
    """Block Vnstock's unrelated import-time editor-rule file generation."""
    agents_module = types.ModuleType("vnstock.core.utils.agents")
    agents_module.init_agent_environment = lambda *args, **kwargs: False
    sys.modules["vnstock.core.utils.agents"] = agents_module


def dataframe_summary(frame: Any) -> dict[str, Any]:
    columns = [str(column) for column in getattr(frame, "columns", [])]
    lowered = {column.lower(): column for column in columns}
    date_column = next(
        (lowered[name] for name in ("time", "date", "trading_date") if name in lowered),
        None,
    )
    result: dict[str, Any] = {
        "rows": int(len(frame)),
        "columns": columns,
        "column_dtypes": {
            str(column): str(dtype)
            for column, dtype in getattr(frame, "dtypes", {}).items()
        },
        "required_ohlcv_columns_present": sorted(
            REQUIRED_PRICE_COLUMNS.intersection(lowered)
        ),
    }
    if date_column and len(frame):
        values = frame[date_column].dropna()
        if len(values):
            result["minimum_date"] = str(values.min())
            result["maximum_date"] = str(values.max())
    result["null_counts"] = {
        str(column): int(count)
        for column, count in getattr(frame, "isna")().sum().items()
    }
    for category_column in ("exchange", "type"):
        if category_column in lowered:
            actual = lowered[category_column]
            counts = frame[actual].fillna("<NULL>").astype(str).value_counts()
            result[f"{category_column}_counts"] = {
                str(category): int(count) for category, count in counts.items()
            }
    if "exchange" in lowered and "type" in lowered:
        stock_rows = frame[
            frame[lowered["type"]].astype(str).str.lower().eq("stock")
        ]
        counts = (
            stock_rows[lowered["exchange"]]
            .fillna("<NULL>")
            .astype(str)
            .value_counts()
        )
        result["stock_exchange_counts"] = {
            str(category): int(count) for category, count in counts.items()
        }
    return result


def capture(name: str, call: Callable[[], Any]) -> dict[str, Any]:
    try:
        frame = call()
        return {"status": "PASS", **dataframe_summary(frame)}
    except Exception as exc:  # POC records capability failure without raw payloads.
        return {
            "status": "FAIL",
            "error_type": type(exc).__name__,
            "error": str(exc)[:500],
            "probe": name,
        }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--start", default="2024-01-01")
    parser.add_argument("--end", default=date.today().isoformat())
    parser.add_argument("--output-dir", type=Path, default=Path("poc-output"))
    args = parser.parse_args()

    suppress_vnstock_agent_bootstrap()
    from vnstock import Market, Reference

    market = Market()
    reference = Reference()
    probes: dict[str, Any] = {}

    for symbol in ("VNINDEX", "HNXINDEX", "UPCOMINDEX"):
        probes[f"index_{symbol}"] = capture(
            symbol,
            lambda symbol=symbol: market.index(symbol).ohlcv(
                start=args.start,
                end=args.end,
                interval="1D",
                count=1000,
                source="kbs",
            ),
        )

    for symbol, venue in (("VNM", "HOSE"), ("SHS", "HNX"), ("MCH", "UPCOM")):
        probes[f"equity_{venue}_{symbol}"] = capture(
            symbol,
            lambda symbol=symbol: market.equity(symbol).ohlcv(
                start=args.start,
                end=args.end,
                interval="1D",
                count=1000,
                source="kbs",
            ),
        )

    probes["equity_reference_universe"] = capture(
        "equity_reference_universe", lambda: reference.equity.list(source="kbs")
    )
    probes["equity_reference_by_exchange"] = capture(
        "equity_reference_by_exchange",
        lambda: reference.equity.list_by_exchange(source="kbs"),
    )

    vnindex = probes["index_VNINDEX"]
    stock_exchange_counts = probes["equity_reference_by_exchange"].get(
        "stock_exchange_counts", {}
    )
    history_probes = [
        value
        for key, value in probes.items()
        if key.startswith("index_") or (key.startswith("equity_") and "reference" not in key)
    ]
    gate_passed = (
        vnindex.get("status") == "PASS"
        and vnindex.get("rows", 0) >= MIN_COMPLETED_SESSIONS
        and REQUIRED_PRICE_COLUMNS.issubset(
            set(vnindex.get("required_ohlcv_columns_present", []))
        )
        and all(
            value.get("status") == "PASS"
            and value.get("rows", 0) >= MIN_COMPLETED_SESSIONS
            and REQUIRED_PRICE_COLUMNS.issubset(
                set(value.get("required_ohlcv_columns_present", []))
            )
            for value in history_probes
        )
        and probes["equity_reference_universe"].get("status") == "PASS"
        and probes["equity_reference_by_exchange"].get("status") == "PASS"
        and all(stock_exchange_counts.get(venue, 0) > 0 for venue in ("HOSE", "HNX", "UPCOM"))
        and stock_exchange_counts.get("<NULL>", 0) == 0
    )
    summary = {
        "probe": "vnstock-history",
        "generated_at": utc_now(),
        "package_version": importlib.metadata.version("vnstock"),
        "selected_upstream_source": "KBS",
        "requested_range": {"start": args.start, "end": args.end},
        "minimum_completed_sessions": MIN_COMPLETED_SESSIONS,
        "gate_passed": gate_passed,
        "contains_raw_market_values": False,
        "probes": probes,
    }
    write_summary(args.output_dir, "vnstock-capability-summary.json", summary)
    return 0 if gate_passed else 2


if __name__ == "__main__":
    raise SystemExit(main())
