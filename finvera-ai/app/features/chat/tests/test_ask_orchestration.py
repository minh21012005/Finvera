import uuid
import pytest
from unittest.mock import AsyncMock

from app.features.chat.service import ChatOrchestrationService, OrchestrateAskRequest
from app.features.orchestration.dispatch import BackendToolClient, OrchestrationDispatcher


@pytest.mark.asyncio
async def test_t014_single_structured_tool_ask_and_attribution():
    """
    T014 [US1] [FR-001, FR-002, FR-003, AI-001]:
    Contract/integration test for single-structured-tool question,
    asserting the returned claim exactly matches that tool's actual response field.
    """
    mock_client = AsyncMock(spec=BackendToolClient)
    mock_client.execute_tool.return_value = (
        True,
        {
            "symbol": "HPG",
            "price": "28500",
            "changePercent": "1.79",
            "volume": 12000000,
            "asOf": "2026-08-20T10:00:00Z",
        },
        None,
    )

    dispatcher = OrchestrationDispatcher(tool_client=mock_client)
    service = ChatOrchestrationService(dispatcher=dispatcher)

    req = OrchestrateAskRequest(
        ownerId=uuid.uuid4(),
        question="Giá cổ phiếu HPG hôm nay bao nhiêu?",
        symbol="HPG",
    )

    events = []
    async for event in service.orchestrate_stream(req):
        events.append(event)

    types = [e["type"] for e in events]
    assert "tool_call" in types
    assert "delta" in types
    assert "final" in types

    final_event = next(e for e in events if e["type"] == "final")
    final_data = final_event["final"]

    assert final_data["refused"] is False
    assert len(final_data["structuredClaims"]) >= 1
    # Check claim fieldPath and asOf
    price_claim = next(c for c in final_data["structuredClaims"] if c["fieldPath"] == "price")
    assert price_claim["claimedValue"] == "28500"
    assert price_claim["asOf"] == "2026-08-20T10:00:00Z"


@pytest.mark.asyncio
async def test_t015_outside_capability_refused():
    """
    T015 [US1] [FR-005, AI-004]:
    A question needing no allowlisted tool returns refused = True, not a fabricated answer.
    """
    service = ChatOrchestrationService()
    req = OrchestrateAskRequest(
        ownerId=uuid.uuid4(),
        question="Thời tiết hôm nay ở Hà Nội có mưa không bạn ơi?",
    )

    events = []
    async for event in service.orchestrate_stream(req):
        events.append(event)

    assert len(events) == 1
    assert events[0]["type"] == "final"
    final_data = events[0]["final"]
    assert final_data["refused"] is True
    assert "phạm vi" in final_data["answer"]


@pytest.mark.asyncio
async def test_t016_max_tool_call_bound_reached():
    """
    T016 [US1] [FR-011, NFR-003]:
    Dispatches stop at max_tool_calls bound; toolCallBoundReached = True.
    """
    mock_client = AsyncMock(spec=BackendToolClient)
    mock_client.execute_tool.return_value = (
        True,
        {"symbol": "HPG", "price": "28500", "asOf": "2026-08-20T10:00:00Z"},
        None,
    )

    dispatcher = OrchestrationDispatcher(tool_client=mock_client)
    service = ChatOrchestrationService(dispatcher=dispatcher)

    # Monkeypatch plan_tools to return 15 proposed tool calls
    service.plan_tools = lambda q, s: [
        {"tool_name": "STOCK", "arguments": {"symbol": f"SYM{i}"}}
        for i in range(15)
    ]

    req = OrchestrateAskRequest(
        ownerId=uuid.uuid4(),
        question="Phân tích tổng hợp 15 mã cổ phiếu",
    )

    events = []
    async for event in service.orchestrate_stream(req):
        events.append(event)

    final_data = next(e for e in events if e["type"] == "final")["final"]
    tool_calls = [e for e in events if e["type"] == "tool_call"]

    assert len(tool_calls) == 10  # Stopped at limit 10
    assert final_data["toolCallBoundReached"] is True


@pytest.mark.asyncio
async def test_t017_tool_failure_degrades_gracefully():
    """
    T017 [US1] [FR-012]:
    One allowlisted tool call fails/times out; other successful tools still contribute.
    """
    mock_client = AsyncMock(spec=BackendToolClient)
    # First call succeeds, second fails
    mock_client.execute_tool.side_effect = [
        (True, {"symbol": "HPG", "price": "28500", "asOf": "2026-08-20T10:00:00Z"}, None),
        (False, None, "TIMEOUT: 10s exceeded"),
    ]

    dispatcher = OrchestrationDispatcher(tool_client=mock_client)
    service = ChatOrchestrationService(dispatcher=dispatcher)

    service.plan_tools = lambda q, s: [
        {"tool_name": "STOCK", "arguments": {"symbol": "HPG"}},
        {"tool_name": "TECHNICAL", "arguments": {"symbol": "HPG"}},
    ]

    req = OrchestrateAskRequest(
        ownerId=uuid.uuid4(),
        question="Giá và kỹ thuật HPG",
        symbol="HPG",
    )

    events = []
    async for event in service.orchestrate_stream(req):
        events.append(event)

    final_data = next(e for e in events if e["type"] == "final")["final"]
    assert final_data["refused"] is False
    assert "28.500" in final_data["answer"]  # vi-VN formatting (Feature 015)
    # Check that failed tool call is in toolCalls with FAILED status
    failed_tool = next(t for t in final_data["toolCalls"] if t["status"] == "FAILED")
    assert failed_tool["failureReason"] == "TIMEOUT: 10s exceeded"


@pytest.mark.asyncio
async def test_t018_stateless_cross_question_isolation():
    """
    T018 [US1] [FR-013]:
    Two sequential unrelated questions show no cross-question memory.
    """
    mock_client = AsyncMock(spec=BackendToolClient)
    mock_client.execute_tool.side_effect = [
        (True, {"symbol": "HPG", "price": "28500", "asOf": "2026-08-20T10:00:00Z"}, None),
        (True, {"symbol": "VNM", "price": "68000", "asOf": "2026-08-20T10:00:00Z"}, None),
    ]

    dispatcher = OrchestrationDispatcher(tool_client=mock_client)
    service = ChatOrchestrationService(dispatcher=dispatcher)

    owner = uuid.uuid4()
    req1 = OrchestrateAskRequest(ownerId=owner, question="Giá HPG", symbol="HPG")
    req2 = OrchestrateAskRequest(ownerId=owner, question="Giá VNM", symbol="VNM")

    events1 = [e async for e in service.orchestrate_stream(req1)]
    events2 = [e async for e in service.orchestrate_stream(req2)]

    final1 = next(e for e in events1 if e["type"] == "final")["final"]
    final2 = next(e for e in events2 if e["type"] == "final")["final"]

    assert "HPG" in final1["answer"]
    assert "VNM" not in final1["answer"]

    assert "VNM" in final2["answer"]
    assert "HPG" not in final2["answer"]


@pytest.mark.asyncio
async def test_t019_streaming_events_order():
    """
    T019 [US1] [FR-014, NFR-001]:
    Event stream emits tool_call, delta, and final in proper sequence.
    """
    mock_client = AsyncMock(spec=BackendToolClient)
    mock_client.execute_tool.return_value = (
        True,
        {"symbol": "FPT", "price": "130000", "changePercent": "2.5", "asOf": "2026-08-20T10:00:00Z"},
        None,
    )

    dispatcher = OrchestrationDispatcher(tool_client=mock_client)
    service = ChatOrchestrationService(dispatcher=dispatcher)

    req = OrchestrateAskRequest(ownerId=uuid.uuid4(), question="Tổng quan FPT", symbol="FPT")

    event_types = []
    async for event in service.orchestrate_stream(req):
        event_types.append(event["type"])

    assert event_types[0] == "tool_call"
    assert "delta" in event_types
    assert event_types[-1] == "final"


@pytest.mark.asyncio
async def test_t049_tools_dispatch_concurrently_but_events_keep_proposal_order():
    """Four independent tools each taking 0.2s must finish well under 0.8s, in order."""
    import asyncio, time
    from unittest.mock import AsyncMock, MagicMock
    from app.features.chat.service import ChatOrchestrationService, OrchestrateAskRequest
    from app.features.orchestration.dispatch import DispatchedToolCall
    from app.features.orchestration.allowlist import ToolName

    async def slow_dispatch(sequence_no, tool_name_raw, arguments_raw, session_owner_id):
        await asyncio.sleep(0.2)
        return DispatchedToolCall(sequence_no=sequence_no, tool_name=ToolName(tool_name_raw), arguments=arguments_raw,
                                  status="SUCCEEDED", response_data={"symbol": "HPG", "price": "28500", "asOf": "2026-08-20T10:00:00Z"})

    dispatcher = MagicMock()
    dispatcher.dispatch_single_tool = AsyncMock(side_effect=slow_dispatch)
    adapter = MagicMock(); adapter.is_online = False
    service = ChatOrchestrationService(dispatcher=dispatcher, llm_adapter=adapter)
    service.plan_tools = lambda q, s: [{"tool_name": "STOCK", "arguments": {"symbol": "HPG"}}] * 4

    started = time.perf_counter()
    events = [e async for e in service.orchestrate_stream(OrchestrateAskRequest(ownerId=uuid.uuid4(), question="giá HPG"))]
    elapsed = time.perf_counter() - started

    tool_events = [e for e in events if e["type"] == "tool_call"]
    assert [e["toolCall"]["sequenceNo"] for e in tool_events] == [1, 2, 3, 4]
    assert elapsed < 0.7, f"dispatch was not concurrent: {elapsed:.2f}s"


def test_strip_synthesis_tags_keeps_markdown_line_structure():
    from app.features.chat.service import strip_synthesis_tags
    raw = (
        "**1. Kỹ thuật**\n"
        "* MA20 là 213.380,0 VND [T1:MA20.value=213380] .\n"
        "  * RSI14 đạt 68,74 [T1:RSI14.value=68.74] điểm.\n"
        "\n"
        "**2. Định giá**\n"
        "P/E là 101,05 [T2:peRatio=101.05]."
    )
    clean = strip_synthesis_tags(raw)
    assert clean.splitlines() == [
        "**1. Kỹ thuật**",
        "* MA20 là 213.380,0 VND.",
        "  * RSI14 đạt 68,74 điểm.",
        "",
        "**2. Định giá**",
        "P/E là 101,05.",
    ]
    assert "[T" not in clean


def test_offline_valuation_withheld_sentence_words_reason_codes():
    """
    Feature 014 FR-007 (contract reason-code-presentation-v1): the offline sentence explains a
    withheld valuation in words; a code the map does not know is still shown, as itself.
    """
    from app.features.orchestration.allowlist import ToolName
    from app.features.orchestration.dispatch import DispatchedToolCall

    mock_client = AsyncMock(spec=BackendToolClient)
    service = ChatOrchestrationService(dispatcher=OrchestrationDispatcher(tool_client=mock_client))
    call = DispatchedToolCall(
        sequence_no=1, tool_name=ToolName.VALUATION, arguments={"symbol": "NBW"}, status="SUCCEEDED",
        response_data={"symbol": "NBW", "classification": None, "peRatio": None,
                       "reasonCodes": ["NO_COMPARISON_BASIS", "SOME_FUTURE_CODE"]},
    )

    answer_parts, raw_claims, _ = service._offline_synthesize([call])

    sentence = " ".join(answer_parts)
    assert "Chưa đủ cơ sở so sánh (lịch sử riêng lẫn ngành)" in sentence
    assert "SOME_FUTURE_CODE" in sentence            # unknown code never hidden
    assert "NO_COMPARISON_BASIS" not in sentence      # known code never shown raw
    assert raw_claims == []                           # nothing fabricated for a withheld valuation
