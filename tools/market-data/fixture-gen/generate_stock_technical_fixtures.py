"""Generate deterministic OHLCV fixtures and hand-verifiable golden indicator
vectors for Feature 002 (stock-detail-analysis), task T003.

This script is fixture-authoring tooling only. It is never imported by
application code and never runs at deployment time. It implements the exact
formulas normatively defined in
specs/002-stock-detail-analysis/contracts/technical-indicators-v1.md,
independently of the future Java engine, so the emitted "expected" values in
bars-golden-250.json are a real cross-check rather than a copy of the
implementation under test.

Run: python tools/market-data/fixture-gen/generate_stock_technical_fixtures.py
Output: finvera-be/src/test/resources/fixtures/stock/technical/*.json
"""

import json
from datetime import date, timedelta
from decimal import Decimal, ROUND_HALF_UP, getcontext
from pathlib import Path

getcontext().prec = 60

SCALE12 = Decimal("0.000000000001")
REPO_ROOT = Path(__file__).resolve().parents[3]
OUT_DIR = REPO_ROOT / "finvera-be" / "src" / "test" / "resources" / "fixtures" / "stock" / "technical"


def d(value) -> Decimal:
    return Decimal(str(value))


def div12(numerator: Decimal, denominator: Decimal):
    """Rule U-3: every division is rounded HALF_UP to scale 12 immediately."""
    if denominator == 0:
        return None
    return (numerator / denominator).quantize(SCALE12, rounding=ROUND_HALF_UP)


def sqrt12(value: Decimal) -> Decimal:
    if value < 0:
        raise ValueError("negative variance")
    return value.sqrt().quantize(SCALE12, rounding=ROUND_HALF_UP)


def s(value) -> str:
    """Canonical fixed-point string for JSON, no exponent notation."""
    if value is None:
        return None
    return format(value, "f")


# ---------------------------------------------------------------------------
# Deterministic pseudo-random generator (no dependency on Python's `random`
# implementation so the series is bit-identical across Python versions).
# ---------------------------------------------------------------------------
class Lcg:
    def __init__(self, seed: int):
        self.state = seed & 0xFFFFFFFF

    def next(self) -> int:
        # Numerical Recipes LCG constants.
        self.state = (1664525 * self.state + 1013904223) & 0xFFFFFFFF
        return self.state

    def unit(self) -> Decimal:
        """Deterministic value in [0, 1) at 6-decimal resolution."""
        return (Decimal(self.next() % 1_000_000) / Decimal(1_000_000)).quantize(
            Decimal("0.000001"), rounding=ROUND_HALF_UP
        )


def trading_dates(count: int, end: date) -> list:
    """`count` consecutive weekdays ending at `end` (inclusive), ascending."""
    out = []
    cursor = end
    while len(out) < count:
        if cursor.weekday() < 5:
            out.append(cursor)
        cursor -= timedelta(days=1)
    out.reverse()
    return out


def generate_golden_series(bar_count: int = 250):
    """A plausible VND-denominated daily series with real intraday range and
    volume, generated from a fixed-seed LCG so it is fully reproducible."""
    rng = Lcg(seed=20260818)
    dates = trading_dates(bar_count, date(2026, 8, 14))
    bars = []
    close = d("45000.000000")
    for i, trading_date in enumerate(dates):
        open_price = close
        # Daily return in roughly [-2.5%, +2.5%], deterministic.
        raw_return = (rng.unit() - d("0.5")) * d("0.05")
        new_close = (open_price * (d("1") + raw_return)).quantize(
            Decimal("0.000001"), rounding=ROUND_HALF_UP
        )
        if new_close <= 0:
            new_close = d("1000.000000")
        extra_high = (rng.unit() * d("300")).quantize(Decimal("0.000001"), rounding=ROUND_HALF_UP)
        extra_low = (rng.unit() * d("300")).quantize(Decimal("0.000001"), rounding=ROUND_HALF_UP)
        high = max(open_price, new_close) + extra_high
        low = min(open_price, new_close) - extra_low
        if low <= 0:
            low = min(open_price, new_close) / d("2")
        volume = 800_000 + (rng.next() % 2_200_000)
        bars.append(
            {
                "tradingDate": trading_date.isoformat(),
                "open": s(open_price),
                "high": s(high),
                "low": s(low),
                "close": s(new_close),
                "volume": volume,
            }
        )
        close = new_close
    return bars


def generate_flat_series(bar_count: int = 250):
    dates = trading_dates(bar_count, date(2026, 8, 14))
    price = s(d("50000.000000"))
    return [
        {
            "tradingDate": trading_date.isoformat(),
            "open": price,
            "high": price,
            "low": price,
            "close": price,
            "volume": 1_000_000,
        }
        for trading_date in dates
    ]


def generate_monotonic_rising_series(bar_count: int = 250):
    dates = trading_dates(bar_count, date(2026, 8, 14))
    bars = []
    close = d("40000.000000")
    increment = d("50.000000")
    for trading_date in dates:
        open_price = close
        new_close = open_price + increment
        high = new_close + d("10.000000")
        low = open_price - d("10.000000")
        bars.append(
            {
                "tradingDate": trading_date.isoformat(),
                "open": s(open_price),
                "high": s(high),
                "low": s(low),
                "close": s(new_close),
                "volume": 1_500_000,
            }
        )
        close = new_close
    return bars


# ---------------------------------------------------------------------------
# Independent reference implementation of technical-indicators-v1, operated
# over a 0-indexed 250-bar window array (idx 0 = w, idx 249 = n).
# ---------------------------------------------------------------------------
def compute_expected(bars: list) -> dict:
    assert len(bars) == 250, "golden vector requires exactly the 250-bar window"
    closes = [d(b["close"]) for b in bars]
    highs = [d(b["high"]) for b in bars]
    lows = [d(b["low"]) for b in bars]
    volumes = [d(b["volume"]) for b in bars]
    n = 249

    def ma(period: int):
        window = closes[n - period + 1 : n + 1]
        return div12(sum(window), d(period))

    ma20 = ma(20)
    ma50 = ma(50)
    ma200 = ma(200)

    # RSI(14), Wilder smoothing, seeded at idx 14.
    gains = [d(0)]
    losses = [d(0)]
    for i in range(1, 250):
        delta = closes[i] - closes[i - 1]
        gains.append(max(delta, d(0)))
        losses.append(max(-delta, d(0)))
    avg_gain = div12(sum(gains[1:15]), d(14))
    avg_loss = div12(sum(losses[1:15]), d(14))
    for i in range(15, 250):
        avg_gain = div12(avg_gain * d(13) + gains[i], d(14))
        avg_loss = div12(avg_loss * d(13) + losses[i], d(14))
    if avg_loss == 0 and avg_gain == 0:
        rsi = d("50")
    elif avg_loss == 0 and avg_gain > 0:
        rsi = d("100")
    else:
        rs = div12(avg_gain, avg_loss)
        rsi = (d("100") - div12(d("100"), (d("1") + rs))).quantize(SCALE12, rounding=ROUND_HALF_UP)

    # MACD(12, 26, 9).
    def ema_series(period: int):
        k = div12(d(2), d(period + 1))
        seed_idx = period - 1
        values = {}
        values[seed_idx] = div12(sum(closes[0:period]), d(period))
        for i in range(seed_idx + 1, 250):
            values[i] = (closes[i] * k + values[i - 1] * (d("1") - k)).quantize(
                SCALE12, rounding=ROUND_HALF_UP
            )
        return values

    ema12 = ema_series(12)
    ema26 = ema_series(26)
    macd_line = {i: (ema12[i] - ema26[i]).quantize(SCALE12, rounding=ROUND_HALF_UP) for i in range(25, 250)}
    k9 = div12(d(2), d(10))
    signal = {33: div12(sum(macd_line[i] for i in range(25, 34)), d(9))}
    for i in range(34, 250):
        signal[i] = (macd_line[i] * k9 + signal[i - 1] * (d("1") - k9)).quantize(
            SCALE12, rounding=ROUND_HALF_UP
        )
    histogram_n = (macd_line[n] - signal[n]).quantize(SCALE12, rounding=ROUND_HALF_UP)

    # Bollinger Bands(20, 2), population standard deviation.
    bb_window = closes[n - 19 : n + 1]
    bb_middle = ma20
    variance = div12(sum((c - bb_middle) ** 2 for c in bb_window), d(20))
    sigma = sqrt12(variance)
    bb_upper = (bb_middle + d("2") * sigma).quantize(SCALE12, rounding=ROUND_HALF_UP)
    bb_lower = (bb_middle - d("2") * sigma).quantize(SCALE12, rounding=ROUND_HALF_UP)
    bandwidth = div12((bb_upper - bb_lower), bb_middle)
    if bandwidth is not None:
        bandwidth = (bandwidth * d("100")).quantize(SCALE12, rounding=ROUND_HALF_UP)

    # ATR(14), Wilder smoothing.
    tr = [d(0)]
    for i in range(1, 250):
        a = highs[i] - lows[i]
        b = abs(highs[i] - closes[i - 1])
        c = abs(lows[i] - closes[i - 1])
        tr.append(max(a, b, c))
    atr = div12(sum(tr[1:15]), d(14))
    for i in range(15, 250):
        atr = div12(atr * d(13) + tr[i], d(14))
    atr_pct = div12(atr, closes[n])
    if atr_pct is not None:
        atr_pct = (atr_pct * d("100")).quantize(SCALE12, rounding=ROUND_HALF_UP)

    # Volume indicators.
    avg_volume20 = div12(sum(volumes[n - 19 : n + 1]), d(20))
    prior_average20 = div12(sum(volumes[n - 20 : n]), d(20))
    relative_volume = div12(volumes[n], prior_average20) if prior_average20 else None

    return {
        "asOfTradingDate": bars[n]["tradingDate"],
        "windowStartDate": bars[0]["tradingDate"],
        "windowEndDate": bars[n]["tradingDate"],
        "indicators": {
            "MA20": {"VALUE": s(ma20)},
            "MA50": {"VALUE": s(ma50)},
            "MA200": {"VALUE": s(ma200)},
            "RSI14": {"VALUE": s(rsi)},
            "MACD": {
                "MACD_LINE": s(macd_line[n]),
                "SIGNAL": s(signal[n]),
                "HISTOGRAM": s(histogram_n),
            },
            "BBANDS": {
                "UPPER": s(bb_upper),
                "MIDDLE": s(bb_middle),
                "LOWER": s(bb_lower),
                "BANDWIDTH": s(bandwidth),
            },
            "ATR14": {
                "VALUE": s(atr),
                "PERCENT_OF_CLOSE": s(atr_pct),
            },
            "AVG_VOLUME20": {"VALUE": s(avg_volume20)},
            "RELATIVE_VOLUME": {"VALUE": s(relative_volume)},
        },
    }


def write_json(path: Path, payload: dict):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as f:
        json.dump(payload, f, indent=2, ensure_ascii=False)
        f.write("\n")


def bar_fixture(symbol: str, adjustment_status: str, bars: list) -> dict:
    return {
        "fixtureVersion": "stock-fixture-v1",
        "symbol": symbol,
        "source": "FINVERA_FIXTURE",
        "adjustmentStatus": adjustment_status,
        "bars": bars,
    }


def main():
    golden_bars = generate_golden_series(250)
    expected = compute_expected(golden_bars)

    truncation_points = [19, 20, 21, 49, 50, 199, 200, 249, 250]
    for count in truncation_points:
        write_json(
            OUT_DIR / f"bars-{count}.json",
            bar_fixture("FPT", "ADJUSTED", golden_bars[:count]),
        )

    golden_payload = bar_fixture("FPT", "ADJUSTED", golden_bars)
    golden_payload["expected"] = expected
    write_json(OUT_DIR / "bars-golden-250.json", golden_payload)

    flat_bars = generate_flat_series(250)
    flat_payload = bar_fixture("FLAT", "ADJUSTED", flat_bars)
    flat_payload["expected"] = {
        "note": "Zero intraday range and zero price change across the whole window.",
        "indicators": {
            "RSI14": {"VALUE": "50.000000000000"},
            "MACD": {
                "MACD_LINE": "0.000000000000",
                "SIGNAL": "0.000000000000",
                "HISTOGRAM": "0.000000000000",
            },
            "ATR14": {"VALUE": "0.000000000000", "PERCENT_OF_CLOSE": "0.000000000000"},
        },
    }
    write_json(OUT_DIR / "bars-flat.json", flat_payload)

    rising_bars = generate_monotonic_rising_series(250)
    rising_payload = bar_fixture("RISE", "ADJUSTED", rising_bars)
    rising_payload["expected"] = {
        "note": "Strictly increasing close every session across the whole window.",
        "indicators": {"RSI14": {"VALUE": "100.000000000000"}},
        "assertions": [
            "MA20[n] > MA20[n-1] > MA20[n-2]",
            "MA50[n] > MA50[n-1] > MA50[n-2]",
            "MA200[n] > MA200[n-1] > MA200[n-2]",
        ],
    }
    write_json(OUT_DIR / "bars-monotonic-rising.json", rising_payload)

    print(f"Wrote {len(truncation_points) + 3} fixture files to {OUT_DIR}")


if __name__ == "__main__":
    main()
