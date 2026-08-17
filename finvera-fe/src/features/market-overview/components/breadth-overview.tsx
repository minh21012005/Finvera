import type { DataStatus, MarketBreadth } from "../api/market-overview";
import { formatAsOf } from "../format/market-format";

export function BreadthOverview({ breadth }: { breadth: MarketBreadth }) {
  const unavailable = breadth.dataStatus === "UNAVAILABLE";
  const adv = breadth.advancing ?? 0;
  const dec = breadth.declining ?? 0;
  const unc = breadth.unchanged ?? 0;
  const total = adv + dec + unc;

  const advPct = total > 0 ? ((adv / total) * 100).toFixed(1) : "0";
  const decPct = total > 0 ? ((dec / total) * 100).toFixed(1) : "0";
  const uncPct = total > 0 ? ((unc / total) * 100).toFixed(1) : "0";

  return (
    <section
      className="card-panel"
      aria-labelledby="market-breadth-heading"
      aria-label={`Độ rộng thị trường: ${statusLabel(breadth.dataStatus)}`}
    >
      <div className="section-title">
        <h2 id="market-breadth-heading">Độ rộng thị trường</h2>
        <span className={`status-pill ${breadth.dataStatus.toLowerCase()}`}>
          Trạng thái: {statusLabel(breadth.dataStatus)}
        </span>
      </div>

      {unavailable ? (
        <div className="unavailable-msg">
          <p role="status">Không có dữ liệu độ rộng: {breadth.reasonCodes.join(", ") || "BREADTH_NOT_AVAILABLE"}</p>
        </div>
      ) : (
        <>
          {total > 0 && (
            <div className="breadth-bar-container">
              <div className="breadth-bar">
                <div className="breadth-segment advancing" style={{ width: `${advPct}%` }} title={`Tăng: ${advPct}%`} />
                <div className="breadth-segment declining" style={{ width: `${decPct}%` }} title={`Giảm: ${decPct}%`} />
                <div className="breadth-segment unchanged" style={{ width: `${uncPct}%` }} title={`Không đổi: ${uncPct}%`} />
              </div>
              <div style={{ display: "flex", justifyContent: "space-between", fontSize: "0.75rem", color: "var(--text-secondary)" }}>
                <span style={{ color: "var(--color-up)" }}>Tăng {advPct}%</span>
                <span style={{ color: "var(--color-unchanged)" }}>Không đổi {uncPct}%</span>
                <span style={{ color: "var(--color-down)" }}>Giảm {decPct}%</span>
              </div>
            </div>
          )}

          <dl className="breadth-grid">
            <div className="breadth-stat-card up">
              <dt>Tăng giá</dt>
              <dd>{breadth.advancing}</dd>
            </div>
            <div className="breadth-stat-card down">
              <dt>Giảm giá</dt>
              <dd>{breadth.declining}</dd>
            </div>
            <div className="breadth-stat-card unchanged">
              <dt>Không đổi</dt>
              <dd>{breadth.unchanged}</dd>
            </div>
            <div className="breadth-stat-card eligible">
              <dt>Đủ điều kiện</dt>
              <dd>{breadth.eligible}</dd>
            </div>
          </dl>

          {breadth.unclassified !== null && breadth.unclassified > 0 && (
            <p role="status" className="unavailable-msg" style={{ margin: "12px 0 0 0" }}>
              {breadth.unclassified} mã chưa phân loại. Lý do: {breadth.reasonCodes.join(", ")}
            </p>
          )}
        </>
      )}

      <p style={{ margin: "16px 0 0 0", fontSize: "0.75rem", color: "var(--text-muted)" }}>
        Universe: {breadth.universeVersion} · Cập nhật: {formatAsOf(breadth.asOf)} · Nguồn: {breadth.source.provider}
      </p>
    </section>
  );
}

function statusLabel(status: DataStatus): string {
  return ({ CURRENT: "Hiện tại", DELAYED: "Chậm", STALE: "Cũ", PARTIAL: "Một phần", UNAVAILABLE: "Không có dữ liệu" })[status];
}
