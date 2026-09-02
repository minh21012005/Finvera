import asyncio
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
Nhiệm vụ của bạn là giải thích kết quả tính toán tài chính tất định ({request.outputType}){sym_text} thành một bản giải thích CÓ CHIỀU SÂU VÀ DỄ HIỂU dựa trên các yếu tố bằng chứng được cung cấp dưới đây.

DANH SÁCH YẾU TỐ BẰNG CHỨNG ĐÃ ĐƯỢC XÁC THỰC:
{factors_text}

HƯỚNG DẪN GIẢI THÍCH THEO LOẠI KẾT QUẢ:
- [VALUATION_CLASSIFICATION]: Giải thích mức phân loại định giá dựa trên so sánh lịch sử/ngành và các hệ số thực tế.
- [SIGNAL]: Giải thích điều kiện kích hoạt tín hiệu chiến lược, các ngưỡng giá và chỉ báo kỹ thuật liên quan.
- [RISK_FACTOR]: Giải thích mức điểm rủi ro và các yếu tố cấu thành chính (biến động, sụt giảm, thanh khoản).
- Yếu tố có mã [{request.outputType}] chính là KẾT QUẢ cần giải thích (nhãn, điểm, độ tin cậy); các yếu tố còn lại là bằng chứng và ghi chú của bộ tính tất định.

NGUYÊN TẮC PHÂN TÍCH:
1. Diễn giải mạch lạc, khách quan dựa trên các yếu tố bằng chứng được cung cấp ở trên.
2. TUYỆT ĐỐI KHÔNG tự bịa đặt các con số, chỉ báo kỹ thuật hay tin tức bên ngoài không có trong danh sách.
3. Sử dụng ngôn ngữ có điều kiện và thận trọng; không đưa ra mệnh lệnh mua/bán trực tiếp, không cam kết lợi nhuận chắc chắn.
4. Trình bày bằng tiếng Việt chuyên nghiệp, ngắn gọn (2-4 câu), súc tích và dễ hiểu.
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
_COUNT_UNIT_BEFORE = re.compile(
    r"(?:tháng|quý|phiên|năm|ngày|tuần|kỳ|mã|số|bước|mức|điểm|vùng)[ \t]*$",
    re.IGNORECASE,
)


def _is_count_token(text: str, match: "re.Match[str]") -> bool:
    if "." in match.group(0) or "," in match.group(0):
        return False
    # Unit after (e.g. "12 tháng", "250 phiên", "8 quý")
    if _COUNT_UNIT_AFTER.match(text[match.end():match.end() + 16]) is not None:
        return True
    # Unit before (e.g. "năm 2026", "quý 4", "ngày 28", "tháng 8", "mức 38")
    prefix = text[max(0, match.start() - 16):match.start()]
    if _COUNT_UNIT_BEFORE.search(prefix) is not None:
        return True
    return False


def fabricated_numbers(generated_text: str, evidence_text: str) -> List[str]:
    """Numbers in the explanation that no evidence figure supports. Q-43: a restated figure may be
    ROUNDED (78,47 % -> 78,5 %, 0,571428571429 -> 0,57, 0,5714 -> 57,14%) -- the model is allowed to
    round what the engine gave it or express weights as percentages, never to introduce a value the
    engine did not. A generated reading g with d decimals is supported when some evidence reading e
    satisfies |g - e| <= 0.5 * 10^-d."""
    raw_evidence_values = [v for tok in _NUMBER_TOKEN.findall(evidence_text) for v, _ in _candidate_values(tok)]
    evidence_values = list(raw_evidence_values)
    # Support percentage <-> decimal equivalences (e.g. 0.571428571429 <-> 57.1428571429%)
    for v in raw_evidence_values:
        evidence_values.append(v * 100.0)
        evidence_values.append(v / 100.0)

    # Standard methodology constants used in explanations (100-point scale, 70/30 history/sector weights, 35.5/64.5 band boundaries)
    standard_constants = [0.0, 1.0, 100.0, 70.0, 30.0, 50.0, 35.5, 64.5, 35.0, 65.0, 20.0, 80.0, 90.0]
    evidence_values.extend(standard_constants)

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


_CODE_SEMANTIC_KEYWORDS: Dict[str, List[str]] = {
    "SIGNAL": ["tín hiệu", "vào lệnh", "long", "short", "mua", "bán"],
    "VALUATION_CLASSIFICATION": ["định giá", "đắt", "rẻ", "phù hợp", "kết luận"],
    "COMPARISON_BASIS": ["cơ sở", "so sánh", "lịch sử", "ngành"],
    "VOLATILITY": ["biến động", "volatility"],
    "ATR": ["atr", "biến động trung bình"],
    "DRAWDOWN": ["sụt giảm", "drawdown", "đỉnh"],
    "LIQUIDITY": ["thanh khoản", "khối lượng", "kl"],
    "STOP_DISTANCE": ["dừng lỗ", "stop loss", "khoảng cách"],
    "MARKET_REGIME": ["thị trường", "regime", "trạng thái"],
}

_PROHIBITED_DIRECTIVE = re.compile(
    r"\b(?:nên|phải|hãy)\s+(?:mua|bán)\b|\b(?:mua|bán)\s+ngay\b|"
    r"\b(?:chắc chắn|đảm bảo|cam kết)\b",
    re.IGNORECASE,
)


def _referenced_factor_codes(text: str, allowed_factors: List[EvidenceFactor]) -> List[str]:
    referenced_codes: List[str] = []
    text_lower = text.lower()
    for factor in allowed_factors:
        code_pat = r"\b" + re.escape(factor.factorCode) + r"\b"
        tokens = re.findall(r"[A-Za-z]+|\d+", factor.factorCode)
        mentioned = bool(
            re.search(code_pat, text, re.IGNORECASE)
            or factor.description.lower() in text_lower
        )
        if not mentioned:
            mentioned = any(
                len(token) >= 3
                and re.search(r"\b" + re.escape(token) + r"\b", text, re.IGNORECASE)
                for token in tokens
            )
        if not mentioned:
            mentioned = any(
                keyword in text_lower
                for keyword in _CODE_SEMANTIC_KEYWORDS.get(factor.factorCode.upper(), [])
            )
        if not mentioned:
            mentioned = any(
                len(phrase.strip()) >= 6 and phrase.strip().lower() in text_lower
                for phrase in re.split(r"[—:;,\n]", factor.description)
            )
        if mentioned:
            referenced_codes.append(factor.factorCode)
    return referenced_codes


def _substantive_sentences(text: str) -> List[str]:
    return [
        sentence.strip(" \t\r\n-*#")
        for sentence in re.split(r"(?<=[.!?])\s+|\n+", text)
        if re.search(r"[A-Za-zÀ-ỹ]", sentence)
    ]


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
    # Known standard financial indicator & factor codes across all Finvera engines
    all_standard_codes = {
        "RSI", "MACD", "MA20", "MA50", "MA200", "SMA", "EMA", "BOLLINGER", "STOCHASTIC", "OBV", "ADX",
        "PE", "PB", "PS", "EV_EBITDA", "DIVIDEND_YIELD", "ROE", "ROA", "DEBT_TO_EQUITY", "BETA",
        "VOLATILITY", "ATR", "DRAWDOWN", "LIQUIDITY", "STOP_DISTANCE", "MARKET_REGIME"
    }

    # An indicator code is allowed if it appears in any supplied factorCode or description
    allowed_standard_codes = set()
    for code in all_standard_codes:
        normalized_code = code.replace("_", "")
        for f in allowed_factors:
            f_code_upper = f.factorCode.upper().replace("_", "")
            f_desc_upper = f.description.upper().replace("_", "")
            if normalized_code in f_code_upper or normalized_code in f_desc_upper:
                allowed_standard_codes.add(code)
                break

    forbidden_codes = all_standard_codes - allowed_standard_codes

    for fcode in forbidden_codes:
        # If text mentions an unsupplied standard financial factor code as evidence
        code_pat = r"\b" + re.escape(fcode) + r"\b"
        if re.search(code_pat, generated_text, re.IGNORECASE):
            logger.warning(f"Faithfulness check failed: unsupplied factor '{fcode}' detected in explanation")
            return False, []

    referenced_codes = _referenced_factor_codes(generated_text, allowed_factors)

    if not referenced_codes:
        logger.warning("Faithfulness check failed: explanation references none of the supplied factors")
        return False, []

    if _PROHIBITED_DIRECTIVE.search(generated_text):
        logger.warning("Faithfulness check failed: prohibited directive or certainty language")
        return False, []

    for sentence in _substantive_sentences(generated_text):
        if not _referenced_factor_codes(sentence, allowed_factors):
            logger.warning("Faithfulness check failed: sentence has no supplied evidence: %s", sentence)
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
            # Bounded with 50s timeout per attempt to give LLM plenty of time while preventing infinite hanging.
            raw_explanation = await asyncio.wait_for(
                adapter.generate_text_strict(prompt),
                timeout=50.0,
            )
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
