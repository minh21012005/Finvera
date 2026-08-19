import type { Signal as SignalData, StockSignals as StockSignalsData } from "../api/stock-signals";
import { dataStatusLabel, formatDecimal } from "../format/stock-format";
import {
  directionLabel,
  evaluationStatusLabel,
  riskFactorLabel,
  riskLevelDisplay,
  signalStrengthLabel,
  strategyLabel,
} from "../format/signal-format";

const DISCLAIMER_COPY: Record<string, string> = {
  QUANTITATIVE_DECISION_SUPPORT:
    "Tín hiệu chiến lược là kịch bản hỗ trợ ra quyết định định lượng dựa trên dữ liệu đã chấp nhận, không phải khuyến nghị đầu tư hay đảm bảo kết quả.",
};

export function StockSignals({ signals }: { signals: StockSignalsData }) {
  return (
    <section aria-labelledby="stock-signals-heading" className="stock-signals-card">
      <header>
        <h2 id="stock-signals-heading">Tín hiệu chiến lược giao dịch</h2>
        <span className={`status-pill ${signals.dataStatus.toLowerCase()}`}>
          {dataStatusLabel(signals.dataStatus)}
        </span>
      </header>

      <ul className="signal-grid">
        {signals.evaluations.map((evaluation) => (
          <li
            key={evaluation.strategyCode}
            className={`signal-card ${evaluation.status.toLowerCase()}`}
          >
            <p className="signal-strategy-name">{strategyLabel(evaluation.strategyCode)}</p>
            <p className="signal-status">{evaluationStatusLabel(evaluation.status)}</p>

            {evaluation.status === "INSUFFICIENT_HISTORY" && (
              <p role="status" className="unavailable-msg">
                Chưa đủ dữ liệu lịch sử để đánh giá chiến lược này ({evaluation.reasonCode ?? "INSUFFICIENT_HISTORY"}).
              </p>
            )}
            {evaluation.status === "WITHHELD" && (
              <p role="status" className="unavailable-msg">
                Tín hiệu tạm giữ do xung đột nguồn dữ liệu ({evaluation.reasonCode ?? "WITHHELD"}).
              </p>
            )}
            {evaluation.status === "NO_SIGNAL" && (
              <p role="status" className="no-signal-msg">
                Điều kiện chiến lược hiện chưa được thỏa mãn — không phải lỗi.
              </p>
            )}

            {evaluation.signal && <SignalDetail signal={evaluation.signal} />}
          </li>
        ))}
      </ul>

      <p className="disclaimer" role="note">
        {DISCLAIMER_COPY[signals.disclaimerCode] ?? signals.disclaimerCode}
      </p>
    </section>
  );
}

function SignalDetail({ signal }: { signal: SignalData }) {
  const risk = riskLevelDisplay(signal.riskLevel);
  return (
    <div className="signal-detail">
      <p className="signal-direction">Chiều: {directionLabel(signal.direction)}</p>

      <dl className="signal-levels">
        <div>
          <dt>Vùng vào lệnh</dt>
          <dd>
            {formatDecimal(signal.entryLow)} – {formatDecimal(signal.entryHigh)}
          </dd>
        </div>
        <div>
          <dt>Dừng lỗ</dt>
          <dd>{formatDecimal(signal.stopLoss)}</dd>
        </div>
        <div>
          <dt>Mục tiêu 1</dt>
          <dd>{formatDecimal(signal.target1)}</dd>
        </div>
        <div>
          <dt>Mục tiêu 2</dt>
          <dd>{formatDecimal(signal.target2)}</dd>
        </div>
        <div>
          <dt>Tỷ lệ Rủi ro / Lợi nhuận</dt>
          <dd>1 : {formatDecimal(signal.riskReward)}</dd>
        </div>
      </dl>

      <div className={`risk-level-badge ${risk.className}`}>
        <span aria-hidden="true">{risk.icon}</span>
        <span>
          {risk.label}
          {signal.riskScore !== null ? ` (${signal.riskScore}/100)` : ""}
        </span>
        <span className="signal-strength">Độ mạnh tín hiệu: {signalStrengthLabel(signal.signalStrength)}</span>
      </div>

      {signal.riskLevel === null && (
        <p role="status" className="unavailable-msg">
          Chưa đủ yếu tố rủi ro để tính điểm tổng hợp
          {signal.reasonCodes.length > 0 ? ` (${signal.reasonCodes.join(", ")})` : " (INSUFFICIENT_RISK_FACTORS)"}.
        </p>
      )}

      <ul className="risk-factor-list" aria-label="Chi tiết yếu tố rủi ro">
        {signal.riskFactors.map((factor) => (
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

      <p className="signal-copy" role="note">
        Đây là một kịch bản định lượng dựa trên dữ liệu đã được chấp nhận, không phải khuyến nghị đầu tư hay đảm
        bảo kết quả.
      </p>
    </div>
  );
}
