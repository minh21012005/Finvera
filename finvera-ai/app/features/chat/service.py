import asyncio
import datetime
import json
import logging
import re
from typing import Any, AsyncIterator, Dict, List, Optional
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
from app.infrastructure.llm.generation import GeminiGenerationAdapter

logger = logging.getLogger(__name__)


class PriorTurn(BaseModel):
    question: str
    answer: str


class OrchestrateAskRequest(BaseModel):
    ownerId: uuid.UUID
    question: str = Field(..., min_length=1, max_length=2000)
    symbol: Optional[str] = None
    priorTurns: List[PriorTurn] = Field(default_factory=list, max_length=10)


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
        Determines the candidate tools to call based on semantic intent and keywords.
        """
        q_upper = question.upper()
        proposed = []

        stop_words = {
            "RSI", "MAC", "SMA", "EMA", "VND", "USD", "EPS", "ROE", "TOP",
            "THE", "AND", "FOR", "GET", "NAY", "HOM", "BAN", "CHO", "CON",
            "MAI", "XEM", "GIA", "DAN", "MUC", "TIN", "DOC", "BAI", "HOI",
            "LAM", "SAO", "KHI", "NAO", "VAN", "ROI", "VOI", "TAI", "DAY"
        }

        # Extract 3-letter stock symbol if present
        matched_symbol = symbol.upper() if symbol else None
        if not matched_symbol:
            # First check if explicit ticker is capitalized in original question
            raw_symbols = re.findall(r"\b[A-Z]{3}\b", question)
            tickers = [s for s in raw_symbols if s not in stop_words]
            if tickers:
                matched_symbol = tickers[0]
            else:
                # Check for "cổ phiếu XXX" or "mã XXX"
                kw_match = re.search(r"(?:CỔ PHIẾU|MÃ)\s+([A-Z]{3})\b", q_upper)
                if kw_match and kw_match.group(1) not in stop_words:
                    matched_symbol = kw_match.group(1)

        # 1. MARKET Overview
        if any(k in q_upper for k in ("THỊ TRƯỜNG", "VN-INDEX", "VNINDEX", "VN30", "ĐỘ RỘNG", "MARKET", "XU HƯỚNG CHUNG")):
            proposed.append({"tool_name": "MARKET", "arguments": {}})

        # 2. STOCK Price
        if matched_symbol and any(k in q_upper for k in ("GIÁ", "STOCK", "THỊ GIÁ", "CỔ PHIẾU", "KHỚP LỆNH")):
            proposed.append({"tool_name": "STOCK", "arguments": {"symbol": matched_symbol}})

        # 3. TECHNICAL Analysis
        if matched_symbol and any(k in q_upper for k in ("KỸ THUẬT", "RSI", "MACD", "MA20", "MA50", "CHỈ BÁO", "TÍN HIỆU", "DÒNG TIỀN", "TECHNICAL", "KHÁNG CỰ", "HỖ TRỢ")):
            proposed.append({"tool_name": "TECHNICAL", "arguments": {"symbol": matched_symbol}})

        # 4. FUNDAMENTAL Analysis
        if matched_symbol and any(k in q_upper for k in ("CƠ BẢN", "TÀI CHÍNH", "EPS", "ROE", "DOANH THU", "LỢI NHUẬN", "BÁO CÁO TÀI CHÍNH", "BCTC", "FUNDAMENTAL", "KẾT QUẢ KINH DOANH")):
            proposed.append({"tool_name": "FUNDAMENTAL", "arguments": {"symbol": matched_symbol}})

        # 5. VALUATION
        if matched_symbol and any(k in q_upper for k in ("ĐỊNH GIÁ", "P/E", "P/B", "PE", "PB", "VALUATION", "ĐẮT", "RẺ")):
            proposed.append({"tool_name": "VALUATION", "arguments": {"symbol": matched_symbol}})

        # 6. PORTFOLIO
        if any(k in q_upper for k in ("DANH MỤC", "TÀI SẢN", "PORTFOLIO", "VỊ THẾ", "HIỆU SUẤT ĐẦU TƯ", "LÃI LỖ")):
            sub_type = "ANALYTICS" if any(k in q_upper for k in ("HIỆU SUẤT", "RỦI RO", "ANALYTICS")) else "POSITIONS"
            proposed.append({"tool_name": "PORTFOLIO", "arguments": {"sub_type": sub_type}})

        # 7. NEWS
        if any(k in q_upper for k in ("TIN TỨC", "TIN MỚI", "BÀI BÁO", "NEWS", "SỰ KIỆN")):
            args: Dict[str, Any] = {"limit": 5}
            if matched_symbol:
                args["symbol"] = matched_symbol
            proposed.append({"tool_name": "NEWS", "arguments": args})

        # 8. RESEARCH_RAG (Specific document questions)
        if any(k in q_upper for k in ("BÁO CÁO THƯỜNG NIÊN", "TÀI LIỆU", "PDF", "TRÍCH XUẤT", "TRÍCH LỤC", "ĐỌC ĐƯỢC", "THEO TÀI LIỆU", "ĐẠI HỘI CỔ ĐÔNG", "ĐHCĐ", "NGHỊ QUYẾT", "CÔNG BỐ", "THUYẾT MINH", "VĂN BẢN")):
            args = {"query": question, "top_k": 5}
            if matched_symbol:
                args["symbol"] = matched_symbol
            proposed.append({"tool_name": "RESEARCH_RAG", "arguments": args})

        # 9. STOCK Summary fallback
        if matched_symbol and not proposed:
            proposed.append({"tool_name": "STOCK", "arguments": {"symbol": matched_symbol}})

        return proposed

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
        # Step 1: Plan candidate tools
        proposed_calls = self.plan_tools(request.question, request.symbol)

        # If no tool could be planned, emit refusal
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

        # Step 2: Dispatch tools
        dispatched_calls: List[DispatchedToolCall] = []
        limit = settings.analyst_max_tool_calls
        bound_reached = False

        for i, call_req in enumerate(proposed_calls, start=1):
            if len(dispatched_calls) >= limit:
                bound_reached = True
                break

            dispatched = await self.dispatcher.dispatch_single_tool(
                sequence_no=i,
                tool_name_raw=call_req["tool_name"],
                arguments_raw=call_req["arguments"],
                session_owner_id=request.ownerId,
            )
            dispatched_calls.append(dispatched)

            # Stream tool_call progress event
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
            # All dispatched tools failed
            refused_result = verify_attribution(
                answer="Không thể lấy dữ liệu từ các công cụ để trả lời câu hỏi.",
                raw_structured_claims=[],
                raw_document_claims=[],
                dispatched_calls=dispatched_calls,
                tool_call_bound_reached=bound_reached,
                explicit_refusal=True,
            )
            yield {
                "type": "final",
                "final": refused_result.model_dump(),
            }
            return

        # Generate synthesis text & claims
        answer_parts: List[str] = []
        raw_claims: List[RawStructuredClaim] = []
        raw_docs: List[Dict[str, Any]] = []

        for call in succeeded_calls:
            seq = call.sequence_no
            data = call.response_data or {}
            if call.tool_name == ToolName.MARKET:
                vn_val = str(data.get("vnIndexValue", "0"))
                vn_chg = str(data.get("vnIndexChangePercent", "0"))
                adv = str(data.get("advancers", 0))
                dec = str(data.get("decliners", 0))
                answer_parts.append(
                    f"Chỉ số VN-INDEX hiện đạt {vn_val} điểm ({vn_chg}%), với độ rộng thị trường ghi nhận {adv} mã tăng và {dec} mã giảm."
                )
                raw_claims.append(RawStructuredClaim(claimText=f"VN-INDEX {vn_val} điểm", sequenceNo=seq, fieldPath="vnIndexValue", claimedValue=vn_val))
                raw_claims.append(RawStructuredClaim(claimText=f"Độ biến động {vn_chg}%", sequenceNo=seq, fieldPath="vnIndexChangePercent", claimedValue=vn_chg))
                raw_claims.append(RawStructuredClaim(claimText=f"{adv} mã tăng", sequenceNo=seq, fieldPath="advancers", claimedValue=adv))

            elif call.tool_name == ToolName.STOCK:
                sym = data.get("symbol", "")
                price = str(data.get("price", "0"))
                chg = str(data.get("changePercent", "0"))
                answer_parts.append(f"Cổ phiếu {sym} hiện giao dịch ở mức giá {price} ({chg}%).")
                raw_claims.append(RawStructuredClaim(claimText=f"Giá {price}", sequenceNo=seq, fieldPath="price", claimedValue=price))
                raw_claims.append(RawStructuredClaim(claimText=f"Thay đổi {chg}%", sequenceNo=seq, fieldPath="changePercent", claimedValue=chg))

            elif call.tool_name == ToolName.TECHNICAL:
                sym = data.get("symbol", "")
                signal = data.get("signal") or {}
                direction = signal.get("direction", "NEUTRAL")
                answer_parts.append(f"Tín hiệu kỹ thuật của {sym} đang ở trạng thái {direction}.")
                raw_claims.append(RawStructuredClaim(claimText=f"Trạng thái {direction}", sequenceNo=seq, fieldPath="signal.direction", claimedValue=direction))

            elif call.tool_name == ToolName.FUNDAMENTAL:
                sym = data.get("symbol", "")
                eps = data.get("eps")
                roe = data.get("roe")
                period = data.get("period", "ANNUAL")
                part = f"Dữ liệu cơ bản kỳ {period} của {sym}"
                if eps:
                    part += f", EPS đạt {eps}"
                    raw_claims.append(RawStructuredClaim(claimText=f"EPS {eps}", sequenceNo=seq, fieldPath="eps", claimedValue=str(eps)))
                if roe:
                    part += f", ROE đạt {roe}%"
                    raw_claims.append(RawStructuredClaim(claimText=f"ROE {roe}%", sequenceNo=seq, fieldPath="roe", claimedValue=str(roe)))
                answer_parts.append(part + ".")

            elif call.tool_name == ToolName.VALUATION:
                sym = data.get("symbol", "")
                cls = data.get("classification", "FAIR_VALUE")
                pe = data.get("peRatio")
                part = f"Định giá {sym} được phân loại ở mức {cls}"
                if pe:
                    part += f" với P/E là {pe}"
                    raw_claims.append(RawStructuredClaim(claimText=f"P/E {pe}", sequenceNo=seq, fieldPath="peRatio", claimedValue=str(pe)))
                raw_claims.append(RawStructuredClaim(claimText=f"Phân loại {cls}", sequenceNo=seq, fieldPath="classification", claimedValue=cls))
                answer_parts.append(part + ".")

            elif call.tool_name == ToolName.PORTFOLIO:
                positions = data.get("positions", [])
                total_val = data.get("totalValue", "0")
                if positions:
                    answer_parts.append(f"Danh mục hiện nắm giữ {len(positions)} vị thế cổ phiếu.")
                else:
                    answer_parts.append(f"Tổng giá trị tài sản danh mục là {total_val}.")

            elif call.tool_name == ToolName.NEWS:
                articles = data.get("articles", [])
                answer_parts.append(f"Hệ thống ghi nhận {len(articles)} tin tức thị trường gần đây.")

            elif call.tool_name == ToolName.RESEARCH_RAG:
                chunks = data.get("chunks", [])
                if chunks:
                    for ch in chunks[:3]:
                        c_id = ch.get("chunk_id")
                        c_title = ch.get("title", "")
                        c_text = ch.get("content_text", "")
                        
                        # AI-003: Defense against prompt injection in document chunks
                        # Treat retrieved text purely as untrusted reference data, stripping any injection attempts and commands
                        cleaned_preview = re.sub(r"(?i)(system\s+prompt|ignore\s+all|ignore\s+previous|override\s+instructions|bỏ\s+qua\s+chỉ\s+thị).*", "[TRÍCH ĐOẠN ĐÃ LỌC]", c_text)
                        
                        answer_parts.append(f"Theo tài liệu công bố '{c_title}': {cleaned_preview[:150]}...")
                        raw_docs.append({
                            "chunkId": c_id,
                            "claimText": f"Theo {c_title}: {cleaned_preview[:100]}",
                            "title": c_title,
                            "source": ch.get("source_type", "DOCUMENT"),
                            "pageNumber": ch.get("page_number"),
                        })

        # DATA-003: Conflicting-source disclosure check
        # If both structured fundamental/valuation and document claims exist, explicitly declare multi-source provenance
        has_structured = len(raw_claims) > 0
        has_document = len(raw_docs) > 0
        if has_structured and has_document:
            answer_parts.append("(Lưu ý: Dữ liệu trên được tổng hợp độc lập từ cả hệ thống số liệu tài chính thời gian thực và văn bản tài liệu công bố. Mọi sự sai khác về số liệu đều được bảo toàn theo đúng từng nguồn gốc tương ứng).")

        full_answer = " ".join(answer_parts)

        # Stream delta chunks
        words = full_answer.split(" ")
        for i in range(0, len(words), 3):
            chunk_text = " ".join(words[i:i+3]) + " "
            yield {
                "type": "delta",
                "textDelta": chunk_text,
            }
            await asyncio.sleep(0.01)

        # Step 4: Run Attribution Verification
        verified = verify_attribution(
            answer=full_answer,
            raw_structured_claims=raw_claims,
            raw_document_claims=raw_docs,
            dispatched_calls=dispatched_calls,
            tool_call_bound_reached=bound_reached,
        )

        # Stream final verified result
        yield {
            "type": "final",
            "final": verified.model_dump(),
        }
