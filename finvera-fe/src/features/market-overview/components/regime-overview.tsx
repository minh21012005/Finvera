import type { DataStatus, MarketRegime } from "../api/market-overview";
import { formatAsOf } from "../format/market-format";

/** Displays the backend's deterministic regime result; it never calculates a market signal in the browser. */
export function RegimeOverview({ regime }: { regime: MarketRegime }) {
  const canPresentAssessment = regime.label !== null && regime.score !== null && regime.confidence !== null;

  return (
    <section aria-labelledby="market-regime-heading" aria-label={`Trạng thái thị trường: ${statusLabel(regime.dataStatus)}`}>
      <h2 id="market-regime-heading">Trạng thái thị trường</h2>
      <p>Trạng thái dữ liệu: {statusLabel(regime.dataStatus)}</p>
      {canPresentAssessment ? (
        <>
          <dl className="regime-grid">
            <div><dt>Phân loại</dt><dd>{regime.label}</dd></div>
            <div><dt>Điểm regime</dt><dd>{regime.score}/100</dd></div>
            <div><dt>Độ tin cậy</dt><dd aria-label={`Chất lượng đánh giá: ${regime.confidence}/100`}>{regime.confidence}/100</dd></div>
          </dl>
          <p>Độ tin cậy phản ánh chất lượng đầu vào và mức đồng thuận của yếu tố, không phải xác suất dự báo.</p>
          <FactorList regime={regime} />
        </>
      ) : (
        <p role="status">Không công bố đánh giá regime: {regime.reasonCodes.join(", ") || "REGIME_UNAVAILABLE"}</p>
      )}
      {canPresentAssessment && regime.reasonCodes.length > 0 && (
        <p role="status">Lưu ý chất lượng: {regime.reasonCodes.join(", ")}</p>
      )}
      <p>Phiên bản quy tắc: {regime.ruleVersion} · Cập nhật: {formatAsOf(regime.asOf)} · Nguồn: {regime.source.provider}</p>
      <p><small>{regime.disclaimerCode}</small></p>
    </section>
  );
}

function FactorList({ regime }: { regime: MarketRegime }) {
  if (regime.factors.length === 0) return null;
  return (
    <section aria-label="Các yếu tố regime">
      <h3>Các yếu tố hỗ trợ</h3>
      <ul>
        {regime.factors.map((factor) => (
          <li key={factor.code}>
            <strong>{factor.code}</strong>: {factor.direction} · Điểm chuẩn hóa: {factor.normalizedScore ?? "Không có"}
            · Trọng số hiệu lực: {factor.effectiveWeight ?? "Không có"} · Đóng góp: {factor.contribution ?? "Không có"}
          </li>
        ))}
      </ul>
    </section>
  );
}

function statusLabel(status: DataStatus): string {
  return ({ CURRENT: "Hiện tại", DELAYED: "Chậm", STALE: "Cũ", PARTIAL: "Một phần", UNAVAILABLE: "Không có dữ liệu" })[status];
}
