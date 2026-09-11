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
      "debtToEquityMax": "100.0",
      "dividendYieldMin": "3.0",
      "dividendYieldMax": "10.0",
      "valuationClassification": ["UNDER_VALUED", "FAIR_VALUED", "OVER_VALUED"]
    }
  },
  "confidence": 0.0 - 1.0,
  "ambiguityNote": "Mô tả giải thích nếu câu hỏi mơ hồ hoặc không xác định được chỉ số định lượng cụ thể" | null
}

QUY TẮC ĐẶC BIỆT:
1. Nếu người dùng yêu cầu lọc theo trường phái đầu tư (Archetype) mà không nêu chỉ số cụ thể:
   - "Tăng trưởng" (Growth): doanh thu tăng >= 10%, EPS tăng >= 10%, ROE >= 15%.
   - "Dài hạn" / "Tích sản" / "Nắm giữ" (Long-term Quality): doanh thu tăng >= 5%, EPS tăng >= 5%, ROE >= 15%, vốn hóa >= 1.000 tỷ VND.
   - "Giá trị" / "Định giá hấp dẫn" (Value): valuationClassification = UNDER_VALUED, ROE >= 12%, vốn hóa >= 1.000 tỷ VND.
   - "Cổ tức" (Dividend): dividend yield >= 3%, P/E được xác định và dương, vốn hóa >= 2.000 tỷ VND.
   - "Lướt sóng" / "Ngắn hạn" (Momentum Screen): Giá trên MA20, RSI từ 50 đến 68, khối lượng tương đối >= 1.2. Đây là bộ lọc ứng viên, không phải tín hiệu MOMENTUM đã kích hoạt.
   Gán confidence = 0.85 và ghi chú giải thích quy đổi vào ambiguityNote.
2. Nếu có số liệu rõ ràng cụ thể từ người dùng (ví dụ: "P/E dưới 10, ROE trên 15%"), gán confidence >= 0.9 và điền chính xác vào filters.
3. Nếu hoàn toàn mơ hồ không thuộc trường phái nào ("cổ phiếu ngon", "cổ phiếu tiềm năng"), gán confidence < 0.6 và giải thích vào ambiguityNote. TUYỆT ĐỐI KHÔNG tự bịa đặt đoán mò số liệu mà không báo trước (FR-009).
4. Trả về DUY NHẤT một chuỗi JSON hợp lệ.
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
    explicit_metrics: set[str] = set()

    # PE
    pe_under = re.search(r"p/?e\s*(?:dưới|<|nhỏ hơn|<=)\s*(\d+(?:\.\d+)?)", q_lower)
    if pe_under:
        fundamental["peMax"] = pe_under.group(1)
        explicit_metrics.add("pe")
        matched_explicit_conditions += 1
    pe_over = re.search(r"p/?e\s*(?:trên|>|lớn hơn|>=)\s*(\d+(?:\.\d+)?)", q_lower)
    if pe_over:
        fundamental["peMin"] = pe_over.group(1)
        explicit_metrics.add("pe")
        matched_explicit_conditions += 1

    # PB
    pb_under = re.search(r"p/?b\s*(?:dưới|<|nhỏ hơn|<=)\s*(\d+(?:\.\d+)?)", q_lower)
    if pb_under:
        fundamental["pbMax"] = pb_under.group(1)
        explicit_metrics.add("pb")
        matched_explicit_conditions += 1
    pb_over = re.search(r"p/?b\s*(?:trên|>|lớn hơn|>=)\s*(\d+(?:\.\d+)?)", q_lower)
    if pb_over:
        fundamental["pbMin"] = pb_over.group(1)
        explicit_metrics.add("pb")
        matched_explicit_conditions += 1

    # ROE
    roe_over = re.search(r"roe\s*(?:trên|>|lớn hơn|>=)\s*(\d+(?:\.\d+)?)%?", q_lower)
    if roe_over:
        fundamental["roeMin"] = roe_over.group(1)
        explicit_metrics.add("roe")
        matched_explicit_conditions += 1
    roe_under = re.search(r"roe\s*(?:dưới|<|nhỏ hơn|<=)\s*(\d+(?:\.\d+)?)%?", q_lower)
    if roe_under:
        fundamental["roeMax"] = roe_under.group(1)
        explicit_metrics.add("roe")
        matched_explicit_conditions += 1

    # ROA
    roa_over = re.search(r"roa\s*(?:trên|>|lớn hơn|>=)\s*(\d+(?:\.\d+)?)%?", q_lower)
    if roa_over:
        fundamental["roaMin"] = roa_over.group(1)
        explicit_metrics.add("roa")
        matched_explicit_conditions += 1

    # EPS/earnings growth. Explicit user constraints always replace the
    # archetype default, including an opposite-side constraint.
    eps_growth_over = re.search(
        r"(?:eps\s*(?:tăng(?:\s*trưởng)?)?|tăng(?:\s*trưởng)?\s*eps)\s*(?:trên|>|lớn hơn|>=)\s*(\d+(?:\.\d+)?)%?",
        q_lower,
    )
    if eps_growth_over:
        fundamental["earningsGrowthPercentMin"] = eps_growth_over.group(1)
        explicit_metrics.add("earnings_growth")
        matched_explicit_conditions += 1
    eps_growth_under = re.search(
        r"(?:eps\s*(?:tăng(?:\s*trưởng)?)?|tăng(?:\s*trưởng)?\s*eps)\s*(?:dưới|<|nhỏ hơn|<=)\s*(\d+(?:\.\d+)?)%?",
        q_lower,
    )
    if eps_growth_under:
        fundamental["earningsGrowthPercentMax"] = eps_growth_under.group(1)
        explicit_metrics.add("earnings_growth")
        matched_explicit_conditions += 1

    # Revenue growth
    revenue_growth_over = re.search(
        r"(?:doanh\s*thu|revenue)\s*(?:tăng(?:\s*trưởng)?)?\s*(?:trên|>|lớn hơn|>=)\s*(\d+(?:\.\d+)?)%?",
        q_lower,
    )
    if revenue_growth_over:
        fundamental["revenueGrowthPercentMin"] = revenue_growth_over.group(1)
        explicit_metrics.add("revenue_growth")
        matched_explicit_conditions += 1
    revenue_growth_under = re.search(
        r"(?:doanh\s*thu|revenue)\s*(?:tăng(?:\s*trưởng)?)?\s*(?:dưới|<|nhỏ hơn|<=)\s*(\d+(?:\.\d+)?)%?",
        q_lower,
    )
    if revenue_growth_under:
        fundamental["revenueGrowthPercentMax"] = revenue_growth_under.group(1)
        explicit_metrics.add("revenue_growth")
        matched_explicit_conditions += 1

    # Debt/Equity canonical unit is percent points: 100 means debt equals equity.
    debt_to_equity_max = re.search(
        r"(?:d/?e|nợ\s*/?\s*(?:vốn chủ sở hữu|vcsh))\s*(?:dưới|<|nhỏ hơn|<=)\s*(\d+(?:\.\d+)?)%?",
        q_lower,
    )
    if debt_to_equity_max:
        fundamental["debtToEquityMax"] = debt_to_equity_max.group(1)
        explicit_metrics.add("debt_to_equity")
        matched_explicit_conditions += 1

    dividend_yield_min = re.search(
        r"(?:dividend\s*yield|lợi\s*suất\s*cổ\s*tức)\s*(?:trên|>|lớn hơn|>=)\s*(\d+(?:\.\d+)?)%?",
        q_lower,
    )
    if dividend_yield_min:
        fundamental["dividendYieldMin"] = dividend_yield_min.group(1)
        explicit_metrics.add("dividend_yield")
        matched_explicit_conditions += 1

    # RSI
    rsi_over = re.search(r"rsi\s*(?:trên|>|lớn hơn|>=)\s*(\d+(?:\.\d+)?)", q_lower)
    if rsi_over:
        technical["rsiMin"] = rsi_over.group(1)
        explicit_metrics.add("rsi")
        matched_explicit_conditions += 1
    rsi_under = re.search(r"rsi\s*(?:dưới|<|nhỏ hơn|<=)\s*(\d+(?:\.\d+)?)", q_lower)
    if rsi_under:
        technical["rsiMax"] = rsi_under.group(1)
        explicit_metrics.add("rsi")
        matched_explicit_conditions += 1

    # MA relationships
    if "cắt lên ma20" in q_lower or "trên ma20" in q_lower:
        technical["maRelationship"] = ["PRICE_ABOVE_MA20"]
        explicit_metrics.add("ma_relationship")
        matched_explicit_conditions += 1
    elif "cắt lên ma50" in q_lower or "trên ma50" in q_lower:
        technical["maRelationship"] = ["PRICE_ABOVE_MA50"]
        explicit_metrics.add("ma_relationship")
        matched_explicit_conditions += 1

    # Exchanges
    if "hose" in q_lower or "hsx" in q_lower:
        market["exchange"] = ["HOSE"]
        explicit_metrics.add("exchange")
        matched_explicit_conditions += 1
    elif "hnx" in q_lower:
        market["exchange"] = ["HNX"]
        explicit_metrics.add("exchange")
        matched_explicit_conditions += 1

    # FR-005: Archetype conversions for natural investment philosophies
    archetype_note = None
    if any(k in q_lower for k in ("tăng trưởng", "growth")):
        if "revenue_growth" not in explicit_metrics:
            fundamental["revenueGrowthPercentMin"] = "10"
        if "earnings_growth" not in explicit_metrics:
            fundamental["earningsGrowthPercentMin"] = "10"
        if "roe" not in explicit_metrics:
            fundamental["roeMin"] = "15"
        archetype_note = "Preset Growth v2 (heuristic): tăng trưởng doanh thu >= 10%, tăng trưởng EPS >= 10%, ROE >= 15%. Không giới hạn sàn và không áp dụng Nợ/VCSH mặc định vì khác biệt ngành. Điều kiện người dùng nhập được ưu tiên."
    elif any(k in q_lower for k in ("cổ tức", "dividend")):
        if "dividend_yield" not in explicit_metrics:
            fundamental["dividendYieldMin"] = "3"
        if "pe" not in explicit_metrics:
            fundamental["peMin"] = "0"
        market.setdefault("marketCapMin", "2000000000000")
        archetype_note = "Preset Dividend v2 (heuristic): lợi suất cổ tức >= 3%, P/E được xác định và dương, vốn hóa >= 2.000 tỷ VND. Chưa đánh giá được độ bền payout nhiều năm. Điều kiện người dùng nhập được ưu tiên."
    elif any(k in q_lower for k in ("giá trị", "định giá hấp dẫn", "value")):
        # A user-provided absolute valuation constraint is authoritative. Do
        # not silently add the relative valuation-v3 classification as a
        # second, stricter condition.
        if "pe" not in explicit_metrics and "pb" not in explicit_metrics:
            fundamental["valuationClassification"] = ["UNDER_VALUED"]
        if "roe" not in explicit_metrics:
            fundamental["roeMin"] = "12"
        market.setdefault("marketCapMin", "1000000000000")
        archetype_note = "Preset Value v2 (heuristic): valuation-v3 phải công bố UNDER_VALUED, ROE >= 12%, vốn hóa >= 1.000 tỷ VND. Không dùng ngưỡng P/E/P/B tuyệt đối cho mọi ngành. Điều kiện người dùng nhập được ưu tiên."
    elif any(k in q_lower for k in ("dài hạn", "tích sản", "lâu dài", "nắm giữ")):
        if "revenue_growth" not in explicit_metrics:
            fundamental["revenueGrowthPercentMin"] = "5"
        if "earnings_growth" not in explicit_metrics:
            fundamental["earningsGrowthPercentMin"] = "5"
        if "roe" not in explicit_metrics:
            fundamental["roeMin"] = "15"
        market.setdefault("marketCapMin", "1000000000000")
        archetype_note = "Preset Long-term Quality v2 (heuristic): tăng trưởng doanh thu >= 5%, tăng trưởng EPS >= 5%, ROE >= 15%, vốn hóa >= 1.000 tỷ VND. Đây là sàng lọc ứng viên, chưa chứng minh tính bền vững nhiều năm. Điều kiện người dùng nhập được ưu tiên."
    elif any(k in q_lower for k in ("lướt sóng", "ngắn hạn", "momentum", "bứt phá")):
        if "rsi" not in explicit_metrics:
            technical["rsiMin"] = "50"
            technical["rsiMax"] = "68"
        if "ma_relationship" not in explicit_metrics:
            technical["maRelationship"] = ["PRICE_ABOVE_MA20"]
        technical.setdefault("relativeVolumeMin", "1.2")
        archetype_note = "Preset Momentum Screen v2 (heuristic): giá trên MA20, RSI từ 50 đến 68, khối lượng tương đối >= 1.2. Đây là bộ lọc ứng viên, không phải tín hiệu MOMENTUM_SIGNAL. Điều kiện người dùng nhập được ưu tiên."

    filters: Dict[str, Any] = {}
    if market:
        filters["market"] = market
    if price:
        filters["price"] = price
    if technical:
        filters["technical"] = technical
    if fundamental:
        filters["fundamental"] = fundamental

    if archetype_note:
        return ScreenerConversionResult(
            filters=filters,
            confidence=0.85 if matched_explicit_conditions == 0 else 0.9,
            ambiguityNote=archetype_note,
        )

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
