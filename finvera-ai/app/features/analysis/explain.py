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

QUY TẮC BẮT BUỘC (FAITHFULNESS CHECK):
1. Bạn CHỈ ĐƯỢC PHÉP giải thích dựa trên các yếu tố bằng chứng được liệt kê ở trên.
2. TUYỆT ĐỐI KHÔNG tự bịa đặt, suy diễn hoặc đưa thêm bất kỳ chỉ báo, tin tức hay số liệu bên ngoài nào không có trong danh sách.
3. Khi đề cập đến một yếu tố bằng chứng, hãy sử dụng mã yếu tố hoặc mô tả chính xác của nó.
4. Trả lời bằng tiếng Việt chuyên nghiệp, ngắn gọn, súc tích và dễ hiểu.
"""


def verify_faithfulness(
    generated_text: str,
    allowed_factors: List[EvidenceFactor],
) -> Tuple[bool, List[str]]:
    """
    Checks if the generated text references only allowed factors and extracts which allowed factors were mentioned.
    If forbidden external factor patterns (e.g. referencing unsupplied technical indicators) are detected, fails.
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

    return True, referenced_codes if referenced_codes else [f.factorCode for f in allowed_factors]


async def explain_deterministic_output(
    request: ExplainRequest,
    llm_adapter: Optional[GeminiGenerationAdapter] = None,
) -> ExplainResult:
    """
    FR-006: Non-orchestrated deterministic-output explanation with 1 retry on faithfulness check failure.
    """
    adapter = llm_adapter or GeminiGenerationAdapter()
    prompt = build_explain_prompt(request)

    # Attempt 1
    for attempt in range(1, 3):
        try:
            # Deterministic fallback text generation if LLM unavailable in test/offline environment
            raw_explanation = await adapter.generate_text(prompt)
            if not raw_explanation:
                # Built-in deterministic template
                factor_descs = ", ".join([f"{f.factorCode} ({f.description})" for f in request.evidenceFactors])
                sym_str = f" cho {request.symbol}" if request.symbol else ""
                raw_explanation = f"Kết quả {request.outputType}{sym_str} được xác định dựa trên các yếu tố: {factor_descs}."

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
            logger.warning(f"Error during explanation generation attempt {attempt}: {e}")

    # Second failure -> return safe fallback state (FR-006)
    return ExplainResult(
        explanation="Hiện chưa có sẵn phần giải thích tự động cho kết quả này do yêu cầu kiểm tra tính xác thực của các yếu tố bằng chứng không đạt chuẩn.",
        factorsReferenced=[],
        verified=False,
        ruleVersion="orchestration-v1",
    )
