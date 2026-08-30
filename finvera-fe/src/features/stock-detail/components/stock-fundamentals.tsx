import type { StockFundamentals as StockFundamentalsData } from "../api/stock-detail";
import { dataStatusLabel, formatFundamentalValue, fundamentalMetricLabel } from "../format/stock-format";

/** Feature 009 FR-002: catalog codes grouped by category; unknown codes fall into "Khác". */
const METRIC_GROUPS: ReadonlyArray<{ title: string; codes: ReadonlySet<string> }> = [
  { title: "Kết quả kinh doanh", codes: new Set(["REVENUE", "REVENUE_TTM", "REVENUE_GROWTH_PERCENT", "GROSS_PROFIT", "OPERATING_PROFIT", "NET_PROFIT", "NET_PROFIT_TTM", "EBITDA", "EBITDA_TTM", "EPS", "EPS_TTM", "TRAILING_EPS", "EPS_GROWTH_PERCENT"]) },
  { title: "Khả năng sinh lời", codes: new Set(["ROE", "ROA", "ROE_TTM", "ROA_TTM", "ROCE", "GROSS_MARGIN", "OPERATING_MARGIN", "NET_MARGIN", "NIM", "COST_INCOME_RATIO"]) },
  { title: "Thanh khoản & khả năng trả nợ", codes: new Set(["CURRENT_RATIO", "QUICK_RATIO", "CASH_RATIO", "INTEREST_COVERAGE", "LOAN_TO_DEPOSIT"]) },
  { title: "Hiệu quả hoạt động", codes: new Set(["TOTAL_ASSET_TURNOVER", "INVENTORY_TURNOVER", "RECEIVABLES_TURNOVER"]) },
  { title: "Cơ cấu vốn", codes: new Set(["DEBT_TO_EQUITY", "DEBT_TO_ASSETS", "LIABILITIES_TO_EQUITY", "EQUITY_TO_ASSETS", "TOTAL_DEBT", "CASH_AND_EQUIVALENTS", "EQUITY_ATTRIBUTABLE_TO_PARENT", "BVPS", "TOTAL_ASSETS_GROWTH_PERCENT", "EQUITY_GROWTH_PERCENT"]) },
  { title: "Dòng tiền & cổ tức", codes: new Set(["FREE_CASH_FLOW", "DIVIDEND_PER_SHARE", "DIVIDEND_PER_SHARE_TTM", "DIVIDEND_YIELD"]) },
  { title: "Thị trường", codes: new Set(["BETA", "PS", "EV_EBITDA"]) },
];

function groupMetrics(metrics: StockFundamentalsData["metrics"]) {
  const groups = METRIC_GROUPS.map((g) => ({ title: g.title, metrics: metrics.filter((m) => g.codes.has(m.metricCode)) }));
  const known = new Set(METRIC_GROUPS.flatMap((g) => [...g.codes]));
  const other = metrics.filter((m) => !known.has(m.metricCode));
  if (other.length > 0) groups.push({ title: "Khác", metrics: other });
  return groups.filter((g) => g.metrics.length > 0);
}

export function StockFundamentals({ fundamentals }: { fundamentals: StockFundamentalsData }) {
  const period = fundamentals.period;

  return (
    <section aria-labelledby="stock-fundamentals-heading" className="stock-fundamentals-card">
      <header>
        <h2 id="stock-fundamentals-heading">Chỉ số cơ bản</h2>
        <span className={`status-pill ${fundamentals.meta.dataStatus.toLowerCase()}`}>
          {dataStatusLabel(fundamentals.meta.dataStatus)}
        </span>
      </header>

      {period ? (
        <div className="period-info">
          <span className="period-badge">{period.label}</span>
          <span className="meta-item">
            {period.reportKind === "CONSOLIDATED" ? "Hợp nhất" : "Riêng lẻ"} ·{" "}
            {period.auditStatus === "AUDITED"
              ? "Kiểm toán"
              : period.auditStatus === "REVIEWED"
              ? "Soát xét"
              : "Chưa kiểm toán"}{" "}
            · Đơn vị: {period.currency}
          </span>
          {period.restated && <span className="restated-badge">Điều chỉnh (Restated)</span>}
        </div>
      ) : (
        <p className="unavailable-msg">Chưa có báo cáo tài chính được ghi nhận.</p>
      )}

      {fundamentals.meta.reasonCodes.length > 0 && (
        <p className="reason-codes" role="note">
          Ghi chú: {fundamentals.meta.reasonCodes.join(", ")}
        </p>
      )}

      {groupMetrics(fundamentals.metrics).map((group) => (
        <div key={group.title} className="fundamental-group">
          <h3 className="fundamental-group-title">{group.title}</h3>
          <ul className="fundamental-grid">
            {group.metrics.map((metric) => (
              <li key={metric.metricCode} className={`fundamental-card ${metric.applicability.toLowerCase()}`}>
                <p className="metric-name">{fundamentalMetricLabel(metric.metricCode)}</p>
                <p className="metric-value">
                  {metric.applicability === "NOT_APPLICABLE"
                    ? `Không áp dụng${metric.reasonCode ? ` (${metric.reasonCode})` : ""}`
                    : metric.applicability === "MISSING"
                    ? `Không có dữ liệu${metric.reasonCode ? ` (${metric.reasonCode})` : ""}`
                    : formatFundamentalValue(metric.value, metric.unit)}
                </p>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </section>
  );
}
