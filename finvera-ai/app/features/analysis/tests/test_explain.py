import uuid
import pytest
from unittest.mock import AsyncMock

from app.infrastructure.llm.generation import GeminiGenerationAdapter
from app.features.analysis.explain import (
    EvidenceFactor,
    ExplainRequest,
    ExplainResult,
    explain_deterministic_output,
    verify_faithfulness,
)


@pytest.mark.asyncio
async def test_t030_faithful_explanation_succeeds():
    owner_id = uuid.uuid4()
    factors = [
        EvidenceFactor(factorCode="RSI_14", description="Chỉ số RSI 14 ngày đạt 72.5 (Vùng quá mua)"),
        EvidenceFactor(factorCode="MA20_CROSS", description="Giá cắt lên trên đường MA20"),
    ]
    req = ExplainRequest(
        ownerId=owner_id,
        outputType="SIGNAL",
        symbol="HPG",
        evidenceFactors=factors,
    )

    mock_llm = AsyncMock(spec=GeminiGenerationAdapter)
    mock_llm.generate_text.return_value = (
        "Tín hiệu kỹ thuật của HPG hình thành do chỉ số RSI_14 đạt 72.5 nằm trong vùng quá mua, "
        "kết hợp việc MA20_CROSS khi giá cắt lên trên đường MA20."
    )

    res: ExplainResult = await explain_deterministic_output(req, llm_adapter=mock_llm)

    assert res.verified is True
    assert "RSI_14" in res.factorsReferenced
    assert "MA20_CROSS" in res.factorsReferenced
    assert res.ruleVersion == "orchestration-v1"


@pytest.mark.asyncio
async def test_t030_unsupplied_factor_rejected_and_retried_then_fallback():
    """
    T030 [US3] [FR-006]: An explanation attempt that references an unsupplied factor
    is rejected, retried once, then returns generic explanation-unavailable state on second failure.
    """
    owner_id = uuid.uuid4()
    # Only PE factor supplied; MACD is forbidden / unsupplied
    factors = [
        EvidenceFactor(factorCode="PE_RATIO", description="P/E hiện tại là 12.5 so với ngành là 15.0"),
    ]
    req = ExplainRequest(
        ownerId=owner_id,
        outputType="VALUATION_CLASSIFICATION",
        symbol="FPT",
        evidenceFactors=factors,
    )

    mock_llm = AsyncMock(spec=GeminiGenerationAdapter)
    # Both attempt 1 and attempt 2 hallucinate unsupplied MACD factor
    mock_llm.generate_text.side_effect = [
        "Định giá FPT dựa trên PE_RATIO và chỉ báo kỹ thuật MACD đang phân kỳ âm.",
        "Giải thích lại: PE_RATIO đạt 12.5 cùng với MACD tích cực.",
    ]

    res: ExplainResult = await explain_deterministic_output(req, llm_adapter=mock_llm)

    # 2 attempts were made
    assert mock_llm.generate_text.call_count == 2
    # Verified is False because both attempts violated faithfulness
    assert res.verified is False
    assert res.factorsReferenced == []
    assert "Hiện chưa có sẵn phần giải thích tự động" in res.explanation


@pytest.mark.asyncio
async def test_t030_retry_succeeds_on_second_attempt():
    owner_id = uuid.uuid4()
    factors = [
        EvidenceFactor(factorCode="BETA", description="Hệ số Beta 1.25"),
    ]
    req = ExplainRequest(
        ownerId=owner_id,
        outputType="RISK_FACTOR",
        symbol="VND",
        evidenceFactors=factors,
    )

    mock_llm = AsyncMock(spec=GeminiGenerationAdapter)
    # Attempt 1 hallucinates RSI; attempt 2 is clean
    mock_llm.generate_text.side_effect = [
        "Rủi ro của VND cao do BETA là 1.25 và RSI đạt mức đỉnh.",
        "Rủi ro biến động của VND được đánh giá qua hệ số BETA là 1.25 so với thị trường chung.",
    ]

    res: ExplainResult = await explain_deterministic_output(req, llm_adapter=mock_llm)

    assert mock_llm.generate_text.call_count == 2
    assert res.verified is True
    assert "BETA" in res.factorsReferenced
