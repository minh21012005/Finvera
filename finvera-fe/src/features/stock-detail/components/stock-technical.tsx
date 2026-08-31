import type { StockTechnical as StockTechnicalData } from "../api/stock-detail";
import { componentLabel, dataStatusLabel, formatIndicatorValue, indicatorLabel } from "../format/stock-format";

import { ApplicabilityNote, ReasonCodes } from "../../../shared/components/reason-codes";
import { reasonCodeLabel } from "../../../shared/format/reason-codes";
const DISCLAIMER_COPY: Record<string, string> = {
  QUANTITATIVE_DECISION_SUPPORT:
    "Chỉ báo kỹ thuật là hỗ trợ ra quyết định định lượng, không phải khuyến nghị đầu tư.",
};

export function StockTechnical({ technical }: { technical: StockTechnicalData }) {
  return (
    <section aria-labelledby="stock-technical-heading" className="stock-technical-card">
      <header>
        <h2 id="stock-technical-heading">Chỉ báo kỹ thuật</h2>
        <span className={`status-pill ${technical.meta.dataStatus.toLowerCase()}`}>
          {dataStatusLabel(technical.meta.dataStatus)}
        </span>
      </header>
      <p className="meta-item">
        Tính trên chuỗi giá {technical.adjustmentStatus === "ADJUSTED" || technical.adjustmentStatus === "PROVIDER_ADJUSTED" ? "đã điều chỉnh sự kiện doanh nghiệp" : "chưa điều chỉnh (RAW)"} ·
        Phiên bản quy tắc {technical.ruleVersion}
      </p>

      {technical.meta.reasonCodes.length > 0 && (
        <p className="reason-codes" role="note">
          <ReasonCodes prefix="Ghi chú: " codes={technical.meta.reasonCodes} />
        </p>
      )}

      <ul className="indicator-grid">
        {technical.indicators.map((indicator, idx) => (
          <li key={`${indicator.indicatorCode}-${idx}`} className={`indicator-card ${indicator.applicability.toLowerCase()}`}>
            <p className="indicator-name">{indicatorLabel(indicator.indicatorCode)}</p>

            {indicator.applicability === "MISSING" ? (
              <p role="status" className="unavailable-msg">
                <ApplicabilityNote applicability="MISSING" reasonCode={indicator.reasonCode} /> — cần {indicator.requiredBars} phiên, hiện có{" "}
                {indicator.availableBars}.
              </p>
            ) : (
              <dl>
                {indicator.components.map((component) => (
                  <div key={component.componentCode}>
                    <dt>{componentLabel(component.componentCode)}</dt>
                    <dd>
                      {component.applicability === "DEFINED"
                        ? formatIndicatorValue(component.value, component.unit)
                        : <ApplicabilityNote applicability={component.applicability} reasonCode={component.reasonCode} />}
                    </dd>
                  </div>
                ))}
              </dl>
            )}
          </li>
        ))}
      </ul>

      <p className="disclaimer" role="note">
        {DISCLAIMER_COPY[technical.disclaimerCode] ?? reasonCodeLabel(technical.disclaimerCode)}
      </p>
    </section>
  );
}
