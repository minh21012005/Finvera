import type { StockSignals as StockSignalsData } from "../api/stock-signals";
import { dataStatusLabel, formatDecimal } from "../format/stock-format";
import {
  directionLabel,
  evaluationStatusLabel,
  riskFactorLabel,
  riskLevelDisplay,
  signalStrengthLabel,
  strategyLabel,
} from "../format/signal-format";
import { ExplainButton } from "../../analyst/components/ExplainButton";
import { CheckCircle2, ShieldAlert, Zap, Layers } from "lucide-react";

const DISCLAIMER_COPY: Record<string, string> = {
  QUANTITATIVE_DECISION_SUPPORT:
    "Tín hiệu chiến lược là kịch bản hỗ trợ ra quyết định định lượng dựa trên dữ liệu đã chấp nhận, không phải khuyến nghị đầu tư hay đảm bảo kết quả.",
};

export function StockSignals({ signals, symbol }: { signals: StockSignalsData; symbol: string }) {
  const activeEvaluations = signals.evaluations.filter((e) => e.status === "SIGNAL" && e.signal);
  const primarySignal = activeEvaluations[0]?.signal;
  const primaryRisk = primarySignal ? riskLevelDisplay(primarySignal.riskLevel) : null;

  return (
    <section aria-labelledby="stock-signals-heading" className="stock-signals-card">
      <header>
        <div>
          <h2 id="stock-signals-heading">Tín hiệu chiến lược giao dịch</h2>
          <p className="text-xs text-slate-400 mt-0.5">
            Hệ thống 8 chiến lược định lượng tự động quét và đánh giá rủi ro theo khung ATR
          </p>
        </div>
        <span className={`status-pill ${signals.dataStatus.toLowerCase()}`}>
          {dataStatusLabel(signals.dataStatus)}
        </span>
      </header>

      {/* ── TẦNG 1: KHUNG KỊCH BẢN VÀO LỆNH & RỦI RO CHUNG (CHỈ HIỂN THỊ 1 LẦN) ── */}
      {primarySignal && primaryRisk && (
        <div className="signal-master-panel">
          <div className="signal-master-header">
            <div className="signal-confluence-info">
              <span className="signal-confluence-badge">
                <Zap size={14} className="text-amber-400" />
                <span>
                  Đồng thuận: <strong>{activeEvaluations.length}/{signals.evaluations.length}</strong> chiến lược kích hoạt tín hiệu
                </span>
              </span>
              <h3 className="signal-master-title">Kịch bản Giao dịch & Quản trị Rủi ro (ATR Framework)</h3>
            </div>

            <div className={`risk-level-badge ${primaryRisk.className}`}>
              <span aria-hidden="true">{primaryRisk.icon}</span>
              <span>
                {primaryRisk.label}
                {primarySignal.riskScore !== null ? ` (${primarySignal.riskScore}/100)` : ""}
              </span>
              <span className="signal-strength">Độ mạnh tín hiệu: {signalStrengthLabel(primarySignal.signalStrength)}</span>
            </div>
          </div>

          <div className="signal-master-grid">
            {/* Cột trái: Khung giá và mục tiêu */}
            <div className="signal-levels-block">
              <div className="signal-direction-row">
                <p className="signal-direction">Chiều: {directionLabel(primarySignal.direction)}</p>
              </div>

              <dl className="signal-levels">
                <div>
                  <dt>Vùng vào lệnh</dt>
                  <dd>
                    {formatDecimal(primarySignal.entryLow)} – {formatDecimal(primarySignal.entryHigh)}
                  </dd>
                </div>
                <div>
                  <dt>Dừng lỗ</dt>
                  <dd className="text-rose-400">{formatDecimal(primarySignal.stopLoss)}</dd>
                </div>
                <div>
                  <dt>Mục tiêu 1</dt>
                  <dd className="text-emerald-400">{formatDecimal(primarySignal.target1)}</dd>
                </div>
                <div>
                  <dt>Mục tiêu 2</dt>
                  <dd className="text-emerald-300">{formatDecimal(primarySignal.target2)}</dd>
                </div>
                <div>
                  <dt>Tỷ lệ Rủi ro / Lợi nhuận</dt>
                  <dd>1 : {formatDecimal(primarySignal.riskReward)}</dd>
                </div>
              </dl>
            </div>

            {/* Cột phải: 6 Yếu tố rủi ro */}
            <div className="signal-risk-block">
              <div className="signal-risk-header">
                <ShieldAlert size={14} className="text-slate-400" />
                <span className="text-xs font-semibold text-slate-300">Chi tiết 6 yếu tố rủi ro</span>
              </div>

              {primarySignal.riskLevel === null && (
                <p role="status" className="unavailable-msg">
                  Chưa đủ yếu tố rủi ro để tính điểm tổng hợp
                  {primarySignal.reasonCodes.length > 0 ? ` (${primarySignal.reasonCodes.join(", ")})` : " (INSUFFICIENT_RISK_FACTORS)"}.
                </p>
              )}

              <ul className="risk-factor-list" aria-label="Chi tiết yếu tố rủi ro">
                {primarySignal.riskFactors.map((factor) => (
                  <li key={factor.factorCode} className={`risk-factor-item ${factor.applicability.toLowerCase()}`}>
                    <span className="factor-code">{riskFactorLabel(factor.factorCode)}</span>
                    {factor.applicability === "DEFINED" ? (
                      <span className="factor-details">
                        Giá trị: {factor.inputValue !== null ? formatDecimal(factor.inputValue) : "—"} · Điểm:{" "}
                        {factor.factorScore}/100
                      </span>
                    ) : (
                      <span className="factor-details unavailable-msg">
                        Không có dữ liệu ({factor.reasonCode ?? factor.applicability})
                      </span>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </div>
      )}

      {/* ── TẦNG 2: DANH SÁCH CHIẾN LƯỢC TINH GỌN (STRATEGY MATRIX) ── */}
      <div className="signal-matrix-header flex items-center gap-2 mb-3">
        <Layers size={15} className="text-cyan-400" />
        <h3 className="text-sm font-semibold text-slate-200">Trạng thái các Chiến lược Kỹ thuật</h3>
      </div>

      <ul className="signal-grid">
        {signals.evaluations.map((evaluation) => {
          const isSignal = evaluation.status === "SIGNAL" && evaluation.signal;
          const evidenceFactors = isSignal
            ? evaluation.signal!.riskFactors
              .filter((factor) => factor.applicability === "DEFINED")
              .map((factor) => ({
                factorCode: factor.factorCode,
                description: `${riskFactorLabel(factor.factorCode)}: ${factor.inputValue !== null ? formatDecimal(factor.inputValue) : "—"
                  } (điểm ${factor.factorScore}/100)`,
              }))
            : [];

          return (
            <li
              key={evaluation.strategyCode}
              className={`signal-card ${evaluation.status.toLowerCase()} ${isSignal ? "has-signal" : ""}`}
            >
              {/* Row 1: Strategy Name & Explain Button */}
              <div className="signal-card-row-name">
                <p className="signal-strategy-name">{strategyLabel(evaluation.strategyCode)}</p>
                {isSignal && (
                  <ExplainButton
                    outputType="SIGNAL"
                    symbol={symbol}
                    evidenceFactors={evidenceFactors}
                    label="Giải thích tín hiệu"
                  />
                )}
              </div>

              {/* Row 2: Status */}
              <div className="signal-card-row-status">
                <p className="signal-status">{evaluationStatusLabel(evaluation.status)}</p>
              </div>

              {/* Row 3: Strategy specific warning / error message if no signal */}
              {evaluation.status === "INSUFFICIENT_HISTORY" && (
                <div className="signal-card-row-message">
                  <p role="status" className="unavailable-msg">
                    Chưa đủ dữ liệu lịch sử để đánh giá chiến lược này ({evaluation.reasonCode ?? "INSUFFICIENT_HISTORY"}).
                  </p>
                </div>
              )}
              {evaluation.status === "WITHHELD" && (
                <div className="signal-card-row-message">
                  <p role="status" className="unavailable-msg">
                    Tín hiệu tạm giữ do xung đột nguồn dữ liệu ({evaluation.reasonCode ?? "WITHHELD"}).
                  </p>
                </div>
              )}
              {evaluation.status === "NO_SIGNAL" && (
                <div className="signal-card-row-message">
                  <p role="status" className="no-signal-msg">
                    Điều kiện chiến lược hiện chưa được thỏa mãn — không phải lỗi.
                  </p>
                </div>
              )}

              {/* Row 4: Evidence Box when triggered */}
              {isSignal && (
                <div className="signal-evidence-box">
                  <div className="signal-evidence-status">
                    <CheckCircle2 size={13} className="text-emerald-400" />
                    <span className="text-xs text-emerald-400 font-medium">
                      Điều kiện kỹ thuật đã kích hoạt
                    </span>
                  </div>
                  {Object.keys(evaluation.signal!.supportingEvidence || {}).length > 0 && (
                    <div className="signal-evidence-items">
                      {Object.entries(evaluation.signal!.supportingEvidence).map(([key, val]) => (
                        <span key={key} className="signal-evidence-tag">
                          <span className="evidence-key">{key}</span>
                          <strong className="evidence-val">{val}</strong>
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </li>
          );
        })}
      </ul>

      <p className="disclaimer" role="note">
        {DISCLAIMER_COPY[signals.disclaimerCode] ?? signals.disclaimerCode}
      </p>
    </section>
  );
}
