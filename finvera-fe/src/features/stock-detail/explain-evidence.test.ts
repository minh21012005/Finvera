import { describe, expect, it } from "vitest";
import { buildSignalEvidence, buildValuationEvidence } from "./format/explain-evidence";
import type { StockValuation } from "./api/stock-detail";
import type { Signal } from "./api/stock-signals";

function valuation(overrides: Partial<StockValuation> = {}): StockValuation {
  return {
    meta: {
      contractVersion: "1.0",
      symbol: "MBB",
      asOf: "2026-08-31T00:04:00Z",
      tradingDate: "2026-08-28",
      timezone: "Asia/Ho_Chi_Minh",
      dataStatus: "CURRENT",
      coherenceKey: "coh-1",
      sources: ["FINVERA_ACCEPTED"],
      reasonCodes: ["HISTORY_SHARES_OUTSTANDING_HELD_CURRENT", "REDUCED_METRIC_SET"],
    },
    ruleVersion: "valuation-v2",
    published: true,
    classification: "UNDER_VALUED",
    score: "12.40",
    displayedScore: 12,
    confidence: 69,
    disclaimerCode: "QUANTITATIVE_DECISION_SUPPORT",
    basis: {
      usedOwnHistory: true, usedSector: false, sector: null, sectorScheme: null, sectorSchemeVersion: null,
      sectorConstituentCount: null, historyPointCount: 750,
    },
    metrics: [
      { metricCode: "PE", value: null, applicability: "NOT_APPLICABLE", ownHistoryPercentile: null, sectorPercentile: null, effectiveWeight: null, reasonCode: "NEGATIVE_OR_ZERO_EPS", ownHistoryBasis: null, ownHistoryComparisonValue: null },
      { metricCode: "PB", value: "1.270000", applicability: "DEFINED", ownHistoryPercentile: "12.4", sectorPercentile: null, effectiveWeight: "1.000000000000", reasonCode: null, ownHistoryBasis: "LATEST_REPORT", ownHistoryComparisonValue: "1.270000" },
      { metricCode: "EV_EBITDA", value: null, applicability: "MISSING", ownHistoryPercentile: null, sectorPercentile: null, effectiveWeight: null, reasonCode: "MISSING_EBITDA", ownHistoryBasis: null, ownHistoryComparisonValue: null },
      { metricCode: "DIVIDEND_YIELD", value: "1.190000", applicability: "DEFINED", ownHistoryPercentile: null, sectorPercentile: null, effectiveWeight: null, reasonCode: null, ownHistoryBasis: null, ownHistoryComparisonValue: null },
    ],
    ...overrides,
  } as StockValuation;
}

describe("explain evidence for valuation-v3 (specs/023)", () => {
  it("attributes a fiscal-year-basis percentile to the value that was ranked, not to the headline", () => {
    const factors = buildValuationEvidence(valuation({
      ruleVersion: "valuation-v3",
      metrics: [
        { metricCode: "PE", value: "13.18", applicability: "DEFINED", ownHistoryPercentile: "69.83", sectorPercentile: null,
          effectiveWeight: "1.000000000000", reasonCode: null, ownHistoryBasis: "FISCAL_YEAR", ownHistoryComparisonValue: "15.47" },
      ],
    }));
    const byCode = Object.fromEntries(factors.map((f) => [f.factorCode, f.description]));
    expect(byCode.PE).toContain("13,18");                 // the headline is still stated
    expect(byCode.PE).toContain("phân vị lịch sử 69,83%");
    expect(byCode.PE).toContain("năm 15,47");             // ...but the percentile belongs to the FY value
    expect(byCode.PE).toContain("không phải 13,18");
  });

  it("adds no basis note for a point-in-time metric ranked on its own value", () => {
    const factors = buildValuationEvidence(valuation({ ruleVersion: "valuation-v3" }));
    const byCode = Object.fromEntries(factors.map((f) => [f.factorCode, f.description]));
    expect(byCode.PB).toContain("phân vị lịch sử 12,4%");
    expect(byCode.PB).not.toContain("năm");
  });
});

describe("explain evidence for valuation (Q-43)", () => {
  it("leads with the classification, score and confidence the AI is asked to explain", () => {
    const factors = buildValuationEvidence(valuation());
    expect(factors[0].factorCode).toBe("VALUATION_CLASSIFICATION");
    expect(factors[0].description).toContain("Định giá thấp");
    expect(factors[0].description).toContain("12/100");
    expect(factors[0].description).toContain("69%");
    expect(factors[0].description).toContain("valuation-v2");
  });

  it("discloses the comparison basis, percentiles, weights, not-applicable core metrics and engine notes", () => {
    const factors = buildValuationEvidence(valuation());
    const byCode = Object.fromEntries(factors.map((f) => [f.factorCode, f.description]));
    expect(byCode.COMPARISON_BASIS).toContain("750 phiên");
    expect(byCode.PB).toContain("phân vị lịch sử");
    expect(byCode.PB).toContain("trọng số");
    expect(byCode.PE_NOT_APPLICABLE).toContain("EPS âm hoặc bằng 0");
    expect(byCode.DIVIDEND_YIELD).toContain("không tính điểm");
    expect(byCode.VALUATION_NOTES).toContain("thu hẹp");
    expect(byCode.EV_EBITDA).toBeUndefined();               // missing data is not evidence
  });

  it("returns nothing when the assessment is withheld", () => {
    expect(buildValuationEvidence(valuation({ published: false, classification: null }))).toEqual([]);
  });
});

describe("explain evidence for signals", () => {
  it("leads with the signal itself, then entry conditions, then defined risk factors", () => {
    const signal: Signal = {
      strategyCode: "TREND_FOLLOWING", ruleVersion: "strategy-signal-v1", direction: "LONG",
      entryLow: "99.500000", entryHigh: "100.500000", stopLoss: "96.000000", target1: "108.000000", target2: "112.000000",
      riskReward: "2.0000", riskScore: 25, riskLevel: "LOW", signalStrength: "STRONG",
      riskFactors: [
        { factorCode: "VOLATILITY", inputValue: "2.5", factorScore: 10, applicability: "DEFINED", reasonCode: null },
        { factorCode: "MARKET_REGIME", inputValue: null, factorScore: null, applicability: "MISSING", reasonCode: "REGIME_UNAVAILABLE" },
      ],
      supportingEvidence: { trend: "UPTREND" }, reasonCodes: [], asOfTradingDate: "2026-08-28", calculatedAt: "2026-08-28T08:15:00Z",
    };
    const factors = buildSignalEvidence(signal);
    expect(factors.map((f) => f.factorCode)).toEqual(["SIGNAL", "CONDITION_TREND", "VOLATILITY"]);
    expect(factors[0].description).toContain("Theo xu hướng");
    expect(factors[0].description).toContain("dừng lỗ");
  });
});
