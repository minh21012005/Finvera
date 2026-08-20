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
        raw_document_claims=[],
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
        raw_document_claims=[],
        dispatched_calls=[failed_call],
        tool_call_bound_reached=False,
    )

    assert len(result.structuredClaims) == 0
    assert result.refused is True  # Refuses when 0 claims survive
