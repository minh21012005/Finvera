import pytest
from app.features.document.classification import classify_news_article, classify_news_offline


@pytest.mark.asyncio
async def test_classify_earnings_positive_news():
    headline = "FPT công bố lợi nhuận quý 4/2025 tăng trưởng kỷ lục 25%"
    body = "Tập đoàn FPT vừa công bố kết quả kinh doanh với doanh thu và lợi nhuận hợp nhất đều vượt kế hoạch năm, khối công nghệ và chuyển đổi số bứt phá mạnh mẽ."
    
    result = await classify_news_article(headline, body, symbol="FPT")
    assert result.category == "COMPANY"
    assert result.sentiment == "POSITIVE"
    assert result.sector == "Technology"
    assert "FPT" in result.entities


@pytest.mark.asyncio
async def test_classify_macro_news():
    headline = "Ngân hàng Nhà nước duy trì điều hành chính sách tiền tệ linh hoạt, ổn định lãi suất"
    body = "Theo báo cáo vĩ mô, SBV định hướng kiểm soát lạm phát và giữ mặt bằng lãi suất cho vay ở mức hợp lý nhằm hỗ trợ tăng trưởng kinh tế."
    
    result = await classify_news_article(headline, body)
    assert result.category == "MACRO"
    assert result.sentiment == "NEUTRAL" or result.sentiment == "POSITIVE"


@pytest.mark.asyncio
async def test_classify_corp_action_news():
    headline = "HPG chốt quyền chi trả cổ tức năm 2025 bằng tiền mặt tỷ lệ 15%"
    body = "Đại hội đồng cổ đông Hòa Phát thông qua phương án phân phối lợi nhuận và chi trả cổ tức đợt 1."
    
    result = await classify_news_article(headline, body, symbol="HPG")
    assert result.category == "COMPANY"
    assert "HPG" in result.entities


@pytest.mark.asyncio
async def test_classify_negative_news():
    headline = "Doanh nghiệp thép ghi nhận quý 4 thua lỗ nặng nề do giá nguyên liệu tăng cao"
    body = "Hoạt động sản xuất kinh doanh gặp nhiều khó khăn, biên lợi nhuận suy giảm mạnh và xuất khẩu thụt lùi đáng kể."
    
    result = await classify_news_article(headline, body)
    assert result.sentiment == "NEGATIVE"
    assert result.sector == "Steel"


def test_classify_offline_market_news():
    headline = "Tổng quan VN-Index phiên giao dịch đầu tuần"
    body = "Thị trường biến động giằng co với thanh khoản duy trì ở mức trung bình."

    result = classify_news_offline(headline, body)
    assert result.category == "MARKET"
    assert result.sentiment == "NEUTRAL"


def test_classify_offline_undetermined_when_no_signal_and_no_symbol():
    """FR-014/AI-004: a field the classifier has no basis to determine is undetermined
    (None here, mapped to Applicability.MISSING by finvera-be), never a guessed default."""
    headline = "Ghi chú nội bộ"
    body = "Bản tin không đề cập nội dung cụ thể nào cần xử lý thêm."

    result = classify_news_offline(headline, body, symbol=None)
    assert result.category is None
    assert result.impactLevel is None


def test_classify_offline_conflicting_sentiment_is_undetermined():
    """A genuine tie between positive and negative signal is conflicting evidence, not
    neutral evidence, and MUST be undetermined rather than defaulted to NEUTRAL."""
    headline = "Kết quả kinh doanh trái chiều"
    body = "Hoạt động kinh doanh có phần khởi sắc nhưng vẫn đối mặt nhiều khó khăn trong quý này."

    result = classify_news_offline(headline, body)
    assert result.sentiment is None


def test_classify_offline_company_category_from_symbol_association():
    """A symbol association is real evidence of COMPANY category even with no topical
    keyword match, so it MUST NOT collapse to undetermined."""
    headline = "Thông báo giao dịch nội bộ"
    body = "Doanh nghiệp công bố thông tin theo quy định hiện hành."

    result = classify_news_offline(headline, body, symbol="FPT")
    assert result.category == "COMPANY"
