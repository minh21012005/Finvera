from __future__ import annotations

import argparse
import hashlib
import importlib.metadata
import json
import sys
import time
import types
from collections import Counter
from dataclasses import dataclass
from datetime import date
from pathlib import Path
from typing import Any, Callable

from poc_common import utc_now, write_summary


MIN_COMPLETED_SESSIONS = 271
REQUIRED_PRICE_COLUMNS = {"open", "high", "low", "close", "volume"}
ELIGIBLE_EXCHANGES = frozenset({"HOSE", "HNX", "UPCOM"})
COMMUNITY_REQUESTS_PER_MINUTE = 60
DEFAULT_FULL_UNIVERSE_REQUESTS_PER_MINUTE = 30
CHECKPOINT_VERSION = "vnstock-full-universe-checkpoint-v2"
LEGACY_CHECKPOINT_VERSION = "vnstock-full-universe-checkpoint-v1"


@dataclass(frozen=True)
class EquityCandidate:
    symbol: str
    exchange: str


class RequestPacer:
    """Spaces outbound requests without retrying or hiding provider failures."""

    def __init__(
        self,
        requests_per_minute: int,
        *,
        monotonic: Callable[[], float] = time.monotonic,
        sleep: Callable[[float], None] = time.sleep,
    ) -> None:
        if not 1 <= requests_per_minute <= COMMUNITY_REQUESTS_PER_MINUTE:
            raise ValueError("REQUESTS_PER_MINUTE_OUT_OF_RANGE")
        self.minimum_interval_seconds = 60 / requests_per_minute
        self._monotonic = monotonic
        self._sleep = sleep
        self._last_request_started_at: float | None = None

    def wait_before_request(self) -> None:
        now = self._monotonic()
        if self._last_request_started_at is not None:
            remaining = self.minimum_interval_seconds - (now - self._last_request_started_at)
            if remaining > 0:
                self._sleep(remaining)
        self._last_request_started_at = self._monotonic()


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
    except (Exception, SystemExit) as exc:  # Never retain a provider's raw exception text.
        import tenacity
        inner = exc
        if isinstance(exc, tenacity.RetryError) and exc.last_attempt is not None:
            try:
                inner = exc.last_attempt.exception()
            except Exception:
                pass
        
        if isinstance(inner, ValueError):
            return {
                "status": "PASS",
                "rows": 0,
                "columns": [],
                "column_dtypes": {},
                "required_ohlcv_columns_present": [],
                "null_counts": {},
            }
        return {"status": "FAIL", "error_type": type(exc).__name__, "probe": name}


def history_requirements_pass(result: dict[str, Any]) -> bool:
    return (
        result.get("status") == "PASS"
        and result.get("rows", 0) >= MIN_COMPLETED_SESSIONS
        and REQUIRED_PRICE_COLUMNS.issubset(
            set(result.get("required_ohlcv_columns_present", []))
        )
    )


def classify_history_result(result: dict[str, Any]) -> str:
    """Classify data availability without inferring a corporate-action cause."""
    if result.get("status") != "PASS":
        return f"PROVIDER_FAILURE_{result.get('error_type', 'UNKNOWN')}"
    if result.get("rows", 0) == 0:
        return "NO_HISTORY"
    if result.get("rows", 0) < MIN_COMPLETED_SESSIONS:
        return "INSUFFICIENT_HISTORY"
    if not REQUIRED_PRICE_COLUMNS.issubset(
        set(result.get("required_ohlcv_columns_present", []))
    ):
        return "OHLCV_SCHEMA_UNAVAILABLE"
    return "AVAILABLE"


def eligible_equity_candidates(reference_frame: Any) -> list[EquityCandidate]:
    columns = {str(column).lower(): str(column) for column in reference_frame.columns}
    required = {"symbol", "exchange", "type"}
    if not required.issubset(columns):
        raise ValueError("REFERENCE_UNIVERSE_SCHEMA_UNSUPPORTED")

    candidates = {
        EquityCandidate(symbol=str(row[columns["symbol"]]).strip().upper(), exchange=exchange)
        for _, row in reference_frame.iterrows()
        if str(row[columns["type"]]).strip().lower() == "stock"
        and (exchange := str(row[columns["exchange"]]).strip().upper()) in ELIGIBLE_EXCHANGES
        and str(row[columns["symbol"]]).strip()
    }
    return sorted(candidates, key=lambda candidate: (candidate.exchange, candidate.symbol))


def symbol_fingerprint(symbol: str) -> str:
    return hashlib.sha256(f"KBS:{symbol.upper()}".encode("utf-8")).hexdigest()


def universe_fingerprint(candidates: list[EquityCandidate]) -> str:
    digest_input = "\n".join(symbol_fingerprint(candidate.symbol) for candidate in candidates)
    return hashlib.sha256(digest_input.encode("utf-8")).hexdigest()


def initial_checkpoint(
    *, source: str, start: str, end: str, universe_hash: str
) -> dict[str, Any]:
    return {
        "checkpoint_version": CHECKPOINT_VERSION,
        "source": source,
        "requested_range": {"start": start, "end": end},
        "universe_fingerprint": universe_hash,
        "processed_symbol_fingerprints": {},
    }


def load_checkpoint(
    path: Path, *, source: str, start: str, end: str, universe_hash: str
) -> dict[str, Any]:
    try:
        checkpoint = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError("CHECKPOINT_UNREADABLE") from exc
    expected = initial_checkpoint(
        source=source, start=start, end=end, universe_hash=universe_hash
    )
    for key in ("source", "requested_range", "universe_fingerprint"):
        if checkpoint.get(key) != expected[key]:
            raise ValueError("CHECKPOINT_MISMATCH")
    if checkpoint.get("checkpoint_version") == LEGACY_CHECKPOINT_VERSION:
        legacy_successes = checkpoint.get("successful_symbol_fingerprints")
        if not isinstance(legacy_successes, dict):
            raise ValueError("CHECKPOINT_SCHEMA_INVALID")
        checkpoint = initial_checkpoint(
            source=source, start=start, end=end, universe_hash=universe_hash
        )
        checkpoint["processed_symbol_fingerprints"] = {
            fingerprint: {"exchange": exchange, "outcome": "AVAILABLE"}
            for fingerprint, exchange in legacy_successes.items()
        }
    if checkpoint.get("checkpoint_version") != CHECKPOINT_VERSION:
        raise ValueError("CHECKPOINT_MISMATCH")
    processed = checkpoint.get("processed_symbol_fingerprints")
    if not isinstance(processed, dict) or any(
        not isinstance(fingerprint, str)
        or len(fingerprint) != 64
        or not isinstance(value, dict)
        or value.get("exchange") not in ELIGIBLE_EXCHANGES
        or value.get("outcome") not in {
            "AVAILABLE", "NO_HISTORY", "INSUFFICIENT_HISTORY", "OHLCV_SCHEMA_UNAVAILABLE"
        }
        for fingerprint, value in processed.items()
    ):
        raise ValueError("CHECKPOINT_SCHEMA_INVALID")
    return checkpoint


def save_checkpoint(path: Path, checkpoint: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path = path.with_suffix(f"{path.suffix}.tmp")
    temporary_path.write_text(
        json.dumps(checkpoint, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    temporary_path.replace(path)


def full_universe_probe(
    *,
    candidates: list[EquityCandidate],
    fetch_history: Callable[[str], Any],
    source: str,
    start: str,
    end: str,
    requests_per_minute: int,
    checkpoint_path: Path,
    resume: bool,
    max_symbols: int | None,
    pacer: RequestPacer | None = None,
) -> dict[str, Any]:
    if max_symbols is not None and max_symbols < 1:
        raise ValueError("MAX_SYMBOLS_MUST_BE_POSITIVE")

    by_fingerprint = {
        symbol_fingerprint(candidate.symbol): candidate for candidate in candidates
    }
    if len(by_fingerprint) != len(candidates):
        raise ValueError("REFERENCE_UNIVERSE_DUPLICATE_SYMBOL")
    current_universe_hash = universe_fingerprint(candidates)
    checkpoint = (
        load_checkpoint(
            checkpoint_path,
            source=source,
            start=start,
            end=end,
            universe_hash=current_universe_hash,
        )
        if resume and checkpoint_path.exists()
        else initial_checkpoint(
            source=source,
            start=start,
            end=end,
            universe_hash=current_universe_hash,
        )
    )
    processed = checkpoint["processed_symbol_fingerprints"]
    resumed_processed_count = len(processed)
    pending = [
        candidate
        for fingerprint, candidate in by_fingerprint.items()
        if fingerprint not in processed
    ]
    planned = pending if max_symbols is None else pending[:max_symbols]
    request_pacer = pacer or RequestPacer(requests_per_minute)
    attempted_by_exchange: Counter[str] = Counter()
    failed_by_exchange: Counter[str] = Counter()
    failure_types: Counter[str] = Counter()

    for candidate in planned:
        request_pacer.wait_before_request()
        attempted_by_exchange[candidate.exchange] += 1
        result = capture("full_universe_history", lambda: fetch_history(candidate.symbol))
        outcome = classify_history_result(result)
        if not outcome.startswith("PROVIDER_FAILURE_"):
            processed[symbol_fingerprint(candidate.symbol)] = {
                "exchange": candidate.exchange,
                "outcome": outcome,
            }
            save_checkpoint(checkpoint_path, checkpoint)
            continue
        failed_by_exchange[candidate.exchange] += 1
        failure_types[outcome] += 1

    processed_by_exchange = Counter(value["exchange"] for value in processed.values())
    available_by_exchange = Counter(
        value["exchange"] for value in processed.values() if value["outcome"] == "AVAILABLE"
    )
    outcome_counts = Counter(value["outcome"] for value in processed.values())
    candidate_by_exchange = Counter(candidate.exchange for candidate in candidates)
    remaining = len(candidates) - len(processed)
    return {
        "status": "PASS",
        "source": source,
        "candidate_count": len(candidates),
        "planned_count": len(planned),
        "attempted_count": len(planned),
        "resumed_processed_count": resumed_processed_count,
        "processed_count": len(processed),
        "successful_count": outcome_counts["AVAILABLE"],
        "unavailable_history_count": len(processed) - outcome_counts["AVAILABLE"],
        "failed_attempt_count": sum(failed_by_exchange.values()),
        "remaining_count": remaining,
        "complete_coverage": remaining == 0,
        "coverage_gate_passed": remaining == 0 and not failed_by_exchange,
        "rate_limit": {
            "requests_per_minute": requests_per_minute,
            "minimum_interval_seconds": 60 / requests_per_minute,
        },
        "checkpoint": {
            "version": CHECKPOINT_VERSION,
            "resumed": resume,
            "contains_raw_market_values": False,
            "contains_symbol_identifiers": False,
        },
        "by_exchange": {
            exchange: {
                "candidate_count": candidate_by_exchange[exchange],
                "attempted_count": attempted_by_exchange[exchange],
                "processed_count": processed_by_exchange[exchange],
                "successful_count": available_by_exchange[exchange],
                "failed_attempt_count": failed_by_exchange[exchange],
            }
            for exchange in sorted(ELIGIBLE_EXCHANGES)
        },
        "failure_type_counts": dict(sorted(failure_types.items())),
        "history_outcome_counts": dict(sorted(outcome_counts.items())),
    }


def execution_passed(
    representative_gate_passed: bool,
    full_universe: dict[str, Any] | None,
    max_symbols: int | None,
) -> bool:
    """A bounded batch can succeed without claiming full coverage completion."""
    if not representative_gate_passed:
        return False
    if full_universe is None:
        return True
    if full_universe.get("status") != "PASS" or full_universe.get("failed_attempt_count", 0):
        return False
    return max_symbols is not None or full_universe.get("coverage_gate_passed", False)


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--start", default="2024-01-01")
    parser.add_argument("--end", default=date.today().isoformat())
    parser.add_argument("--output-dir", type=Path, default=Path("poc-output"))
    parser.add_argument("--full-universe", action="store_true")
    parser.add_argument("--max-symbols", type=int)
    parser.add_argument(
        "--requests-per-minute",
        type=int,
        default=DEFAULT_FULL_UNIVERSE_REQUESTS_PER_MINUTE,
    )
    parser.add_argument("--resume", action="store_true")
    parser.add_argument(
        "--skip-representative",
        action="store_true",
        help="Avoid re-running sample history probes during a reviewed resume batch.",
    )
    parser.add_argument("--checkpoint", type=Path)
    args = parser.parse_args()
    if args.max_symbols is not None and args.max_symbols < 1:
        parser.error("--max-symbols must be positive")
    if not 1 <= args.requests_per_minute <= COMMUNITY_REQUESTS_PER_MINUTE:
        parser.error("--requests-per-minute must be between 1 and 60")
    if args.resume and not args.full_universe:
        parser.error("--resume requires --full-universe")
    if args.max_symbols is not None and not args.full_universe:
        parser.error("--max-symbols requires --full-universe")
    if args.skip_representative and not (args.full_universe and args.resume):
        parser.error("--skip-representative requires --full-universe --resume")
    return args


def main() -> int:
    args = parse_arguments()
    suppress_vnstock_agent_bootstrap()
    from vnstock import Market, Reference

    market = Market()
    reference = Reference()
    source = "KBS"
    probes: dict[str, Any] = {}
    representative_rechecked = not args.skip_representative
    if representative_rechecked:
        for symbol in ("VNINDEX", "HNXINDEX", "UPCOMINDEX"):
            probes[f"index_{symbol}"] = capture(
                symbol,
                lambda symbol=symbol: market.index(symbol).ohlcv(
                    start=args.start, end=args.end, interval="1D", count=1000, source=source.lower()
                ),
            )
        for symbol, venue in (("VNM", "HOSE"), ("SHS", "HNX"), ("MCH", "UPCOM")):
            probes[f"equity_{venue}_{symbol}"] = capture(
                symbol,
                lambda symbol=symbol: market.equity(symbol).ohlcv(
                    start=args.start, end=args.end, interval="1D", count=1000, source=source.lower()
                ),
            )
        probes["equity_reference_universe"] = capture(
            "equity_reference_universe", lambda: reference.equity.list(source=source.lower())
        )
    reference_by_exchange: Any | None = None
    try:
        reference_by_exchange = reference.equity.list_by_exchange(source=source.lower())
        probes["equity_reference_by_exchange"] = {
            "status": "PASS",
            **dataframe_summary(reference_by_exchange),
        }
    except (Exception, SystemExit) as exc:
        probes["equity_reference_by_exchange"] = {
            "status": "FAIL",
            "error_type": type(exc).__name__,
            "probe": "equity_reference_by_exchange",
        }

    if representative_rechecked:
        stock_exchange_counts = probes["equity_reference_by_exchange"].get("stock_exchange_counts", {})
        history_probes = [
            value
            for key, value in probes.items()
            if key.startswith("index_") or (key.startswith("equity_") and "reference" not in key)
        ]
        representative_gate_passed: bool | None = (
            all(history_requirements_pass(value) for value in history_probes)
            and probes["equity_reference_universe"].get("status") == "PASS"
            and probes["equity_reference_by_exchange"].get("status") == "PASS"
            and all(stock_exchange_counts.get(venue, 0) > 0 for venue in ELIGIBLE_EXCHANGES)
            and stock_exchange_counts.get("<NULL>", 0) == 0
        )
    else:
        representative_gate_passed = None

    full_universe: dict[str, Any] | None = None
    if args.full_universe and reference_by_exchange is not None:
        try:
            checkpoint_path = args.checkpoint or args.output_dir / "vnstock-full-universe-checkpoint.json"
            full_universe = full_universe_probe(
                candidates=eligible_equity_candidates(reference_by_exchange),
                fetch_history=lambda symbol: market.equity(symbol).ohlcv(
                    start=args.start, end=args.end, interval="1D", count=1000, source=source.lower()
                ),
                source=source,
                start=args.start,
                end=args.end,
                requests_per_minute=args.requests_per_minute,
                checkpoint_path=checkpoint_path,
                resume=args.resume,
                max_symbols=args.max_symbols,
            )
        except ValueError as exc:
            full_universe = {
                "status": "FAIL",
                "reason": str(exc),
                "contains_raw_market_values": False,
                "contains_symbol_identifiers": False,
            }
    elif args.full_universe:
        full_universe = {
            "status": "FAIL",
            "reason": "REFERENCE_UNIVERSE_UNAVAILABLE",
            "contains_raw_market_values": False,
            "contains_symbol_identifiers": False,
        }

    gate_passed = representative_gate_passed is True and (
        full_universe is None or full_universe.get("coverage_gate_passed", False)
    )
    summary = {
        "probe": "vnstock-history",
        "generated_at": utc_now(),
        "package_version": importlib.metadata.version("vnstock"),
        "selected_upstream_source": source,
        "requested_range": {"start": args.start, "end": args.end},
        "minimum_completed_sessions": MIN_COMPLETED_SESSIONS,
        "representative_gate_passed": representative_gate_passed,
        "representative_gate_status": "RECHECKED" if representative_rechecked else "NOT_RECHECKED",
        "gate_passed": gate_passed,
        "contains_raw_market_values": False,
        "probes": probes,
    }
    if full_universe is not None:
        summary["full_universe"] = full_universe
    write_summary(args.output_dir, "vnstock-capability-summary.json", summary)
    return 0 if execution_passed(
        representative_gate_passed is True or args.skip_representative,
        full_universe,
        args.max_symbols,
    ) else 2


if __name__ == "__main__":
    raise SystemExit(main())
