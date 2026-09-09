import type { CalculationBasis, MarketRegime } from "../api/market-overview";
import { formatAsOf } from "../format/market-format";
import { Activity } from "lucide-react";

import { ReasonCode, ReasonCodes } from "../../../shared/components/reason-codes";
import { dataStatusLabel as statusLabel } from "../../../shared/format/reason-codes";
/** Displays the backend's deterministic regime result; it never calculates a market signal in the browser. */
export function RegimeOverview({ regime }: { regime: MarketRegime }) {
  const canPresentAssessment = regime.label !== null && regime.score !== null && regime.confidence !== null;
  const basis = basisCopy(regime.assessmentBasis);

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
        <p className="text-xs text-slate-400 -mt-2 mb-3.5">
          {basis.label}: {basis.description}
        </p>

        {canPresentAssessment ? (
          <>
            {/* 3 thẻ chỉ số cốt lõi — Không lặp lại thông tin, cùng 1 hàng ngang */}
            <dl className="regime-hero-grid">
              {/* Thẻ 1: Phân loại xu hướng */}
              <div className="regime-hero-card primary">
                <dt>Phân loại xu hướng</dt>
                <dd className="regime-tag font-bold">
                  {regime.label}
                </dd>
                <span className="hero-card-sub">Chu kỳ thị trường định lượng</span>
              </div>

              {/* Thẻ 2: Điểm regime kèm mini arc gauge */}
              <div className="regime-hero-card">
                <dt>Điểm regime</dt>
                <div className="score-with-gauge">
                  <svg width="28" height="28" viewBox="0 0 28 28" className="score-mini-arc" aria-hidden="true">
                    <circle cx="14" cy="14" r="11" fill="none" stroke="#162438" strokeWidth="3" />
                    <circle
                      cx="14"
                      cy="14"
                      r="11"
                      fill="none"
                      stroke={regime.score! >= 60 ? "#00e599" : regime.score! >= 40 ? "#00d2e0" : "#f43f5e"}
                      strokeWidth="3"
                      strokeDasharray={69.115}
                      strokeDashoffset={69.115 - (69.115 * (regime.score ?? 0)) / 100}
                      strokeLinecap="round"
                      transform="rotate(-90 14 14)"
                    />
                  </svg>
                  <dd className="font-mono font-bold text-slate-100">
                    {regime.score}/100
                  </dd>
                </div>
                <span className="hero-card-sub font-mono">
                  Trạng thái: {regime.score! >= 60 ? "TÍCH CỰC" : regime.score! >= 40 ? "TRUNG LẬP" : "THẬN TRỌNG"}
                </span>
              </div>

              {/* Thẻ 3: Độ tin cậy */}
              <div className="regime-hero-card">
                <dt>Độ tin cậy</dt>
                <dd className="font-mono font-bold text-slate-200" aria-label={`Chất lượng đánh giá: ${regime.confidence}/100`}>
                  {regime.confidence}/100
                </dd>
                <span className="hero-card-sub">Chất lượng đánh giá mô hình</span>
              </div>
            </dl>

            <FactorList regime={regime} />
          </>
        ) : (
          <div className="unavailable-msg">
            <p role="status">Không công bố đánh giá regime: <ReasonCodes codes={regime.reasonCodes.length > 0 ? regime.reasonCodes : ["REGIME_UNAVAILABLE"]} /></p>
          </div>
        )}

        {canPresentAssessment && regime.reasonCodes.length > 0 && (
          <div className="unavailable-msg mt-3">
            <p role="status"><ReasonCodes prefix="Lưu ý chất lượng: " codes={regime.reasonCodes} /></p>
          </div>
        )}
      </div>

      <div className="mt-4 border-t border-slate-800/60 pt-3">
        <p className="text-xs text-slate-500 font-mono mb-1">
          Phiên bản quy tắc: {regime.ruleVersion} · Basis: {regime.assessmentBasis ?? "N/A"} · Cập nhật: {formatAsOf(regime.asOf)} · Nguồn: {regime.source.provider}
        </p>
        <p className="m-0 text-slate-500">
          <small><ReasonCode code={regime.disclaimerCode} /></small>
        </p>
      </div>
    </section>
  );
}

function FactorList({ regime }: { regime: MarketRegime }) {
  if (regime.factors.length === 0) return null;
  return (
    <div className="mt-3">
      <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-2">
        Các yếu tố thành phần
      </h3>
      <ul className="factors-2col-grid">
        {regime.factors.map((factor) => {
          const dirClass =
            factor.direction === "POSITIVE"
              ? "factor-positive"
              : factor.direction === "NEGATIVE"
              ? "factor-negative"
              : "factor-neutral";
          return (
            <li
              key={factor.code}
              className={`factor-item ${dirClass}`}
            >
              <div className="flex items-center gap-2">
                <strong className="text-slate-200">{factor.code}</strong>
                <span className={`factor-dir-badge ${dirClass}`}>
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


function basisCopy(basis: CalculationBasis | null): { label: string; description: string } {
  if (basis === "LIVE") {
    return { label: "Trong phiên", description: "TCBS live, thay đổi theo khớp lệnh và chỉ dùng như overlay realtime." };
  }
  if (basis === "EOD") {
    return { label: "Cuối phiên", description: "Dữ liệu completed-session, phù hợp cho chỉ báo daily và strategy/risk." };
  }
  return { label: "Chưa xác định basis", description: "Dữ liệu cũ hoặc chưa đủ provenance LIVE/EOD." };
}
