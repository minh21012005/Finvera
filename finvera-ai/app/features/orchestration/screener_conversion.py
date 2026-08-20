import json
import logging
import re
from typing import Any, Dict, List, Optional, Tuple
from pydantic import BaseModel, Field

from app.infrastructure.llm.generation import GeminiGenerationAdapter

logger = logging.getLogger(__name__)


class ScreenerConversionResult(BaseModel):
    filters: Dict[str, Any]
    confidence: float
    ambiguityNote: Optional[str] = None


CONVERSION_SYSTEM_PROMPT = """Bạn là trợ lý AI chuyên gia phân tích tài chính, nhiệm vụ của bạn là chuyển đổi tiêu chí tìm kiếm/lọc cổ phiếu bằng ngôn ngữ tự nhiên (tiếng Việt) thành bộ lọc có cấu trúc JSON hợp lệ cho công cụ Finvera Screener.

CẤU TRÚC JSON BẮT BUỘC:
{
  "filters": {
    "market": {
      "exchange": ["HOSE", "HNX", "UPCOM"],
      "marketCapMin": "1000000000000",
      "marketCapMax": "50000000000000"
    },
    "price": {
      "priceMin": "10000",
      "priceMax": "50000",
      "priceChangePercentMin": "-5.0",
      "priceChangePercentMax": "7.0"
    },
    "technical": {
      "rsiMin": "30",
      "rsiMax": "70",
      "macdSignal": "BULLISH_CROSSOVER" | "BEARISH_CROSSOVER" | "ABOVE_ZERO" | "BELOW_ZERO",
      "maRelationship": ["PRICE_ABOVE_MA20", "PRICE_ABOVE_MA50", "PRICE_ABOVE_MA200", "MA20_ABOVE_MA50", "GOLDEN_CROSS", "DEATH_CROSS"],
      "volumeMin": 100000,
      "volumeMax": 10000000,
      "relativeVolumeMin": "1.5",
      "relativeVolumeMax": "5.0",
      "breakout": "RESISTANCE_BREAKOUT_20D" | "RESISTANCE_BREAKOUT_50D" | "SUPPORT_BREAKDOWN_20D" | "SUPPORT_BREAKDOWN_50D" | "WEEK_52_HIGH" | "WEEK_52_LOW",
      "trend": "UPTREND_STRONG" | "UPTREND_MODERATE" | "DOWNTREND_STRONG" | "DOWNTREND_MODERATE" | "SIDEWAYS"
    },
    "fundamental": {
      "revenueGrowthPercentMin": "10.0",
      "revenueGrowthPercentMax": "50.0",
      "earningsGrowthPercentMin": "15.0",
      "earningsGrowthPercentMax": "100.0",
      "roeMin": "15.0",
      "roeMax": "40.0",
      "roaMin": "5.0",
      "roaMax": "20.0",
      "peMin": "5.0",
      "peMax": "15.0",
      "pbMin": "0.5",
      "pbMax": "3.0",
      "debtToEquityMin": "0.0",
      "debtToEquityMax": "2.0"
    }
  },
  "confidence": 0.0 - 1.0,
  "ambiguityNote": "Mô tả giải thích nếu câu hỏi mơ hồ hoặc không xác định được chỉ số định lượng cụ thể" | null
}

QUY TẮC ĐẶC BIỆT:
1. Nếu tiêu chí mơ hồ (ví dụ: "cổ phiếu ngon", "cổ phiếu tiềm năng", "giá rẻ" mà không có số cụ thể), gán confidence < 0.6 và điền giải thích vào ambiguityNote. TUYỆT ĐỐI KHÔNG tự bịa đặt đoán mò số liệu mà không báo trước (FR-009).
2. Nếu có số liệu rõ ràng (ví dụ: "P/E dưới 10, ROE trên 15%"), gán confidence >= 0.8 và điền chính xác vào filters.fundamental (ví dụ: peMax: "10", roeMin: "15").
3. Trả về DUY NHẤT một chuỗi JSON hợp lệ.
"""


def _rule_based_extract(query: str) -> Optional[ScreenerConversionResult]:
    """
    Fast rule-based extraction for common Vietnamese natural-language financial screening patterns.
    Ensures deterministic behavior in testing and high-speed execution.
    """
    q_lower = query.lower()
    fundamental: Dict[str, Any] = {}
    technical: Dict[str, Any] = {}
    market: Dict[str, Any] = {}
    price: Dict[str, Any] = {}

    matched_explicit_conditions = 0

    # PE
    pe_under = re.search(r"p/?e\s*(?:dưới|<|nhỏ hơn|<=)\s*(\d+(?:\.\d+)?)", q_lower)
    if pe_under:
        fundamental["peMax"] = pe_under.group(1)
        matched_explicit_conditions += 1
    pe_over = re.search(r"p/?e\s*(?:trên|>|lớn hơn|>=)\s*(\d+(?:\.\d+)?)", q_lower)
    if pe_over:
        fundamental["peMin"] = pe_over.group(1)
        matched_explicit_conditions += 1

    # PB
    pb_under = re.search(r"p/?b\s*(?:dưới|<|nhỏ hơn|<=)\s*(\d+(?:\.\d+)?)", q_lower)
    if pb_under:
        fundamental["pbMax"] = pb_under.group(1)
        matched_explicit_conditions += 1
    pb_over = re.search(r"p/?b\s*(?:trên|>|lớn hơn|>=)\s*(\d+(?:\.\d+)?)", q_lower)
    if pb_over:
        fundamental["pbMin"] = pb_over.group(1)
        matched_explicit_conditions += 1

    # ROE
    roe_over = re.search(r"roe\s*(?:trên|>|lớn hơn|>=)\s*(\d+(?:\.\d+)?)%?", q_lower)
    if roe_over:
        fundamental["roeMin"] = roe_over.group(1)
        matched_explicit_conditions += 1
    roe_under = re.search(r"roe\s*(?:dưới|<|nhỏ hơn|<=)\s*(\d+(?:\.\d+)?)%?", q_lower)
    if roe_under:
        fundamental["roeMax"] = roe_under.group(1)
        matched_explicit_conditions += 1

    # ROA
    roa_over = re.search(r"roa\s*(?:trên|>|lớn hơn|>=)\s*(\d+(?:\.\d+)?)%?", q_lower)
    if roa_over:
        fundamental["roaMin"] = roa_over.group(1)
        matched_explicit_conditions += 1

    # RSI
    rsi_over = re.search(r"rsi\s*(?:trên|>|lớn hơn|>=)\s*(\d+(?:\.\d+)?)", q_lower)
    if rsi_over:
        technical["rsiMin"] = rsi_over.group(1)
        matched_explicit_conditions += 1
    rsi_under = re.search(r"rsi\s*(?:dưới|<|nhỏ hơn|<=)\s*(\d+(?:\.\d+)?)", q_lower)
    if rsi_under:
        technical["rsiMax"] = rsi_under.group(1)
        matched_explicit_conditions += 1

    # MA relationships
    if "cắt lên ma20" in q_lower or "trên ma20" in q_lower:
        technical["maRelationship"] = ["PRICE_ABOVE_MA20"]
        matched_explicit_conditions += 1
    elif "cắt lên ma50" in q_lower or "trên ma50" in q_lower:
        technical["maRelationship"] = ["PRICE_ABOVE_MA50"]
        matched_explicit_conditions += 1

    # Exchanges
    if "hose" in q_lower or "hsx" in q_lower:
        market["exchange"] = ["HOSE"]
        matched_explicit_conditions += 1
    elif "hnx" in q_lower:
        market["exchange"] = ["HNX"]
        matched_explicit_conditions += 1

    filters: Dict[str, Any] = {}
    if market:
        filters["market"] = market
    if price:
        filters["price"] = price
    if technical:
        filters["technical"] = technical
    if fundamental:
        filters["fundamental"] = fundamental

    # If completely vague without any numbers or clear indicators (e.g. "cổ phiếu ngon", "cổ phiếu tiềm năng")
    vague_keywords = ["ngon", "tiềm năng", "tốt", "giá rẻ", "đẹp", "hấp dẫn", "đáng mua"]
    is_vague = any(kw in q_lower for kw in vague_keywords) and matched_explicit_conditions == 0

    if is_vague:
        return ScreenerConversionResult(
            filters={},
            confidence=0.4,
            ambiguityNote="Yêu cầu tìm kiếm chưa cung cấp tiêu chí định lượng cụ thể (như P/E, ROE, RSI hoặc sàn giao dịch). Finvera giữ nguyên tiêu chí mở rộng không suy đoán.",
        )

    if matched_explicit_conditions > 0:
        return ScreenerConversionResult(
            filters=filters,
            confidence=0.9,
            ambiguityNote=None,
        )

    return None


async def convert_natural_language_to_filters(
    query: str,
    llm_adapter: Optional[GeminiGenerationAdapter] = None,
) -> ScreenerConversionResult:
    """
    FR-007, FR-008, FR-009: Converts natural-language screening criterion to structured filters.
    Includes confidence scoring and ambiguity disclosure if below confidence floor (0.6).
    """
    # 1. Check rule-based fast extractor first
    rule_res = _rule_based_extract(query)
    if rule_res is not None:
        return rule_res

    # 2. LLM-based extraction
    adapter = llm_adapter or GeminiGenerationAdapter()
    prompt = f"{CONVERSION_SYSTEM_PROMPT}\n\nCÂU HỎI CỦA NGƯỜI DÙNG: \"{query}\"\n\nJSON:"

    try:
        raw = await adapter.generate_text(prompt)
        json_match = re.search(r"\{.*\}", raw, re.DOTALL)
        if json_match:
            data = json.loads(json_match.group(0))
            filters = data.get("filters", {})
            confidence = float(data.get("confidence", 0.5))
            ambiguity_note = data.get("ambiguityNote")

            if confidence < 0.6 and not ambiguity_note:
                ambiguity_note = "Tiêu chí tìm kiếm có độ mơ hồ cao. Finvera không tự ý đưa ra giả định số liệu."

            return ScreenerConversionResult(
                filters=filters,
                confidence=confidence,
                ambiguityNote=ambiguity_note,
            )
    except Exception as e:
        logger.warning(f"Error converting natural language to screener filters: {e}")

    # Safe fallback if ambiguous
    return ScreenerConversionResult(
        filters={},
        confidence=0.3,
        ambiguityNote="Không thể trích xuất tiêu chí lọc định lượng cụ thể từ câu hỏi. Vui lòng cung cấp các chỉ số rõ ràng hơn như P/E, ROE, vốn hóa hoặc sàn giao dịch.",
    )
