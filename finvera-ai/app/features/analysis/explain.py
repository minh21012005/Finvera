import logging
import re
from typing import Any, Dict, List, Optional, Tuple
import uuid
from pydantic import BaseModel, Field

from app.infrastructure.llm.generation import GeminiGenerationAdapter

logger = logging.getLogger(__name__)


class EvidenceFactor(BaseModel):
    factorCode: str
    description: str


class ExplainRequest(BaseModel):
    ownerId: uuid.UUID
    outputType: str  # SIGNAL, INDICATOR_READING, VALUATION_CLASSIFICATION, RISK_FACTOR
    symbol: Optional[str] = None
    evidenceFactors: List[EvidenceFactor] = Field(..., min_length=1)


class ExplainResult(BaseModel):
    explanation: str
    factorsReferenced: List[str]
    verified: bool
    ruleVersion: str = "orchestration-v1"


def build_explain_prompt(request: ExplainRequest) -> str:
    factors_text = "\n".join([f"- [{f.factorCode}]: {f.description}" for f in request.evidenceFactors])
    sym_text = f" của mã cổ phiếu {request.symbol}" if request.symbol else ""
    return f"""Bạn là chuyên gia phân tích tài chính AI của Finvera.
Nhiệm vụ của bạn là giải thích kết quả tính toán tài chính tất định ({request.outputType}){sym_text} dựa DUY NHẤT trên các yếu tố bằng chứng được cung cấp dưới đây.

DANH SÁCH YẾU TỐ BẰNG CHỨNG ĐÃ ĐƯỢC XÁC THỰC:
{factors_text}

Yếu tố có mã [{request.outputType}] (nếu có) chính là KẾT QUẢ cần giải thích (nhãn, điểm, độ tin cậy);
các yếu tố còn lại là bằng chứng và ghi chú của bộ tính tất định. Hãy giải thích vì sao các bằng chứng
dẫn tới kết quả đó; nếu có yếu tố ghi rằng bộ chỉ số bị thu hẹp hoặc một chỉ số không áp dụng, hãy nêu rõ
giới hạn ấy. Nếu không có yếu tố kết quả, hãy nói rõ là chỉ mô tả bằng chứng.

QUY TẮC BẮT BUỘC (FAITHFULNESS CHECK):
1. Bạn CHỈ ĐƯỢC PHÉP giải thích dựa trên các yếu tố bằng chứng được liệt kê ở trên.
2. TUYỆT ĐỐI KHÔNG tự bịa đặt, suy diễn hoặc đưa thêm bất kỳ chỉ báo, tin tức hay số liệu bên ngoài nào không có trong danh sách.
3. Khi đề cập đến một yếu tố bằng chứng, hãy sử dụng mã yếu tố hoặc mô tả chính xác của nó.
4. Trả lời bằng tiếng Việt chuyên nghiệp, ngắn gọn, súc tích và dễ hiểu.
"""


_NUMBER_TOKEN = re.compile(r"\d+(?:[.,]\d+)*")


def _numeric_tokens(text: str) -> set:
    """Digit groups with separators stripped, so '1,25' / '1.25' / '1.250' compare alike."""
    return {re.sub(r"[.,]", "", tok) for tok in _NUMBER_TOKEN.findall(text)}


def _candidate_values(token: str) -> List[Tuple[float, int]]:
    """Readings of a numeric token as (value, decimals): the last separator as the decimal mark
    (vi '1,27' / en '1.27'), and every separator as a thousands mark ('21.050' -> 21050)."""
    out: List[Tuple[float, int]] = []
    digits_only = re.sub(r"[.,]", "", token)
    if digits_only:
        out.append((float(digits_only), 0))
    m = re.match(r"^(\d+(?:[.,]\d+)*)[.,](\d+)$", token)
    if m:
        whole = re.sub(r"[.,]", "", m.group(1))
        frac = m.group(2)
        out.append((float(f"{whole}.{frac}"), len(frac)))
    return out


SMALL_COUNT_LIMIT = 10  # "7 yếu tố", "2 cơ sở": counting words are not financial figures
# Feature 015: "EPS 12 tháng", "8 quý", "250 phiên", "24 mã" — an integer followed by a counting
# unit is a period/count, not a figure the engine had to supply (the reason-code wording itself
# says "12 tháng").
_COUNT_UNIT_AFTER = re.compile(
    r"^[ \t]*(?:tháng|quý|phiên|năm|mã|yếu tố|cổ phiếu|ngày|chiến lược|công cụ|bậc|lần|tuần|kỳ)(?![\w])",
    re.IGNORECASE,
)


def _is_count_token(text: str, match: "re.Match[str]") -> bool:
    return "." not in match.group(0) and "," not in match.group(0) \
        and _COUNT_UNIT_AFTER.match(text[match.end():match.end() + 16]) is not None


def fabricated_numbers(generated_text: str, evidence_text: str) -> List[str]:
    """Numbers in the explanation that no evidence figure supports. Q-43: a restated figure may be
    ROUNDED (78,47 % -> 78,5 %, 0,571428571429 -> 0,57) -- the model is allowed to round what the
    engine gave it, never to introduce a value the engine did not. A generated reading g with d
    decimals is supported when some evidence reading e satisfies |g - e| <= 0.5 * 10^-d."""
    evidence_values = [v for tok in _NUMBER_TOKEN.findall(evidence_text) for v, _ in _candidate_values(tok)]
    fabricated: List[str] = []
    for match in _NUMBER_TOKEN.finditer(generated_text):
        tok = match.group(0)
        if _is_count_token(generated_text, match):
            continue
        readings = _candidate_values(tok)
        supported = False
        for g, d in readings:
            if d == 0 and g <= SMALL_COUNT_LIMIT:
                supported = True
                break
            tolerance = 0.5 * (10 ** -d)
            if any(abs(g - e) <= tolerance + 1e-12 for e in evidence_values):
                supported = True
                break
        if not supported:
            fabricated.append(tok)
    return fabricated


def verify_faithfulness(
    generated_text: str,
    allowed_factors: List[EvidenceFactor],
) -> Tuple[bool, List[str]]:
    """
    Checks that the generated text (1) references at least one supplied factor,
    (2) mentions no unsupplied standard indicator, and (3) states no number that
    does not appear in the supplied evidence -- an explanation may restate the
    deterministic engine's figures, never introduce its own (Constitution I).
    Returns the factors actually referenced; never claims "all" when none were.
    """
    allowed_codes = {f.factorCode.upper() for f in allowed_factors}
    for f in allowed_factors:
        for part in f.factorCode.upper().split("_"):
            if part:
                allowed_codes.add(part)

    referenced_codes: List[str] = []

    # Check which allowed codes are present
    for f in allowed_factors:
        code_pat = r"\b" + re.escape(f.factorCode) + r"\b"
        if re.search(code_pat, generated_text, re.IGNORECASE) or f.description.lower() in generated_text.lower():
            referenced_codes.append(f.factorCode)

    # Known standard indicator factor codes
    all_standard_codes = {"RSI", "MACD", "MA20", "MA50", "SMA", "EMA", "PE", "PB", "ROE", "ROA", "DEBT_TO_EQUITY", "BETA", "VOLATILITY"}
    forbidden_codes = all_standard_codes - allowed_codes

    for fcode in forbidden_codes:
        # If text mentions an unsupplied standard financial factor code as evidence
        if re.search(r"\b" + re.escape(fcode) + r"\b", generated_text, re.IGNORECASE):
            logger.warning(f"Faithfulness check failed: unsupplied factor '{fcode}' detected in explanation")
            return False, []

    if not referenced_codes:
        logger.warning("Faithfulness check failed: explanation references none of the supplied factors")
        return False, []

    evidence_text = " ".join(f"{f.factorCode} {f.description}" for f in allowed_factors)
    fabricated = fabricated_numbers(generated_text, evidence_text)
    if fabricated:
        logger.warning(f"Faithfulness check failed: numbers not present in evidence: {sorted(fabricated)}")
        return False, []

    return True, referenced_codes


def _builtin_template(request: ExplainRequest) -> str:
    factor_descs = ", ".join([f"{f.factorCode} ({f.description})" for f in request.evidenceFactors])
    sym_str = f" cho {request.symbol}" if request.symbol else ""
    return f"Kết quả {request.outputType}{sym_str} được xác định dựa trên các yếu tố: {factor_descs}."


async def explain_deterministic_output(
    request: ExplainRequest,
    llm_adapter: Optional[GeminiGenerationAdapter] = None,
) -> ExplainResult:
    """
    FR-006: Non-orchestrated deterministic-output explanation with 1 retry on faithfulness check failure.
    """
    adapter = llm_adapter or GeminiGenerationAdapter()
    prompt = build_explain_prompt(request)

    if not adapter.is_online:
        # No LLM provider configured (dev/test) — this is an expected, non-error mode,
        # not a failure, so serve the deterministic template directly without retrying.
        raw_explanation = _builtin_template(request)
        _, ref_factors = verify_faithfulness(raw_explanation, request.evidenceFactors)
        return ExplainResult(
            explanation=raw_explanation,
            factorsReferenced=ref_factors,
            verified=True,
            ruleVersion="orchestration-v1",
        )

    provider_error: Optional[Exception] = None
    for attempt in range(1, 3):
        try:
            # generate_text_strict raises on a real provider failure instead of masking
            # it as RAG-shaped offline text — a wrong-context message that would then
            # wrongly pass or fail the faithfulness check below.
            raw_explanation = await adapter.generate_text_strict(prompt)
            provider_error = None

            is_faithful, ref_factors = verify_faithfulness(raw_explanation, request.evidenceFactors)
            if is_faithful:
                return ExplainResult(
                    explanation=raw_explanation.strip(),
                    factorsReferenced=ref_factors,
                    verified=True,
                    ruleVersion="orchestration-v1",
                )
            else:
                logger.info(f"Faithfulness check attempt {attempt} failed, retrying...")

        except Exception as e:
            provider_error = e
            logger.error(f"LLM explain generation failed on attempt {attempt}: {type(e).__name__}: {e}")

    if provider_error is not None:
        # The LLM provider itself failed (invalid key, network, quota, ...) — say so
        # plainly rather than reusing the faithfulness-check fallback message, which
        # would misattribute the cause.
        return ExplainResult(
            explanation="Không thể tạo giải thích tự động do lỗi kết nối tới dịch vụ AI. Vui lòng thử lại sau.",
            factorsReferenced=[],
            verified=False,
            ruleVersion="orchestration-v1",
        )

    # Both attempts produced text but failed the faithfulness check -> safe fallback (FR-006)
    return ExplainResult(
        explanation="Hiện chưa có sẵn phần giải thích tự động cho kết quả này do yêu cầu kiểm tra tính xác thực của các yếu tố bằng chứng không đạt chuẩn.",
        factorsReferenced=[],
        verified=False,
        ruleVersion="orchestration-v1",
    )
