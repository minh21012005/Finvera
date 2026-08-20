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
