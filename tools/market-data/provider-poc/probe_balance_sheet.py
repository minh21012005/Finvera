"""Feature 008 research probe: KBS balance_sheet / cash_flow / ratio field evidence.

Read-only, owner-operated, pinned vnstock (see pyproject). Prints item ids, labels,
and the two most recent period values so unit scale and availability can be
recorded in specs/008-provider-data-expansion/research.md. Never touches the
database and never prints credentials.
"""
from __future__ import annotations

import argparse
import json
from typing import Any


WANTED = {
    "balance_sheet": None,  # everything: never probed before
    "cash_flow": {
        "operating_cash_flow",
        "i_cash_flows_from_operating_activities",
        "payment_for_fixed_assets_constructions_and_other_long_term_assets",
        "depreciation_of_fixed_assets_and_investment_properties",
        "investing_cash_flow",
    },
    "income_statement": {
        "revenue", "operating_profit", "profit_before_tax", "of_which_interest_expense",
        "finance_expenses", "net_profit", "gross_profit",
    },
    "ratio": {"ebitda_net_revenue", "ebit_margin", "ev_ebitda", "ev_ebit", "debt_to_equity",
              "debt_to_assets", "liabilities_to_assets"},
}


def rows(frame: Any, keep: set[str] | None) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    if frame is None or "item_id" not in frame.columns:
        return out
    period_cols = [c for c in frame.columns if c not in ("item", "item_id")]
    recent = period_cols[:2]
    for _, r in frame.iterrows():
        item_id = str(r["item_id"])
        if keep is not None and item_id not in keep:
            continue
        out.append({
            "item_id": item_id,
            "label": str(r.get("item", "")),
            "recent": {str(c): (None if r[c] != r[c] else str(r[c])) for c in recent},
        })
    return out


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--symbols", default="VNM,FPT,HPG,MBB")
    parser.add_argument("--period", default="quarter", choices=("quarter", "year"))
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    from vnstock import Finance

    evidence: dict[str, Any] = {}
    for symbol in [s.strip().upper() for s in args.symbols.split(",") if s.strip()]:
        finance = Finance(symbol=symbol, source="kbs")
        per_symbol: dict[str, Any] = {}
        for stmt, keep in WANTED.items():
            try:
                frame = getattr(finance, stmt)(period=args.period)
                per_symbol[stmt] = {
                    "columns": [str(c) for c in (frame.columns if frame is not None else [])][:6],
                    "row_count": int(len(frame)) if frame is not None else 0,
                    "rows": rows(frame, keep),
                }
            except Exception as exc:  # noqa: BLE001 -- evidence probe records the failure class only
                per_symbol[stmt] = {"error": type(exc).__name__}
        evidence[symbol] = per_symbol
        print(f"{symbol}: " + ", ".join(f"{k}={v.get('row_count', 'ERR')}" for k, v in per_symbol.items()))
    with open(args.output, "w", encoding="utf-8") as fh:
        json.dump(evidence, fh, ensure_ascii=False, indent=1)
    print(f"wrote {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
