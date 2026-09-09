import type { CalculationBasis, MarketBreadth } from "../api/market-overview";
import { formatAsOf } from "../format/market-format";
import { TrendingUp, TrendingDown, Minus, PieChart } from "lucide-react";

import { ReasonCodes } from "../../../shared/components/reason-codes";
import { dataStatusLabel as statusLabel } from "../../../shared/format/reason-codes";
export function BreadthOverview({ breadth }: { breadth: MarketBreadth }) {
  const unavailable = breadth.dataStatus === "UNAVAILABLE";
  const adv = breadth.advancing ?? 0;
  const dec = breadth.declining ?? 0;
  const unc = breadth.unchanged ?? 0;
  const total = adv + dec + unc;
  const eligible = breadth.eligible ?? total + (breadth.unclassified ?? 0);
  const unclassified = breadth.unclassified ?? 0;
  const classified = Math.max(0, eligible - unclassified);

  const advPct = total > 0 ? ((adv / total) * 100).toFixed(1) : "0";
  const decPct = total > 0 ? ((dec / total) * 100).toFixed(1) : "0";
  const uncPct = total > 0 ? ((unc / total) * 100).toFixed(1) : "0";
  const adRatio = dec > 0 ? (adv / dec).toFixed(2) : adv > 0 ? "N/A (0 Dec)" : "1.00";
  const basis = basisCopy(breadth.calculationBasis);

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
        <p className="text-xs text-slate-400 -mt-2 mb-3.5">
          {basis.label}: {basis.description}
        </p>

        {unavailable ? (
          <div className="unavailable-msg">
            <p role="status">Không có dữ liệu độ rộng: <ReasonCodes codes={breadth.reasonCodes.length > 0 ? breadth.reasonCodes : ["BREADTH_NOT_AVAILABLE"]} /></p>
          </div>
        ) : (
          <>
            <div className="bg-slate-950/70 border border-slate-800/80 rounded-xl p-4 mb-4">
              <div className="flex items-center justify-between text-xs text-slate-400 mb-2 font-medium">
                <span>Phân bổ mã toàn sàn (HOSE, HNX, UPCOM)</span>
                <span className="font-mono text-cyan-400">Tỷ lệ A/D: <strong>{adRatio}</strong></span>
              </div>

              {total > 0 && (
                <div className="breadth-bar-container my-2.5">
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
                <dd className="font-mono">{breadth.advancing}</dd>
              </div>
              <div className="breadth-stat-card down">
                <dt>Giảm giá</dt>
                <dd className="font-mono">{breadth.declining}</dd>
              </div>
              <div className="breadth-stat-card unchanged">
                <dt>Không đổi</dt>
                <dd className="font-mono">{breadth.unchanged}</dd>
              </div>
              <div className="breadth-stat-card eligible">
                <dt>Tổng universe</dt>
                <dd className="font-mono">{breadth.eligible}</dd>
              </div>
            </dl>

            <p className="text-xs text-slate-400 font-mono mt-2.5">
              Đã phân loại: <strong className="text-slate-200">{classified}</strong> / {eligible}
              {unclassified > 0 ? (
                <> · Chưa phân loại: <strong className="text-slate-200">{unclassified}</strong></>
              ) : null}
            </p>

            {breadth.unclassified !== null && breadth.unclassified > 0 && (
              <p role="status" className="unavailable-msg mt-3">
                {breadth.unclassified} mã chưa phân loại. <ReasonCodes prefix="Lý do: " codes={breadth.reasonCodes} />
              </p>
            )}
          </>
        )}
      </div>

      <footer className="breadth-footer-strip font-mono">
        <span>Universe: {breadth.universeVersion}</span>
        <span className="strip-dot" aria-hidden="true">•</span>
        <span>Basis: {breadth.calculationBasis ?? "N/A"}</span>
        <span className="strip-dot" aria-hidden="true">•</span>
        <span>Cập nhật: {formatAsOf(breadth.asOf)}</span>
        <span className="strip-dot" aria-hidden="true">•</span>
        <span>Nguồn: {breadth.source.provider}</span>
      </footer>
    </section>
  );
}


function basisCopy(basis: CalculationBasis | null): { label: string; description: string } {
  if (basis === "LIVE") {
    return { label: "Trong phiên", description: "TCBS live, phản ánh trạng thái tạm thời trong phiên." };
  }
  if (basis === "EOD") {
    return { label: "Cuối phiên", description: "Dữ liệu completed-session, dùng cho phân tích daily." };
  }
  return { label: "Chưa xác định basis", description: "Dữ liệu cũ hoặc chưa đủ provenance LIVE/EOD." };
}
