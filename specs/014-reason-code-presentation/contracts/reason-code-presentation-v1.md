# Contract: reason-code-presentation-v1

Presentation contract for every reason / quality / status code the Finvera
APIs emit to the frontend. Implemented by
`finvera-fe/src/shared/format/reason-codes.ts`; mirrored for five codes in
`finvera-ai/app/features/chat/service.py`.

## Rules

| # | Rule |
|---|---|
| P-1 | A known code renders its wording below as text; the code is retained on the element as `data-reason-code="<code>"` and `title="<code>"`. |
| P-2 | An unknown code renders **the code itself** as text (never blank, never dropped). |
| P-3 | Lists render one wording per code, joined with `"; "`. An empty list where a reason is expected renders `Dữ liệu chưa hoàn thiện`. |
| P-4 | Applicability cells: `NOT_APPLICABLE` → `Không áp dụng` and `MISSING` → `Không có dữ liệu`, each followed by ` — <wording>` when a reason code is present. `DEFINED` → no note. |
| P-5 | Data-status labels: `CURRENT` Hiện tại · `DELAYED` Chậm · `STALE` Cũ · `PARTIAL` Một phần · `UNAVAILABLE` Không có dữ liệu · other → the raw value. |
| P-6 | Wording states what the engine did or lacks; it never asserts a cause the code does not carry (e.g. `FUNDAMENTALS_STALE` says the report is old, not why). |
| P-7 | Derivation rule ids (`kbs-*`) keep the id inside the wording as the pointer to their contract. |

## Dictionary (v1)

| Code | Wording |
|---|---|
| MISSING_INDEX | Không có dữ liệu chỉ số được chấp nhận |
| MISSING_REFERENCE_LEVEL | Thiếu mức tham chiếu phiên trước — không tính % thay đổi |
| MISSING_INDEX_LEVEL | Bản ghi chỉ số không có mức điểm |
| NO_ACCEPTED_INDEX_DATA | Chưa có dữ liệu chỉ số nào được chấp nhận |
| MULTIPLE_ACCEPTED_SOURCES | Dữ liệu đến từ nhiều nguồn — không ghi nhãn nguồn |
| VNSTOCK_PRIVATE_PACKAGE | Nguồn: gói dữ liệu vnstock riêng của chủ sở hữu |
| UNRESOLVED_IDENTITY | Không khớp được mã với danh mục tham chiếu |
| MISSING_PRICE | Thiếu giá đóng cửa |
| MISSING_REFERENCE_PRICE | Thiếu giá đóng cửa phiên trước |
| MISSING_PRIOR_CLOSE | Thiếu giá đóng cửa phiên trước |
| BREADTH_NOT_AVAILABLE | Chưa có dữ liệu độ rộng thị trường cho phiên này |
| NO_ACTIVE_COMMON_EQUITY_UNIVERSE | Không có danh mục cổ phiếu để tính |
| NO_DAILY_BAR_HISTORY | Chưa có lịch sử giá ngày |
| NO_DAILY_BAR_HISTORY_FOR_LATEST_SESSION | Phiên gần nhất chưa có dữ liệu giá ngày |
| PROVIDER_AGGREGATE_BREADTH | Độ rộng lấy từ số tổng hợp của nhà cung cấp |
| MANDATORY_INPUT_UNAVAILABLE | Thiếu đầu vào bắt buộc (chỉ số hoặc độ rộng) |
| REQUIRED_INPUT_NOT_TIMELY_AVAILABLE | Đầu vào bắt buộc chưa đủ mới |
| INSUFFICIENT_COMPONENT_COMPLETENESS | Không đủ thành phần khả dụng để đánh giá |
| TREND_COMPONENT_UNAVAILABLE | Thiếu thành phần xu hướng |
| AGGREGATE_BREADTH_COMPONENT_UNAVAILABLE | Thiếu thành phần độ rộng tổng hợp |
| SOURCE_CONFLICT | Các nguồn dữ liệu mâu thuẫn — tạm giữ, không suy đoán |
| REGIME_NOT_AVAILABLE | Chưa có đánh giá trạng thái thị trường cho phiên này |
| REGIME_UNAVAILABLE | Chưa có đánh giá trạng thái thị trường |
| ASSESSMENT_QUALITY_NOT_FORECAST_PROBABILITY | Chất lượng đánh giá không phải xác suất dự báo |
| QUANTITATIVE_DECISION_SUPPORT_NOT_INVESTMENT_ADVICE | Hỗ trợ ra quyết định định lượng, không phải khuyến nghị đầu tư |
| QUANTITATIVE_DECISION_SUPPORT | Hỗ trợ ra quyết định định lượng, không phải khuyến nghị đầu tư |
| TCBS_STREAM_RECEIVE_TIME | Thời điểm là lúc nhận dữ liệu luồng TCBS, không phải giờ sàn |
| TCBS_STREAM_ORDERING_UNAVAILABLE | Luồng TCBS không đảm bảo thứ tự bản ghi |
| LIVE_OVERLAY_DISABLED | Lớp dữ liệu trực tiếp đang tắt |
| PROVIDER_AUTH_REQUIRED | Cần đăng nhập lại nhà cung cấp dữ liệu |
| PROVIDER_CONNECTIVITY_FAILED | Không kết nối được nhà cung cấp dữ liệu |
| TCBS_THESIS_LIVE_QUOTE | Giá từ luồng trực tiếp TCBS |
| PRICE_UNAVAILABLE | Chưa có giá được chấp nhận |
| PRICE_STALE | Giá đã cũ nhiều phiên |
| PRICE_DELAYED | Giá trễ một phiên |
| REFERENCE_PRICE_UNAVAILABLE | Thiếu giá tham chiếu (đóng cửa phiên trước) |
| REFERENCE_PRICE_INVALID | Giá tham chiếu không hợp lệ |
| PROFILE_UNAVAILABLE | Chưa có hồ sơ doanh nghiệp |
| PRICE_LIMITS_UNAVAILABLE | Chưa có giá trần/sàn và room |
| AT_CEILING | Giá trần |
| AT_FLOOR | Giá sàn |
| ADJUSTMENT_BASIS_UNAVAILABLE | Chưa có cơ sở điều chỉnh sự kiện doanh nghiệp — dùng giá chưa điều chỉnh |
| INSUFFICIENT_HISTORY | Chưa đủ lịch sử dữ liệu |
| NOT_APPLICABLE | Không áp dụng cho mã này |
| MISSING | Thiếu dữ liệu |
| VOLUME_UNAVAILABLE | Chưa có khối lượng |
| NO_BARS_AVAILABLE | Chưa có dữ liệu giá |
| FUNDAMENTALS_UNAVAILABLE | Chưa có báo cáo tài chính được ghi nhận |
| FUNDAMENTALS_DELAYED | Báo cáo tài chính trễ một kỳ |
| FUNDAMENTALS_STALE | Báo cáo tài chính đã cũ |
| ANNUAL_BASIS | Tính trên số liệu năm (chưa đủ 4 quý) |
| PROVIDER_TRAILING_EPS | EPS 12 tháng lấy theo số trailing của nhà cung cấp (không có EPS quý) |
| NO_DATA | Không có số liệu |
| NOT_REPORTED | Báo cáo không công bố chỉ tiêu này |
| NEGATIVE_OR_ZERO_PRIOR_EPS | EPS kỳ trước âm hoặc bằng 0 — không tính tăng trưởng |
| NEGATIVE_OR_ZERO_PRIOR_REVENUE | Doanh thu kỳ trước âm hoặc bằng 0 — không tính tăng trưởng |
| PROVIDER_REPORTED | Số liệu do nhà cung cấp công bố |
| kbs-trailing-ratio-as-annualized-v1 | Tỷ số 12 tháng của KBS dùng làm tỷ số năm hoá (quy tắc kbs-trailing-ratio-as-annualized-v1) |
| kbs-ebitda-margin-x-net-revenue-v1 | EBITDA suy ra = biên EBITDA × doanh thu thuần (quy tắc kbs-ebitda-margin-x-net-revenue-v1) |
| kbs-fcf-ocf-plus-capex-v1 | Dòng tiền tự do = dòng tiền HĐKD + chi đầu tư (quy tắc kbs-fcf-ocf-plus-capex-v1) |
| kbs-fcf-ocf-plus-capex-v2 | Dòng tiền tự do = dòng tiền HĐKD + chi đầu tư (quy tắc kbs-fcf-ocf-plus-capex-v2) |
| kbs-yearly-statement-labels-mirrored-v1 | Số liệu báo cáo năm đã được gán lại đúng năm tài chính — nhãn năm của nhà cung cấp bị đảo (quy tắc kbs-yearly-statement-labels-mirrored-v1) |
| HISTORY_BASIS_INSUFFICIENT | Lịch sử riêng chưa đủ 500 phiên nên không dùng |
| SECTOR_BASIS_INSUFFICIENT | Cơ sở ngành không đủ 8 mã nên không dùng |
| NO_COMPARISON_BASIS | Chưa đủ cơ sở so sánh (lịch sử riêng lẫn ngành) |
| CORE_METRIC_UNAVAILABLE | Không có chỉ số lõi (P/E và P/B) khả dụng |
| INSUFFICIENT_METRIC_COVERAGE | Độ phủ chỉ số dưới ngưỡng 50 % |
| REDUCED_METRIC_SET | Bộ chỉ số bị thu hẹp: một chỉ số lõi (P/E hoặc P/B) không áp dụng, kết luận dựa trên chỉ số lõi còn lại |
| HISTORY_SHARES_OUTSTANDING_HELD_CURRENT | Lịch sử riêng được tính với số cổ phiếu lưu hành hiện tại |
| MISSING_EPS | Thiếu EPS |
| NEGATIVE_OR_ZERO_EPS | EPS âm hoặc bằng 0 |
| MISSING_BVPS | Thiếu giá trị sổ sách trên cổ phiếu |
| NEGATIVE_OR_ZERO_BVPS | Giá trị sổ sách âm hoặc bằng 0 |
| MISSING_EBITDA | Thiếu EBITDA |
| NEGATIVE_OR_ZERO_EBITDA | EBITDA âm hoặc bằng 0 |
| MISSING_EV_INPUTS | Thiếu đầu vào EV (nợ, tiền, số cổ phiếu) |
| PE_NOT_DEFINED | P/E không xác định nên không tính PEG |
| MISSING_GROWTH | Thiếu tăng trưởng EPS |
| NEGATIVE_OR_ZERO_GROWTH | Tăng trưởng EPS âm hoặc bằng 0 |
| MISSING_DIVIDEND | Thiếu cổ tức |
| ZERO_PRICE | Giá bằng 0 |
| SHARES_OUTSTANDING_UNAVAILABLE | Thiếu số cổ phiếu lưu hành |
| SHARES_OUTSTANDING_UNVERIFIED | Số cổ phiếu lưu hành chưa được xác minh |
| OWN_HISTORY | Lịch sử riêng |
| SECTOR | Ngành |
| SECTOR_UNCLASSIFIED | Mã chưa được phân ngành |
| SHARES_OUTSTANDING_MISSING | Thiếu số cổ phiếu lưu hành — không tính vốn hoá |
| VALUATION_WITHHELD | Định giá chưa công bố |
| NO_CANDIDATES | Không có mã nào để đánh giá |
| SIGNAL | Có tín hiệu |
| NO_SIGNAL | Không có tín hiệu |
| WITHHELD | Tạm giữ do xung đột dữ liệu |
| INSUFFICIENT_RISK_FACTORS | Chưa đủ yếu tố rủi ro để tính điểm tổng hợp |
| INPUT_UNAVAILABLE | Thiếu dữ liệu đầu vào |
| TRAILING_AVERAGE_ATR_ZERO | ATR trung bình 250 phiên bằng 0 |
| HIGHEST_CLOSE_INVALID | Đỉnh giá 250 phiên không hợp lệ |
| POSITION_PRICE_UNAVAILABLE | Thiếu giá: ít nhất một mã chưa có giá được chấp nhận — tổng chưa bao gồm mã đó |
| POSITION_PRICE_DELAYED | Giá các vị thế trễ một phiên |
| POSITION_PRICE_STALE | Giá các vị thế đã cũ nhiều phiên |
| NO_POSITIONS | Danh mục chưa có vị thế |
| NO_SIGNALS_FOR_POSITIONS | Không có tín hiệu nào cho các mã đang nắm giữ |
| BENCHMARK_UNAVAILABLE | Chưa có giá VN-Index để so sánh |
| PARTIAL_DATA_GAP | Thiếu dữ liệu một phần tại điểm này |
| NET_CONTRIBUTED_CAPITAL_METHOD | Lợi suất tính theo vốn ròng đã góp |
| MISSING_SYMBOL | Mã chứng khoán không tồn tại trong danh mục tham chiếu |
| UNKNOWN_SYMBOL | Không nhận diện được mã chứng khoán |
| NO_FUNDAMENTAL_REPORT | Chưa có báo cáo tài chính |
| NO_VALUATION | Chưa có đánh giá định giá |

## Versioning

Adding wording for a new code is a v1 change (append). Rewording that changes
meaning, or changing a rule P-1…P-7, is v2.
