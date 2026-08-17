import type { DataStatus, MarketRegime } from "../api/market-overview";
import { formatAsOf } from "../format/market-format";

/** Displays the backend's deterministic regime result; it never calculates a market signal in the browser. */
export function RegimeOverview({ regime }: { regime: MarketRegime }) {
  const canPresentAssessment = regime.label !== null && regime.score !== null && regime.confidence !== null;

  return (
    <section
      className="card-panel"
      aria-labelledby="market-regime-heading"
      aria-label={`Trạng thái thị trường: ${statusLabel(regime.dataStatus)}`}
    >
      <div className="section-title">
        <h2 id="market-regime-heading">Trạng thái thị trường</h2>
        <span className={`status-pill ${regime.dataStatus.toLowerCase()}`}>
          Trạng thái dữ liệu: {statusLabel(regime.dataStatus)}
        </span>
      </div>

      {canPresentAssessment ? (
        <>
          <div className="regime-banner">
            <div className="regime-label-box">
              <span style={{ fontSize: "0.75rem", color: "var(--text-muted)", fontWeight: 600 }}>DỮ LIỆU REGIME CHỦ ĐẠO</span>
              <span className="eyebrow" style={{ fontSize: "0.875rem" }}>ĐÁNH GIÁ ĐỊNH TÍNH</span>
            </div>
            <div className="regime-score-box">
              <div className="score-circle">
                <span className="score-num">{regime.score}</span>
                <span className="score-sub">/100</span>
              </div>
            </div>
          </div>

          <dl className="regime-grid" style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: "12px", margin: "16px 0" }}>
            <div className="breadth-stat-card eligible">
              <dt>Phân loại</dt>
              <dd className="regime-tag" style={{ fontSize: "1.1rem" }}>{regime.label}</dd>
            </div>
            <div className="breadth-stat-card eligible">
              <dt>Điểm regime</dt>
              <dd style={{ fontSize: "1.1rem" }}>{regime.score}/100</dd>
            </div>
            <div className="breadth-stat-card eligible">
              <dt>Độ tin cậy</dt>
              <dd style={{ fontSize: "1.1rem" }} aria-label={`Chất lượng đánh giá: ${regime.confidence}/100`}>
                {regime.confidence}/100
              </dd>
            </div>
          </dl>

          <p style={{ fontSize: "0.8125rem", color: "var(--text-secondary)", margin: "12px 0 16px 0" }}>
            Độ tin cậy phản ánh chất lượng đầu vào và mức đồng thuận của yếu tố, không phải xác suất dự báo.
          </p>

          <FactorList regime={regime} />
        </>
      ) : (
        <div className="unavailable-msg">
          <p role="status">Không công bố đánh giá regime: {regime.reasonCodes.join(", ") || "REGIME_UNAVAILABLE"}</p>
        </div>
      )}

      {canPresentAssessment && regime.reasonCodes.length > 0 && (
        <div className="unavailable-msg" style={{ margin: "12px 0 0 0" }}>
          <p role="status">Lưu ý chất lượng: {regime.reasonCodes.join(", ")}</p>
        </div>
      )}

      <p style={{ margin: "16px 0 4px 0", fontSize: "0.75rem", color: "var(--text-muted)" }}>
        Phiên bản quy tắc: {regime.ruleVersion} · Cập nhật: {formatAsOf(regime.asOf)} · Nguồn: {regime.source.provider}
      </p>
      <p style={{ margin: "0", color: "var(--text-muted)" }}>
        <small>{regime.disclaimerCode}</small>
      </p>
    </section>
  );
}

function FactorList({ regime }: { regime: MarketRegime }) {
  if (regime.factors.length === 0) return null;
  return (
    <section aria-label="Các yếu tố regime" className="regime-factors">
      <h3 style={{ fontSize: "0.9375rem", color: "#ffffff", margin: "0 0 12px 0", fontWeight: 700 }}>Các yếu tố hỗ trợ</h3>
      <ul style={{ listStyle: "none", padding: 0, margin: 0 }}>
        {regime.factors.map((factor) => {
          const borderAccent =
            factor.direction === "POSITIVE"
              ? "var(--color-up)"
              : factor.direction === "NEGATIVE"
              ? "var(--color-down)"
              : "var(--color-unchanged)";
          return (
            <li key={factor.code} className="factor-item" style={{ borderLeft: `4px solid ${borderAccent}` }}>
              <div>
                <strong>{factor.code}</strong>: {factor.direction} · Điểm chuẩn hóa: {factor.normalizedScore ?? "Không có"} · Trọng số hiệu lực: {factor.effectiveWeight ?? "Không có"} · Đóng góp: {factor.contribution ?? "Không có"}
              </div>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

function statusLabel(status: DataStatus): string {
  return ({ CURRENT: "Hiện tại", DELAYED: "Chậm", STALE: "Cũ", PARTIAL: "Một phần", UNAVAILABLE: "Không có dữ liệu" })[status];
}
