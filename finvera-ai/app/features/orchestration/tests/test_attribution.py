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


def test_scale_multipliers_attribution_supports_vietnamese_units():
    call = DispatchedToolCall(
        sequence_no=1,
        tool_name=ToolName.PORTFOLIO,
        arguments={},
        status="SUCCEEDED",
        response_data={
            "totalValue": 16205750,
            "positions": [{"symbol": "MBB", "allocation": 0.3162, "unrealizedPnlPercent": 12.85}],
            "asOf": "2026-09-02T10:00:00Z",
        },
    )
    statement1 = "Danh mục có tổng giá trị 16,2 triệu đồng và MBB chiếm tỷ trọng 31,62%."
    statement2 = "Vị thế MBB đang lãi 12,85% theo giá thị trường."

    raw_claims = [
        RawStructuredClaim(claimText=statement1, sequenceNo=1, fieldPath="totalValue", claimedValue="16205750"),
        RawStructuredClaim(claimText=statement1, sequenceNo=1, fieldPath="positions[0].allocation", claimedValue="0.3162"),
        RawStructuredClaim(claimText=statement2, sequenceNo=1, fieldPath="positions[0].unrealizedPnlPercent", claimedValue="12.85"),
    ]

    result = verify_attribution(
        answer=f"{statement1}\n\n{statement2}",
        raw_structured_claims=raw_claims,
        verified_document_claims=[],
        dispatched_calls=[call],
        tool_call_bound_reached=False,
        synthesis_mode="ONLINE",
    )

    assert result.refused is False
    assert result.claimCoverage == "FULL"
    assert len(result.structuredClaims) == 3


def test_technical_indicator_names_with_numbers_do_not_extract_phantom_digits():
    call = DispatchedToolCall(
        sequence_no=1,
        tool_name=ToolName.TECHNICAL,
        arguments={"symbol": "STB"},
        status="SUCCEEDED",
        response_data={
            "indicators": {
                "MA20": {"applicability": "DEFINED", "components": [{"value": 74.785}]},
                "MA50": {"applicability": "DEFINED", "components": [{"value": 73.140}]},
            },
            "asOf": "2026-09-10T10:22:25Z",
        },
    )
    raw_claims = [
        RawStructuredClaim(claimText="MA20 74.785", sequenceNo=1, fieldPath="indicators.MA20.components[0].value", claimedValue="74.785"),
        RawStructuredClaim(claimText="MA50 73.140", sequenceNo=1, fieldPath="indicators.MA50.components[0].value", claimedValue="73.140"),
    ]
    result = verify_attribution(
        answer="Chỉ báo kỹ thuật STB: MA20 74.785, MA50 73.140.",
        raw_structured_claims=raw_claims,
        verified_document_claims=[],
        dispatched_calls=[call],
        tool_call_bound_reached=False,
        synthesis_mode="ONLINE",
    )
    assert result.refused is False
    assert result.claimCoverage == "FULL"
    assert len(result.structuredClaims) == 2


def test_online_mode_with_synthetic_claims_preserves_full_model_analysis():
    call = DispatchedToolCall(
        sequence_no=1,
        tool_name=ToolName.STOCK,
        arguments={"symbol": "STB"},
        status="SUCCEEDED",
        response_data={"symbol": "STB", "price": 76700.0, "asOf": "2026-09-10T10:22:24Z"},
    )
    raw_claims = [
        RawStructuredClaim(claimText="Giá 76700.0", sequenceNo=1, fieldPath="price", claimedValue="76700.0"),
    ]
    model_analysis = (
        "### 1. Tóm tắt tổng quan:\nCổ phiếu STB đang ở vùng giá 76.700 đ.\n\n"
        "### 2. Phân tích chi tiết:\nNhà đầu tư nên kiên nhẫn theo dõi diễn biến cung cầu."
    )
    result = verify_attribution(
        answer=model_analysis,
        raw_structured_claims=raw_claims,
        verified_document_claims=[],
        dispatched_calls=[call],
        tool_call_bound_reached=False,
        synthesis_mode="ONLINE",
        synthetic_claims=True,
    )
    assert result.refused is False
    assert result.claimCoverage == "FULL"
    assert result.answer == model_analysis
    assert len(result.structuredClaims) == 1


