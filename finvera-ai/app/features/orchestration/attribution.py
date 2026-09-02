import math
import re
from typing import Any, Dict, List, Literal, Optional, Tuple, Union
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
    # Feature 015: how the text and the plan were produced (contract internal-api FinalEvent).
    synthesisMode: Optional[str] = None   # ONLINE | OFFLINE_TEMPLATE
    plannerMode: Optional[str] = None     # MODEL | KEYWORD_FALLBACK
    # Coverage of retained evidence-linked statements after verification.
    claimCoverage: Literal["FULL", "PARTIAL", "NONE"] = "NONE"


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

    # Try dotted path traversal. Feature 015: list steps are allowed — `signals[0].direction`,
    # `matches.0.symbol` — and `<list>.length` reads the list size, so a template can state
    # "no positions" / "12 matches" as a verifiable claim instead of being refused.
    parts: List[str] = []
    for raw_part in field_path.split("."):
        m = _INDEXED_PART.match(raw_part)
        if m:
            parts.append(m.group(1))
            parts.append(m.group(2))
        else:
            parts.append(raw_part)
    current: Any = data
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
        elif isinstance(current, list):
            if part == "length":
                current = len(current)
            elif part.isdigit() and int(part) < len(current):
                current = current[int(part)]
            else:
                return False, None
        else:
            return False, None

    return True, current


_INDEXED_PART = __import__("re").compile(r"^([^\[\]]+)\[(\d+)\]$")
_STANDALONE_NUMBER = re.compile(r"(?<![A-Za-z_])[-+]?\d+(?:[.,]\d+)*(?![A-Za-z_])")
_PROHIBITED_DIRECTIVE = re.compile(
    r"\b(?:nên|phải|hãy)\s+(?:mua|bán)\b|\b(?:mua|bán)\s+ngay\b|"
    r"\b(?:chắc chắn|đảm bảo|cam kết)\b",
    re.IGNORECASE,
)
_SCALE_MULTIPLIERS = [
    (re.compile(r"^(?:tỷ|ty|b\b)", re.IGNORECASE), 1_000_000_000.0),
    (re.compile(r"^(?:triệu|trieu|tr\b|m\b)", re.IGNORECASE), 1_000_000.0),
    (re.compile(r"^(?:nghìn|ngàn|nghin|ngan|k\b)", re.IGNORECASE), 1_000.0),
]
_DATE_TIME_PATTERN = re.compile(
    r"\b(?:\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{4}[/-]\d{1,2}[/-]\d{1,2}|\d{1,2}:\d{2}(?::\d{2})?)\b"
)
_LIST_NUMBER_PREFIX = re.compile(
    r"(?:^|[#\n\r(]|\b(?:phần|mục|bước|top|vd|ví dụ|số)\s*)\s*(\d+)(?:\.|\)|\s*:)",
    re.IGNORECASE,
)
_COUNT_UNIT_AFTER = re.compile(
    r"^[ \t]*(?:tháng|quý|phiên|năm|mã|yếu tố|cổ phiếu|ngày|chiến lược|công cụ|bậc|lần|tuần|kỳ|vị thế)(?![\w])",
    re.IGNORECASE,
)


def _parse_candidate_numbers(raw_str: str) -> List[float]:
    s = raw_str.strip().rstrip("%").rstrip("đ").rstrip("₫").strip()
    candidates = []
    # 1. Plain / English float: "1,234.56" or "21050"
    try:
        candidates.append(float(s.replace(",", "")))
    except ValueError:
        pass
    # 2. Vietnamese thousand dots & decimal comma: "21.050", "2.631.250", "12,5"
    try:
        cleaned_vn = s.replace(".", "").replace(",", ".")
        candidates.append(float(cleaned_vn))
    except ValueError:
        pass
    # 3. Direct without separators
    try:
        candidates.append(float(s.replace(" ", "")))
    except ValueError:
        pass
    return candidates


def _extract_all_numbers_from_data(data: Any) -> set:
    """Recursively collect all numeric values from tool response dict/list."""
    nums: set = set()
    if isinstance(data, (int, float)):
        nums.add(float(data))
    elif isinstance(data, str):
        for candidate in _parse_candidate_numbers(data):
            nums.add(candidate)
    elif isinstance(data, dict):
        for v in data.values():
            nums.update(_extract_all_numbers_from_data(v))
    elif isinstance(data, list):
        for item in data:
            nums.update(_extract_all_numbers_from_data(item))
    return nums


def match_claimed_value(claimed: str, actual: Any) -> bool:
    """
    U-5: Exact match for strings/enums/booleans, rounding-tolerant for decimals/floats,
    and supports Vietnamese thousand separators (dots) and decimal percentages.
    """
    if actual is None:
        return claimed.strip().lower() in ("none", "null", "n/a", "")

    actual_str = str(actual).strip()
    claimed_str = str(claimed).strip()

    # Exact string match (case-insensitive)
    if claimed_str.lower() == actual_str.lower():
        return True

    # Numeric comparison with Vietnamese format & percentage support
    claimed_nums = _parse_candidate_numbers(claimed_str)
    actual_nums = _parse_candidate_numbers(actual_str)
    for c_num in claimed_nums:
        for a_num in actual_nums:
            if math.isclose(c_num, a_num, rel_tol=1e-2, abs_tol=1e-2):
                return True
            # Support percentage <-> decimal equivalence (e.g. 0.1278 <-> 12.78%)
            if math.isclose(c_num, a_num * 100.0, rel_tol=1e-2, abs_tol=1e-2) or \
               math.isclose(c_num * 100.0, a_num, rel_tol=1e-2, abs_tol=1e-2):
                return True

    return False


def statement_numbers_are_attributed(
    statement: str,
    claims: List[RawStructuredClaim],
    tool_available_numbers: Optional[set] = None,
) -> bool:
    """AI-005: every standalone number in a structured statement must be
    supported by one of that statement's own evidence tags or authentic tool data.
    Structural markers (list numbers like '1.', dates, counting units) are excluded."""
    for match in _STANDALONE_NUMBER.finditer(statement):
        token = match.group(0)
        start, end = match.start(), match.end()

        # 1. Date / Time tokens are timestamps, not financial assertions
        if any(d.start() <= start and end <= d.end() for d in _DATE_TIME_PATTERN.finditer(statement)):
            continue

        # 2. Section / list numbers (e.g. "### 1.", "1. ", "(1) ")
        if any(l.start(1) == start and l.end(1) == end for l in _LIST_NUMBER_PREFIX.finditer(statement)):
            continue

        # 3. Small count units (e.g. "3 mã", "6 vị thế", "12 tháng")
        after_token = statement[end:]
        if _COUNT_UNIT_AFTER.match(after_token):
            try:
                v = float(token.replace(",", "").replace(".", ""))
                if v <= 50 and v.is_integer():
                    continue
            except ValueError:
                pass

        percentage_context = after_token.lstrip().startswith("%")
        scale_mult = 1.0
        for pattern, mult in _SCALE_MULTIPLIERS:
            if pattern.match(after_token.lstrip()):
                scale_mult = mult
                break

        token_values = _parse_candidate_numbers(token)
        if scale_mult != 1.0:
            token_values.extend([v * scale_mult for v in token_values])

        supported = False
        for claim in claims:
            # Date/count components can be supported by an exact component in a
            # tagged ISO/string value even though the whole value is not numeric.
            if re.search(rf"(?<!\d){re.escape(token)}(?!\d)", str(claim.claimedValue)):
                supported = True
                break

            claim_values = _parse_candidate_numbers(str(claim.claimedValue))
            if any(
                math.isclose(token_value, claim_value, rel_tol=2e-2, abs_tol=1e-2)
                for token_value in token_values
                for claim_value in claim_values
            ):
                supported = True
                break

            ratio_field = "percent" in claim.fieldPath.lower() or "allocation" in claim.fieldPath.lower()
            if percentage_context and ratio_field and match_claimed_value(token, claim.claimedValue):
                supported = True
                break

        # 4. Fallback: check if the number exists in authentic tool response data
        if not supported and tool_available_numbers:
            for tv in token_values:
                for av in tool_available_numbers:
                    if math.isclose(tv, av, rel_tol=2e-2, abs_tol=1e-2):
                        supported = True
                        break
                    if percentage_context and (
                        math.isclose(tv, av * 100.0, rel_tol=2e-2, abs_tol=1e-2)
                        or math.isclose(tv * 100.0, av, rel_tol=2e-2, abs_tol=1e-2)
                    ):
                        supported = True
                        break
                if supported:
                    break

        if not supported:
            return False
    return True


def statement_is_calibrated(statement: str) -> bool:
    """Reject unconditional trading directives and certainty/guarantee language."""
    return _PROHIBITED_DIRECTIVE.search(statement) is None


def verify_attribution(
    answer: str,
    raw_structured_claims: List[RawStructuredClaim],
    verified_document_claims: List[DocumentClaim],
    dispatched_calls: List[DispatchedToolCall],
    tool_call_bound_reached: bool,
    explicit_refusal: bool = False,
    synthesis_mode: Optional[str] = None,
    planner_mode: Optional[str] = None,
    unattributed_content_present: bool = False,
) -> VerifiedAttributionResult:
    """
    U-5 & orchestration-v1 attribution verification pipeline:
    1. Validates each structured claim against actual response_data of succeeded tool calls.
    2. Drops misstated claims or claims pointing to non-existent/failed tools.
    3. Programmatically sets claim asOf from the tool's response (DATA-002).
    4. Flags refusal if zero claims (structured + document combined) survive when tools
       were dispatched.
    """
    calls_by_seq: Dict[int, DispatchedToolCall] = {c.sequence_no: c for c in dispatched_calls}
    succeeded_calls = [c for c in dispatched_calls if c.status == "SUCCEEDED" and c.response_data]

    # Collect all authentic numbers from succeeded tool responses for context awareness
    all_tool_numbers: set = set()
    for call in succeeded_calls:
        all_tool_numbers.update(_extract_all_numbers_from_data(call.response_data))

    evaluated_claims: List[Tuple[RawStructuredClaim, Optional[StructuredClaim]]] = []

    for raw_claim in raw_structured_claims:
        call = calls_by_seq.get(raw_claim.sequenceNo)
        found = False
        actual_val = None

        if call and call.status == "SUCCEEDED" and call.response_data:
            found, actual_val = get_nested_value(call.response_data, raw_claim.fieldPath)

        # Cross-tool fallback: if tool sequence was shifted or combined
        if not found:
            for other_call in succeeded_calls:
                if other_call.sequence_no != raw_claim.sequenceNo:
                    f, val = get_nested_value(other_call.response_data, raw_claim.fieldPath)
                    if f and match_claimed_value(raw_claim.claimedValue, val):
                        found, actual_val = True, val
                        call = other_call
                        break

        if not found or not call:
            evaluated_claims.append((raw_claim, None))
            continue

        if not match_claimed_value(raw_claim.claimedValue, actual_val):
            evaluated_claims.append((raw_claim, None))
            continue

        # Programmatically extract asOf from tool response, never trusting model
        tool_as_of = str(call.response_data.get("asOf") or call.called_at)

        evaluated_claims.append(
            (raw_claim, StructuredClaim(
                claimText=raw_claim.claimText,
                sequenceNo=raw_claim.sequenceNo,
                fieldPath=raw_claim.fieldPath,
                claimedValue=str(actual_val),
                asOf=tool_as_of,
            ))
        )

    # ONLINE statements are atomic: all tags on a statement must validate and
    # every number in its visible prose must be supported by tags or tool data.
    surviving_structured: List[StructuredClaim] = []
    surviving_statement_texts: List[str] = []
    rejected_statement = False
    grouped: Dict[str, List[Tuple[RawStructuredClaim, Optional[StructuredClaim]]]] = {}
    for pair in evaluated_claims:
        grouped.setdefault(pair[0].claimText, []).append(pair)

    for statement, pairs in grouped.items():
        raw_for_statement = [pair[0] for pair in pairs]
        if synthesis_mode == "ONLINE" and (
            any(pair[1] is None for pair in pairs)
            or not statement_numbers_are_attributed(statement, raw_for_statement, all_tool_numbers)
            or not statement_is_calibrated(statement)
        ):
            rejected_statement = True
            continue
        valid = [pair[1] for pair in pairs if pair[1] is not None]
        if not valid:
            rejected_statement = True
            continue
        surviving_structured.extend(valid)
        if statement not in surviving_statement_texts:
            surviving_statement_texts.append(statement)

    surviving_docs: List[DocumentClaim] = list(verified_document_claims)
    total_surviving = len(surviving_structured) + len(surviving_docs)
    all_tools_failed = len(dispatched_calls) > 0 and len(succeeded_calls) == 0

    refused = explicit_refusal or all_tools_failed or (len(dispatched_calls) > 0 and total_surviving == 0)

    failed_calls = [call for call in dispatched_calls if call.status != "SUCCEEDED"]
    degraded = bool(failed_calls) or tool_call_bound_reached

    if refused:
        claim_coverage = "NONE"
    elif rejected_statement or unattributed_content_present or degraded:
        claim_coverage = "PARTIAL"
    else:
        claim_coverage = "FULL"



    clean_ans = re.sub(r"\[T\d+:[^\]]+\]", "", answer).strip()
    clean_ans = re.sub(r"\[Block\s*\d+\]", "", clean_ans, flags=re.IGNORECASE).strip()

    verified_answer = answer
    if synthesis_mode == "ONLINE" and not refused:
        if not rejected_statement and not unattributed_content_present and not failed_calls:
            # All claims and statements verified: preserve full Markdown response
            verified_answer = clean_ans if clean_ans else answer
        else:
            # Reconstruct from surviving statements plus verified RAG claims
            safe_parts = list(surviving_statement_texts)
            for doc in surviving_docs:
                if doc.claimText not in safe_parts:
                    safe_parts.append(doc.claimText)
            for call in failed_calls:
                tool_name = call.tool_name.value if hasattr(call.tool_name, "value") else str(call.tool_name)
                safe_parts.append(f"Không thể sử dụng công cụ {tool_name}; phần dữ liệu tương ứng không khả dụng.")
            if tool_call_bound_reached:
                safe_parts.append("Đã đạt giới hạn gọi công cụ; kết quả chỉ phản ánh phần dữ liệu đã được xác minh.")
            verified_answer = "\n\n".join(safe_parts)

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
        answer=verified_answer if not refused else "Không đủ dữ liệu tin cậy hoặc thông tin ngoài phạm vi để trả lời.",
        structuredClaims=surviving_structured if not refused else [],
        documentClaims=surviving_docs if not refused else [],
        synthesisMode=synthesis_mode,
        plannerMode=planner_mode,
        refused=refused,
        toolCalls=tool_calls_payload,
        toolCallBoundReached=tool_call_bound_reached,
        ruleVersion="orchestration-v1",
        claimCoverage=claim_coverage,
    )


