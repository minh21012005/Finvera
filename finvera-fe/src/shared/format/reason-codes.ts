/**
 * Contract reason-code-presentation-v1 (specs/014-reason-code-presentation/contracts).
 *
 * The APIs disclose *why* a value is missing, withheld or computed on another basis as an
 * engine identifier (`ANNUAL_BASIS`, `NO_COMPARISON_BASIS`, `kbs-fcf-ocf-plus-capex-v2`, …).
 * This module is the single place that turns such a code into wording for the reader.
 *
 *  P-1  known code  → wording; the code itself is kept on the element (data-reason-code, title)
 *  P-2  unknown code → the code, verbatim — never blank, never dropped
 *  P-3  lists       → one wording per code joined with "; "; empty → explicit fallback sentence
 *  P-4  applicability cells → "Không áp dụng" / "Không có dữ liệu" + " — <wording>"
 *  P-5  data status → fixed five labels, raw value as fallback
 *
 * Adding a code = appending a row here AND in reason-codes.test.ts (the inventory list).
 */
export const REASON_CODE_LABELS: Readonly<Record<string, string>> = {
  // market — index
  MISSING_INDEX: "Không có dữ liệu chỉ số được chấp nhận",
  MISSING_REFERENCE_LEVEL: "Thiếu mức tham chiếu phiên trước — không tính % thay đổi",
  MISSING_INDEX_LEVEL: "Bản ghi chỉ số không có mức điểm",
  NO_ACCEPTED_INDEX_DATA: "Chưa có dữ liệu chỉ số nào được chấp nhận",
  MULTIPLE_ACCEPTED_SOURCES: "Dữ liệu đến từ nhiều nguồn — không ghi nhãn nguồn",
  VNSTOCK_PRIVATE_PACKAGE: "Nguồn: gói dữ liệu vnstock riêng của chủ sở hữu",
  // market — breadth
  UNRESOLVED_IDENTITY: "Không khớp được mã với danh mục tham chiếu",
  MISSING_PRICE: "Thiếu giá đóng cửa",
  MISSING_REFERENCE_PRICE: "Thiếu giá đóng cửa phiên trước",
  MISSING_PRIOR_CLOSE: "Thiếu giá đóng cửa phiên trước",
  BREADTH_NOT_AVAILABLE: "Chưa có dữ liệu độ rộng thị trường cho phiên này",
  NO_ACTIVE_COMMON_EQUITY_UNIVERSE: "Không có danh mục cổ phiếu để tính",
  NO_DAILY_BAR_HISTORY: "Chưa có lịch sử giá ngày",
  NO_DAILY_BAR_HISTORY_FOR_LATEST_SESSION: "Phiên gần nhất chưa có dữ liệu giá ngày",
  PROVIDER_AGGREGATE_BREADTH: "Độ rộng lấy từ số tổng hợp của nhà cung cấp",
  // market — regime
  MANDATORY_INPUT_UNAVAILABLE: "Thiếu đầu vào bắt buộc (chỉ số hoặc độ rộng)",
  REQUIRED_INPUT_NOT_TIMELY_AVAILABLE: "Đầu vào bắt buộc chưa đủ mới",
  INSUFFICIENT_COMPONENT_COMPLETENESS: "Không đủ thành phần khả dụng để đánh giá",
  TREND_COMPONENT_UNAVAILABLE: "Thiếu thành phần xu hướng",
  AGGREGATE_BREADTH_COMPONENT_UNAVAILABLE: "Thiếu thành phần độ rộng tổng hợp",
  SOURCE_CONFLICT: "Các nguồn dữ liệu mâu thuẫn — tạm giữ, không suy đoán",
  REGIME_NOT_AVAILABLE: "Chưa có đánh giá trạng thái thị trường cho phiên này",
  REGIME_UNAVAILABLE: "Chưa có đánh giá trạng thái thị trường",
  ASSESSMENT_QUALITY_NOT_FORECAST_PROBABILITY: "Chất lượng đánh giá không phải xác suất dự báo",
  QUANTITATIVE_DECISION_SUPPORT_NOT_INVESTMENT_ADVICE: "Hỗ trợ ra quyết định định lượng, không phải khuyến nghị đầu tư",
  QUANTITATIVE_DECISION_SUPPORT: "Hỗ trợ ra quyết định định lượng, không phải khuyến nghị đầu tư",
  // market — live / provider
  TCBS_STREAM_RECEIVE_TIME: "Thời điểm là lúc nhận dữ liệu luồng TCBS, không phải giờ sàn",
  TCBS_STREAM_ORDERING_UNAVAILABLE: "Luồng TCBS không đảm bảo thứ tự bản ghi",
  LIVE_OVERLAY_DISABLED: "Lớp dữ liệu trực tiếp đang tắt",
  PROVIDER_AUTH_REQUIRED: "Cần đăng nhập lại nhà cung cấp dữ liệu",
  PROVIDER_CONNECTIVITY_FAILED: "Không kết nối được nhà cung cấp dữ liệu",
  TCBS_THESIS_LIVE_QUOTE: "Giá từ luồng trực tiếp TCBS",
  // stock overview / price
  PRICE_UNAVAILABLE: "Chưa có giá được chấp nhận",
  PRICE_STALE: "Giá đã cũ nhiều phiên",
  PRICE_DELAYED: "Giá trễ một phiên",
  REFERENCE_PRICE_UNAVAILABLE: "Thiếu giá tham chiếu (đóng cửa phiên trước)",
  REFERENCE_PRICE_INVALID: "Giá tham chiếu không hợp lệ",
  PROFILE_UNAVAILABLE: "Chưa có hồ sơ doanh nghiệp",
  PRICE_LIMITS_UNAVAILABLE: "Chưa có giá trần/sàn và room",
  AT_CEILING: "Giá trần",
  AT_FLOOR: "Giá sàn",
  ADJUSTMENT_BASIS_UNAVAILABLE: "Chưa có cơ sở điều chỉnh sự kiện doanh nghiệp — dùng giá chưa điều chỉnh",
  // technical
  INSUFFICIENT_HISTORY: "Chưa đủ lịch sử dữ liệu",
  NOT_APPLICABLE: "Không áp dụng cho mã này",
  MISSING: "Thiếu dữ liệu",
  VOLUME_UNAVAILABLE: "Chưa có khối lượng",
  NO_BARS_AVAILABLE: "Chưa có dữ liệu giá",
  // fundamentals
  FUNDAMENTALS_UNAVAILABLE: "Chưa có báo cáo tài chính được ghi nhận",
  FUNDAMENTALS_DELAYED: "Báo cáo tài chính trễ một kỳ",
  FUNDAMENTALS_STALE: "Báo cáo tài chính đã cũ",
  ANNUAL_BASIS: "Tính trên số liệu năm (chưa đủ 4 quý)",
  PROVIDER_TRAILING_EPS: "EPS 12 tháng lấy theo số trailing của nhà cung cấp (không có EPS quý)",
  NO_DATA: "Không có số liệu",
  NOT_REPORTED: "Báo cáo không công bố chỉ tiêu này",
  NEGATIVE_OR_ZERO_PRIOR_EPS: "EPS kỳ trước âm hoặc bằng 0 — không tính tăng trưởng",
  NEGATIVE_OR_ZERO_PRIOR_REVENUE: "Doanh thu kỳ trước âm hoặc bằng 0 — không tính tăng trưởng",
  PROVIDER_REPORTED: "Số liệu do nhà cung cấp công bố",
  "kbs-trailing-ratio-as-annualized-v1":
    "Tỷ số 12 tháng của KBS dùng làm tỷ số năm hoá (quy tắc kbs-trailing-ratio-as-annualized-v1)",
  "kbs-ebitda-margin-x-net-revenue-v1":
    "EBITDA suy ra = biên EBITDA × doanh thu thuần (quy tắc kbs-ebitda-margin-x-net-revenue-v1)",
  "kbs-fcf-ocf-plus-capex-v1": "Dòng tiền tự do = dòng tiền HĐKD + chi đầu tư (quy tắc kbs-fcf-ocf-plus-capex-v1)",
  "kbs-fcf-ocf-plus-capex-v2": "Dòng tiền tự do = dòng tiền HĐKD + chi đầu tư (quy tắc kbs-fcf-ocf-plus-capex-v2)",
  "vci-trailing-eps-parent-profit-over-shares-v1": "EPS 12 tháng = tổng LN cổ đông công ty mẹ 4 quý gần nhất ÷ số CP lưu hành kỳ đó (quy tắc vci-trailing-eps-parent-profit-over-shares-v1)",
  "vci-bvps-parent-equity-over-shares-v1": "Giá trị sổ sách/CP = vốn chủ sở hữu của công ty mẹ ÷ số CP lưu hành kỳ đó (quy tắc vci-bvps-parent-equity-over-shares-v1)",
  "vci-roe-parent-profit-over-average-equity-v1": "ROE = LN cổ đông công ty mẹ ÷ vốn chủ sở hữu bình quân (quy tắc vci-roe-parent-profit-over-average-equity-v1)",
  "vci-roe-parent-profit-over-average-equity-v1-end": "ROE = LN cổ đông công ty mẹ ÷ vốn chủ sở hữu cuối kỳ — chưa đủ số dư đầu kỳ để lấy bình quân (quy tắc vci-roe…-v1-end)",
  "vci-roa-net-profit-over-average-assets-v1": "ROA = LN sau thuế ÷ tổng tài sản bình quân (quy tắc vci-roa-net-profit-over-average-assets-v1)",
  "vci-roa-net-profit-over-average-assets-v1-end": "ROA = LN sau thuế ÷ tổng tài sản cuối kỳ — chưa đủ số dư đầu kỳ để lấy bình quân (quy tắc vci-roa…-v1-end)",
  "vci-margin-v1": "Biên lợi nhuận = lợi nhuận ÷ doanh thu thuần × 100 (quy tắc vci-margin-v1)",
  "vci-debt-to-equity-v1": "Nợ vay/Vốn chủ = (vay ngắn hạn + vay dài hạn) ÷ vốn chủ sở hữu công ty mẹ (quy tắc vci-debt-to-equity-v1)",
  "vci-fcf-ocf-plus-capex-v1": "Dòng tiền tự do = dòng tiền HĐKD + chi đầu tư TSCĐ (quy tắc vci-fcf-ocf-plus-capex-v1)",
  "vci-ebitda-operating-profit-plus-da-v1": "EBITDA = lợi nhuận hoạt động + khấu hao (quy tắc vci-ebitda-operating-profit-plus-da-v1)",
  SOURCE_SUPERSEDED: "Bản ghi từ nguồn cũ đã được thay bằng nguồn mới có nhãn kỳ đã kiểm chứng",
  SOURCE_RETIRED: "Bản ghi từ nguồn cũ đã bị thu hồi vì nhãn kỳ của nguồn đó không đáng tin; không có bản thay thế cho kỳ này",
  "kbs-yearly-statement-labels-mirrored-v1":
    "Số liệu báo cáo năm đã được gán lại đúng năm tài chính — nhãn năm của nhà cung cấp bị đảo (quy tắc kbs-yearly-statement-labels-mirrored-v1)",
  // valuation
  HISTORY_BASIS_INSUFFICIENT: "Lịch sử riêng chưa đủ 500 phiên nên không dùng",
  SECTOR_BASIS_INSUFFICIENT: "Cơ sở ngành không đủ 8 mã nên không dùng",
  NO_COMPARISON_BASIS: "Chưa đủ cơ sở so sánh (lịch sử riêng lẫn ngành)",
  CORE_METRIC_UNAVAILABLE: "Không có chỉ số lõi (P/E và P/B) khả dụng",
  INSUFFICIENT_METRIC_COVERAGE: "Độ phủ chỉ số dưới ngưỡng 50 %",
  REDUCED_METRIC_SET:
    "Bộ chỉ số bị thu hẹp: một chỉ số lõi (P/E hoặc P/B) không áp dụng, kết luận dựa trên chỉ số lõi còn lại",
  HISTORY_SHARES_OUTSTANDING_HELD_CURRENT: "Lịch sử riêng được tính với số cổ phiếu lưu hành hiện tại",
  MISSING_EPS: "Thiếu EPS",
  NEGATIVE_OR_ZERO_EPS: "EPS âm hoặc bằng 0",
  MISSING_BVPS: "Thiếu giá trị sổ sách trên cổ phiếu",
  NEGATIVE_OR_ZERO_BVPS: "Giá trị sổ sách âm hoặc bằng 0",
  MISSING_EBITDA: "Thiếu EBITDA",
  NEGATIVE_OR_ZERO_EBITDA: "EBITDA âm hoặc bằng 0",
  MISSING_EV_INPUTS: "Thiếu đầu vào EV (nợ, tiền, số cổ phiếu)",
  PE_NOT_DEFINED: "P/E không xác định nên không tính PEG",
  MISSING_GROWTH: "Thiếu tăng trưởng EPS",
  NEGATIVE_OR_ZERO_GROWTH: "Tăng trưởng EPS âm hoặc bằng 0",
  MISSING_DIVIDEND: "Thiếu cổ tức",
  ZERO_PRICE: "Giá bằng 0",
  SHARES_OUTSTANDING_UNAVAILABLE: "Thiếu số cổ phiếu lưu hành",
  SHARES_OUTSTANDING_UNVERIFIED: "Số cổ phiếu lưu hành chưa được xác minh",
  OWN_HISTORY: "Lịch sử riêng",
  SECTOR: "Ngành",
  // screener
  SECTOR_UNCLASSIFIED: "Mã chưa được phân ngành",
  SHARES_OUTSTANDING_MISSING: "Thiếu số cổ phiếu lưu hành — không tính vốn hoá",
  VALUATION_WITHHELD: "Định giá chưa công bố",
  NO_CANDIDATES: "Không có mã nào để đánh giá",
  // signal / risk
  SIGNAL: "Có tín hiệu",
  NO_SIGNAL: "Không có tín hiệu",
  WITHHELD: "Tạm giữ do xung đột dữ liệu",
  INSUFFICIENT_RISK_FACTORS: "Chưa đủ yếu tố rủi ro để tính điểm tổng hợp",
  INPUT_UNAVAILABLE: "Thiếu dữ liệu đầu vào",
  TRAILING_AVERAGE_ATR_ZERO: "ATR trung bình 250 phiên bằng 0",
  HIGHEST_CLOSE_INVALID: "Đỉnh giá 250 phiên không hợp lệ",
  // portfolio / watchlist
  POSITION_PRICE_UNAVAILABLE: "Thiếu giá: ít nhất một mã chưa có giá được chấp nhận — tổng chưa bao gồm mã đó",
  POSITION_PRICE_DELAYED: "Giá các vị thế trễ một phiên",
  POSITION_PRICE_STALE: "Giá các vị thế đã cũ nhiều phiên",
  NO_POSITIONS: "Danh mục chưa có vị thế",
  NO_SIGNALS_FOR_POSITIONS: "Không có tín hiệu nào cho các mã đang nắm giữ",
  BENCHMARK_UNAVAILABLE: "Chưa có giá VN-Index để so sánh",
  PARTIAL_DATA_GAP: "Thiếu dữ liệu một phần tại điểm này",
  NET_CONTRIBUTED_CAPITAL_METHOD: "Lợi suất tính theo vốn ròng đã góp",
  MISSING_SYMBOL: "Mã chứng khoán không tồn tại trong danh mục tham chiếu",
  // analyst tool bridge
  UNKNOWN_SYMBOL: "Không nhận diện được mã chứng khoán",
  NO_FUNDAMENTAL_REPORT: "Chưa có báo cáo tài chính",
  NO_VALUATION: "Chưa có đánh giá định giá",
};

/** P-3 fallback when a reason is expected but the list is empty. */
export const NO_REASON_GIVEN = "Dữ liệu chưa hoàn thiện";

export function isKnownReasonCode(code: string): boolean {
  return Object.prototype.hasOwnProperty.call(REASON_CODE_LABELS, code);
}

/** P-1 / P-2: wording for a known code, the code itself otherwise. */
export function reasonCodeLabel(code: string): string {
  return REASON_CODE_LABELS[code] ?? code;
}

/** P-3: one wording per code, joined; explicit sentence when the list is empty. */
export function describeReasonCodes(codes: readonly string[] | null | undefined, fallback: string = NO_REASON_GIVEN): string {
  if (!codes || codes.length === 0) return fallback;
  return codes.map(reasonCodeLabel).join("; ");
}

export type ApplicabilityValue = "DEFINED" | "NOT_APPLICABLE" | "MISSING";

/** P-4: the text for a metric / indicator / factor cell that carries no value. */
export function applicabilityNote(applicability: ApplicabilityValue | string, reasonCode: string | null | undefined): string | null {
  if (applicability === "DEFINED") return null;
  const head = applicability === "NOT_APPLICABLE" ? "Không áp dụng" : "Không có dữ liệu";
  return reasonCode ? `${head} — ${reasonCodeLabel(reasonCode)}` : head;
}

const DATA_STATUS_LABELS: Readonly<Record<string, string>> = {
  CURRENT: "Hiện tại",
  DELAYED: "Chậm",
  STALE: "Cũ",
  PARTIAL: "Một phần",
  UNAVAILABLE: "Không có dữ liệu",
};

/** P-5: data-status wording; the raw value if the API ever adds a state. */
export function dataStatusLabel(status: string): string {
  return DATA_STATUS_LABELS[status] ?? status;
}
