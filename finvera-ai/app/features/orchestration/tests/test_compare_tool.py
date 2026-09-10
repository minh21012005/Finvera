import uuid
import pytest

from app.features.chat.service import ChatOrchestrationService
from app.features.orchestration.allowlist import ToolName, validate_tool_call
from app.features.orchestration.dispatch import DispatchedToolCall


def test_plan_tools_compare_routing():
    service = ChatOrchestrationService()

    # Query 1: Direct comparison between SSI and VND
    q1 = "So sánh SSI và VND xem mã nào tốt hơn để đầu tư"
    tools1 = service.plan_tools(q1, symbol=None)
    assert len(tools1) == 1
    assert tools1[0]["tool_name"] == "COMPARE"
    assert tools1[0]["arguments"]["symbols"] == ["SSI", "VND"]

    # Query 2: Three-way comparison
    q2 = "Giữa HPG, HSG và NKG mã nào đang có định giá hấp dẫn hơn?"
    tools2 = service.plan_tools(q2, symbol=None)
    assert len(tools2) == 1
    assert tools2[0]["tool_name"] == "COMPARE"
    assert tools2[0]["arguments"]["symbols"] == ["HPG", "HSG", "NKG"]

    # Query 3: Currency VND shouldn't trigger comparison when only 1 stock
    q3 = "Giá cổ phiếu HPG hôm nay là 28000 VND"
    tools3 = service.plan_tools(q3, symbol=None)
    assert not any(t["tool_name"] == "COMPARE" for t in tools3)
    assert any(t["tool_name"] == "STOCK" for t in tools3)

    # Query 4: Lowercase tickers with 'vs' operator
    q4 = "ssi vs vnd mã nào tiềm năng hơn?"
    tools4 = service.plan_tools(q4, symbol=None)
    assert len(tools4) == 1
    assert tools4[0]["tool_name"] == "COMPARE"
    assert tools4[0]["arguments"]["symbols"] == ["SSI", "VND"]

    # Query 5: 'Nên mua ... hay ...'
    q5 = "Nên mua HPG hay HSG?"
    tools5 = service.plan_tools(q5, symbol=None)
    assert len(tools5) == 1
    assert tools5[0]["tool_name"] == "COMPARE"
    assert tools5[0]["arguments"]["symbols"] == ["HPG", "HSG"]


def test_compare_tool_validation():
    owner_id = uuid.uuid4()

    # Valid comparison with 2 symbols
    is_valid, tool_name, parsed_args, err = validate_tool_call(
        tool_name_raw="COMPARE",
        arguments_raw={"symbols": ["ssi", "vnd"]},
        session_owner_id=owner_id,
    )
    assert is_valid is True
    assert tool_name == ToolName.COMPARE
    assert parsed_args.symbols == ["SSI", "VND"]
    assert parsed_args.owner_id == owner_id

    # Invalid comparison with only 1 symbol
    is_valid_single, _, _, err_single = validate_tool_call(
        tool_name_raw="COMPARE",
        arguments_raw={"symbols": ["SSI"]},
        session_owner_id=owner_id,
    )
    assert is_valid_single is False
    assert "at least 2" in err_single or "INVALID_ARGUMENTS" in err_single


def test_offline_synthesize_compare_generates_markdown_table_and_claims():
    service = ChatOrchestrationService()
    owner_id = uuid.uuid4()

    mock_compare_data = {
        "items": [
            {
                "symbol": "SSI",
                "companyName": "CTCP Chứng khoán SSI",
                "exchange": "HOSE",
                "sectorName": "Dịch vụ tài chính",
                "price": "32500",
                "changePercent": "1.2",
                "pe": "18.5",
                "pb": "1.8",
                "valuationClassification": "FAIR",
                "roe": "14.5",
                "revenueGrowthPercent": "22.5",
                "rsi14": "58.2",
                "primarySignal": "MOMENTUM (BULLISH)",
            },
            {
                "symbol": "VND",
                "companyName": "CTCP Chứng khoán VNDIRECT",
                "exchange": "HOSE",
                "sectorName": "Dịch vụ tài chính",
                "price": "18200",
                "changePercent": "-0.5",
                "pe": "14.2",
                "pb": "1.3",
                "valuationClassification": "ATTRACTIVE",
                "roe": "11.8",
                "revenueGrowthPercent": "15.0",
                "rsi14": "46.5",
                "primarySignal": None,
            },
        ],
        "asOf": "2026-09-10T10:00:00Z",
    }

    call = DispatchedToolCall(
        sequence_no=1,
        tool_name=ToolName.COMPARE,
        arguments={"owner_id": owner_id, "symbols": ["SSI", "VND"]},
        status="SUCCEEDED",
        response_data=mock_compare_data,
    )

    answer_parts, raw_claims, doc_claims = service._offline_synthesize([call])
    full_answer = "\n".join(answer_parts)

    # Verify table structure
    assert "| Chỉ số / Tiêu chí | SSI | VND |" in full_answer
    assert "SSI" in full_answer
    assert "VND" in full_answer
    assert "18,5" in full_answer
    assert "14,2" in full_answer
    assert "14,5" in full_answer
    assert "Hấp dẫn" in full_answer or "ATTRACTIVE" in full_answer
    assert "không cấu thành khuyến nghị mua/bán" in full_answer

    # Verify structured claims for attribution
    field_paths = [c.fieldPath for c in raw_claims]
    assert "items[0].price" in field_paths
    assert "items[0].pe" in field_paths
    assert "items[0].roe" in field_paths
    assert "items[1].price" in field_paths
    assert "items[1].pe" in field_paths
    assert "items[1].valuationClassification" in field_paths
