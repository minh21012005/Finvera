import pytest
from unittest.mock import AsyncMock

from app.infrastructure.llm.generation import GeminiGenerationAdapter
from app.features.orchestration.screener_conversion import (
    ScreenerConversionResult,
    convert_natural_language_to_filters,
)


@pytest.mark.asyncio
async def test_t034_unambiguous_screening_converts_to_exact_filters():
    """
    T034 [US4] [FR-007, FR-008]: Unambiguous query converts to exact structured filters with confidence >= 0.6.
    """
    query = "Tìm các mã cổ phiếu trên sàn HOSE có P/E dưới 10 và ROE trên 15%"
    res: ScreenerConversionResult = await convert_natural_language_to_filters(query)

    assert res.confidence >= 0.6
    assert res.ambiguityNote is None
    assert "fundamental" in res.filters
    assert res.filters["fundamental"]["peMax"] == "10"
    assert res.filters["fundamental"]["roeMin"] == "15"
    assert "market" in res.filters
    assert res.filters["market"]["exchange"] == ["HOSE"]


@pytest.mark.asyncio
async def test_t034_technical_criteria_converts_properly():
    query = "Lọc các cổ phiếu có RSI dưới 30 và cắt lên MA20"
    res: ScreenerConversionResult = await convert_natural_language_to_filters(query)

    assert res.confidence >= 0.6
    assert res.ambiguityNote is None
    assert "technical" in res.filters
    assert res.filters["technical"]["rsiMax"] == "30"
    assert "PRICE_ABOVE_MA20" in res.filters["technical"]["maRelationship"]


@pytest.mark.asyncio
async def test_t035_ambiguous_criteria_below_confidence_floor_carries_ambiguity_note():
    """
    T035 [US4] [FR-009]: A criterion scored below the 0.6 confidence floor carries a disclosed ambiguityNote,
    never a silent guess.
    """
    query = "Cho tôi danh sách các cổ phiếu ngon tiềm năng giá rẻ"
    res: ScreenerConversionResult = await convert_natural_language_to_filters(query)

    assert res.confidence < 0.6
    assert res.ambiguityNote is not None
    assert "chưa cung cấp tiêu chí định lượng" in res.ambiguityNote or "mơ hồ" in res.ambiguityNote
    # Ensure no fabricated hallucinated filters were silently applied
    assert res.filters == {}


@pytest.mark.asyncio
async def test_t035_llm_fallback_low_confidence_includes_ambiguity_note():
    mock_llm = AsyncMock(spec=GeminiGenerationAdapter)
    mock_llm.generate_text.return_value = '{"filters": {}, "confidence": 0.35, "ambiguityNote": "Câu hỏi không có chỉ số định lượng cụ thể"}'

    query = "Cổ phiếu nào sắp tăng mạnh nhất?"
    res: ScreenerConversionResult = await convert_natural_language_to_filters(query, llm_adapter=mock_llm)

    assert res.confidence < 0.6
    assert res.ambiguityNote is not None
    assert "Câu hỏi không có chỉ số" in res.ambiguityNote
