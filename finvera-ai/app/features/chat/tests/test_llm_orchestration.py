import uuid
from unittest.mock import AsyncMock, MagicMock

import pytest

from app.features.chat.service import ChatOrchestrationService, OrchestrateAskRequest
from app.features.orchestration.dispatch import BackendToolClient, OrchestrationDispatcher


def make_online_adapter() -> MagicMock:
    """A mock GeminiGenerationAdapter that reports itself as online, so
    ChatOrchestrationService takes the real-LLM code path rather than the
    keyword/template fallback — this is what these tests exist to exercise."""
    adapter = MagicMock()
    adapter.is_online = True
    adapter.propose_tool_calls = AsyncMock()
    adapter.generate_stream_raw = MagicMock()
    return adapter


@pytest.mark.asyncio
async def test_propose_tool_calls_uses_real_llm_function_calling_output_when_online():
    """
    Foundational fix regression test: orchestration-v1's dispatch algorithm begins with
    "proposedCalls = model's function-calling output" — verify ChatOrchestrationService
    actually uses the LLM adapter's function-calling result when online, rather than
    always falling through to the keyword heuristic (the bug found in code review).
    """
    adapter = make_online_adapter()
    adapter.propose_tool_calls.return_value = [
        {"tool_name": "STOCK", "arguments": {"symbol": "FPT"}},
    ]
    service = ChatOrchestrationService(llm_adapter=adapter)

    proposed = await service.propose_tool_calls("Phân tích FPT hiện tại thế nào?", None, [])

    adapter.propose_tool_calls.assert_awaited_once()
    assert proposed == [{"tool_name": "STOCK", "arguments": {"symbol": "FPT"}}]


@pytest.mark.asyncio
async def test_propose_tool_calls_falls_back_to_keyword_heuristic_when_llm_returns_none():
    """When the online provider call itself fails (adapter returns None), falls back to
    the deterministic plan_tools() heuristic rather than propagating the failure."""
    adapter = make_online_adapter()
    adapter.propose_tool_calls.return_value = None
    service = ChatOrchestrationService(llm_adapter=adapter)

    proposed = await service.propose_tool_calls("Giá cổ phiếu FPT hôm nay bao nhiêu?", "FPT", [])

    assert proposed == [{"tool_name": "STOCK", "arguments": {"symbol": "FPT"}}]


@pytest.mark.asyncio
async def test_online_synthesis_extracts_and_verifies_structured_claim_from_inline_tag():
    """
    U-5: a structured claim the model states inline via [T<seq>:<field>=<value>] must
    be extracted and verified against the tool's actual response — not trusted blindly.
    """

    async def fake_stream(prompt, system_instruction=None):
        for chunk in ["Giá FPT hiện là 130000 ", "[T1:price=130000]."]:
            yield chunk

    adapter = make_online_adapter()
    adapter.generate_stream_raw.side_effect = fake_stream

    mock_client = AsyncMock(spec=BackendToolClient)
    mock_client.execute_tool.return_value = (
        True,
        {"symbol": "FPT", "price": "130000", "asOf": "2026-08-20T10:00:00Z"},
        None,
    )
    dispatcher = OrchestrationDispatcher(tool_client=mock_client)

    adapter.propose_tool_calls.return_value = [{"tool_name": "STOCK", "arguments": {"symbol": "FPT"}}]
    service = ChatOrchestrationService(dispatcher=dispatcher, llm_adapter=adapter)

    req = OrchestrateAskRequest(ownerId=uuid.uuid4(), question="Giá FPT?", symbol="FPT")
    events = [e async for e in service.orchestrate_stream(req)]

    final = next(e for e in events if e["type"] == "final")["final"]
    assert final["refused"] is False
    assert len(final["structuredClaims"]) == 1
    claim = final["structuredClaims"][0]
    assert claim["fieldPath"] == "price"
    assert claim["claimedValue"] == "130000"
    assert claim["asOf"] == "2026-08-20T10:00:00Z"
    # Tags must not leak into the displayed answer.
    assert "[T1:" not in final["answer"]


@pytest.mark.asyncio
async def test_online_synthesis_drops_misstated_structured_claim():
    """U-5: a claimed value that does not match the tool's real response is dropped,
    exactly the scenario that was previously unreachable because there was no real LLM
    output to misstate anything."""

    async def fake_stream(prompt, system_instruction=None):
        yield "Giá FPT là 999999 [T1:price=999999]."

    adapter = make_online_adapter()
    adapter.generate_stream_raw.side_effect = fake_stream

    mock_client = AsyncMock(spec=BackendToolClient)
    mock_client.execute_tool.return_value = (
        True,
        {"symbol": "FPT", "price": "130000", "asOf": "2026-08-20T10:00:00Z"},
        None,
    )
    dispatcher = OrchestrationDispatcher(tool_client=mock_client)
    adapter.propose_tool_calls.return_value = [{"tool_name": "STOCK", "arguments": {"symbol": "FPT"}}]
    service = ChatOrchestrationService(dispatcher=dispatcher, llm_adapter=adapter)

    req = OrchestrateAskRequest(ownerId=uuid.uuid4(), question="Giá FPT?", symbol="FPT")
    events = [e async for e in service.orchestrate_stream(req)]

    final = next(e for e in events if e["type"] == "final")["final"]
    assert final["refused"] is True
    assert len(final["structuredClaims"]) == 0


@pytest.mark.asyncio
async def test_online_synthesis_delegates_document_citation_to_rag_v1_verification():
    """
    orchestration-v1 step 4: document claims must be verified via rag-v1's own
    verify_citation_claims (delegated), not a reimplementation. A [Block N] tag outside
    [1, total_blocks_k] must be dropped exactly like rag-v1's own contract requires.
    """
    chunk_id = uuid.uuid4()

    async def fake_stream(prompt, system_instruction=None):
        yield "Theo tài liệu, kế hoạch doanh thu đạt 100 tỷ [Block 1]. Một trích dẫn không hợp lệ [Block 9]."

    adapter = make_online_adapter()
    adapter.generate_stream_raw.side_effect = fake_stream

    mock_client = AsyncMock(spec=BackendToolClient)
    mock_client.execute_tool.return_value = (
        True,
        {
            "passages": [
                {
                    "chunkId": str(chunk_id),
                    "sourceType": "DOCUMENT",
                    "sourceId": str(uuid.uuid4()),
                    "sourceTitle": "Báo cáo thường niên",
                    "location": "Page 1",
                    "source": "FPT Investor Relations",
                    "excerpt": "Kế hoạch doanh thu đạt 100 tỷ đồng.",
                    "score": 0.9,
                }
            ]
        },
        None,
    )
    dispatcher = OrchestrationDispatcher(tool_client=mock_client)
    adapter.propose_tool_calls.return_value = [
        {"tool_name": "RESEARCH_RAG", "arguments": {"query": "kế hoạch doanh thu"}}
    ]
    service = ChatOrchestrationService(dispatcher=dispatcher, llm_adapter=adapter)

    req = OrchestrateAskRequest(ownerId=uuid.uuid4(), question="Kế hoạch doanh thu?")
    events = [e async for e in service.orchestrate_stream(req)]

    final = next(e for e in events if e["type"] == "final")["final"]
    assert final["refused"] is False
    assert len(final["documentClaims"]) == 1
    assert final["documentClaims"][0]["chunkId"] == chunk_id
    assert "[Block" not in final["answer"]


@pytest.mark.asyncio
async def test_online_synthesis_failure_falls_back_to_offline_templates_without_partial_stream():
    """A mid-attempt online synthesis failure must fall back entirely to the
    deterministic offline templates, never leave the client with a truncated/mixed
    answer."""

    async def failing_stream(prompt, system_instruction=None):
        raise RuntimeError("simulated provider outage")
        yield ""  # pragma: no cover - unreachable, makes this an async generator

    adapter = make_online_adapter()
    adapter.generate_stream_raw.side_effect = failing_stream

    mock_client = AsyncMock(spec=BackendToolClient)
    mock_client.execute_tool.return_value = (
        True,
        {"symbol": "FPT", "price": "130000", "changePercent": "2.5", "asOf": "2026-08-20T10:00:00Z"},
        None,
    )
    dispatcher = OrchestrationDispatcher(tool_client=mock_client)
    adapter.propose_tool_calls.return_value = [{"tool_name": "STOCK", "arguments": {"symbol": "FPT"}}]
    service = ChatOrchestrationService(dispatcher=dispatcher, llm_adapter=adapter)

    req = OrchestrateAskRequest(ownerId=uuid.uuid4(), question="Giá FPT?", symbol="FPT")
    events = [e async for e in service.orchestrate_stream(req)]

    final = next(e for e in events if e["type"] == "final")["final"]
    assert final["refused"] is False
    assert "130000" in final["answer"]
    deltas = [e for e in events if e["type"] == "delta"]
    assert len(deltas) > 0
