import uuid
import pytest
from unittest.mock import AsyncMock
from app.features.orchestration.allowlist import ToolName
from app.features.orchestration.dispatch import (
    BackendToolClient,
    OrchestrationDispatcher,
)


@pytest.mark.asyncio
async def test_dispatch_bound_reached():
    mock_client = AsyncMock(spec=BackendToolClient)
    mock_client.execute_tool.return_value = (True, {"symbol": "HPG", "price": "28500", "asOf": "2026-08-20T10:00:00Z"}, None)

    dispatcher = OrchestrationDispatcher(tool_client=mock_client)
    session_owner_id = uuid.uuid4()

    proposed_calls = [
        {"tool_name": "STOCK", "arguments": {"symbol": f"SYM{i}"}}
        for i in range(1, 15)
    ]

    dispatched, bound_reached = await dispatcher.execute_dispatch_plan(
        proposed_calls=proposed_calls,
        session_owner_id=session_owner_id,
        max_tool_calls=5,
    )

    assert len(dispatched) == 5
    assert bound_reached is True


@pytest.mark.asyncio
async def test_dispatch_handles_backend_failure_gracefully():
    mock_client = AsyncMock(spec=BackendToolClient)
    mock_client.execute_tool.return_value = (False, None, "TIMEOUT: Tool call exceeded 10.0s")

    dispatcher = OrchestrationDispatcher(tool_client=mock_client)
    session_owner_id = uuid.uuid4()

    result = await dispatcher.dispatch_single_tool(
        sequence_no=1,
        tool_name_raw="TECHNICAL",
        arguments_raw={"symbol": "HPG"},
        session_owner_id=session_owner_id,
    )

    assert result.status == "FAILED"
    assert "TIMEOUT" in result.failure_reason
    assert result.response_data is None


@pytest.mark.asyncio
async def test_dispatch_strategy_scan_success():
    mock_client = AsyncMock(spec=BackendToolClient)
    mock_scan_data = {
        "strategyCode": "BREAKOUT",
        "matches": [
            {
                "symbol": "HPG",
                "companyName": "Tập đoàn Hòa Phát",
                "exchange": "HOSE",
                "signal": {
                    "strategyCode": "BREAKOUT",
                    "direction": "BULLISH",
                    "entryLow": "28000.0",
                    "entryHigh": "28500.0",
                    "stopLoss": "27000.0",
                    "target1": "30000.0",
                    "signalStrength": "STRONG",
                }
            }
        ],
        "totalMatchCount": 1,
    }
    mock_client.execute_tool.return_value = (True, mock_scan_data, None)

    dispatcher = OrchestrationDispatcher(tool_client=mock_client)
    session_owner_id = uuid.uuid4()

    result = await dispatcher.dispatch_single_tool(
        sequence_no=1,
        tool_name_raw="STRATEGY_SCAN",
        arguments_raw={"strategyCode": "BREAKOUT", "limit": 5},
        session_owner_id=session_owner_id,
    )

    assert result.status == "SUCCEEDED"
    assert result.tool_name == ToolName.STRATEGY_SCAN
    assert result.response_data == mock_scan_data
    mock_client.execute_tool.assert_called_once_with(
        tool_name=ToolName.STRATEGY_SCAN,
        arguments={"owner_id": session_owner_id, "strategy_code": "BREAKOUT", "limit": 5},
        owner_id=session_owner_id,
    )


@pytest.mark.asyncio
async def test_dispatch_compare_success():
    mock_client = AsyncMock(spec=BackendToolClient)
    mock_compare_data = {
        "items": [
            {"symbol": "SSI", "price": "32500", "pe": "18.5", "roe": "14.5"},
            {"symbol": "VND", "price": "18200", "pe": "14.2", "roe": "11.8"},
        ],
        "asOf": "2026-09-10T10:00:00Z",
    }
    mock_client.execute_tool.return_value = (True, mock_compare_data, None)

    dispatcher = OrchestrationDispatcher(tool_client=mock_client)
    session_owner_id = uuid.uuid4()

    result = await dispatcher.dispatch_single_tool(
        sequence_no=1,
        tool_name_raw="COMPARE",
        arguments_raw={"symbols": ["SSI", "VND"]},
        session_owner_id=session_owner_id,
    )

    assert result.status == "SUCCEEDED"
    assert result.tool_name == ToolName.COMPARE
    assert result.response_data == mock_compare_data
    mock_client.execute_tool.assert_called_once_with(
        tool_name=ToolName.COMPARE,
        arguments={"owner_id": session_owner_id, "symbols": ["SSI", "VND"]},
        owner_id=session_owner_id,
    )


