"""Feature 015 (specs/015-analyst-e2e-on-real-data): degraded-mode honesty and offline templates
that survive attribution verification for every tool type — found on the real stack on
2026-08-31 (Q-50 refusals after successful TECHNICAL/SCREENING/PORTFOLIO calls, Q-51 silent
template answers under Gemini 429, Q-52 "hôm nay" on DELAYED data)."""
import uuid
from unittest.mock import AsyncMock

import pytest

from app.features.chat.service import (
    ChatOrchestrationService,
    OFFLINE_TEMPLATE_DISCLOSURE,
    OrchestrateAskRequest,
    fmt_vi,
)
from app.features.orchestration.allowlist import ToolName
from app.features.orchestration.attribution import get_nested_value, verify_attribution
from app.features.orchestration.dispatch import BackendToolClient, DispatchedToolCall, OrchestrationDispatcher
from app.infrastructure.llm.generation import quota_retry_delay_seconds


def _service() -> ChatOrchestrationService:
    return ChatOrchestrationService(dispatcher=OrchestrationDispatcher(tool_client=AsyncMock(spec=BackendToolClient)))


def _call(seq: int, tool: ToolName, data: dict, args: dict | None = None) -> DispatchedToolCall:
    return DispatchedToolCall(sequence_no=seq, tool_name=tool, arguments=args or {}, status="SUCCEEDED", response_data=data)


TECHNICAL_HPG = {
    "symbol": "HPG",
    "indicators": {
        "MA20": {"indicatorCode": "MA20", "applicability": "DEFINED", "components": [{"componentCode": "VALUE", "value": 21820.0}]},
        "RSI14": {"indicatorCode": "RSI14", "applicability": "DEFINED", "components": [{"componentCode": "VALUE", "value": 58.12}]},
        "MACD": {"indicatorCode": "MACD", "applicability": "MISSING", "components": []},
    },
    "signal": None,
    "signals": [],
    "riskFactors": [],
    "asOf": "2026-08-31T09:28:00Z",
    "dataStatus": "DELAYED",
    "raw": {"tradingDate": "2026-08-28"},
}
SCREENING = {"matches": [{"symbol": "ABT", "matchedValues": {"pe": "4.79"}}, {"symbol": "ACE", "matchedValues": {"pe": "6.10"}}],
             "totalMatches": 2, "asOf": "2026-08-31T09:29:00Z"}
PORTFOLIO_EMPTY = {"totalValue": "0", "totalUnrealizedPnlPercent": "0", "asOf": "2026-08-31T09:29:05Z", "raw": {}}
PORTFOLIO_POSITIONS = {"positions": [], "asOf": "2026-08-31T09:29:05Z"}
PORTFOLIO_NON_EMPTY = {
    "positions": [{
        "symbol": "FPT",
        "quantity": "10",
        "marketValue": "1300000",
        "unrealizedPnlPercent": "12.5000",
        "allocation": "0.250000000000",
    }],
    "asOf": "2026-09-02T03:00:00Z",
}


def test_offline_templates_yield_verifiable_claims_for_every_tool_type():
    # Q-50: the old TECHNICAL template cited `signal.direction` (absent) and PORTFOLIO/SCREENING
    # had no claims at all, so verify_attribution refused answers whose tools had succeeded.
    svc = _service()
    calls = [_call(1, ToolName.TECHNICAL, TECHNICAL_HPG, {"symbol": "HPG"}),
             _call(2, ToolName.SCREENING, SCREENING),
             _call(3, ToolName.PORTFOLIO, PORTFOLIO_EMPTY, {"sub_type": "ANALYTICS"}),
             _call(4, ToolName.PORTFOLIO, PORTFOLIO_POSITIONS)]
    parts, raw_claims, _ = svc._offline_synthesize(calls)
    verified = verify_attribution(" ".join(parts), raw_claims, [], calls, False)

    assert verified.refused is False
    by_seq = {}
    for c in verified.structuredClaims:
        by_seq.setdefault(c.sequenceNo, []).append(c.fieldPath)
    assert "indicators.MA20.components[0].value" in by_seq[1]
    assert "signals.length" in by_seq[1]
    assert by_seq[2] == ["totalMatches"]
    assert by_seq[3] == ["totalValue"]
    assert by_seq[4] == ["positions.length"]
    text = " ".join(parts)
    assert "MA20 21.820" in text                      # vi-VN formatting
    assert "theo phiên 2026-08-28" in text and "trễ một phiên" in text   # Q-52: date + status, no "hôm nay"
    assert "không có vị thế nào" in text              # honest no-data instead of a refusal
    assert "ABT, ACE" in text


def test_nested_lookup_supports_list_steps_and_length():
    data = {"signals": [{"direction": "LONG"}], "matches": []}
    assert get_nested_value(data, "signals[0].direction") == (True, "LONG")
    assert get_nested_value(data, "signals.0.direction") == (True, "LONG")
    assert get_nested_value(data, "signals.length") == (True, 1)
    assert get_nested_value(data, "matches.length") == (True, 0)
    assert get_nested_value(data, "signals[3].direction") == (False, None)


def test_fmt_vi_formats_for_vietnamese_readers():
    assert fmt_vi("62300.000000", 0) == "62.300"
    assert fmt_vi("1832.120000") == "1.832,12"
    assert fmt_vi("-0.320000") == "-0,32"
    assert fmt_vi("20.220000") == "20,22"
    assert fmt_vi("abc") == "abc"


def test_offline_portfolio_accepts_contracted_string_units_without_invented_risk_band():
    svc = _service()
    call = _call(1, ToolName.PORTFOLIO, PORTFOLIO_NON_EMPTY)
    parts, raw_claims, _ = svc._offline_synthesize([call])
    answer = " ".join(parts)
    verified = verify_attribution(answer, raw_claims, [], [call], False)

    assert verified.refused is False
    assert "25%" in answer
    assert "12,5%" in answer
    assert "rủi ro tập trung" not in answer.lower()
    assert {claim.fieldPath for claim in raw_claims} >= {
        "positions[0].allocation", "positions[0].unrealizedPnlPercent",
    }


def test_offline_templates_do_not_create_hidden_threshold_classifications():
    svc = _service()
    calls = [
        _call(1, ToolName.MARKET, {
            "vnIndexValue": "1280", "vnIndexChangePercent": "1.2",
            "advancers": 300, "decliners": 100, "asOf": "2026-09-02T03:00:00Z",
        }),
        _call(2, ToolName.FUNDAMENTAL, {
            "symbol": "FPT", "period": "TTM", "roe": "22",
            "asOf": "2026-09-02T03:00:00Z",
        }),
    ]
    parts, _, _ = svc._offline_synthesize(calls)
    answer = " ".join(parts).lower()

    assert "phe tăng chiếm ưu thế" not in answer
    assert "tăng tích cực" not in answer
    assert "rất cao" not in answer


def test_quota_retry_delay_only_for_bounded_429_hints():
    e429 = RuntimeError("429 RESOURCE_EXHAUSTED. {'error': {'code': 429, 'details': [{'retryDelay': '9s'}]}}")
    assert quota_retry_delay_seconds(e429) == 10.0
    daily = RuntimeError("429 RESOURCE_EXHAUSTED ... 'retryDelay': '3600s'")
    assert quota_retry_delay_seconds(daily) is None
    daily_short_hint = RuntimeError("429 RESOURCE_EXHAUSTED 'quotaId': 'GenerateRequestsPerDayPerProjectPerModel-FreeTier' 'retryDelay': '59s'")
    assert quota_retry_delay_seconds(daily_short_hint) is None  # observed 2026-08-31: 2 x 60 s waits per question for nothing
    assert quota_retry_delay_seconds(RuntimeError("401 UNAUTHENTICATED")) is None
    assert quota_retry_delay_seconds(RuntimeError("503 UNAVAILABLE. This model is currently experiencing high demand")) == 5.0


@pytest.mark.asyncio
async def test_final_event_discloses_offline_template_and_keyword_planner():
    # Q-51: provider failure -> the answer opens with the disclosure and the modes are reported.
    mock_client = AsyncMock(spec=BackendToolClient)
    mock_client.execute_tool.return_value = (True, {"symbol": "HPG", "price": "28500", "changePercent": "0", "volume": 1,
                                                   "asOf": "2026-08-31T09:00:00Z", "dataStatus": "CURRENT", "raw": {}}, None)

    class FlakyAdapter:
        is_online = True

        async def propose_tool_calls(self, **_):
            return None  # provider failed -> keyword fallback

        async def generate_stream_raw(self, *_a, **_k):
            raise RuntimeError("429 RESOURCE_EXHAUSTED")
            yield  # pragma: no cover

    svc = ChatOrchestrationService(dispatcher=OrchestrationDispatcher(tool_client=mock_client), llm_adapter=FlakyAdapter())
    events = [e async for e in svc.orchestrate_stream(OrchestrateAskRequest(ownerId=uuid.uuid4(), question="Giá cổ phiếu HPG?", symbol="HPG"))]
    final = next(e for e in events if e["type"] == "final")["final"]

    assert final["refused"] is False
    assert final["synthesisMode"] == "OFFLINE_TEMPLATE"
    assert final["plannerMode"] == "KEYWORD_FALLBACK"
    assert final["answer"].startswith(OFFLINE_TEMPLATE_DISCLOSURE)


def test_citation_tags_with_list_index_paths_are_extracted_and_stripped():
    # Q-56: seen on 2026-08-31 — "[T2:metrics[0].ownHistoryPercentile=32.866666666667]" stayed in the answer.
    from app.features.chat.service import extract_structured_claims_from_text, strip_synthesis_tags
    text = ("P/E ở bách phân vị 32,87 % [T2:metrics[0].ownHistoryPercentile=32.866666666667]. "
            "Danh sách vị thế trống [T1:positions.length=0].")
    claims = extract_structured_claims_from_text(text)
    assert [(c.sequenceNo, c.fieldPath, c.claimedValue) for c in claims] == [
        (2, "metrics[0].ownHistoryPercentile", "32.866666666667"), (1, "positions.length", "0")]
    clean = strip_synthesis_tags(text)
    assert "[T" not in clean and "]" not in clean
    assert clean == "P/E ở bách phân vị 32,87 %. Danh sách vị thế trống."
