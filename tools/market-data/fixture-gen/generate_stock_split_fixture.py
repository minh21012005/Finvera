"""Generate the split-in-window chart/technical fixture for Feature 002 task
T002. A single accepted 1:2 stock split lands inside the window; the fixture
carries the RAW series once and the correctly ADJUSTED series once, so a test
can assert neither run splices the two bases (DATA-008, research R-004).

Run: python tools/market-data/fixture-gen/generate_stock_split_fixture.py
Output: finvera-be/src/test/resources/fixtures/stock/chart/chart-split-in-window.json
"""

import json
from datetime import date, timedelta
from decimal import Decimal, ROUND_HALF_UP, getcontext
from pathlib import Path

getcontext().prec = 60
REPO_ROOT = Path(__file__).resolve().parents[3]
OUT_PATH = (
    REPO_ROOT
    / "finvera-be"
    / "src"
    / "test"
    / "resources"
    / "fixtures"
    / "stock"
    / "chart"
    / "chart-split-in-window.json"
)


def d(value) -> Decimal:
    return Decimal(str(value))


def s(value) -> str:
    return format(value, "f")


def trading_dates(count: int, end: date) -> list:
    out = []
    cursor = end
    while len(out) < count:
        if cursor.weekday() < 5:
            out.append(cursor)
        cursor -= timedelta(days=1)
    out.reverse()
    return out


def main():
    dates = trading_dates(60, date(2026, 8, 14))
    # A 1:2 split (ratio_numerator=1, ratio_denominator=2 -> two shares per one
    # old share) becomes ex-date effective at the 31st bar of the window.
    ex_date_index = 30
    ex_date = dates[ex_date_index]
    pre_split_close = d("120000.000000")

    raw_bars = []
    close = pre_split_close
    for i, trading_date in enumerate(dates):
        if i == ex_date_index:
            # The provider's raw tape reflects the actual post-split price
            # level from ex-date onward: roughly half the pre-split level.
            close = d("61000.000000")
        else:
            close = close + d("150.000000") if i < ex_date_index else close + d("70.000000")
        open_price = close - d("40.000000")
        raw_bars.append(
            {
                "tradingDate": trading_date.isoformat(),
                "open": s(open_price),
                "high": s(close + d("60.000000")),
                "low": s(open_price - d("60.000000")),
                "close": s(close),
                "volume": 900_000 + i * 1_000,
            }
        )

    adjustment_factor = d("0.5")  # multiplier applied to every pre-ex-date price
    adjusted_bars = []
    for i, bar in enumerate(raw_bars):
        if i < ex_date_index:
            factor = adjustment_factor
        else:
            factor = d("1")
        adjusted_bars.append(
            {
                "tradingDate": bar["tradingDate"],
                "open": s((d(bar["open"]) * factor).quantize(Decimal("0.000001"), rounding=ROUND_HALF_UP)),
                "high": s((d(bar["high"]) * factor).quantize(Decimal("0.000001"), rounding=ROUND_HALF_UP)),
                "low": s((d(bar["low"]) * factor).quantize(Decimal("0.000001"), rounding=ROUND_HALF_UP)),
                "close": s((d(bar["close"]) * factor).quantize(Decimal("0.000001"), rounding=ROUND_HALF_UP)),
                "volume": bar["volume"],
            }
        )

    payload = {
        "fixtureVersion": "stock-fixture-v1",
        "symbol": "SPLIT",
        "source": "FINVERA_FIXTURE",
        "description": (
            "One accepted 1:2 stock split with ex-date inside the 60-bar window. "
            "rawSeries.adjustmentStatus=RAW must never be spliced with "
            "adjustedSeries.adjustmentStatus=ADJUSTED within one returned series; "
            "a consumer must serve exactly one basis for the whole window."
        ),
        "corporateAction": {
            "actionType": "SPLIT",
            "exDate": ex_date.isoformat(),
            "ratioNumerator": "1",
            "ratioDenominator": "2",
            "adjustmentFactor": s(adjustment_factor),
            "source": "FINVERA_FIXTURE",
        },
        "rawSeries": {"adjustmentStatus": "RAW", "bars": raw_bars},
        "adjustedSeries": {"adjustmentStatus": "ADJUSTED", "bars": adjusted_bars},
    }

    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    with OUT_PATH.open("w", encoding="utf-8", newline="\n") as f:
        json.dump(payload, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"Wrote {OUT_PATH}")


if __name__ == "__main__":
    main()
