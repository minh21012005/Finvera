from __future__ import annotations

import argparse
import importlib.metadata
import sys
from pathlib import Path
from typing import Any, Callable

from poc_common import utc_now, write_summary
from poc_vnstock import dataframe_summary, suppress_vnstock_agent_bootstrap

# research.md R-012 G-01: sanitized schema/period/industry-coverage evidence
# for the Vnstock Finance API before any live FundamentalReportProvider is
# coded. One representative symbol per sector shape the contract calls out
# (ordinary industrial, bank, securities) — banks and securities firms use a
# materially different balance-sheet/income-statement layout than an ordinary
# company, which is exactly the coverage question G-01 asks.
REPRESENTATIVE_SYMBOLS: dict[str, str] = {
    "ORDINARY": "VNM",
    "BANK": "VCB",
    "SECURITIES": "SSI",
}
STATEMENTS: tuple[str, ...] = ("balance_sheet", "income_statement", "cash_flow", "ratio")


def period_identity_columns(columns: list[str]) -> list[str]:
    lowered = {c.lower(): c for c in columns}
    candidates = ("year", "quarter", "period", "yeareport", "lengthreport", "ticker")
    return sorted({lowered[c] for c in candidates if c in lowered})


def item_label_evidence(frame: Any) -> dict[str, Any] | None:
    """Line-item labels are public schema metadata (what the statement covers),
    not a secret or a market value — capturing them is what makes G-01's
    metric-mapping question answerable at all."""
    lowered = {str(c).lower(): c for c in getattr(frame, "columns", [])}
    if "item" not in lowered or "item_id" not in lowered:
        return None
    item_col, id_col = lowered["item"], lowered["item_id"]
    duplicate_ids = frame[id_col][frame[id_col].duplicated(keep=False)].unique().tolist()
    return {
        "item_count": int(frame[item_col].nunique()),
        "item_id_count": int(frame[id_col].nunique()),
        "row_count": int(len(frame)),
        "duplicate_item_ids": [str(v) for v in duplicate_ids][:20],
        "items": [str(v) for v in frame[item_col].tolist()],
        "item_ids": [str(v) for v in frame[id_col].tolist()],
    }


def probe_symbol(source: str, symbol: str, period: str) -> dict[str, Any]:
    from vnstock import Finance

    try:
        finance = Finance(source=source, symbol=symbol, period=period, show_log=False)
    except (Exception, SystemExit) as exc:
        return {
            statement: {"status": "FAIL", "error_type": type(exc).__name__, "stage": "construction"}
            for statement in STATEMENTS
        }
    result: dict[str, Any] = {}
    for statement in STATEMENTS:
        call: Callable[[], Any] = getattr(finance, statement)
        try:
            frame = call()
        except (Exception, SystemExit) as exc:
            result[statement] = {"status": "FAIL", "error_type": type(exc).__name__}
            continue
        outcome = {"status": "PASS", **dataframe_summary(frame)}
        outcome["period_identity_columns"] = period_identity_columns(outcome.get("columns", []))
        labels = item_label_evidence(frame)
        if labels is not None:
            outcome["item_label_evidence"] = labels
        result[statement] = outcome
    return result


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", default="kbs", choices=["kbs", "vci"])
    parser.add_argument("--output-dir", type=Path, default=Path("poc-output"))
    return parser.parse_args()


def main() -> int:
    args = parse_arguments()
    suppress_vnstock_agent_bootstrap()

    probes: dict[str, Any] = {}
    for shape, symbol in REPRESENTATIVE_SYMBOLS.items():
        probes[f"{shape}_{symbol}"] = {
            "quarter": probe_symbol(args.source, symbol, "quarter"),
            "year": probe_symbol(args.source, symbol, "year"),
        }

    all_results = [
        outcome
        for shape_result in probes.values()
        for period_result in shape_result.values()
        for outcome in period_result.values()
    ]
    gate_passed = all(r.get("status") == "PASS" and r.get("rows", 0) > 0 for r in all_results)

    summary = {
        "probe": "vnstock-fundamentals",
        "generated_at": utc_now(),
        "package_version": importlib.metadata.version("vnstock"),
        "selected_upstream_source": args.source.upper(),
        "representative_symbols": REPRESENTATIVE_SYMBOLS,
        "statements_probed": list(STATEMENTS),
        "gate_passed": gate_passed,
        "contains_raw_market_values": False,
        "probes": probes,
    }
    write_summary(args.output_dir, "vnstock-fundamentals-capability-summary.json", summary)
    return 0 if gate_passed else 2


if __name__ == "__main__":
    raise SystemExit(main())
