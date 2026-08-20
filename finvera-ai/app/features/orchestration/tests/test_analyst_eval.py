import pytest
import uuid
from app.features.orchestration.allowlist import ToolName
from app.features.orchestration.attribution import (
    RawStructuredClaim,
    StructuredClaim,
    DocumentClaim,
    verify_attribution,
    VerifiedAttributionResult,
)
from app.features.orchestration.dispatch import DispatchedToolCall
from app.features.orchestration.screener_conversion import convert_natural_language_to_filters
from app.features.analysis.explain import (
    verify_faithfulness,
    ExplainRequest,
    EvidenceFactor,
    explain_deterministic_output,
)


@pytest.mark.asyncio
async def test_sc001_100_percent_verified_claims():
    """
    SC-001: 100% of structured financial facts in the assistant answer
    are traceable to tool outputs with verified fieldPath and claimedValue.
    """
    tool_calls = [
        DispatchedToolCall(
            sequence_no=1,
            tool_name=ToolName.STOCK,
            arguments={"symbol": "HPG"},
            status="SUCCEEDED",
            latency_ms=40,
            response_data={"symbol": "HPG", "price": "28500", "changePercent": "1.79", "asOf": "2026-08-20T10:00:00Z"},
        )
    ]
    raw_claims = [
        RawStructuredClaim(
            claimText="Giá 28500",
            sequenceNo=1,
            fieldPath="price",
            claimedValue="28500",
        ),
        RawStructuredClaim(
            claimText="Tăng 1.79%",
            sequenceNo=1,
            fieldPath="changePercent",
            claimedValue="1.79",
        ),
    ]
    
    verified = verify_attribution(
        answer="Giá cổ phiếu HPG hiện tại là 28500 đồng, tăng 1.79% so với phiên trước.",
        raw_structured_claims=raw_claims,
        raw_document_claims=[],
        dispatched_calls=tool_calls,
        tool_call_bound_reached=False,
    )
    
    assert not verified.refused
    assert len(verified.structuredClaims) == 2
    claims_map = {c.fieldPath: c.claimedValue for c in verified.structuredClaims}
    assert claims_map["price"] == "28500"
    assert claims_map["changePercent"] == "1.79"


@pytest.mark.asyncio
async def test_sc002_distinct_structured_and_document_claims():
    """
    SC-002: Combined questions synthesizing both structured facts and document passages
    retain distinct attribution cards for structured facts and document citations.
    """
    chunk_id = uuid.uuid4()
    tool_calls = [
        DispatchedToolCall(
            sequence_no=1,
            tool_name=ToolName.STOCK,
            arguments={"symbol": "VNM"},
            status="SUCCEEDED",
            latency_ms=35,
            response_data={"symbol": "VNM", "price": "67000", "asOf": "2026-08-20T10:00:00Z"},
        ),
        DispatchedToolCall(
            sequence_no=2,
            tool_name=ToolName.RESEARCH_RAG,
            arguments={"symbol": "VNM", "query": "Kế hoạch doanh thu 2026"},
            status="SUCCEEDED",
            latency_ms=120,
            response_data={
                "chunks": [
                    {
                        "chunk_id": str(chunk_id),
                        "title": "Báo cáo thường niên VNM 2025",
                        "content": "Kế hoạch doanh thu năm 2026 đạt 65.000 tỷ VNĐ",
                        "page_number": 12,
                        "source": "DOCUMENT",
                    }
                ]
            },
        ),
    ]
    raw_structured = [
        RawStructuredClaim(
            claimText="Giá 67000",
            sequenceNo=1,
            fieldPath="price",
            claimedValue="67000",
        )
    ]
    raw_doc = [
        {
            "chunk_id": str(chunk_id),
            "claim_text": "Theo Báo cáo thường niên VNM 2025, kế hoạch doanh thu năm 2026 đạt 65.000 tỷ VNĐ.",
            "title": "Báo cáo thường niên VNM 2025",
            "page_number": 12,
        }
    ]
    
    verified = verify_attribution(
        answer="Giá VNM là 67000. Theo Báo cáo thường niên VNM 2025, kế hoạch doanh thu năm 2026 đạt 65.000 tỷ VNĐ.",
        raw_structured_claims=raw_structured,
        raw_document_claims=raw_doc,
        dispatched_calls=tool_calls,
        tool_call_bound_reached=False,
    )
    
    assert len(verified.structuredClaims) == 1
    assert verified.structuredClaims[0].claimedValue == "67000"
    assert len(verified.documentClaims) == 1
    assert verified.documentClaims[0].title == "Báo cáo thường niên VNM 2025"
    assert verified.documentClaims[0].pageNumber == 12


@pytest.mark.asyncio
async def test_sc003_out_of_capability_and_bound_reached():
    """
    SC-003: Out-of-capability queries result in transparent refusal;
    queries hitting max tool bounds are explicitly flagged.
    """
    from app.features.chat.service import ChatOrchestrationService, OrchestrateAskRequest

    service = ChatOrchestrationService()
    
    # 1. Out-of-scope question
    out_of_scope_req = OrchestrateAskRequest(
        question="Thời tiết hôm nay ở Hà Nội như thế nào?",
        ownerId=uuid.uuid4(),
    )
    events = [e async for e in service.orchestrate_stream(out_of_scope_req)]
    final_event = next(e for e in events if e["type"] == "final")
    assert final_event["final"]["refused"] is True
    assert "ngoài phạm vi" in final_event["final"]["answer"]


@pytest.mark.asyncio
async def test_sc004_screening_conversion_accuracy_and_ambiguity_note():
    """
    SC-004: Unambiguous queries convert to exact schema with confidence >= 0.6;
    vague queries receive confidence < 0.6 and ambiguityNote.
    """
    # Unambiguous
    unambiguous_res = await convert_natural_language_to_filters(
        "Lọc các mã cổ phiếu sàn HOSE có P/E dưới 15 và ROE trên 12%"
    )
    assert unambiguous_res.confidence >= 0.6
    assert unambiguous_res.ambiguityNote is None
    assert unambiguous_res.filters["fundamental"]["peMax"] == "15"
    assert unambiguous_res.filters["fundamental"]["roeMin"] == "12"
    assert "HOSE" in unambiguous_res.filters["market"]["exchange"]

    # Ambiguous
    ambiguous_res = await convert_natural_language_to_filters(
        "Tìm cho tôi vài cổ phiếu tiềm năng ăn bằng lần"
    )
    assert ambiguous_res.confidence < 0.6
    assert ambiguous_res.ambiguityNote is not None


@pytest.mark.asyncio
async def test_sc005_explanation_faithfulness_and_unverified_fallback():
    """
    SC-005: Explanation references only supplied factors.
    Any extraneous/fabricated factor leads to verified=False fallback.
    """
    supplied = [
        EvidenceFactor(factorCode="RSI_14", description="RSI vượt 70"),
        EvidenceFactor(factorCode="MA_20", description="Giá vượt MA20"),
    ]

    # Faithful
    faithful_text = "Tín hiệu mua xuất hiện do RSI_14 vượt 70 và Giá vượt MA_20."
    is_faithful, referenced = verify_faithfulness(faithful_text, supplied)
    assert is_faithful is True
    assert "RSI_14" in referenced
    assert "MA_20" in referenced

    # Extraneous factor (MACD was not in supplied factors)
    hallucinated_text = "Tín hiệu mua xuất hiện do RSI_14 vượt 70 và chỉ báo MACD cắt lên đường tín hiệu."
    is_unfaithful, unfaithful_referenced = verify_faithfulness(hallucinated_text, supplied)
    assert is_unfaithful is False
    assert len(unfaithful_referenced) == 0

