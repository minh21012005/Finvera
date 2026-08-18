import type { StockFundamentals as StockFundamentalsData } from "../api/stock-detail";
import { dataStatusLabel, formatFundamentalValue, fundamentalMetricLabel } from "../format/stock-format";

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

      <ul className="fundamental-grid">
        {fundamentals.metrics.map((metric) => (
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
    </section>
  );
}
