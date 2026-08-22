import type { DataStatus, MarketBreadth } from "../api/market-overview";
import { formatAsOf } from "../format/market-format";
import { TrendingUp, TrendingDown, Minus, PieChart } from "lucide-react";

export function BreadthOverview({ breadth }: { breadth: MarketBreadth }) {
  const unavailable = breadth.dataStatus === "UNAVAILABLE";
  const adv = breadth.advancing ?? 0;
  const dec = breadth.declining ?? 0;
  const unc = breadth.unchanged ?? 0;
  const total = adv + dec + unc;

  const advPct = total > 0 ? ((adv / total) * 100).toFixed(1) : "0";
  const decPct = total > 0 ? ((dec / total) * 100).toFixed(1) : "0";
  const uncPct = total > 0 ? ((unc / total) * 100).toFixed(1) : "0";
  const adRatio = dec > 0 ? (adv / dec).toFixed(2) : adv > 0 ? "N/A (0 Dec)" : "1.00";

  return (
    <section
      className="card-panel"
      aria-labelledby="market-breadth-heading"
      aria-label={`Độ rộng thị trường: ${statusLabel(breadth.dataStatus)}`}
    >
      <div>
        <div className="flex items-center justify-between gap-3 mb-4 pb-3 border-b border-slate-800">
          <div className="flex items-center gap-2.5">
            <PieChart size={18} className="text-cyan-400" />
            <h2 id="market-breadth-heading" className="text-base font-bold text-slate-100">
              Độ rộng thị trường
            </h2>
          </div>
          <span className={`status-pill ${breadth.dataStatus.toLowerCase()}`}>
            {statusLabel(breadth.dataStatus)}
          </span>
        </div>

        {unavailable ? (
          <div className="unavailable-msg">
            <p role="status">Không có dữ liệu độ rộng: {breadth.reasonCodes.join(", ") || "BREADTH_NOT_AVAILABLE"}</p>
          </div>
        ) : (
          <>
            <div className="bg-slate-950/70 border border-slate-800/80 rounded-xl p-4 mb-4">
              <div className="flex items-center justify-between text-xs text-slate-400 mb-2 font-medium">
                <span>Phân bổ mã toàn sàn (HOSE, HNX, UPCOM)</span>
                <span className="font-mono text-cyan-400">Tỷ lệ A/D: <strong>{adRatio}</strong></span>
              </div>

              {total > 0 && (
                <div className="breadth-bar-container" style={{ margin: "10px 0" }}>
                  <div className="breadth-bar">
                    <div className="breadth-segment advancing" style={{ width: `${advPct}%` }} title={`Tăng: ${advPct}%`} />
                    <div className="breadth-segment unchanged" style={{ width: `${uncPct}%` }} title={`Không đổi: ${uncPct}%`} />
                    <div className="breadth-segment declining" style={{ width: `${decPct}%` }} title={`Giảm: ${decPct}%`} />
                  </div>
                  <div className="flex items-center justify-between text-xs font-semibold pt-1">
                    <span className="text-emerald-400 flex items-center gap-1">
                      <TrendingUp size={13} /> Tăng {advPct}% ({adv})
                    </span>
                    <span className="text-amber-400 flex items-center gap-1">
                      <Minus size={13} /> Tham chiếu {uncPct}% ({unc})
                    </span>
                    <span className="text-rose-400 flex items-center gap-1">
                      <TrendingDown size={13} /> Giảm {decPct}% ({dec})
                    </span>
                  </div>
                </div>
              )}
            </div>

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
      </div>

      <p style={{ margin: "16px 0 0 0", fontSize: "0.75rem", color: "var(--text-muted)" }}>
        Universe: {breadth.universeVersion} · Cập nhật: {formatAsOf(breadth.asOf)} · Nguồn: {breadth.source.provider}
      </p>
    </section>
  );
}

function statusLabel(status: DataStatus): string {
  return ({ CURRENT: "Hiện tại", DELAYED: "Chậm", STALE: "Cũ", PARTIAL: "Một phần", UNAVAILABLE: "Không có dữ liệu" })[status];
}
