import asyncio
from dataclasses import dataclass, field
import datetime
import logging
import time
from typing import Any, Dict, List, Optional, Tuple
import uuid
import httpx

from app.core.settings import settings
from app.features.orchestration.allowlist import (
    ToolName,
    validate_tool_call,
)

logger = logging.getLogger(__name__)


@dataclass
class DispatchedToolCall:
    sequence_no: int
    tool_name: ToolName
    arguments: Dict[str, Any]
    status: str  # "SUCCEEDED" or "FAILED"
    failure_reason: Optional[str] = None
    latency_ms: int = 0
    called_at: str = field(default_factory=lambda: datetime.datetime.now(datetime.timezone.utc).isoformat())
    response_data: Optional[Dict[str, Any]] = None


class BackendToolClient:
    """
    HTTP client for calling finvera-be's /internal/v1/tools/* endpoints (SEC-002, research R-004).
    """

    def __init__(self, base_url: Optional[str] = None, api_key: Optional[str] = None, timeout: Optional[float] = None):
        self.base_url = (base_url or settings.backend_internal_api_url).rstrip("/")
        self.api_key = api_key or settings.internal_api_key
        self.timeout = timeout or settings.analyst_tool_call_timeout_seconds

    async def execute_tool(
        self,
        tool_name: ToolName,
        arguments: Dict[str, Any],
        owner_id: uuid.UUID,
    ) -> Tuple[bool, Optional[Dict[str, Any]], Optional[str]]:
        """
        Calls the appropriate endpoint on finvera-be.
        Returns (success, response_dict, failure_reason).
        """
        headers = {
            "X-Internal-Api-Key": self.api_key,
            "Content-Type": "application/json",
        }
        params = {"ownerId": str(owner_id)}

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                if tool_name == ToolName.MARKET:
                    resp = await client.get(f"{self.base_url}/tools/market/overview", params=params, headers=headers)
                elif tool_name == ToolName.STOCK:
                    symbol = arguments.get("symbol", "").upper()
                    resp = await client.get(f"{self.base_url}/tools/stocks/{symbol}", params=params, headers=headers)
                elif tool_name == ToolName.TECHNICAL:
                    symbol = arguments.get("symbol", "").upper()
                    resp = await client.get(f"{self.base_url}/tools/stocks/{symbol}/technical", params=params, headers=headers)
                elif tool_name == ToolName.FUNDAMENTAL:
                    symbol = arguments.get("symbol", "").upper()
                    resp = await client.get(f"{self.base_url}/tools/stocks/{symbol}/fundamentals", params=params, headers=headers)
                elif tool_name == ToolName.VALUATION:
                    symbol = arguments.get("symbol", "").upper()
                    resp = await client.get(f"{self.base_url}/tools/stocks/{symbol}/valuation", params=params, headers=headers)
                elif tool_name == ToolName.PORTFOLIO:
                    sub_type = str(arguments.get("sub_type", "POSITIONS")).upper()
                    endpoint = "analytics" if sub_type == "ANALYTICS" else "positions"
                    resp = await client.get(f"{self.base_url}/tools/portfolios/{endpoint}", params=params, headers=headers)
                elif tool_name == ToolName.NEWS:
                    news_params = dict(params)
                    if arguments.get("symbol"):
                        news_params["symbol"] = str(arguments["symbol"]).upper()
                    if arguments.get("limit"):
                        news_params["limit"] = str(arguments["limit"])
                    resp = await client.get(f"{self.base_url}/tools/research/news", params=news_params, headers=headers)
                elif tool_name == ToolName.SCREENING:
                    resp = await client.post(
                        f"{self.base_url}/tools/screener/executions",
                        params=params,
                        json=arguments.get("filters", {}),
                        headers=headers,
                    )
                elif tool_name == ToolName.RESEARCH_RAG:
                    body = {
                        "query": arguments.get("query", ""),
                        "filters": {
                            "symbol": arguments.get("symbol"),
                            "documentType": arguments.get("document_type"),
                            "newsCategory": arguments.get("news_category"),
                            "dateFrom": arguments.get("date_from"),
                            "dateTo": arguments.get("date_to"),
                        },
                    }
                    resp = await client.post(
                        f"{self.base_url}/tools/research/retrieve",
                        params=params,
                        json=body,
                        headers=headers,
                    )
                else:
                    return False, None, f"Unsupported tool: {tool_name}"

                if resp.is_success:
                    return True, resp.json(), None
                else:
                    return False, None, f"HTTP_{resp.status_code}: {resp.text[:200]}"

        except httpx.TimeoutException:
            return False, None, f"TIMEOUT: Tool call exceeded {self.timeout}s"
        except Exception as e:
            return False, None, f"NETWORK_ERROR: {str(e)}"


class OrchestrationDispatcher:
    """
    Executes the multi-tool dispatch loop per orchestration-v1 rules.
    """

    def __init__(self, tool_client: Optional[BackendToolClient] = None):
        self.tool_client = tool_client or BackendToolClient()

    async def dispatch_single_tool(
        self,
        sequence_no: int,
        tool_name_raw: str,
        arguments_raw: Dict[str, Any],
        session_owner_id: uuid.UUID,
    ) -> DispatchedToolCall:
        """
        Validates and dispatches a single tool call per U-1, U-2, and per-call timeout.
        """
        start_time = time.perf_counter()
        now_iso = datetime.datetime.now(datetime.timezone.utc).isoformat()

        # U-1 & U-2 Validation
        is_valid, tool_name, parsed_args, err = validate_tool_call(
            tool_name_raw=tool_name_raw,
            arguments_raw=arguments_raw,
            session_owner_id=session_owner_id,
        )

        if not is_valid or tool_name is None:
            latency_ms = int((time.perf_counter() - start_time) * 1000)
            return DispatchedToolCall(
                sequence_no=sequence_no,
                tool_name=tool_name or ToolName.MARKET,
                arguments=arguments_raw,
                status="FAILED",
                failure_reason=err or "VALIDATION_FAILED",
                latency_ms=latency_ms,
                called_at=now_iso,
                response_data=None,
            )

        args_dict = parsed_args.model_dump() if parsed_args else arguments_raw

        # Dispatch — every tool, including RESEARCH_RAG, goes out to finvera-be's
        # /internal/v1/tools/* surface (research R-004). RESEARCH_RAG specifically MUST
        # NOT read finvera-ai's own retrieval module directly: Qdrant/RankedChunk never
        # carries chunk content_text (data-model.md), only finvera-be's Postgres-backed
        # RetrievalService can resolve it, so routing this tool the same way as the
        # other eight is not just consistency — it's the only path that can return real
        # passage text for synthesis.
        success, response_dict, failure_reason = await self.tool_client.execute_tool(
            tool_name=tool_name,
            arguments=args_dict,
            owner_id=session_owner_id,
        )

        latency_ms = int((time.perf_counter() - start_time) * 1000)
        return DispatchedToolCall(
            sequence_no=sequence_no,
            tool_name=tool_name,
            arguments=args_dict,
            status="SUCCEEDED" if success else "FAILED",
            failure_reason=failure_reason,
            latency_ms=latency_ms,
            called_at=now_iso,
            response_data=response_dict,
        )

    async def execute_dispatch_plan(
        self,
        proposed_calls: List[Dict[str, Any]],
        session_owner_id: uuid.UUID,
        max_tool_calls: Optional[int] = None,
    ) -> Tuple[List[DispatchedToolCall], bool]:
        """
        Executes a sequence of proposed tool calls up to max_tool_calls.
        Returns (dispatched_calls, tool_call_bound_reached).
        """
        limit = max_tool_calls or settings.analyst_max_tool_calls
        dispatched: List[DispatchedToolCall] = []
        bound_reached = False

        for i, call_req in enumerate(proposed_calls, start=1):
            if len(dispatched) >= limit:
                bound_reached = True
                break

            tool_name = call_req.get("tool_name") or call_req.get("name") or ""
            args = call_req.get("arguments") or call_req.get("args") or {}

            result = await self.dispatch_single_tool(
                sequence_no=i,
                tool_name_raw=tool_name,
                arguments_raw=args,
                session_owner_id=session_owner_id,
            )
            dispatched.append(result)

        return dispatched, bound_reached
