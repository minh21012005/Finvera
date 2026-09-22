import uuid
from unittest.mock import AsyncMock, MagicMock
import pytest

from app.features.chat.service import ChatOrchestrationService, OrchestrateAskRequest
from app.features.orchestration.dispatch import BackendToolClient, OrchestrationDispatcher
from app.infrastructure.llm.generation import GeminiGenerationAdapter


@pytest.mark.asyncio
async def test_empty_stream_triggers_offline_fallback():
    """
    Verifies that when Gemini generation yields an empty stream (0 chunks / empty text),
    the orchestration pipeline catches it and falls back to _offline_synthesize,
    guaranteeing a non-empty answer and preserved claims.
    """
    mock_client = AsyncMock(spec=BackendToolClient)
    mock_client.execute_tool.return_value = (
        True,
        {
            "symbol": "MBB",
            "price": "20200",
            "changePercent": "0.50",
            "asOf": "2026-09-22T14:28:00Z",
        },
        None,
    )

    dispatcher = OrchestrationDispatcher(tool_client=mock_client)

    # Mock LLM adapter with empty stream
    mock_llm = MagicMock(spec=GeminiGenerationAdapter)
    mock_llm.is_online = True

    async def mock_empty_stream(prompt, system_instruction=None):
        if False:
            yield ""

    mock_llm.generate_stream_raw = mock_empty_stream
    mock_llm.propose_tool_calls = AsyncMock(return_value=[{"tool_name": "STOCK", "arguments": {"symbol": "MBB"}}])

    service = ChatOrchestrationService(dispatcher=dispatcher, llm_adapter=mock_llm)

    req = OrchestrateAskRequest(
        ownerId=uuid.uuid4(),
        question="Giá MBB hiện tại thế nào?",
        symbol="MBB",
    )

    events = []
    async for event in service.orchestrate_stream(req):
        events.append(event)

    final_event = next(e for e in events if e["type"] == "final")
    final = final_event["final"]

    # Must NOT be refused and must NOT have empty answer
    assert final["refused"] is False
    assert len(final["answer"].strip()) > 0
    assert final["synthesisMode"] == "OFFLINE_TEMPLATE"
    assert len(final["structuredClaims"]) >= 1


@pytest.mark.asyncio
async def test_generation_adapter_raises_on_empty_stream():
    """
    Verifies that GeminiGenerationAdapter raises RuntimeError when the underlying
    client returns a stream that yields no text chunks.
    """
    adapter = GeminiGenerationAdapter(api_key="mock", model="gemini-mock")
    mock_client = MagicMock()
    mock_response = MagicMock()
    mock_chunk = MagicMock()
    mock_chunk.text = None
    mock_candidate = MagicMock()
    mock_candidate.finish_reason = "SAFETY"
    mock_chunk.candidates = [mock_candidate]
    mock_client.models.generate_content_stream.return_value = [mock_chunk]
    adapter._client = mock_client

    with pytest.raises(RuntimeError, match="without yielding text"):
        async for _ in adapter._generate_stream_raw_once("test prompt"):
            pass
