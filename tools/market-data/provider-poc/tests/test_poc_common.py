import hashlib
import json
import sys
from pathlib import Path

import httpx
import pandas as pd

from poc_common import schema_of_mapping, value_type, write_summary
from poc_tcbs import summarize_error_response, summarize_response
from poc_vnstock import (
    EquityCandidate,
    RequestPacer,
    capture,
    classify_history_result,
    eligible_equity_candidates,
    execution_passed,
    full_universe_probe,
    suppress_vnstock_agent_bootstrap,
)


def test_value_type_keeps_boolean_distinct_from_integer() -> None:
    assert value_type(True) == "boolean"
    assert value_type(1) == "integer"


def test_schema_summary_contains_types_not_values() -> None:
    result = schema_of_mapping({"token": "secret", "count": 3, "missing": None})
    assert result == {"count": "integer", "missing": "null", "token": "string"}
    assert "secret" not in result.values()


def test_tcbs_summary_exposes_schema_but_not_market_values() -> None:
    result = summarize_response(
        {
            "data": [{"symbol": "TCB", "matchPrice": 123_456.78}],
            "tradingDate": "2099-12-31",
        }
    )
    serialized = json.dumps(result)

    assert result["data_count"] == 1
    assert result["item_schema"] == {"matchPrice": "number", "symbol": "string"}
    assert result["has_trading_date"] is True
    assert "TCB" not in serialized
    assert "123456" not in serialized
    assert "2099-12-31" not in serialized


def test_tcbs_error_summary_keeps_code_but_suppresses_message() -> None:
    response = httpx.Response(
        400,
        json={"code": "203071", "message": "Account details must stay private"},
    )
    result = summarize_error_response(response)
    serialized = json.dumps(result)

    assert result["provider_code"] == "203071"
    assert result["response_schema"] == {"code": "string", "message": "string"}
    assert "Account details" not in serialized


def test_written_summary_digest_matches_file_bytes(capsys) -> None:
    output_dir = Path("poc-output") / "pytest"
    path = write_summary(output_dir, "summary.json", {"gate_passed": True})
    try:
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        assert digest in capsys.readouterr().out
    finally:
        path.unlink(missing_ok=True)
        output_dir.rmdir()


def test_vnstock_agent_bootstrap_is_replaced_with_noop() -> None:
    module_name = "vnstock.core.utils.agents"
    original = sys.modules.get(module_name)
    try:
        suppress_vnstock_agent_bootstrap()
        assert sys.modules[module_name].init_agent_environment() is False
    finally:
        if original is None:
            sys.modules.pop(module_name, None)
        else:
            sys.modules[module_name] = original


def test_vnstock_capture_suppresses_provider_exception_text() -> None:
    result = capture("history", lambda: (_ for _ in ()).throw(RuntimeError("token-value")))

    assert result == {"status": "FAIL", "error_type": "RuntimeError", "probe": "history"}
    assert "token-value" not in json.dumps(result)


def test_vnstock_capture_converts_provider_system_exit_to_safe_failure() -> None:
    result = capture("history", lambda: (_ for _ in ()).throw(SystemExit("rate limited")))

    assert result == {"status": "FAIL", "error_type": "SystemExit", "probe": "history"}


def test_full_universe_candidates_include_only_eligible_common_equities() -> None:
    reference = pd.DataFrame(
        [
            {"symbol": "aaa", "exchange": "HOSE", "type": "stock"},
            {"symbol": "bbb", "exchange": "HNX", "type": "stock"},
            {"symbol": "ccc", "exchange": "UPCOM", "type": "stock"},
            {"symbol": "ddd", "exchange": "HOSE", "type": "fund"},
            {"symbol": "eee", "exchange": "XHNF", "type": "stock"},
        ]
    )

    assert eligible_equity_candidates(reference) == [
        EquityCandidate("BBB", "HNX"),
        EquityCandidate("AAA", "HOSE"),
        EquityCandidate("CCC", "UPCOM"),
    ]


def test_full_universe_probe_is_bounded_sanitized_and_resumable(tmp_path: Path) -> None:
    candidates = [
        EquityCandidate("AAA", "HOSE"),
        EquityCandidate("BBB", "HNX"),
        EquityCandidate("CCC", "UPCOM"),
    ]
    frame = pd.DataFrame(
        {
            "time": pd.date_range("2024-01-01", periods=271),
            "open": [1.0] * 271,
            "high": [1.0] * 271,
            "low": [1.0] * 271,
            "close": [1.0] * 271,
            "volume": [1] * 271,
        }
    )
    checkpoint = tmp_path / "checkpoint.json"
    requests: list[str] = []

    class NoWaitPacer:
        def wait_before_request(self) -> None:
            return None

    first = full_universe_probe(
        candidates=candidates,
        fetch_history=lambda symbol: requests.append(symbol) or frame,
        source="KBS",
        start="2024-01-01",
        end="2026-08-17",
        requests_per_minute=30,
        checkpoint_path=checkpoint,
        resume=False,
        max_symbols=2,
        pacer=NoWaitPacer(),
    )

    assert first["attempted_count"] == 2
    assert first["processed_count"] == 2
    assert first["successful_count"] == 2
    assert first["remaining_count"] == 1
    assert first["coverage_gate_passed"] is False
    assert requests == ["AAA", "BBB"]
    assert "AAA" not in checkpoint.read_text(encoding="utf-8")
    assert "BBB" not in json.dumps(first)

    resumed = full_universe_probe(
        candidates=candidates,
        fetch_history=lambda symbol: requests.append(symbol) or frame,
        source="KBS",
        start="2024-01-01",
        end="2026-08-17",
        requests_per_minute=30,
        checkpoint_path=checkpoint,
        resume=True,
        max_symbols=2,
        pacer=NoWaitPacer(),
    )

    assert resumed["resumed_processed_count"] == 2
    assert resumed["attempted_count"] == 1
    assert resumed["coverage_gate_passed"] is True
    assert requests == ["AAA", "BBB", "CCC"]


def test_full_universe_records_short_history_without_retrying_it(tmp_path: Path) -> None:
    candidates = [EquityCandidate("AAA", "HOSE")]
    short_history = pd.DataFrame(
        {
            "time": pd.date_range("2026-01-01", periods=10),
            "open": [1.0] * 10,
            "high": [1.0] * 10,
            "low": [1.0] * 10,
            "close": [1.0] * 10,
            "volume": [1] * 10,
        }
    )
    checkpoint = tmp_path / "checkpoint.json"

    class NoWaitPacer:
        def wait_before_request(self) -> None:
            return None

    result = full_universe_probe(
        candidates=candidates,
        fetch_history=lambda _: short_history,
        source="KBS",
        start="2024-01-01",
        end="2026-08-17",
        requests_per_minute=30,
        checkpoint_path=checkpoint,
        resume=False,
        max_symbols=1,
        pacer=NoWaitPacer(),
    )

    assert result["processed_count"] == 1
    assert result["successful_count"] == 0
    assert result["unavailable_history_count"] == 1
    assert result["history_outcome_counts"] == {"INSUFFICIENT_HISTORY": 1}
    assert result["coverage_gate_passed"] is True
    assert "AAA" not in checkpoint.read_text(encoding="utf-8")


def test_history_result_classifies_provider_failure_separately() -> None:
    assert classify_history_result({"status": "FAIL", "error_type": "SystemExit"}) == "PROVIDER_FAILURE_SystemExit"


def test_request_pacer_spaces_requests_below_community_limit() -> None:
    sleeps: list[float] = []
    pacer = RequestPacer(30, monotonic=lambda: 0.0, sleep=sleeps.append)

    pacer.wait_before_request()
    pacer.wait_before_request()

    assert sleeps == [2.0]


def test_bounded_execution_does_not_claim_or_require_full_coverage() -> None:
    partial = {"status": "PASS", "failed_attempt_count": 0, "coverage_gate_passed": False}

    assert execution_passed(True, partial, max_symbols=25) is True
    assert execution_passed(True, partial, max_symbols=None) is False
    assert execution_passed(True, {**partial, "failed_attempt_count": 1}, max_symbols=25) is False
