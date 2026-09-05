import type { EvidenceFactor } from "../../analyst/api/analyst";
import type { StockValuation } from "../api/stock-detail";
import type { Signal } from "../api/stock-signals";
import { formatDecimal, valuationLabel, valuationMetricLabel } from "./stock-format";
import { directionLabel, riskFactorLabel, riskLevelDisplay, strategyLabel } from "./signal-format";
import { reasonCodeLabel } from "../../../shared/format/reason-codes";

/**
 * Evidence handed to the AI "explain" endpoint (Feature 007 FR-006). The AI may only
 * restate what is listed here, so the list must carry the RESULT being explained — the
 * label/score/confidence, the comparison basis, per-metric percentiles and weights, and the
 * engine's own disclosure codes — not just the raw metric values (Q-43: the first version
 * sent only PE/PB/dividend yield and the model rightly answered that it had not been given
 * the classification).
 */
export function buildValuationEvidence(valuation: StockValuation): EvidenceFactor[] {
  if (!valuation.published || valuation.classification === null) {
    return [];
  }
  const factors: EvidenceFactor[] = [];
  factors.push({
    factorCode: "VALUATION_CLASSIFICATION",
    description:
      `Kết luận: ${valuationLabel(valuation.classification)} — điểm đắt/rẻ ${valuation.displayedScore ?? "—"}/100` +
      (valuation.confidence !== null ? `, độ hoàn thiện dữ liệu ${valuation.confidence}%` : "") +
      ` (quy tắc ${valuation.ruleVersion})`,
  });

  const bases: string[] = [];
  if (valuation.basis.usedOwnHistory) {
    bases.push(`lịch sử riêng của mã (${valuation.basis.historyPointCount ?? 0} phiên)`);
  }
  if (valuation.basis.usedSector) {
    bases.push(`ngành ${valuation.basis.sector ?? ""} (${valuation.basis.sectorConstituentCount ?? 0} mã cùng ngành)`.trim());
  }
  if (bases.length > 0) {
    factors.push({ factorCode: "COMPARISON_BASIS", description: `Cơ sở so sánh: ${bases.join("; ")}` });
  }

  for (const m of valuation.metrics) {
    const label = valuationMetricLabel(m.metricCode);
    if (m.applicability === "DEFINED") {
      const parts = [`${label}: ${formatDecimal(m.value)}`];
      if (m.ownHistoryPercentile !== null) {
        // valuation-v3: the percentile of a flow metric belongs to its fiscal-year value, not to the
        // headline just stated — hand the model the number that was actually ranked.
        const rankedOn = m.ownHistoryBasis === "FISCAL_YEAR" && m.ownHistoryComparisonValue !== null
          ? `; tính trên ${label} năm ${formatDecimal(m.ownHistoryComparisonValue)}, không phải ${formatDecimal(m.value)}`
          : "";
        parts.push(`phân vị lịch sử ${formatDecimal(m.ownHistoryPercentile)}% (càng cao càng đắt so với chính mã${rankedOn})`);
      }
      if (m.sectorPercentile !== null) {
        parts.push(`phân vị ngành ${formatDecimal(m.sectorPercentile)}%`);
      }
      if (m.effectiveWeight !== null) {
        const weightNum = Number(m.effectiveWeight);
        const weightPct = !isNaN(weightNum) ? ` (${formatDecimal((weightNum * 100).toFixed(2))}%)` : "";
        parts.push(`trọng số ${formatDecimal(m.effectiveWeight)}${weightPct}`);
      } else if (m.metricCode === "DIVIDEND_YIELD") {
        parts.push("chỉ hiển thị, không tính điểm");
      }
      factors.push({ factorCode: m.metricCode, description: parts.join(" — ") });
    } else if (m.applicability === "NOT_APPLICABLE") {
      factors.push({
        factorCode: `${m.metricCode}_NOT_APPLICABLE`,
        description: `${label} không áp dụng cho công ty này${m.reasonCode ? ` (${reasonCodeLabel(m.reasonCode)})` : ""}`,
      });
    }
  }

  // Every engine note is handed over (contract reason-code-presentation-v1 P-2: never dropped).
  const notes = valuation.meta.reasonCodes.map(reasonCodeLabel);
  if (notes.length > 0) {
    factors.push({ factorCode: "VALUATION_NOTES", description: `Ghi chú của bộ tính: ${notes.join("; ")}` });
  }
  return factors;
}

export function buildSignalEvidence(signal: Signal): EvidenceFactor[] {
  const risk = riskLevelDisplay(signal.riskLevel);
  const factors: EvidenceFactor[] = [
    {
      factorCode: "SIGNAL",
      description:
        `Tín hiệu ${strategyLabel(signal.strategyCode)} — ${directionLabel(signal.direction)}; ` +
        `vùng vào ${formatDecimal(signal.entryLow)}–${formatDecimal(signal.entryHigh)}, ` +
        `dừng lỗ ${formatDecimal(signal.stopLoss)}, mục tiêu ${formatDecimal(signal.target1)} / ${formatDecimal(signal.target2)}, ` +
        `lợi nhuận/rủi ro ${formatDecimal(signal.riskReward)}` +
        (signal.riskScore !== null ? `; mức rủi ro ${risk.label} (điểm ${signal.riskScore}/100)` : "") +
        ` (quy tắc ${signal.ruleVersion}, phiên ${signal.asOfTradingDate})`,
    },
  ];
  for (const [key, value] of Object.entries(signal.supportingEvidence)) {
    factors.push({ factorCode: `CONDITION_${key.toUpperCase()}`, description: `Điều kiện vào lệnh ${key}: ${value}` });
  }
  for (const factor of signal.riskFactors) {
    if (factor.applicability === "DEFINED") {
      factors.push({
        factorCode: factor.factorCode,
        description: `${riskFactorLabel(factor.factorCode)}: ${factor.inputValue !== null ? formatDecimal(factor.inputValue) : "—"} (điểm ${factor.factorScore}/100)`,
      });
    }
  }
  return factors;
}
