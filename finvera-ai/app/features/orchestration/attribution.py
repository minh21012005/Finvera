import math
from typing import Any, Dict, List, Optional, Tuple, Union
import uuid
from pydantic import BaseModel, Field

from app.features.orchestration.allowlist import ToolName
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
    chunkId: uuid.UUID
    claimText: str
    title: Optional[str] = None
    source: Optional[str] = None
    pageNumber: Optional[int] = None


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
    raw_document_claims: List[Dict[str, Any]],
    dispatched_calls: List[DispatchedToolCall],
    tool_call_bound_reached: bool,
    explicit_refusal: bool = False,
) -> VerifiedAttributionResult:
    """
    U-5 & orchestration-v1 attribution verification pipeline:
    1. Validates each structured claim against actual response_data of succeeded tool calls.
    2. Drops misstated claims or claims pointing to non-existent/failed tools.
    3. Programmatically sets claim asOf from the tool's response (DATA-002).
    4. Validates document citations against retrieved chunk IDs.
    5. Flags refusal if zero claims survive when tools were dispatched.
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

    # Document citations verification
    surviving_docs: List[DocumentClaim] = []
    rag_calls = [
        c for c in dispatched_calls
        if (c.tool_name == ToolName.RESEARCH_RAG or str(c.tool_name) == "RESEARCH_RAG")
        and c.status == "SUCCEEDED"
        and c.response_data
    ]
    valid_chunks_map = {}
    for rag in rag_calls:
        for ch in rag.response_data.get("chunks", []):
            if ch.get("chunk_id"):
                try:
                    c_uuid = uuid.UUID(str(ch["chunk_id"]))
                    valid_chunks_map[str(c_uuid)] = ch
                except (ValueError, TypeError):
                    pass

    for doc_claim_raw in raw_document_claims:
        c_id_raw = doc_claim_raw.get("chunkId") or doc_claim_raw.get("chunk_id")
        claim_text = doc_claim_raw.get("claimText") or doc_claim_raw.get("claim_text") or ""
        if not c_id_raw or not claim_text:
            continue

        try:
            parsed_chunk_id = uuid.UUID(str(c_id_raw))
            if str(parsed_chunk_id) in valid_chunks_map:
                ch_info = valid_chunks_map[str(parsed_chunk_id)]
                surviving_docs.append(
                    DocumentClaim(
                        chunkId=parsed_chunk_id,
                        claimText=claim_text,
                        title=doc_claim_raw.get("title") or ch_info.get("title"),
                        source=doc_claim_raw.get("source") or ch_info.get("source_type"),
                        pageNumber=doc_claim_raw.get("pageNumber") or ch_info.get("page_number"),
                    )
                )
        except (ValueError, TypeError):
            continue

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
