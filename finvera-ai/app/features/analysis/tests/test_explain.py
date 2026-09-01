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


def _online_mock_llm() -> AsyncMock:
    mock_llm = AsyncMock(spec=GeminiGenerationAdapter)
    mock_llm.is_online = True
    return mock_llm


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

    mock_llm = _online_mock_llm()
    mock_llm.generate_text_strict.return_value = (
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

    mock_llm = _online_mock_llm()
    # Both attempt 1 and attempt 2 hallucinate unsupplied MACD factor
    mock_llm.generate_text_strict.side_effect = [
        "Định giá FPT dựa trên PE_RATIO và chỉ báo kỹ thuật MACD đang phân kỳ âm.",
        "Giải thích lại: PE_RATIO đạt 12.5 cùng với MACD tích cực.",
    ]

    res: ExplainResult = await explain_deterministic_output(req, llm_adapter=mock_llm)

    # 2 attempts were made
    assert mock_llm.generate_text_strict.call_count == 2
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

    mock_llm = _online_mock_llm()
    # Attempt 1 hallucinates RSI; attempt 2 is clean
    mock_llm.generate_text_strict.side_effect = [
        "Rủi ro của VND cao do BETA là 1.25 và RSI đạt mức đỉnh.",
        "Rủi ro biến động của VND được đánh giá qua hệ số BETA là 1.25 so với thị trường chung.",
    ]

    res: ExplainResult = await explain_deterministic_output(req, llm_adapter=mock_llm)

    assert mock_llm.generate_text_strict.call_count == 2
    assert res.verified is True
    assert "BETA" in res.factorsReferenced


@pytest.mark.asyncio
async def test_t030_offline_adapter_uses_builtin_template_without_calling_llm():
    """
    When no LLM provider is configured (dev/test), the explain flow should serve the
    deterministic built-in template directly and never call the LLM.
    """
    owner_id = uuid.uuid4()
    factors = [
        EvidenceFactor(factorCode="ATR", description="Biến động ATR: 1.23"),
    ]
    req = ExplainRequest(
        ownerId=owner_id,
        outputType="SIGNAL",
        symbol="MBB",
        evidenceFactors=factors,
    )

    mock_llm = AsyncMock(spec=GeminiGenerationAdapter)
    mock_llm.is_online = False

    res: ExplainResult = await explain_deterministic_output(req, llm_adapter=mock_llm)

    mock_llm.generate_text_strict.assert_not_called()
    assert res.verified is True
    assert "ATR" in res.factorsReferenced
    assert "được xác định dựa trên các yếu tố" in res.explanation


@pytest.mark.asyncio
async def test_t030_provider_failure_surfaces_distinct_error_not_faithfulness_message():
    """
    A real LLM provider failure (invalid API key, network error, ...) must be reported
    with its own clear message, not the generic faithfulness-check-failed fallback and
    not a RAG-context "document not found" message that has no meaning here.
    """
    owner_id = uuid.uuid4()
    factors = [
        EvidenceFactor(factorCode="VOLATILITY", description="Biến động giá (ATR/giá): 1.98 (điểm 0/100)"),
    ]
    req = ExplainRequest(
        ownerId=owner_id,
        outputType="SIGNAL",
        symbol="MBB",
        evidenceFactors=factors,
    )

    mock_llm = _online_mock_llm()
    mock_llm.generate_text_strict.side_effect = RuntimeError("400 API_KEY_INVALID: API key not valid.")

    res: ExplainResult = await explain_deterministic_output(req, llm_adapter=mock_llm)

    assert mock_llm.generate_text_strict.call_count == 2
    assert res.verified is False
    assert res.factorsReferenced == []
    assert "lỗi kết nối tới dịch vụ AI" in res.explanation
    assert "Hiện chưa có sẵn phần giải thích tự động" not in res.explanation
    assert "Không tìm thấy thông tin" not in res.explanation


def test_q21_no_referenced_factor_is_not_attributed_to_all_factors():
    from app.features.analysis.explain import verify_faithfulness
    factors = [EvidenceFactor(factorCode="BETA", description="Hệ số Beta 1.25")]
    ok, refs = verify_faithfulness("Cổ phiếu này nhìn chung khá ổn.", factors)
    assert ok is False
    assert refs == []


def test_q21_fabricated_number_fails_faithfulness():
    from app.features.analysis.explain import verify_faithfulness
    factors = [EvidenceFactor(factorCode="BETA", description="Hệ số Beta 1.25")]
    ok, refs = verify_faithfulness("Hệ số BETA là 1.25 nên giá có thể tăng 15% quý tới.", factors)
    assert ok is False
    assert refs == []
    ok2, refs2 = verify_faithfulness("Hệ số BETA là 1,25 so với thị trường.", factors)
    assert ok2 is True
    assert refs2 == ["BETA"]


def test_q43_prompt_names_the_result_factor_to_explain():
    from app.features.analysis.explain import build_explain_prompt
    req = ExplainRequest(
        ownerId=uuid.uuid4(), outputType="VALUATION_CLASSIFICATION", symbol="MBB",
        evidenceFactors=[
            EvidenceFactor(factorCode="VALUATION_CLASSIFICATION", description="Kết luận: Định giá thấp — điểm 12/100"),
            EvidenceFactor(factorCode="PB", description="P/B: 1,27 — phân vị lịch sử 12,4%"),
        ],
    )
    prompt = build_explain_prompt(req)
    assert "[VALUATION_CLASSIFICATION]" in prompt
    assert "KẾT QUẢ cần giải thích" in prompt


def test_q43_rounded_restatements_pass_but_new_figures_still_fail():
    from app.features.analysis.explain import verify_faithfulness
    factors = [
        EvidenceFactor(factorCode="VALUATION_CLASSIFICATION", description="Kết luận: Định giá cao — điểm đắt/rẻ 69/100, độ hoàn thiện dữ liệu 87%"),
        EvidenceFactor(factorCode="PE", description="P/E: 5,2 — phân vị lịch sử 78,47% — phân vị ngành 21,74% — trọng số 0,571428571429"),
        EvidenceFactor(factorCode="PB", description="P/B: 1,27 — phân vị lịch sử 99,05% — trọng số 0,428571428571"),
    ]
    ok, refs = verify_faithfulness(
        "Kết luận Định giá cao (69/100) đến từ 2 yếu tố: PE ở phân vị lịch sử 78,5% với trọng số 0,57 và PB ở phân vị 99% với trọng số 0,43.",
        factors)
    assert ok is True and set(refs) >= {"PE", "PB"}
    ok2, _ = verify_faithfulness("PE 5,2 cho thấy cổ phiếu có thể tăng 15% trong quý tới.", factors)
    assert ok2 is False                       # 15 is nobody's rounding
    ok3, _ = verify_faithfulness("PE 5,2 và giá mục tiêu 27.500 đồng.", factors)
    assert ok3 is False                       # a new price level is fabricated


def test_count_words_are_not_fabricated_numbers():
    # Feature 015: the reason-code wording says "EPS 12 tháng"; "8 quý" / "250 phiên" are periods.
    from app.features.analysis.explain import fabricated_numbers
    evidence = "EPS_TTM=4050.73 ROE=20.22"
    assert fabricated_numbers("EPS 12 tháng là 4.050,73; tính trên 8 quý, 250 phiên; ROE 20,22 %", evidence) == []
    assert fabricated_numbers("EPS 12 tháng là 4.999", evidence) == ["4.999"]   # a real figure still has to exist
    assert fabricated_numbers("lợi suất 12 %", evidence) == ["12"]               # "12 %" is a figure, not a count


def test_weight_percentage_restatements_pass_faithfulness():
    from app.features.analysis.explain import verify_faithfulness
    factors = [
        EvidenceFactor(factorCode="VALUATION_CLASSIFICATION", description="Kết luận: Định giá thấp — điểm đắt/rẻ 34/100, độ hoàn thiện dữ liệu 87% (quy tắc valuation-v2)"),
        EvidenceFactor(factorCode="COMPARISON_BASIS", description="Cơ sở so sánh: lịch sử riêng của mã (750 phiên); ngành 8770 (47 mã cùng ngành)"),
        EvidenceFactor(factorCode="PE", description="P/E: 11,11 — phân vị lịch sử 9,93% — phân vị ngành 30,23% — trọng số 0,571428571429"),
        EvidenceFactor(factorCode="PB", description="P/B: 1,31 — phân vị lịch sử 52,07% — phân vị ngành 61,7% — trọng số 0,428571428571"),
    ]
    explanation = (
        "Mã SSI được đánh giá Định giá thấp với điểm 34/100. "
        "Yếu tố PE có trọng số 57,14% (phân vị lịch sử 9,93% và phân vị ngành 30,23%) "
        "kết hợp cùng PB có trọng số 42,86% (phân vị lịch sử 52,07%) "
        "dựa trên 750 phiên và 47 mã cùng ngành."
    )
    ok, refs = verify_faithfulness(explanation, factors)
    assert ok is True
    assert set(refs) >= {"PE", "PB"}


def test_signal_explain_subword_indicators_pass_faithfulness():
    from app.features.analysis.explain import verify_faithfulness
    factors = [
        EvidenceFactor(factorCode="SIGNAL", description="Tín hiệu Động lượng — Mua (LONG); vùng vào 21.206,31–21.493,69, dừng lỗ 20.200,51, mục tiêu 23.648,98 / 24.798,47, lợi nhuận/rủi ro 2; mức rủi ro Rủi ro trung bình (điểm 38/100)"),
        EvidenceFactor(factorCode="CONDITION_MACDHISTOGRAM", description="Điều kiện vào lệnh macdHistogram: 236.157629103096"),
        EvidenceFactor(factorCode="CONDITION_RSI14", description="Điều kiện vào lệnh rsi14: 62.862132478922"),
        EvidenceFactor(factorCode="VOLATILITY", description="Biến động giá (ATR/giá): 2,69 (điểm 9/100)"),
        EvidenceFactor(factorCode="ATR", description="Biến động so với trung bình 250 phiên: 0,87 (điểm 16/100)"),
        EvidenceFactor(factorCode="DRAWDOWN", description="Mức sụt giảm từ đỉnh 250 phiên: 29,26 (điểm 98/100)"),
        EvidenceFactor(factorCode="LIQUIDITY", description="Thanh khoản (KL tương đối): 0,96 (điểm 54/100)"),
        EvidenceFactor(factorCode="STOP_DISTANCE", description="Khoảng cách tới điểm dừng lỗ: 5,38 (điểm 20/100)"),
        EvidenceFactor(factorCode="MARKET_REGIME", description="Trạng thái thị trường chung: 68 (điểm 32/100)"),
    ]
    explanation = (
        "Tín hiệu Mua (LONG) được tạo dựa trên chỉ báo MACD (macdHistogram đạt 236,16) "
        "kết hợp cùng chỉ số RSI (rsi14 đạt 62,86). "
        "Điểm dừng lỗ tại 20.200,51 và mục tiêu 23.648,98."
    )
    ok, refs = verify_faithfulness(explanation, factors)
    assert ok is True
    assert set(refs) >= {"CONDITION_MACDHISTOGRAM", "CONDITION_RSI14"}
