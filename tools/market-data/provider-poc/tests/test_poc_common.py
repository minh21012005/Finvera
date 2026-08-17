import hashlib
import json
import sys
from pathlib import Path

import httpx

from poc_common import schema_of_mapping, value_type, write_summary
from poc_tcbs import summarize_error_response, summarize_response
from poc_vnstock import suppress_vnstock_agent_bootstrap


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
