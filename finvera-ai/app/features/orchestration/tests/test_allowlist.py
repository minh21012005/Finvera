import uuid
import pytest
from app.features.orchestration.allowlist import ToolName, validate_tool_call


def test_validate_allowed_tool_success():
    owner_id = uuid.uuid4()
    ok, tool_name, args, err = validate_tool_call(
        tool_name_raw="TECHNICAL",
        arguments_raw={"symbol": "fpt"},
        session_owner_id=owner_id,
    )
    assert ok is True
    assert tool_name == ToolName.TECHNICAL
    assert getattr(args, "symbol") == "FPT"
    assert getattr(args, "owner_id") == owner_id
    assert err is None


def test_validate_unlisted_tool_rejection():
    owner_id = uuid.uuid4()
    ok, tool_name, args, err = validate_tool_call(
        tool_name_raw="CRYPTOCURRENCY_TRADING",
        arguments_raw={"symbol": "BTC"},
        session_owner_id=owner_id,
    )
    assert ok is False
    assert tool_name is None
    assert "UNLISTED_TOOL" in err


def test_validate_malformed_arguments_rejection():
    owner_id = uuid.uuid4()
    ok, tool_name, args, err = validate_tool_call(
        tool_name_raw="NEWS",
        arguments_raw={"limit": 999},  # exceeds max 20
        session_owner_id=owner_id,
    )
    assert ok is False
    assert tool_name == ToolName.NEWS
    assert "INVALID_ARGUMENTS" in err


def test_validate_owner_mismatch_rejection():
    owner_id = uuid.uuid4()
    different_owner_id = uuid.uuid4()
    ok, tool_name, args, err = validate_tool_call(
        tool_name_raw="STOCK",
        arguments_raw={"symbol": "VNM", "owner_id": str(different_owner_id)},
        session_owner_id=owner_id,
    )
    assert ok is False
    assert tool_name == ToolName.STOCK
    assert "OWNER_MISMATCH" in err
