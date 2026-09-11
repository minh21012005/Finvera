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


@pytest.mark.asyncio
async def test_archetype_growth_conversion():
    query = "Lọc cho tôi các cổ phiếu tăng trưởng cao"
    res: ScreenerConversionResult = await convert_natural_language_to_filters(query)

    assert res.confidence >= 0.8
    assert res.ambiguityNote is not None
    assert "tăng trưởng" in res.ambiguityNote.lower()
    assert "fundamental" in res.filters
    f = res.filters["fundamental"]
    assert f["revenueGrowthPercentMin"] == "10"
    assert f["earningsGrowthPercentMin"] == "10"
    assert f["roeMin"] == "15"
    assert "debtToEquityMax" not in f
    assert "market" not in res.filters


@pytest.mark.asyncio
async def test_combined_value_longterm_phrase_uses_value_preset_precedence():
    query = "Tìm các mã cổ phiếu đầu tư dài hạn giá trị tích sản"
    res: ScreenerConversionResult = await convert_natural_language_to_filters(query)

    assert res.confidence >= 0.8
    assert res.ambiguityNote is not None
    assert "preset value v2" in res.ambiguityNote.lower()
    assert "fundamental" in res.filters
    f = res.filters["fundamental"]
    assert f["valuationClassification"] == ["UNDER_VALUED"]
    assert f["roeMin"] == "12"
    assert res.filters["market"]["marketCapMin"] == "1000000000000"


@pytest.mark.asyncio
async def test_archetype_dividend_conversion():
    query = "Lọc cổ phiếu chi trả cổ tức đều đặn"
    res: ScreenerConversionResult = await convert_natural_language_to_filters(query)

    assert res.confidence >= 0.8
    assert res.ambiguityNote is not None
    assert "cổ tức" in res.ambiguityNote.lower()
    assert "fundamental" in res.filters
    f = res.filters["fundamental"]
    assert f["dividendYieldMin"] == "3"
    assert f["peMin"] == "0"
    assert res.filters["market"]["marketCapMin"] == "2000000000000"


@pytest.mark.asyncio
async def test_archetype_momentum_screener_conversion():
    query = "Lọc các mã lướt sóng ngắn hạn bứt phá"
    res: ScreenerConversionResult = await convert_natural_language_to_filters(query)

    assert res.confidence >= 0.8
    assert res.ambiguityNote is not None
    assert "momentum screen v2" in res.ambiguityNote.lower()
    assert "technical" in res.filters
    t = res.filters["technical"]
    assert t["rsiMin"] == "50"
    assert t["rsiMax"] == "68"
    assert t["relativeVolumeMin"] == "1.2"
    assert "PRICE_ABOVE_MA20" in t["maRelationship"]


@pytest.mark.asyncio
async def test_archetype_with_explicit_override():
    query = "Lọc cổ phiếu tăng trưởng có ROE trên 25%"
    res: ScreenerConversionResult = await convert_natural_language_to_filters(query)

    assert res.confidence >= 0.85
    assert "fundamental" in res.filters
    f = res.filters["fundamental"]
    assert f["roeMin"] == "25"
    assert f["revenueGrowthPercentMin"] == "10"
    assert f["earningsGrowthPercentMin"] == "10"


@pytest.mark.asyncio
async def test_archetype_explicit_upper_bound_does_not_conflict_with_default():
    res = await convert_natural_language_to_filters("Lọc cổ phiếu growth ROE dưới 10%")

    fundamental = res.filters["fundamental"]
    assert fundamental["roeMax"] == "10"
    assert "roeMin" not in fundamental


@pytest.mark.asyncio
async def test_archetype_explicit_eps_growth_overrides_default():
    res = await convert_natural_language_to_filters("Lọc cổ phiếu growth EPS > 50%")

    assert res.filters["fundamental"]["earningsGrowthPercentMin"] == "50"


@pytest.mark.asyncio
async def test_archetype_longterm_quality_is_distinct_from_value():
    res = await convert_natural_language_to_filters("Tìm cổ phiếu dài hạn tích sản")

    f = res.filters["fundamental"]
    assert f["revenueGrowthPercentMin"] == "5"
    assert f["earningsGrowthPercentMin"] == "5"
    assert f["roeMin"] == "15"
    assert "valuationClassification" not in f
    assert res.filters["market"]["marketCapMin"] == "1000000000000"


@pytest.mark.asyncio
async def test_value_explicit_pe_replaces_relative_classification_default():
    res = await convert_natural_language_to_filters("Lọc cổ phiếu value P/E dưới 10")

    fundamental = res.filters["fundamental"]
    assert fundamental["peMax"] == "10"
    assert "valuationClassification" not in fundamental


@pytest.mark.asyncio
async def test_explicit_debt_to_equity_uses_percent_point_contract():
    res = await convert_natural_language_to_filters("Lọc cổ phiếu D/E dưới 100%")

    assert res.filters["fundamental"]["debtToEquityMax"] == "100"
