import uuid
import pytest
from unittest.mock import AsyncMock

from app.features.chat.service import ChatOrchestrationService
from app.features.orchestration.allowlist import ToolName, validate_tool_call
from app.features.orchestration.dispatch import (
    BackendToolClient,
    DispatchedToolCall,
    OrchestrationDispatcher,
)


def test_plan_tools_strategy_scan_routing():
    service = ChatOrchestrationService()

    # Query 1: General short-term trading signal query
    q1 = "Lọc cho tôi trên thị trường có mã cổ phiếu nào có tín hiệu tốt để trading ngắn hạn ko"
    tools1 = service.plan_tools(q1, symbol=None)
    assert any(t["tool_name"] == "STRATEGY_SCAN" for t in tools1)
    strat_tool1 = next(t for t in tools1 if t["tool_name"] == "STRATEGY_SCAN")
    assert strat_tool1["arguments"]["strategyCode"] == "MOMENTUM"

    # Query 2: Breakout query
    q2 = "Tìm mã cổ phiếu có tín hiệu breakout vượt đỉnh"
    tools2 = service.plan_tools(q2, symbol=None)
    strat_tool2 = next(t for t in tools2 if t["tool_name"] == "STRATEGY_SCAN")
    assert strat_tool2["arguments"]["strategyCode"] == "BREAKOUT"

    # Query 3: Pullback query
    q3 = "Có mã nào đang pullback điều chỉnh để lướt sóng ngắn hạn không"
    tools3 = service.plan_tools(q3, symbol=None)
    strat_tool3 = next(t for t in tools3 if t["tool_name"] == "STRATEGY_SCAN")
    assert strat_tool3["arguments"]["strategyCode"] == "PULLBACK"


def test_strategy_scan_validation():
    owner_id = uuid.uuid4()
    is_valid, tool_name, parsed_args, err = validate_tool_call(
        tool_name_raw="STRATEGY_SCAN",
        arguments_raw={"strategyCode": "BREAKOUT", "limit": 5},
        session_owner_id=owner_id,
    )

    assert is_valid is True
    assert tool_name == ToolName.STRATEGY_SCAN
    assert parsed_args.strategy_code == "BREAKOUT"
    assert parsed_args.limit == 5
    assert parsed_args.owner_id == owner_id


def test_offline_synthesize_strategy_scan_with_matches():
    service = ChatOrchestrationService()
    owner_id = uuid.uuid4()

    mock_scan_data = {
        "strategyCode": "BREAKOUT",
        "matches": [
            {
                "symbol": "HPG",
                "companyName": "Tập đoàn Hòa Phát",
                "exchange": "HOSE",
                "signal": {
                    "strategyCode": "BREAKOUT",
                    "direction": "BULLISH",
                    "entryLow": "28000.0",
                    "entryHigh": "28500.0",
                    "stopLoss": "27000.0",
                    "target1": "30000.0",
                    "target2": "32000.0",
                    "riskReward": "2.0",
                    "riskScore": 25,
                    "riskLevel": "LOW",
                    "signalStrength": "STRONG",
                },
            }
        ],
        "totalMatchCount": 1,
    }

    call = DispatchedToolCall(
        sequence_no=1,
        tool_name=ToolName.STRATEGY_SCAN,
        arguments={"owner_id": owner_id, "strategy_code": "BREAKOUT", "limit": 5},
        status="SUCCEEDED",
        response_data=mock_scan_data,
    )

    answer_parts, raw_claims, doc_claims = service._offline_synthesize([call])
    full_answer = "\n".join(answer_parts)

    assert "BREAKOUT" in full_answer
    assert "1 mã" in full_answer
    assert "HPG" in full_answer
    assert "28.000" in full_answer or "28000" in full_answer
    assert "27.000" in full_answer or "27000" in full_answer
    assert "30.000" in full_answer or "30000" in full_answer
    assert "không cam kết lợi nhuận" in full_answer

    # Verify structured claims for attribution
    field_paths = [c.fieldPath for c in raw_claims]
    assert "totalMatchCount" in field_paths
    assert "strategyCode" in field_paths
    assert "matches[0].signal.entryLow" in field_paths
    assert "matches[0].signal.stopLoss" in field_paths


def test_offline_synthesize_strategy_scan_zero_matches():
    service = ChatOrchestrationService()
    owner_id = uuid.uuid4()

    mock_scan_data = {
        "strategyCode": "MOMENTUM",
        "matches": [],
        "totalMatchCount": 0,
    }

    call = DispatchedToolCall(
        sequence_no=1,
        tool_name=ToolName.STRATEGY_SCAN,
        arguments={"owner_id": owner_id, "strategy_code": "MOMENTUM", "limit": 5},
        status="SUCCEEDED",
        response_data=mock_scan_data,
    )

    answer_parts, raw_claims, doc_claims = service._offline_synthesize([call])
    full_answer = "\n".join(answer_parts)

    assert "0 mã" in full_answer
    assert "Không có mã nào" in full_answer or "không có mã nào" in full_answer
