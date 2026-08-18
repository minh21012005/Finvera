import type { StockValuation as StockValuationData } from "../api/stock-detail";
import { dataStatusLabel, formatDecimal, valuationLabel, valuationMetricLabel } from "../format/stock-format";

const DISCLAIMER_COPY: Record<string, string> = {
  QUANTITATIVE_DECISION_SUPPORT:
    "Định giá tương đối là công cụ hỗ trợ ra quyết định định lượng, không phải dự báo giá hay khuyến nghị đầu tư.",
};

export function StockValuation({ valuation }: { valuation: StockValuationData }) {
  const published = valuation.published && valuation.classification !== null;

  return (
    <section aria-labelledby="stock-valuation-heading" className="stock-valuation-card">
      <header>
        <h2 id="stock-valuation-heading">Định giá tương đối</h2>
        <span className={`status-pill ${valuation.meta.dataStatus.toLowerCase()}`}>
          {dataStatusLabel(valuation.meta.dataStatus)}
        </span>
      </header>
      <p className="meta-item">
        Quy tắc định giá {valuation.ruleVersion}
      </p>

      {published ? (
        <div className="valuation-summary">
          <div className="valuation-badge-container">
            <span className={`valuation-label-badge ${valuation.classification?.toLowerCase()}`}>
              {valuationLabel(valuation.classification)}
            </span>
            <div className="valuation-scores">
              <p className="score-main">
                Điểm đắt/rẻ: <strong>{valuation.displayedScore}</strong>
                <span className="score-max"> / 100</span>
              </p>
              {valuation.confidence !== null && (
                <p className="confidence-label">
                  Độ hoàn thiện dữ liệu: <strong>{valuation.confidence}%</strong>
                </p>
              )}
            </div>
          </div>

          <div className="basis-disclosure">
            <p className="meta-item">
              Cơ sở so sánh đã sử dụng:{" "}
              {valuation.basis.usedOwnHistory && (
                <span className="basis-tag">
                  Lịch sử riêng ({valuation.basis.historyPointCount ?? 0} phiên)
                </span>
              )}
              {valuation.basis.usedSector && (
                <span className="basis-tag">
                  Ngành ({valuation.basis.sectorConstituentCount ?? 0} mã cùng ngành)
                </span>
              )}
            </p>
          </div>

          <ul className="valuation-metric-grid">
            {valuation.metrics.map((m) => (
              <li key={m.metricCode} className={`valuation-metric-item ${m.applicability.toLowerCase()}`}>
                <p className="metric-name">{valuationMetricLabel(m.metricCode)}</p>
                <p className="metric-value">
                  {m.applicability === "NOT_APPLICABLE"
                    ? `Không áp dụng${m.reasonCode ? ` (${m.reasonCode})` : ""}`
                    : m.applicability === "MISSING"
                    ? `Không có dữ liệu${m.reasonCode ? ` (${m.reasonCode})` : ""}`
                    : formatDecimal(m.value)}
                </p>
                {m.ownHistoryPercentile && (
                  <p className="percentile-label">
                    Vị thế lịch sử: Phân vị {formatDecimal(m.ownHistoryPercentile)}%
                  </p>
                )}
                {m.sectorPercentile && (
                  <p className="percentile-label">
                    Vị thế ngành: Phân vị {formatDecimal(m.sectorPercentile)}%
                  </p>
                )}
              </li>
            ))}
          </ul>
        </div>
      ) : (
        <div className="withheld-notice" role="status">
          <p className="withheld-title">Định giá tạm thời chưa thể công bố</p>
          <p className="reason-codes">
            Lý do: {valuation.meta.reasonCodes.length > 0
              ? valuation.meta.reasonCodes.map(r => r === "NO_COMPARISON_BASIS" ? "Chưa đủ cơ sở so sánh (NO_COMPARISON_BASIS)" : r).join(", ")
              : "Dữ liệu chưa hoàn thiện"}
          </p>
        </div>
      )}

      <p className="disclaimer" role="note">
        {DISCLAIMER_COPY[valuation.disclaimerCode] ?? valuation.disclaimerCode}
      </p>
    </section>
  );
}
