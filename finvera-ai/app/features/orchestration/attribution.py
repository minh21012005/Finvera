import math
from typing import Any, Dict, List, Optional, Tuple, Union
import uuid
from pydantic import BaseModel, Field

from app.features.orchestration.dispatch import DispatchedToolCall


class RawStructuredClaim(BaseModel):
    claimText: str
    sequenceNo: int
    fieldPath: str
    claimedValue: str


class StructuredClaim(BaseModel):
    claimText: str
    sequenceNo: int
    fieldPath: str
    claimedValue: str
    asOf: str


class DocumentClaim(BaseModel):
    # Matches internal-api.openapi.yaml's DocumentClaim exactly (identical shape to
    # Feature 006's internal Citation schema) — resolution to sourceType/sourceId/
    # sourceTitle/location/source happens in finvera-be, from chunkId, mirroring
    # AskService.java's own citation resolution for Feature 006's /research/ask.
    chunkId: uuid.UUID
    claimText: str


class VerifiedAttributionResult(BaseModel):
    answer: str
    structuredClaims: List[StructuredClaim]
    documentClaims: List[DocumentClaim]
    refused: bool
    toolCalls: List[Dict[str, Any]]
    toolCallBoundReached: bool
    ruleVersion: str = "orchestration-v1"


def get_nested_value(data: Any, field_path: str) -> Tuple[bool, Any]:
    """
    Looks up a value at field_path (e.g. 'price', 'signal.direction', 'indicators.RSI.value')
    in a nested dict or list. Returns (found, value).
    """
    if not isinstance(data, dict):
        return False, None

    # Try direct key match first
    if field_path in data:
        return True, data[field_path]

    # Try case-insensitive direct key match
    for k, v in data.items():
        if k.lower() == field_path.lower():
            return True, v

    # Try dotted path traversal
    parts = field_path.split(".")
    current = data
    for part in parts:
        if isinstance(current, dict):
            found = False
            for k, v in current.items():
                if k.lower() == part.lower():
                    current = v
                    found = True
                    break
            if not found:
                return False, None
        else:
            return False, None

    return True, current


def match_claimed_value(claimed: str, actual: Any) -> bool:
    """
    U-5: Exact match for strings/enums/booleans, rounding-tolerant for decimals/floats.
    """
    if actual is None:
        return claimed.strip().lower() in ("none", "null", "n/a", "")

    actual_str = str(actual).strip()
    claimed_str = str(claimed).strip()

    # Exact string match (case-insensitive)
    if claimed_str.lower() == actual_str.lower():
        return True

    # Numeric comparison
    try:
        claimed_num = float(claimed_str.replace(",", "").replace("%", ""))
        actual_num = float(actual_str.replace(",", "").replace("%", ""))

        if math.isclose(claimed_num, actual_num, rel_tol=1e-2, abs_tol=1e-2):
            return True
    except (ValueError, TypeError):
        pass

    return False


def verify_attribution(
    answer: str,
    raw_structured_claims: List[RawStructuredClaim],
    verified_document_claims: List[DocumentClaim],
    dispatched_calls: List[DispatchedToolCall],
    tool_call_bound_reached: bool,
    explicit_refusal: bool = False,
) -> VerifiedAttributionResult:
    """
    U-5 & orchestration-v1 attribution verification pipeline:
    1. Validates each structured claim against actual response_data of succeeded tool calls.
    2. Drops misstated claims or claims pointing to non-existent/failed tools.
    3. Programmatically sets claim asOf from the tool's response (DATA-002).
    4. Flags refusal if zero claims (structured + document combined) survive when tools
       were dispatched.

    Document-claim citation verification is NOT done here: orchestration-v1 step 4
    requires delegating to rag-v1's own verify_citation_claims unchanged rather than
    reimplementing it, so the caller (chat/service.py) runs that verification first and
    passes in the already-verified `DocumentClaim` list.
    """
    calls_by_seq: Dict[int, DispatchedToolCall] = {c.sequence_no: c for c in dispatched_calls}
    surviving_structured: List[StructuredClaim] = []

    for raw_claim in raw_structured_claims:
        call = calls_by_seq.get(raw_claim.sequenceNo)
        if not call or call.status != "SUCCEEDED" or not call.response_data:
            continue

        found, actual_val = get_nested_value(call.response_data, raw_claim.fieldPath)
        if not found:
            continue

        if not match_claimed_value(raw_claim.claimedValue, actual_val):
            continue

        # Programmatically extract asOf from tool response, never trusting model
        tool_as_of = str(call.response_data.get("asOf") or call.called_at)

        surviving_structured.append(
            StructuredClaim(
                claimText=raw_claim.claimText,
                sequenceNo=raw_claim.sequenceNo,
                fieldPath=raw_claim.fieldPath,
                claimedValue=str(actual_val),
                asOf=tool_as_of,
            )
        )

    surviving_docs: List[DocumentClaim] = list(verified_document_claims)
    total_surviving = len(surviving_structured) + len(surviving_docs)
    refused = explicit_refusal or (len(dispatched_calls) > 0 and total_surviving == 0)

    # Format tool calls for response
    tool_calls_payload = [
        {
            "sequenceNo": c.sequence_no,
            "toolName": c.tool_name.value if hasattr(c.tool_name, "value") else str(c.tool_name),
            "arguments": c.arguments,
            "status": c.status,
            "failureReason": c.failure_reason,
            "latencyMs": c.latency_ms,
        }
        for c in dispatched_calls
    ]

    return VerifiedAttributionResult(
        answer=answer if not refused else "Không đủ dữ liệu tin cậy hoặc thông tin ngoài phạm vi để trả lời.",
        structuredClaims=surviving_structured if not refused else [],
        documentClaims=surviving_docs if not refused else [],
        refused=refused,
        toolCalls=tool_calls_payload,
        toolCallBoundReached=tool_call_bound_reached,
        ruleVersion="orchestration-v1",
    )
