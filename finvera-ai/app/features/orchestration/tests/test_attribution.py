import uuid
import pytest
from app.features.orchestration.allowlist import ToolName
from app.features.orchestration.attribution import (
    RawStructuredClaim,
    StructuredClaim,
    verify_attribution,
)
from app.features.orchestration.dispatch import DispatchedToolCall


def test_attribution_drops_misstated_claim():
    tool_call = DispatchedToolCall(
        sequence_no=1,
        tool_name=ToolName.TECHNICAL,
        arguments={"symbol": "HPG"},
        status="SUCCEEDED",
        response_data={
            "symbol": "HPG",
            "price": "28500",
            "rsi": 65.4,
            "asOf": "2026-08-20T10:00:00Z",
        },
    )

    raw_claims = [
        RawStructuredClaim(
            claimText="Giá HPG là 99,000",
            sequenceNo=1,
            fieldPath="price",
            claimedValue="99000",  # Misstated!
        ),
        RawStructuredClaim(
            claimText="RSI của HPG là 65.4",
            sequenceNo=1,
            fieldPath="rsi",
            claimedValue="65.4",  # Accurate
        ),
    ]

    result = verify_attribution(
        answer="HPG có giá 99,000 và RSI 65.4",
        raw_structured_claims=raw_claims,
        verified_document_claims=[],
        dispatched_calls=[tool_call],
        tool_call_bound_reached=False,
    )

    assert len(result.structuredClaims) == 1
    surviving = result.structuredClaims[0]
    assert surviving.fieldPath == "rsi"
    assert surviving.claimedValue == "65.4"
    assert surviving.asOf == "2026-08-20T10:00:00Z"  # Programmatically set


def test_attribution_drops_claims_on_failed_tool():
    failed_call = DispatchedToolCall(
        sequence_no=1,
        tool_name=ToolName.FUNDAMENTAL,
        arguments={"symbol": "VNM"},
        status="FAILED",
        failure_reason="TIMEOUT",
        response_data=None,
    )

    raw_claims = [
        RawStructuredClaim(
            claimText="EPS VNM là 4500",
            sequenceNo=1,
            fieldPath="eps",
            claimedValue="4500",
        )
    ]

    result = verify_attribution(
        answer="VNM có EPS là 4500",
        raw_structured_claims=raw_claims,
        verified_document_claims=[],
        dispatched_calls=[failed_call],
        tool_call_bound_reached=False,
    )

    assert len(result.structuredClaims) == 0
    assert result.refused is True  # Refuses when 0 claims survive


def test_online_fluent_but_unattributed_answer_fails_closed():
    call = DispatchedToolCall(
        sequence_no=1,
        tool_name=ToolName.STOCK,
        arguments={"symbol": "FPT"},
        status="SUCCEEDED",
        response_data={"symbol": "FPT", "price": "130000", "asOf": "2026-09-02T03:00:00Z"},
    )
    result = verify_attribution(
        answer=("FPT đang có giá 999.999 đồng và chắc chắn sẽ tăng mạnh; "
                "nhà đầu tư nên mua ngay để tối đa hóa lợi nhuận."),
        raw_structured_claims=[],
        verified_document_claims=[],
        dispatched_calls=[call],
        tool_call_bound_reached=False,
        synthesis_mode="ONLINE",
    )
    assert result.refused is True
    assert result.claimCoverage == "NONE"
    assert "999.999" not in result.answer


def test_online_answer_keeps_only_fully_verified_statements_and_reports_partial():
    call = DispatchedToolCall(
        sequence_no=1,
        tool_name=ToolName.STOCK,
        arguments={"symbol": "FPT"},
        status="SUCCEEDED",
        response_data={"symbol": "FPT", "price": "130000", "changePercent": "1.5", "asOf": "2026-09-02T03:00:00Z"},
    )
    claims = [
        RawStructuredClaim(claimText="FPT đóng cửa ở 130.000 đồng.", sequenceNo=1, fieldPath="price", claimedValue="130000"),
        RawStructuredClaim(claimText="Mức tăng 99% cho thấy cổ phiếu chắc chắn tiếp tục đi lên.", sequenceNo=1, fieldPath="changePercent", claimedValue="99"),
    ]
    result = verify_attribution(
        answer="FPT đóng cửa ở 130.000 đồng. Mức tăng 99% cho thấy cổ phiếu chắc chắn tiếp tục đi lên.",
        raw_structured_claims=claims,
        verified_document_claims=[],
        dispatched_calls=[call],
        tool_call_bound_reached=False,
        synthesis_mode="ONLINE",
    )
    assert result.refused is False
    assert result.claimCoverage == "PARTIAL"
    assert result.answer == "FPT đóng cửa ở 130.000 đồng."
    assert [claim.fieldPath for claim in result.structuredClaims] == ["price"]


def test_online_statement_rejects_extra_number_not_supported_by_its_valid_tag():
    call = DispatchedToolCall(
        sequence_no=1,
        tool_name=ToolName.STOCK,
        arguments={"symbol": "FPT"},
        status="SUCCEEDED",
        response_data={"symbol": "FPT", "price": "130000", "asOf": "2026-09-02T03:00:00Z"},
    )
    claim = RawStructuredClaim(
        claimText="FPT có giá 130.000 đồng và sẽ tăng 99%.", sequenceNo=1,
        fieldPath="price", claimedValue="130000",
    )
    result = verify_attribution(
        answer=claim.claimText,
        raw_structured_claims=[claim],
        verified_document_claims=[],
        dispatched_calls=[call],
        tool_call_bound_reached=False,
        synthesis_mode="ONLINE",
    )
    assert result.refused is True
    assert result.claimCoverage == "NONE"
    assert result.structuredClaims == []


def test_online_statement_rejects_direct_trading_advice_even_with_valid_tag():
    call = DispatchedToolCall(
        sequence_no=1,
        tool_name=ToolName.STOCK,
        arguments={"symbol": "FPT"},
        status="SUCCEEDED",
        response_data={"symbol": "FPT", "price": "130000", "asOf": "2026-09-02T03:00:00Z"},
    )
    claim = RawStructuredClaim(
        claimText="FPT có giá 130.000 đồng nên mua ngay.", sequenceNo=1,
        fieldPath="price", claimedValue="130000",
    )

    result = verify_attribution(
        answer=claim.claimText,
        raw_structured_claims=[claim],
        verified_document_claims=[],
        dispatched_calls=[call],
        tool_call_bound_reached=False,
        synthesis_mode="ONLINE",
    )

    assert result.refused is True
    assert result.claimCoverage == "NONE"


def test_price_claim_cannot_support_unrelated_hundredfold_number():
    call = DispatchedToolCall(
        sequence_no=1, tool_name=ToolName.STOCK, arguments={"symbol": "FPT"},
        status="SUCCEEDED",
        response_data={"price": "130000", "asOf": "2026-09-02T03:00:00Z"},
    )
    claim = RawStructuredClaim(
        claimText="Giá FPT là 130.000 đồng nhưng mục tiêu là 1.300 đồng.",
        sequenceNo=1, fieldPath="price", claimedValue="130000",
    )

    result = verify_attribution(
        answer=claim.claimText, raw_structured_claims=[claim], verified_document_claims=[],
        dispatched_calls=[call], tool_call_bound_reached=False, synthesis_mode="ONLINE",
    )

    assert result.refused is True
