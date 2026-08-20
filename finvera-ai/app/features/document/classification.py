import json
import logging
import re
from typing import Any, Dict, List, Optional
from pydantic import BaseModel
from app.core.settings import settings

logger = logging.getLogger(__name__)

VALID_CATEGORIES = {
    "COMPANY",
    "SECTOR",
    "MARKET",
    "MACRO",
    "REGULATION",
}

VALID_SENTIMENTS = {"POSITIVE", "NEUTRAL", "NEGATIVE"}
VALID_IMPACT_LEVELS = {"HIGH", "MEDIUM", "LOW"}


class NewsClassificationResult(BaseModel):
    # None means the system could not confidently determine this field (FR-014/AI-004) —
    # finvera-be maps None to Applicability.MISSING, never a guessed/default value.
    category: Optional[str] = None
    sentiment: Optional[str] = None
    impactLevel: Optional[str] = None
    sector: Optional[str] = None
    entities: List[str] = []

    def to_dict(self) -> Dict[str, Any]:
        return {
            "category": self.category,
            "sentiment": self.sentiment,
            "impactLevel": self.impactLevel,
            "sector": self.sector,
            "entities": self.entities,
        }


CLASSIFICATION_PROMPT = """Analyze the following Vietnamese stock market news article and extract structured classification in valid JSON format.

JSON Schema:
{
  "category": "COMPANY" | "SECTOR" | "MARKET" | "MACRO" | "REGULATION",
  "sentiment": "POSITIVE" | "NEUTRAL" | "NEGATIVE",
  "impactLevel": "HIGH" | "MEDIUM" | "LOW",
  "sector": "string (e.g. Banking, Technology, Real Estate, Steel, Consumer) or null",
  "entities": ["list of stock tickers mentioned, e.g. FPT, VNM, HPG"]
}

Article Headline: {headline}
Article Body:
{body}

Output ONLY the JSON object.
"""


def extract_stock_tickers(text: str) -> List[str]:
    """Extracts potential 3-letter stock tickers."""
    # Find uppercase 3-letter words
    tokens = re.findall(r"\b[A-Z]{3}\b", text)
    # Filter out common acronyms
    stopwords = {"GDP", "CPI", "VND", "USD", "EUR", "SBV", "BCT", "TTC", "STB", "EVN"}
    known_stocks = {"FPT", "VNM", "HPG", "VCB", "MBB", "SSI", "VIC", "VHM", "MWG", "TCB", "VPB", "GAS", "MSN", "STB"}
    
    found = []
    for t in tokens:
        if t in known_stocks or (t not in stopwords and t.isupper() and len(t) == 3):
            if t not in found:
                found.append(t)
    return found


def classify_news_offline(headline: str, body_text: str, symbol: Optional[str] = None) -> NewsClassificationResult:
    """
    Deterministic rule-based classification fallback for test/offline environments.
    """
    full_text = f"{headline} {body_text}".lower()

    # Category Detection (COMPANY, SECTOR, MARKET, MACRO, REGULATION)
    if any(k in full_text for k in ["nghị định", "thông tư", "luật", "ủy ban chứng khoán", "ubck", "xử phạt", "thanh tra", "pháp lý"]):
        category = "REGULATION"
    elif any(k in full_text for k in ["ngân hàng nhà nước", "lãi suất", "lạm phát", "gdp", "cpi", "tỷ giá", "vĩ mô", "sbv"]):
        category = "MACRO"
    elif any(k in full_text for k in ["ngành", "chuỗi cung ứng", "xuất khẩu", "nhập khẩu", "thị phần", "giá thép", "giá dầu", "bất động sản khu công nghiệp"]):
        category = "SECTOR"
    elif any(k in full_text for k in ["vn-index", "thị trường", "phiên giao dịch", "khối ngoại", "thanh khoản", "toàn sàn"]):
        category = "MARKET"
    elif symbol:
        # No topical keyword bucket matched, but the owner associated this article with a
        # specific instrument — that association is itself real evidence the article
        # concerns that company, not a guess (FR-014 only forbids values with no basis).
        category = "COMPANY"
    else:
        # No keyword bucket matched and no symbol association: no basis to pick a
        # category, so this is undetermined (FR-014/AI-004) rather than a guessed default.
        category = None

    # Sentiment Detection
    positive_words = ["tăng trưởng", "kỷ lục", "vượt kế hoạch", "khởi sắc", "tích cực", "đột phá", "khả quan", "lợi nhuận tăng"]
    negative_words = ["suy giảm", "thua lỗ", "khó khăn", "lao dốc", "bị phạt", "tiêu cực", "rủi ro", "thụt lùi", "giảm mạnh"]

    pos_score = sum(1 for w in positive_words if w in full_text)
    neg_score = sum(1 for w in negative_words if w in full_text)

    if pos_score > neg_score:
        sentiment = "POSITIVE"
    elif neg_score > pos_score:
        sentiment = "NEGATIVE"
    elif pos_score == 0 and neg_score == 0:
        # Absence of any charged language across the whole article is itself evidence of a
        # neutral, factual tone, not an absence of evidence.
        sentiment = "NEUTRAL"
    else:
        # A genuine tie with both positive and negative signal present is conflicting
        # evidence, not neutral evidence: undetermined (FR-014/AI-004).
        sentiment = None

    # Impact Level
    if any(k in full_text for k in ["đột biến", "kỷ lục", "ảnh hưởng lớn", "thay đổi cấu trúc", "phá sản", "sáp nhập"]):
        impact_level = "HIGH"
    elif any(k in full_text for k in ["đáng kể", "mở rộng", "điều chỉnh", "tăng trưởng tốt"]):
        impact_level = "MEDIUM"
    else:
        # No impact keyword matched: there is no basis to conclude LOW specifically,
        # so this is undetermined (FR-014/AI-004) rather than a default guess.
        impact_level = None

    # Sector Detection
    sector = None
    if any(k in full_text for k in ["ngân hàng", "tín dụng", "nợ xấu", "casa"]):
        sector = "Banking"
    elif any(k in full_text for k in ["công nghệ", "phần mềm", "ai", "chuyển đổi số", "chip"]):
        sector = "Technology"
    elif any(k in full_text for k in ["thép", "quặng", "hrc"]):
        sector = "Steel"
    elif any(k in full_text for k in ["bất động sản", "nhà ở", "dự án", "đất đai"]):
        sector = "Real Estate"
    elif any(k in full_text for k in ["bán lẻ", "tiêu dùng", "siêu thị", "sữa"]):
        sector = "Consumer"

    entities = extract_stock_tickers(f"{headline} {body_text}")
    if symbol and symbol.upper() not in entities:
        entities.append(symbol.upper())

    return NewsClassificationResult(
        category=category,
        sentiment=sentiment,
        impactLevel=impact_level,
        sector=sector,
        entities=entities,
    )


async def classify_news_article(
    headline: str,
    body_text: str,
    symbol: Optional[str] = None,
) -> NewsClassificationResult:
    """
    Classifies a news article using Gemini with deterministic rule fallback.
    """
    if settings.gemini_api_key and settings.gemini_api_key not in ("mock", "fixture"):
        try:
            from google import genai
            from google.genai import types

            client = genai.Client(api_key=settings.gemini_api_key)
            prompt = CLASSIFICATION_PROMPT.format(headline=headline, body=body_text[:4000])

            response = client.models.generate_content(
                model=settings.gemini_generation_model,
                contents=prompt,
                config=types.GenerateContentConfig(
                    temperature=0.1,
                    response_mime_type="application/json",
                ),
            )

            if response.text:
                data = json.loads(response.text)
                raw_cat = data.get("category")
                raw_sent = data.get("sentiment")
                raw_imp = data.get("impactLevel")
                cat = raw_cat.upper() if isinstance(raw_cat, str) else None
                sent = raw_sent.upper() if isinstance(raw_sent, str) else None
                imp = raw_imp.upper() if isinstance(raw_imp, str) else None

                # A missing or schema-invalid value is undetermined (FR-014/AI-004),
                # never coerced to an arbitrary in-range default.
                return NewsClassificationResult(
                    category=cat if cat in VALID_CATEGORIES else None,
                    sentiment=sent if sent in VALID_SENTIMENTS else None,
                    impactLevel=imp if imp in VALID_IMPACT_LEVELS else None,
                    sector=data.get("sector"),
                    entities=data.get("entities", []),
                )
        except Exception as e:
            logger.warning(f"Online Gemini news classification failed: {e}, falling back to offline classifier")

    return classify_news_offline(headline, body_text, symbol)
