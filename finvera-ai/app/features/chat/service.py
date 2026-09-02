import asyncio
import datetime
import json
import logging
import re
from decimal import Decimal, InvalidOperation
from typing import Any, AsyncIterator, Dict, List, Optional, Tuple
import uuid
from pydantic import BaseModel, Field

from app.core.settings import settings
from app.features.orchestration.allowlist import ToolName
from app.features.orchestration.attribution import (
    DocumentClaim,
    RawStructuredClaim,
    StructuredClaim,
    VerifiedAttributionResult,
    verify_attribution,
)
from app.features.orchestration.dispatch import (
    DispatchedToolCall,
    OrchestrationDispatcher,
)
from app.features.orchestration.screener_conversion import (
    convert_natural_language_to_filters,
)
from app.features.rag.citations import verify_citation_claims
from app.features.rag.synthesis import extract_claims_and_citations
from app.infrastructure.llm.generation import GeminiGenerationAdapter
from app.features.chat.prompts import SYNTHESIS_SYSTEM_INSTRUCTION

logger = logging.getLogger(__name__)

# T049: upper bound on concurrent backend tool calls per question.
DISPATCH_CONCURRENCY = 5


class PriorTurn(BaseModel):
    question: str
    answer: str


class OrchestrateAskRequest(BaseModel):
    ownerId: uuid.UUID
    question: str = Field(..., min_length=1, max_length=2000)
    symbol: Optional[str] = None
    priorTurns: List[PriorTurn] = Field(default_factory=list, max_length=10)


# Tool function-calling declarations (U-1, orchestration-v1). `owner_id` is
# deliberately absent from every schema: it is resolved from the session and injected
# server-side at validate_tool_call (U-2) and MUST NEVER be a model-controlled argument.
TOOL_DECLARATIONS: List[Dict[str, Any]] = [
    {
        "name": "MARKET",
        "description": "Tổng quan thị trường chứng khoán Việt Nam: VN-Index, độ rộng thị trường, số mã tăng/giảm.",
        "parameters": {"type": "object", "properties": {}},
    },
    {
        "name": "STOCK",
        "description": "Giá và tổng quan giao dịch hiện tại của một mã cổ phiếu cụ thể.",
        "parameters": {
            "type": "object",
            "properties": {"symbol": {"type": "string", "description": "Mã cổ phiếu, ví dụ FPT, HPG, VNM"}},
            "required": ["symbol"],
        },
    },
    {
        "name": "TECHNICAL",
        "description": "Chỉ báo và tín hiệu phân tích kỹ thuật (RSI, MACD, MA, xu hướng, kháng cự/hỗ trợ) của một mã cổ phiếu.",
        "parameters": {
            "type": "object",
            "properties": {"symbol": {"type": "string"}},
            "required": ["symbol"],
        },
    },
    {
        "name": "FUNDAMENTAL",
        "description": "Số liệu cơ bản (EPS, ROE, doanh thu, lợi nhuận, kết quả kinh doanh) của một mã cổ phiếu.",
        "parameters": {
            "type": "object",
            "properties": {"symbol": {"type": "string"}},
            "required": ["symbol"],
        },
    },
    {
        "name": "VALUATION",
        "description": "Định giá (P/E, P/B, phân loại đắt/rẻ so với ngành) của một mã cổ phiếu.",
        "parameters": {
            "type": "object",
            "properties": {"symbol": {"type": "string"}},
            "required": ["symbol"],
        },
    },
    {
        "name": "PORTFOLIO",
        "description": "Vị thế nắm giữ hoặc hiệu suất/rủi ro danh mục đầu tư của chủ sở hữu.",
        "parameters": {
            "type": "object",
            "properties": {
                "sub_type": {
                    "type": "string",
                    "enum": ["POSITIONS", "ANALYTICS"],
                    "description": "POSITIONS cho danh sách vị thế, ANALYTICS cho hiệu suất/rủi ro tổng hợp",
                }
            },
        },
    },
    {
        "name": "NEWS",
        "description": "Tin tức thị trường hoặc cổ phiếu gần đây mà chủ sở hữu đã lưu trữ.",
        "parameters": {
            "type": "object",
            "properties": {
                "symbol": {"type": "string"},
                "limit": {"type": "integer", "description": "Số lượng tin tối đa, mặc định 5"},
            },
        },
    },
    {
        "name": "RESEARCH_RAG",
        "description": (
            "Tìm kiếm và trích dẫn nội dung từ tài liệu/báo cáo/tin tức đã lưu trữ của chủ sở "
            "hữu (báo cáo tài chính, nghị quyết ĐHCĐ, tài liệu công bố, bài báo...). Dùng khi "
            "câu hỏi cần trích dẫn một tài liệu cụ thể, không phải một con số thị trường."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "query": {"type": "string", "description": "Nội dung cần tìm trong kho tài liệu"},
                "symbol": {"type": "string"},
            },
            "required": ["query"],
        },
    },
    {
        "name": "SCREENING",
        "description": "Lọc/tìm cổ phiếu thoả một tiêu chí (ví dụ: momentum tốt, P/E thấp, RSI quá bán).",
        "parameters": {
            "type": "object",
            "properties": {"query": {"type": "string", "description": "Tiêu chí lọc bằng ngôn ngữ tự nhiên"}},
            "required": ["query"],
        },
    },
]

TOOL_PROPOSAL_SYSTEM_INSTRUCTION = """Bạn là bộ định tuyến công cụ (tool router) cho Finvera AI Analyst, một trợ lý
nghiên cứu đầu tư cho thị trường chứng khoán Việt Nam.

Nhiệm vụ DUY NHẤT của bạn: quyết định câu hỏi của chủ sở hữu cần gọi những công cụ nào trong số các
công cụ đã khai báo (function declarations), với đối số gì. Bạn KHÔNG trả lời câu hỏi ở bước này.

Quy tắc bắt buộc:
1. Chỉ được đề xuất các công cụ đã khai báo. Không tự bịa ra công cụ khác.
2. Nếu câu hỏi có nhắc mã cổ phiếu, hãy truyền đúng mã đó (viết hoa) vào đối số symbol của mọi
   công cụ cần symbol.
3. Nếu câu hỏi cần nhiều loại dữ liệu (ví dụ vừa giá vừa kỹ thuật), đề xuất nhiều công cụ.
4. Nếu câu hỏi không thể trả lời bằng bất kỳ công cụ nào ở trên (ví dụ hỏi về thời tiết, hỏi
   ngoài phạm vi tài chính/đầu tư), KHÔNG đề xuất công cụ nào cả.
5. owner_id KHÔNG bao giờ là một đối số bạn cung cấp — hệ thống tự gắn giá trị đó."""

# SYNTHESIS_SYSTEM_INSTRUCTION is imported from app.features.chat.prompts


def extract_structured_claims_from_text(text: str) -> List[RawStructuredClaim]:
    """
    Extracts inline [T<sequenceNo>:<fieldPath>=<claimedValue>] tags the synthesis model
    is instructed to emit next to every structured claim it states, mirroring rag-v1's
    own [Block N] convention for document claims.
    """
    claims: List[RawStructuredClaim] = []
    tag_pattern = re.compile(TAG_FIELD_PATTERN)
    sentences = re.split(r"(?<=[.!?\n])\s+", text)

    for sentence in sentences:
        matches = tag_pattern.findall(sentence)
        if not matches:
            continue
        clean = tag_pattern.sub("", sentence).strip()
        clean = re.sub(r"\s+", " ", clean).strip()
        if not clean:
            continue
        for seq_str, field_path, claimed_value in matches:
            claims.append(
                RawStructuredClaim(
                    claimText=clean,
                    sequenceNo=int(seq_str),
                    fieldPath=field_path,
                    claimedValue=claimed_value.strip(),
                )
            )
    return claims


# Contract reason-code-presentation-v1 (specs/014-reason-code-presentation): the five codes the
# valuation tool emits when it withholds, worded exactly as the frontend dictionary words them.
VALUATION_WITHHOLD_LABELS: Dict[str, str] = {
    "NO_COMPARISON_BASIS": "Chưa đủ cơ sở so sánh (lịch sử riêng lẫn ngành)",
    "CORE_METRIC_UNAVAILABLE": "Không có chỉ số lõi (P/E và P/B) khả dụng",
    "INSUFFICIENT_METRIC_COVERAGE": "Độ phủ chỉ số dưới ngưỡng 50 %",
    "PRICE_UNAVAILABLE": "Chưa có giá được chấp nhận",
    "FUNDAMENTALS_UNAVAILABLE": "Chưa có báo cáo tài chính được ghi nhận",
    "NO_VALUATION": "Chưa có đánh giá định giá",
}


OFFLINE_TEMPLATE_DISCLOSURE = (
    "(Mô hình AI tạm thời không khả dụng — câu trả lời dưới đây được lập theo mẫu, trực tiếp từ "
    "dữ liệu công cụ, chưa qua diễn giải.)"
)

DATA_STATUS_WORDS = {"DELAYED": "trễ một phiên", "STALE": "đã cũ", "PARTIAL": "một phần", "UNAVAILABLE": "không có"}


def fmt_vi(value: Any, decimals: int = 2) -> str:
    """vi-VN number text for the offline templates (claimedValue stays the raw string)."""
    num = _as_decimal(value)
    if num is None:
        return str(value)
    if decimals == 0 or num == num.to_integral_value() and abs(num) >= Decimal("1000"):
        text = f"{num:,.0f}"
    else:
        text = f"{num:,.{decimals}f}".rstrip("0").rstrip(".")
    return text.replace(",", "\u0000").replace(".", ",").replace("\u0000", ".")


def _as_decimal(value: Any) -> Optional[Decimal]:
    """Parse backend numeric strings without binary floating-point arithmetic."""
    try:
        return Decimal(str(value).replace(",", "").strip())
    except (InvalidOperation, TypeError, ValueError):
        return None


def _status_note(data: Dict[str, Any]) -> str:
    status = str(data.get("dataStatus") or "CURRENT").upper()
    trading_date = (data.get("raw") or {}).get("tradingDate") if isinstance(data.get("raw"), dict) else None
    parts = []
    if trading_date:
        parts.append(f"theo phiên {trading_date}")
    if status in DATA_STATUS_WORDS:
        parts.append(f"dữ liệu {DATA_STATUS_WORDS[status]}")
    return f" ({'; '.join(parts)})" if parts else ""


# Q-56 (Feature 015): a field path may step into a list — `metrics[0].ownHistoryPercentile`,
# `signals[1].direction` — now that get_nested_value resolves such paths; the tag regexes must
# accept the same shape, otherwise the tag survives in the reader's text ("[T2:metrics[0]…").
TAG_FIELD = r"[\w.]+(?:\[\d+\][\w.]*)*"
TAG_FIELD_PATTERN = r"\[T(\d+):(" + TAG_FIELD + r")=([^\[\]]+)\]"
TAG_STRIP_PATTERN = r"\[T\d+:" + TAG_FIELD + r"=[^\[\]]+\]"


def strip_synthesis_tags(text: str) -> str:
    """Removes the inline citation tags while KEEPING the model's line structure
    (markdown headings, bullets, paragraphs) -- collapsing every whitespace run to one
    space flattened the whole answer into a single line (owner report 2026-08-31)."""
    clean = re.sub(TAG_STRIP_PATTERN, "", text)
    clean = re.sub(r"\[Block\s*\d+\]", "", clean, flags=re.IGNORECASE)
    clean = re.sub(r"[ \t]+([.,;:!?])", r"\1", clean)      # "VND ." left by a removed tag -> "VND."
    clean = re.sub(r"(?<=\S)[ \t]{2,}", " ", clean)          # inner runs only; leading indentation is list nesting
    clean = re.sub(r"\n{3,}", "\n\n", clean)
    return clean.strip()


def has_unattributed_substantive_content(text: str) -> bool:
    """Returns True when model prose contains a substantive non-heading unit
    without either a structured evidence tag or a document block citation."""
    for unit in re.split(r"(?<=[.!?])\s+|\n+", text):
        candidate = unit.strip()
        if not candidate or re.match(r"^#{1,6}\s+\S", candidate):
            continue
        if re.search(TAG_FIELD_PATTERN, candidate) or re.search(r"\[Block\s*\d+\]", candidate, re.IGNORECASE):
            continue
        visible = re.sub(r"^[*+\-\d.)\s]+", "", candidate).strip()
        if re.search(r"[A-Za-zÀ-ỹ]", visible):
            return True
    return False


class ChatOrchestrationService:
    """
    FR-001, FR-002, FR-003, FR-005, FR-011 to FR-015, AI-001, AI-004.
    Orchestrates multi-tool question answering, SSE streaming, and U-5 attribution verification.
    """

    def __init__(
        self,
        dispatcher: Optional[OrchestrationDispatcher] = None,
        llm_adapter: Optional[GeminiGenerationAdapter] = None,
    ):
        self.dispatcher = dispatcher or OrchestrationDispatcher()
        self.llm_adapter = llm_adapter or GeminiGenerationAdapter()

    def plan_tools(self, question: str, symbol: Optional[str]) -> List[Dict[str, Any]]:
        """
        Deterministic keyword-based tool-selection fallback, used when no real LLM
        provider is configured or the model's own function-calling proposal fails —
        never the primary decision path when a real provider is available (see
        propose_tool_calls). Kept fully deterministic so offline/test environments
        remain reproducible (U-6).
        """
        q_upper = question.upper()
        proposed = []

        stop_words = {
            "RSI", "MAC", "SMA", "EMA", "VND", "USD", "EPS", "ROE", "TOP",
            "THE", "AND", "FOR", "GET", "NAY", "HOM", "BAN", "CHO", "CON",
            "MAI", "XEM", "GIA", "DAN", "MUC", "TIN", "DOC", "BAI", "HOI",
            "LAM", "SAO", "KHI", "NAO", "VAN", "ROI", "VOI", "TAI", "DAY"
        }

        matched_symbol = symbol.upper() if symbol else None
        if not matched_symbol:
            raw_symbols = re.findall(r"\b[A-Z]{3}\b", question)
            tickers = [s for s in raw_symbols if s not in stop_words]
            if tickers:
                matched_symbol = tickers[0]
            else:
                kw_match = re.search(r"(?:CỔ PHIẾU|MÃ)\s+([A-Z]{3})\b", q_upper)
                if kw_match and kw_match.group(1) not in stop_words:
                    matched_symbol = kw_match.group(1)

        if any(k in q_upper for k in ("THỊ TRƯỜNG", "VN-INDEX", "VNINDEX", "VN30", "ĐỘ RỘNG", "MARKET", "XU HƯỚNG CHUNG")):
            proposed.append({"tool_name": "MARKET", "arguments": {}})

        if matched_symbol and any(k in q_upper for k in ("GIÁ", "STOCK", "THỊ GIÁ", "CỔ PHIẾU", "KHỚP LỆNH")):
            proposed.append({"tool_name": "STOCK", "arguments": {"symbol": matched_symbol}})

        if matched_symbol and any(k in q_upper for k in ("KỸ THUẬT", "RSI", "MACD", "MA20", "MA50", "CHỈ BÁO", "TÍN HIỆU", "DÒNG TIỀN", "TECHNICAL", "KHÁNG CỰ", "HỖ TRỢ")):
            proposed.append({"tool_name": "TECHNICAL", "arguments": {"symbol": matched_symbol}})

        if matched_symbol and any(k in q_upper for k in ("CƠ BẢN", "TÀI CHÍNH", "EPS", "ROE", "DOANH THU", "LỢI NHUẬN", "BÁO CÁO TÀI CHÍNH", "BCTC", "FUNDAMENTAL", "KẾT QUẢ KINH DOANH")):
            proposed.append({"tool_name": "FUNDAMENTAL", "arguments": {"symbol": matched_symbol}})

        if matched_symbol and any(k in q_upper for k in ("ĐỊNH GIÁ", "P/E", "P/B", "PE", "PB", "VALUATION", "ĐẮT", "RẺ")):
            proposed.append({"tool_name": "VALUATION", "arguments": {"symbol": matched_symbol}})

        if any(k in q_upper for k in ("DANH MỤC", "TÀI SẢN", "PORTFOLIO", "VỊ THẾ", "HIỆU SUẤT ĐẦU TƯ", "LÃI LỖ")):
            sub_type = "ANALYTICS" if any(k in q_upper for k in ("HIỆU SUẤT", "RỦI RO", "ANALYTICS")) else "POSITIONS"
            proposed.append({"tool_name": "PORTFOLIO", "arguments": {"sub_type": sub_type}})

        if any(k in q_upper for k in ("TIN TỨC", "TIN MỚI", "BÀI BÁO", "NEWS", "SỰ KIỆN")):
            args: Dict[str, Any] = {"limit": 5}
            if matched_symbol:
                args["symbol"] = matched_symbol
            proposed.append({"tool_name": "NEWS", "arguments": args})

        if any(k in q_upper for k in ("LỌC CỔ PHIẾU", "TÌM CỔ PHIẾU", "LỌC MÃ", "MÃ NÀO CÓ", "CỔ PHIẾU CÓ", "TÌM MÃ", "SCREENER", "SCREENING", "DANH SÁCH CỔ PHIẾU", "CỔ PHIẾU NÀO", "CÁC MÃ CÓ", "CỔ PHIẾU THOẢ")):
            proposed.append({"tool_name": "SCREENING", "arguments": {"query": question}})

        if any(k in q_upper for k in ("BÁO CÁO THƯỜNG NIÊN", "TÀI LIỆU", "PDF", "TRÍCH XUẤT", "TRÍCH LỤC", "ĐỌC ĐƯỢC", "THEO TÀI LIỆU", "ĐẠI HỘI CỔ ĐÔNG", "ĐHCĐ", "NGHỊ QUYẾT", "CÔNG BỐ", "THUYẾT MINH", "VĂN BẢN")):
            args = {"query": question, "top_k": 5}
            if matched_symbol:
                args["symbol"] = matched_symbol
            proposed.append({"tool_name": "RESEARCH_RAG", "arguments": args})

        if matched_symbol and not proposed:
            proposed.append({"tool_name": "STOCK", "arguments": {"symbol": matched_symbol}})

        return proposed

    def _build_tool_proposal_prompt(
        self, question: str, symbol: Optional[str], prior_turns: List[PriorTurn]
    ) -> str:
        lines: List[str] = []
        if prior_turns:
            lines.append("Bối cảnh các lượt hỏi trước (chỉ tham khảo, không dùng để chọn lại công cụ cho câu hỏi cũ):")
            for t in prior_turns[-5:]:
                lines.append(f"- Hỏi: {t.question}\n  Đáp: {t.answer[:300]}")
            lines.append("")
        lines.append(f"Câu hỏi hiện tại: {question}")
        if symbol:
            lines.append(f"Mã cổ phiếu người dùng đang xem (dùng nếu liên quan): {symbol}")
        return "\n".join(lines)

    async def propose_tool_calls(
        self, question: str, symbol: Optional[str], prior_turns: List[PriorTurn]
    ) -> List[Dict[str, Any]]:
        """
        orchestration-v1: "proposedCalls = model's function-calling output". Uses real
        Gemini native function-calling when a provider is configured; falls back to the
        deterministic plan_tools() heuristic only when no provider is available or the
        online call itself fails (never when the model genuinely proposes zero tools —
        that is a legitimate "outside current capability" decision, not a failure).
        """
        calls, _mode = await self.propose_tool_calls_with_mode(question, symbol, prior_turns)
        return calls

    async def propose_tool_calls_with_mode(
        self, question: str, symbol: Optional[str], prior_turns: List[PriorTurn]
    ) -> Tuple[List[Dict[str, Any]], str]:
        """Feature 015: also reports HOW the plan was made — MODEL or KEYWORD_FALLBACK — so the
        final event can disclose a degraded run instead of hiding it."""
        if not self.llm_adapter.is_online:
            return self.plan_tools(question, symbol), "KEYWORD_FALLBACK"

        prompt = self._build_tool_proposal_prompt(question, symbol, prior_turns)
        proposed = await self.llm_adapter.propose_tool_calls(
            prompt=prompt,
            system_instruction=TOOL_PROPOSAL_SYSTEM_INSTRUCTION,
            tool_declarations=TOOL_DECLARATIONS,
        )
        if proposed is None:
            return self.plan_tools(question, symbol), "KEYWORD_FALLBACK"
        return proposed, "MODEL"

    def _offline_synthesize(
        self, succeeded_calls: List[DispatchedToolCall]
    ) -> Tuple[List[str], List[RawStructuredClaim], List[DocumentClaim]]:
        """
        Deterministic template-based synthesis for offline/test environments (no real
        LLM provider configured). Kept fully reproducible (U-6); the online path
        (_online_synthesize) is what real deployments use.
        """
        answer_parts: List[str] = []
        raw_claims: List[RawStructuredClaim] = []
        document_claims: List[DocumentClaim] = []

        for call in succeeded_calls:
            seq = call.sequence_no
            data = call.response_data or {}
            if call.tool_name == ToolName.MARKET:
                vn_val = str(data.get("vnIndexValue", "0"))
                vn_chg = str(data.get("vnIndexChangePercent", "0"))
                adv = str(data.get("advancers", 0))
                dec = str(data.get("decliners", 0))
                regime = (data.get("raw") or {}).get("marketRegime") if isinstance(data.get("raw"), dict) else None
                breadth_raw = (data.get("raw") or {}).get("breadth") if isinstance(data.get("raw"), dict) else None
                trading_date = (breadth_raw or {}).get("tradingDate") if isinstance(breadth_raw, dict) else None
                when = f" (phiên {trading_date})" if trading_date else ""

                parts_market = [
                    f"**Tổng quan thị trường{when}:** VN-INDEX đạt {fmt_vi(vn_val)} điểm ({fmt_vi(vn_chg)}%).",
                    f"**Độ rộng:** {adv} mã tăng / {dec} mã giảm.",
                ]
                if regime:
                    parts_market.append(f"**Trạng thái:** {regime}.")
                answer_parts.append(" ".join(parts_market))
                raw_claims.append(RawStructuredClaim(claimText=f"VN-INDEX {vn_val} điểm", sequenceNo=seq, fieldPath="vnIndexValue", claimedValue=vn_val))
                raw_claims.append(RawStructuredClaim(claimText=f"Độ biến động {vn_chg}%", sequenceNo=seq, fieldPath="vnIndexChangePercent", claimedValue=vn_chg))
                raw_claims.append(RawStructuredClaim(claimText=f"{adv} mã tăng", sequenceNo=seq, fieldPath="advancers", claimedValue=adv))

            elif call.tool_name == ToolName.STOCK:
                sym = data.get("symbol", "")
                if data.get("price") is None:
                    answer_parts.append(f"Giá của {sym} hiện không khả dụng ({data.get('dataStatus', 'UNAVAILABLE')}).")
                else:
                    price = str(data.get("price"))
                    chg = str(data.get("changePercent", "0"))
                    answer_parts.append(f"Cổ phiếu {sym} đóng cửa ở {fmt_vi(price, 0)} đồng ({fmt_vi(chg)} %){_status_note(data)}.")
                    raw_claims.append(RawStructuredClaim(claimText=f"Giá {price}", sequenceNo=seq, fieldPath="price", claimedValue=price))
                    raw_claims.append(RawStructuredClaim(claimText=f"Thay đổi {chg}%", sequenceNo=seq, fieldPath="changePercent", claimedValue=chg))

            elif call.tool_name == ToolName.TECHNICAL:
                sym = data.get("symbol", "")
                indicators = data.get("indicators") or {}
                bits = []
                for code, label in (("MA20", "MA20"), ("MA50", "MA50"), ("RSI14", "RSI14")):
                    ind = indicators.get(code) or {}
                    comps = ind.get("components") or []
                    if ind.get("applicability") == "DEFINED" and comps and comps[0].get("value") is not None:
                        val = comps[0]["value"]
                        bits.append(f"{label} {fmt_vi(val, 0 if code.startswith('MA') else 2)}")
                        raw_claims.append(RawStructuredClaim(claimText=f"{label} {fmt_vi(val)}", sequenceNo=seq,
                                                             fieldPath=f"indicators.{code}.components[0].value", claimedValue=str(val)))
                signals = data.get("signals") or []
                triggered = [s_ for s_ in signals if isinstance(s_, dict) and s_.get("strategyCode")]
                tech_parts = [f"**Chỉ báo kỹ thuật {sym}{_status_note(data)}:** " + (", ".join(bits) if bits else "chưa có chỉ báo khả dụng") + "."]
                if triggered:
                    names = ", ".join(f"{s_['strategyCode']} ({s_.get('direction')})" for s_ in triggered)
                    tech_parts.append(f"**Tín hiệu kích hoạt:** {names}.")
                    raw_claims.append(RawStructuredClaim(claimText=f"{len(triggered)} tín hiệu", sequenceNo=seq, fieldPath="signals.length", claimedValue=str(len(triggered))))
                else:
                    tech_parts.append("Không có tín hiệu chiến lược nào đang kích hoạt.")
                    raw_claims.append(RawStructuredClaim(claimText="0 tín hiệu", sequenceNo=seq, fieldPath="signals.length", claimedValue="0"))
                answer_parts.append(" ".join(tech_parts))

            elif call.tool_name == ToolName.FUNDAMENTAL:
                sym = data.get("symbol", "")
                eps = data.get("eps")
                roe = data.get("roe")
                period = data.get("period", "ANNUAL")
                reasons = {m.get("metricCode"): m.get("qualityReason") for m in (data.get("metrics") or []) if isinstance(m, dict)}
                fund_parts = [f"**Dữ liệu cơ bản {sym} (kỳ {period}):**"]
                details = []
                if eps:
                    details.append(f"EPS quý {fmt_vi(eps, 0)} đ")
                    raw_claims.append(RawStructuredClaim(claimText=f"EPS {eps}", sequenceNo=seq, fieldPath="eps", claimedValue=str(eps)))
                eps_ttm = data.get("epsTtm")
                if eps_ttm:
                    basis = " (theo số trailing của NCC)" if reasons.get("EPS_TTM") == "PROVIDER_TRAILING_EPS" else ""
                    details.append(f"EPS 12 tháng {fmt_vi(eps_ttm, 0)} đ{basis}")
                    raw_claims.append(RawStructuredClaim(claimText=f"EPS TTM {eps_ttm}", sequenceNo=seq, fieldPath="epsTtm", claimedValue=str(eps_ttm)))
                if roe:
                    details.append(f"ROE {fmt_vi(roe, 2)}%")
                    raw_claims.append(RawStructuredClaim(claimText=f"ROE {roe}%", sequenceNo=seq, fieldPath="roe", claimedValue=str(roe)))
                for code, key, label in (("REVENUE_GROWTH_PERCENT", "revenueGrowthPercent", "tăng trưởng doanh thu"),
                                         ("EPS_GROWTH_PERCENT", "epsGrowthPercent", "tăng trưởng EPS")):
                    val = data.get(key)
                    if val is not None:
                        basis = " (tính theo năm)" if reasons.get(code) == "ANNUAL_BASIS" else ""
                        details.append(f"{label} {fmt_vi(val, 2)}%{basis}")
                        raw_claims.append(RawStructuredClaim(claimText=f"{label} {val}%", sequenceNo=seq, fieldPath=key, claimedValue=str(val)))
                if details:
                    fund_parts.append(", ".join(details) + ".")

                answer_parts.append(" ".join(fund_parts))

            elif call.tool_name == ToolName.VALUATION:
                sym = data.get("symbol", "")
                cls = data.get("classification")
                pe = data.get("peRatio")
                pb = data.get("pbRatio")
                val_parts = []
                if cls:
                    cls_desc = {
                        "VERY_ATTRACTIVE": "Rất hấp dẫn",
                        "ATTRACTIVE": "Hấp dẫn",
                        "FAIR": "Phù hợp",
                        "EXPENSIVE": "Đắt",
                        "VERY_EXPENSIVE": "Rất đắt",
                    }.get(str(cls), str(cls))
                    val_parts.append(f"**Định giá {sym}:** Phân loại ở mức **{cls_desc}**.")
                    raw_claims.append(RawStructuredClaim(claimText=f"Phân loại {cls}", sequenceNo=seq, fieldPath="classification", claimedValue=str(cls)))
                    codes = [str(c) for c in (data.get("reasonCodes") or [])]
                    if "REDUCED_METRIC_SET" in codes:
                        val_parts.append("(Lưu ý: Bộ chỉ số bị thu hẹp do một chỉ số lõi không áp dụng, kết luận dựa trên chỉ số còn lại).")
                else:
                    # Contract reason-code-presentation-v1 (specs/014): wording, never a bare identifier;
                    # an unknown code is still shown as itself.
                    reasons = "; ".join(VALUATION_WITHHOLD_LABELS.get(str(r), str(r)) for r in (data.get("reasonCodes") or [])) or "chưa đủ dữ liệu"
                    val_parts.append(f"**Định giá {sym}:** Hiện chưa được công bố ({reasons}).")

                multiples = []
                if pe:
                    multiples.append(f"P/E = {fmt_vi(pe, 2)}")
                    raw_claims.append(RawStructuredClaim(claimText=f"P/E {pe}", sequenceNo=seq, fieldPath="peRatio", claimedValue=str(pe)))
                if pb:
                    multiples.append(f"P/B = {fmt_vi(pb, 2)}")
                    raw_claims.append(RawStructuredClaim(claimText=f"P/B {pb}", sequenceNo=seq, fieldPath="pbRatio", claimedValue=str(pb)))
                if multiples:
                    val_parts.append("Hệ số định giá: " + ", ".join(multiples) + ".")

                # Q-52: the bases the engine used and the price date are part of the answer.
                basis_words = []
                input_basis = data.get("inputBasis") or {}
                if isinstance(input_basis, dict):
                    if input_basis.get("EPS_TTM") == "PROVIDER_TRAILING_EPS":
                        basis_words.append("EPS 12 tháng lấy theo số trailing của NCC")
                    annual = [k for k, v in input_basis.items() if v == "ANNUAL_BASIS"]
                    if annual:
                        basis_words.append("tăng trưởng/cổ tức tính trên số liệu năm")
                price_date = data.get("priceTradingDate")
                status = str(data.get("dataStatus") or "CURRENT").upper()
                if price_date:
                    basis_words.append(f"giá theo phiên {price_date}" + (f", dữ liệu {DATA_STATUS_WORDS[status]}" if status in DATA_STATUS_WORDS else ""))
                if basis_words:
                    val_parts.append("(" + "; ".join(basis_words) + ")")
                answer_parts.append(" ".join(val_parts))

            elif call.tool_name == ToolName.PORTFOLIO:
                positions = data.get("positions")
                total_val = data.get("totalValue")
                cash_balance = data.get("cashBalance")
                total_unrealized_pnl = data.get("totalUnrealizedPL")
                total_unrealized_pnl_pct = data.get("totalUnrealizedPnlPercent")

                if isinstance(positions, list):
                    if not positions:
                        answer_parts.append("Danh mục hiện không có vị thế nào — chưa có gì để đánh giá rủi ro hay hiệu suất.")
                        raw_claims.append(RawStructuredClaim(claimText="0 vị thế", sequenceNo=seq, fieldPath="positions.length", claimedValue="0"))
                    else:
                        port_parts = [f"**Danh mục nắm giữ ({len(positions)} vị thế):**"]
                        raw_claims.append(RawStructuredClaim(claimText=f"{len(positions)} vị thế", sequenceNo=seq, fieldPath="positions.length", claimedValue=str(len(positions))))

                        position_lines = []
                        for idx, pos in enumerate(positions):
                            sym_p = pos.get("symbol", "?")
                            alloc = pos.get("allocation")
                            pnl_pct = pos.get("unrealizedPnlPercent")
                            mv = pos.get("marketValue")
                            line = f"- {sym_p}"
                            if alloc is not None:
                                alloc_value = _as_decimal(alloc)
                                if alloc_value is not None:
                                    # Contract: allocation is always a ratio in [0, 1].
                                    alloc_pct = alloc_value * Decimal("100")
                                    line += f": tỷ trọng {fmt_vi(alloc_pct, 1)}%"
                                    raw_claims.append(RawStructuredClaim(
                                        claimText=f"Tỷ trọng {sym_p} {alloc_pct}%", sequenceNo=seq,
                                        fieldPath=f"positions[{idx}].allocation", claimedValue=str(alloc),
                                    ))
                            if mv is not None:
                                line += f", giá trị {fmt_vi(mv, 0)} đ"
                                raw_claims.append(RawStructuredClaim(
                                    claimText=f"Giá trị {sym_p} {mv}", sequenceNo=seq,
                                    fieldPath=f"positions[{idx}].marketValue", claimedValue=str(mv),
                                ))
                            if pnl_pct is not None:
                                pnl_value = _as_decimal(pnl_pct)
                                if pnl_value is not None:
                                    # Contract: unrealizedPnlPercent is already percentage points.
                                    sign = "+" if pnl_value > 0 else ""
                                    line += f", lãi/lỗ {sign}{fmt_vi(pnl_value, 2)}%"
                                    raw_claims.append(RawStructuredClaim(
                                        claimText=f"Lãi/lỗ {sym_p} {pnl_pct}%", sequenceNo=seq,
                                        fieldPath=f"positions[{idx}].unrealizedPnlPercent", claimedValue=str(pnl_pct),
                                    ))
                            position_lines.append(line)

                        # Show top positions (max 6)
                        for pl in position_lines[:6]:
                            port_parts.append(pl)
                        if len(position_lines) > 6:
                            port_parts.append(f"- ... và {len(position_lines) - 6} vị thế khác.")

                        answer_parts.append("\n".join(port_parts))

                elif total_val is not None:
                    if str(total_val).strip() in ("0", "0.0", "0.00"):
                        answer_parts.append("Tổng giá trị danh mục là 0 đồng — chưa có vị thế nào được ghi nhận.")
                    else:
                        analytics_parts = [f"**Tổng quan danh mục:** Giá trị {fmt_vi(total_val, 0)} đ"]
                        if cash_balance is not None:
                            analytics_parts[0] += f", tiền mặt {fmt_vi(cash_balance, 0)} đ"
                            raw_claims.append(RawStructuredClaim(
                                claimText=f"Tiền mặt {cash_balance}", sequenceNo=seq,
                                fieldPath="cashBalance", claimedValue=str(cash_balance),
                            ))
                        analytics_parts[0] += "."
                        if total_unrealized_pnl is not None:
                            pnl_str = fmt_vi(total_unrealized_pnl, 0)
                            pnl_value = _as_decimal(total_unrealized_pnl)
                            sign = "+" if pnl_value is not None and pnl_value > 0 else ""
                            pnl_line = f"**Lãi/Lỗ chưa thực hiện:** {sign}{pnl_str} đ"
                            if total_unrealized_pnl_pct is not None:
                                # Contract: this field is already percentage points.
                                pnl_line += f" ({sign}{fmt_vi(total_unrealized_pnl_pct, 2)}%)"
                                raw_claims.append(RawStructuredClaim(
                                    claimText=f"Tỷ lệ lãi/lỗ chưa thực hiện {total_unrealized_pnl_pct}%", sequenceNo=seq,
                                    fieldPath="totalUnrealizedPnlPercent", claimedValue=str(total_unrealized_pnl_pct),
                                ))
                            analytics_parts.append(pnl_line + ".")
                            raw_claims.append(RawStructuredClaim(
                                claimText=f"Lãi/lỗ chưa thực hiện {total_unrealized_pnl}", sequenceNo=seq,
                                fieldPath="totalUnrealizedPL", claimedValue=str(total_unrealized_pnl),
                            ))
                        answer_parts.append(" ".join(analytics_parts))
                    raw_claims.append(RawStructuredClaim(claimText=f"Tổng giá trị {total_val}", sequenceNo=seq, fieldPath="totalValue", claimedValue=str(total_val)))

            elif call.tool_name == ToolName.SCREENING:
                matches = data.get("matches") or []
                total = data.get("totalMatches", len(matches))
                names = ", ".join(str(m.get("symbol")) for m in matches[:10] if isinstance(m, dict) and m.get("symbol"))
                res_line = f"**Kết quả lọc cổ phiếu:** Tìm thấy **{total} mã** thoả mãn tiêu chí"
                if names:
                    res_line += f": {names}" + (" …" if len(matches) > 10 else "")
                answer_parts.append(res_line + ".")
                raw_claims.append(RawStructuredClaim(claimText=f"{total} mã", sequenceNo=seq, fieldPath="totalMatches", claimedValue=str(total)))

            elif call.tool_name == ToolName.NEWS:
                articles = data.get("articles", [])
                if articles:
                    news_titles = []
                    for idx, article in enumerate(articles[:3]):
                        if not isinstance(article, dict) or not article.get("title"):
                            continue
                        title = str(article["title"])
                        news_titles.append(f"- {title}")
                        raw_claims.append(RawStructuredClaim(
                            claimText=f"Tiêu đề tin: {title}", sequenceNo=seq,
                            fieldPath=f"articles[{idx}].title", claimedValue=title,
                        ))
                    answer_parts.append(f"**Tin tức thị trường gần đây ({len(articles)} tin):**\n" + "\n".join(news_titles))
                    raw_claims.append(RawStructuredClaim(
                        claimText=f"{len(articles)} tin", sequenceNo=seq,
                        fieldPath="articles.length", claimedValue=str(len(articles)),
                    ))
                else:
                    answer_parts.append("Hệ thống chưa ghi nhận tin tức mới nào liên quan.")
                    raw_claims.append(RawStructuredClaim(
                        claimText="0 tin", sequenceNo=seq,
                        fieldPath="articles.length", claimedValue="0",
                    ))

            elif call.tool_name == ToolName.RESEARCH_RAG:
                passages = data.get("passages", [])
                if passages:
                    for p in passages[:3]:
                        c_id_raw = p.get("chunkId")
                        c_title = p.get("sourceTitle", "")
                        c_text = p.get("excerpt", "")

                        # AI-003: treat retrieved text purely as untrusted reference data,
                        # stripping any injection attempt before it ever reaches the answer.
                        cleaned_preview = re.sub(
                            r"(?i)(system\s+prompt|ignore\s+all|ignore\s+previous|override\s+instructions|bỏ\s+qua\s+chỉ\s+thị).*",
                            "[TRÍCH ĐOẠN ĐÃ LỌC]",
                            c_text,
                        )

                        answer_parts.append(f"Theo tài liệu công bố '{c_title}': {cleaned_preview[:150]}...")
                        if c_id_raw:
                            try:
                                document_claims.append(
                                    DocumentClaim(
                                        chunkId=uuid.UUID(str(c_id_raw)),
                                        claimText=f"Theo {c_title}: {cleaned_preview[:100]}",
                                    )
                                )
                            except (ValueError, TypeError):
                                pass

        return answer_parts, raw_claims, document_claims

    def _build_online_synthesis_prompt(
        self,
        question: str,
        succeeded_calls: List[DispatchedToolCall],
        rag_passages: List[Dict[str, Any]],
    ) -> str:
        lines: List[str] = [f"Câu hỏi của chủ sở hữu: {question}", ""]
        for call in succeeded_calls:
            lines.append(f"[Tool {call.sequence_no}: {call.tool_name.value if hasattr(call.tool_name, 'value') else call.tool_name}]")
            # JSON, not Python repr: field names/values must read exactly as the model must cite them.
            lines.append(json.dumps(call.response_data or {}, ensure_ascii=False, default=str))
            lines.append("")
        for i, p in enumerate(rag_passages):
            excerpt = str(p.get("excerpt", "")).strip()
            lines.append(f"[Block {i + 1}]: {excerpt}")
            lines.append("")
        return "\n".join(lines)

    async def _online_synthesize(
        self,
        question: str,
        succeeded_calls: List[DispatchedToolCall],
    ) -> AsyncIterator[Dict[str, Any]]:
        """
        Real LLM-driven synthesis (orchestration-v1 steps 1-6): streams delta events as
        the model generates, then yields a final internal event
        {"raw_structured_claims", "document_claims", "answer"} for the caller to run
        through verify_attribution. Structured claims are extracted from the model's own
        inline [T<seq>:<field>=<value>] tags (never trusted without verification);
        document claims are extracted and verified via rag-v1's own, unmodified
        extract_claims_and_citations/verify_citation_claims (delegated, not
        reimplemented, per orchestration-v1 step 4).
        """
        rag_passages: List[Dict[str, Any]] = []
        for call in succeeded_calls:
            if call.tool_name == ToolName.RESEARCH_RAG:
                rag_passages.extend((call.response_data or {}).get("passages", []))

        block_to_chunk_id: Dict[int, uuid.UUID] = {}
        passage_by_chunk_id: Dict[str, Dict[str, Any]] = {}
        for i, p in enumerate(rag_passages):
            chunk_id_raw = p.get("chunkId")
            if not chunk_id_raw:
                continue
            try:
                cid = uuid.UUID(str(chunk_id_raw))
            except (ValueError, TypeError):
                continue
            block_to_chunk_id[i + 1] = cid
            passage_by_chunk_id[str(cid)] = p

        prompt = self._build_online_synthesis_prompt(question, succeeded_calls, rag_passages)

        accumulated = ""
        # generate_stream_raw (not generate_stream): a mid-attempt failure here must
        # propagate to the caller so it can fall back to _offline_synthesize's
        # deterministic templates instead of generate_stream's own generic,
        # rag-v1-shaped offline text, which isn't tagged for this tool-call format.
        async for delta in self.llm_adapter.generate_stream_raw(prompt, system_instruction=SYNTHESIS_SYSTEM_INSTRUCTION):
            accumulated += delta
            yield {"type": "delta", "textDelta": delta}

        raw_structured = extract_structured_claims_from_text(accumulated)

        document_claims: List[DocumentClaim] = []
        if block_to_chunk_id:
            raw_citation_claims = extract_claims_and_citations(accumulated)
            citation_result = verify_citation_claims(
                raw_answer=accumulated,
                raw_claims=raw_citation_claims,
                total_blocks_k=len(rag_passages),
                block_to_chunk_id_map=block_to_chunk_id,
            )
            for vc in citation_result.citations:
                document_claims.append(DocumentClaim(chunkId=vc.chunk_id, claimText=vc.claim_text))

        clean_answer = strip_synthesis_tags(accumulated)
        yield {
            "type": "_internal_synthesis_result",
            "answer": clean_answer,
            "raw_structured_claims": raw_structured,
            "document_claims": document_claims,
            "unattributed_content_present": has_unattributed_substantive_content(accumulated),
        }

    async def orchestrate_stream(
        self,
        request: OrchestrateAskRequest,
    ) -> AsyncIterator[Dict[str, Any]]:
        """
        Orchestration pipeline yielding SSE-formatted dict events:
        - `tool_call`
        - `delta`
        - `final`
        """
        # Step 1: Propose tools — real LLM function-calling when available, otherwise
        # the deterministic keyword heuristic (propose_tool_calls handles the fallback).
        proposed_calls, planner_mode = await self.propose_tool_calls_with_mode(request.question, request.symbol, request.priorTurns)

        if not proposed_calls:
            refused_result = VerifiedAttributionResult(
                answer="Câu hỏi nằm ngoài phạm vi phân tích hỗ trợ hiện tại của hệ thống hoặc không có công cụ phù hợp.",
                structuredClaims=[],
                documentClaims=[],
                refused=True,
                toolCalls=[],
                toolCallBoundReached=False,
                ruleVersion="orchestration-v1",
            )
            yield {
                "type": "final",
                "final": refused_result.model_dump(),
            }
            return

        # Step 2: Dispatch tools. Independent read-only calls run concurrently (bounded);
        # sequence numbers and the emitted tool_call events keep the proposal order so the
        # [T<n>:...] citation tags and the audit trail stay deterministic (T049).
        limit = settings.analyst_max_tool_calls
        bound_reached = len(proposed_calls) > limit
        to_dispatch = proposed_calls[:limit]
        semaphore = asyncio.Semaphore(DISPATCH_CONCURRENCY)

        async def _prepare_and_dispatch(i: int, call_req: Dict[str, Any]) -> DispatchedToolCall:
            tool_name = call_req["tool_name"]
            arguments = dict(call_req["arguments"])
            # If SCREENING tool, run natural language conversion first (FR-007, FR-009)
            if tool_name == "SCREENING" and "filters" not in arguments:
                conv_result = await convert_natural_language_to_filters(
                    arguments.get("query", request.question),
                    self.llm_adapter,
                )
                arguments["filters"] = conv_result.filters
                if conv_result.ambiguityNote:
                    arguments["ambiguityNote"] = conv_result.ambiguityNote
            async with semaphore:
                return await self.dispatcher.dispatch_single_tool(
                    sequence_no=i,
                    tool_name_raw=tool_name,
                    arguments_raw=arguments,
                    session_owner_id=request.ownerId,
                )

        dispatched_calls: List[DispatchedToolCall] = list(await asyncio.gather(
            *[_prepare_and_dispatch(i, call_req) for i, call_req in enumerate(to_dispatch, start=1)]
        ))

        for dispatched in dispatched_calls:
            # Stream tool_call progress events in proposal order
            yield {
                "type": "tool_call",
                "toolCall": {
                    "sequenceNo": dispatched.sequence_no,
                    "toolName": dispatched.tool_name.value if hasattr(dispatched.tool_name, "value") else str(dispatched.tool_name),
                    "arguments": dispatched.arguments,
                    "status": dispatched.status,
                    "failureReason": dispatched.failure_reason,
                    "latencyMs": dispatched.latency_ms,
                },
            }

        # Step 3: Synthesize Answer and Claims
        succeeded_calls = [c for c in dispatched_calls if c.status == "SUCCEEDED" and c.response_data]

        if not succeeded_calls:
            refused_result = verify_attribution(
                answer="Không thể lấy dữ liệu từ các công cụ để trả lời câu hỏi.",
                raw_structured_claims=[],
                verified_document_claims=[],
                dispatched_calls=dispatched_calls,
                tool_call_bound_reached=bound_reached,
                explicit_refusal=True,
                planner_mode=planner_mode,
            )
            yield {
                "type": "final",
                "final": refused_result.model_dump(),
            }
            return

        raw_claims: List[RawStructuredClaim] = []
        document_claims: List[DocumentClaim] = []
        full_answer = ""
        online_succeeded = False
        unattributed_content_present = False
        online_attempted = bool(self.llm_adapter.is_online)

        if self.llm_adapter.is_online:
            try:
                # Buffered rather than streamed live: a failure partway through
                # generate_stream_raw must not leave the client with a half-delivered
                # answer it can never complete — correctness here is worth the small
                # latency cost of buffering a typically-fast LLM call before replaying
                # it as real delta events.
                async for event in self._online_synthesize(request.question, succeeded_calls):
                    if event["type"] == "delta":
                        # Buffer model output until attribution verification. Raw model
                        # prose must never become user-visible before it is checked.
                        continue
                    else:
                        full_answer = event["answer"]
                        raw_claims = event["raw_structured_claims"]
                        document_claims = event["document_claims"]
                        unattributed_content_present = event["unattributed_content_present"]
                online_succeeded = True
            except Exception as e:
                logger.warning(f"Online synthesis failed, falling back to offline templates: {e}")

        if not online_succeeded:
            answer_parts, raw_claims, document_claims = self._offline_synthesize(succeeded_calls)

            has_structured = len(raw_claims) > 0
            has_document = len(document_claims) > 0
            if has_structured and has_document:
                # DATA-003: conflicting-source disclosure — code-guaranteed, not merely
                # requested of the model, since this branch never involves a model at all.
                answer_parts.append(
                    "(Lưu ý: Dữ liệu trên được tổng hợp độc lập từ cả hệ thống số liệu tài chính thời gian thực và văn bản tài liệu công bố. Mọi sự sai khác về số liệu đều được bảo toàn theo đúng từng nguồn gốc tương ứng)."
                )

            if online_attempted:
                # Q-51: a provider failure must be visible to the reader, not served as if the
                # model had written the answer.
                answer_parts.insert(0, OFFLINE_TEMPLATE_DISCLOSURE)
            full_answer = " ".join(answer_parts)

        # Step 4: Run Attribution Verification
        verified = verify_attribution(
            answer=full_answer,
            raw_structured_claims=raw_claims,
            verified_document_claims=document_claims,
            dispatched_calls=dispatched_calls,
            tool_call_bound_reached=bound_reached,
            synthesis_mode="ONLINE" if online_succeeded else "OFFLINE_TEMPLATE",
            planner_mode=planner_mode,
            unattributed_content_present=unattributed_content_present,
        )

        # Preserve the SSE delta contract, but replay only the verified/rebuilt
        # answer. This closes the transient leak where unsafe model prose appeared
        # in the UI before the final event replaced it.
        words = verified.answer.split(" ")
        for i in range(0, len(words), 3):
            chunk_text = " ".join(words[i : i + 3]) + " "
            yield {"type": "delta", "textDelta": chunk_text}
            await asyncio.sleep(0.01)

        yield {
            "type": "final",
            "final": verified.model_dump(),
        }
