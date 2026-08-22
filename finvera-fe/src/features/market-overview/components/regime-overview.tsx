import type { DataStatus, MarketRegime } from "../api/market-overview";
import { formatAsOf } from "../format/market-format";
import { Activity } from "lucide-react";

/** Displays the backend's deterministic regime result; it never calculates a market signal in the browser. */
export function RegimeOverview({ regime }: { regime: MarketRegime }) {
  const canPresentAssessment = regime.label !== null && regime.score !== null && regime.confidence !== null;

  return (
    <section
      className="card-panel"
      aria-labelledby="market-regime-heading"
      aria-label={`Trạng thái thị trường: ${statusLabel(regime.dataStatus)}`}
    >
      <div>
        <div className="flex items-center justify-between gap-3 mb-4 pb-3 border-b border-slate-800">
          <div className="flex items-center gap-2.5">
            <Activity size={18} className="text-cyan-400" />
            <h2 id="market-regime-heading" className="text-base font-bold text-slate-100">
              Trạng thái thị trường
            </h2>
          </div>
          <span className={`status-pill ${regime.dataStatus.toLowerCase()}`}>
            {statusLabel(regime.dataStatus)}
          </span>
        </div>

        {canPresentAssessment ? (
          <>
            <div className="regime-banner" style={{ margin: "0 0 16px 0" }}>
              <div className="regime-label-box">
                <span style={{ fontSize: "0.725rem", color: "var(--text-muted)", fontWeight: 700, letterSpacing: "0.08em" }}>
                  MÔ HÌNH ĐỊNH LƯỢNG REGIME
                </span>
                <span className="eyebrow" style={{ fontSize: "0.875rem" }}>
                  ĐÁNH GIÁ ĐỊNH TÍNH
                </span>
              </div>
              <div className="regime-score-box">
                <div className="score-circle">
                  <span className="score-num">{regime.score}</span>
                  <span className="score-sub">/100</span>
                </div>
              </div>
            </div>

            <dl className="regime-grid" style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: "12px", margin: "14px 0" }}>
              <div className="breadth-stat-card eligible">
                <dt>Phân loại</dt>
                <dd className="regime-tag" style={{ fontSize: "1.05rem" }}>{regime.label}</dd>
              </div>
              <div className="breadth-stat-card eligible">
                <dt>Điểm regime</dt>
                <dd style={{ fontSize: "1.05rem" }}>{regime.score}/100</dd>
              </div>
              <div className="breadth-stat-card eligible">
                <dt>Độ tin cậy</dt>
                <dd style={{ fontSize: "1.05rem" }} aria-label={`Chất lượng đánh giá: ${regime.confidence}/100`}>
                  {regime.confidence}/100
                </dd>
              </div>
            </dl>

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
      </div>

      <div style={{ marginTop: "16px" }}>
        <p style={{ margin: "0 0 4px 0", fontSize: "0.75rem", color: "var(--text-muted)" }}>
          Phiên bản quy tắc: {regime.ruleVersion} · Cập nhật: {formatAsOf(regime.asOf)} · Nguồn: {regime.source.provider}
        </p>
        <p style={{ margin: "0", color: "var(--text-muted)" }}>
          <small>{regime.disclaimerCode}</small>
        </p>
      </div>
    </section>
  );
}

function FactorList({ regime }: { regime: MarketRegime }) {
  if (regime.factors.length === 0) return null;
  return (
    <div className="mt-3">
      <h3 style={{ fontSize: "0.8125rem", color: "var(--text-secondary)", margin: "0 0 8px 0", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.05em" }}>
        Các yếu tố thành phần
      </h3>
      <ul style={{ listStyle: "none", padding: 0, margin: 0, display: "flex", flexDirection: "column", gap: "6px" }}>
        {regime.factors.map((factor) => {
          const borderAccent =
            factor.direction === "POSITIVE"
              ? "var(--color-up)"
              : factor.direction === "NEGATIVE"
              ? "var(--color-down)"
              : "var(--color-unchanged)";
          const badgeBg =
            factor.direction === "POSITIVE"
              ? "var(--color-up-bg)"
              : factor.direction === "NEGATIVE"
              ? "var(--color-down-bg)"
              : "var(--color-unchanged-bg)";
          return (
            <li
              key={factor.code}
              className="factor-item"
              style={{
                borderLeft: `3px solid ${borderAccent}`,
                padding: "8px 12px",
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                gap: "10px",
                fontSize: "0.775rem",
              }}
            >
              <div className="flex items-center gap-2">
                <strong className="text-slate-200">{factor.code}</strong>
                <span
                  style={{
                    background: badgeBg,
                    color: borderAccent,
                    padding: "2px 6px",
                    borderRadius: "4px",
                    fontSize: "0.7rem",
                    fontWeight: 700,
                  }}
                >
                  {factor.direction}
                </span>
              </div>
              <div className="text-slate-400 font-mono text-xs">
                Score: <span className="text-slate-200">{factor.normalizedScore ?? "--"}</span> · Weight: <span className="text-slate-200">{factor.effectiveWeight ?? "--"}</span>
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

function statusLabel(status: DataStatus): string {
  return ({ CURRENT: "Hiện tại", DELAYED: "Chậm", STALE: "Cũ", PARTIAL: "Một phần", UNAVAILABLE: "Không có dữ liệu" })[status];
}
