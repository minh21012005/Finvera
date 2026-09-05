import { describe, expect, it } from "vitest";
import { parseStockValuation } from "./stock-detail";

/**
 * Feature 023 (contract valuation-v3, FR-006): the parser accepts valuation-v2 rows (no basis
 * fields) and valuation-v3 rows (with them) side by side, and still refuses anything unknown —
 * the previous parser threw on every version but v2, which would have blanked the valuation
 * section for every stock on the day the backend moved to v3 (specs/023 research R-005).
 */
function payload(ruleVersion: string, metric: Record<string, unknown>) {
  return {
    meta: {
      contractVersion: "1.0",
      symbol: "VNM",
      asOf: "2026-08-28T08:15:00Z",
      tradingDate: "2026-08-28",
      timezone: "Asia/Ho_Chi_Minh",
      dataStatus: "CURRENT",
      coherenceKey: "coh-1",
      sources: ["FINVERA_ACCEPTED"],
      reasonCodes: [],
    },
    ruleVersion,
    published: true,
    classification: "FAIR_VALUED",
    score: "48.20",
    displayedScore: 48,
    confidence: 72,
    disclaimerCode: "QUANTITATIVE_DECISION_SUPPORT",
    basis: { usedOwnHistory: true, usedSector: false, historyPointCount: 750 },
    metrics: [metric],
  };
}

describe("parseStockValuation rule versions", () => {
  it("parses a valuation-v2 row and leaves the v3 basis fields null", () => {
    const parsed = parseStockValuation(payload("valuation-v2", {
      metricCode: "PE", value: "13.18", applicability: "DEFINED", ownHistoryPercentile: "14.20",
      sectorPercentile: null, effectiveWeight: "0.571428571429", reasonCode: null,
    }));
    expect(parsed.ruleVersion).toBe("valuation-v2");
    expect(parsed.metrics[0].ownHistoryBasis).toBeNull();
    expect(parsed.metrics[0].ownHistoryComparisonValue).toBeNull();
  });

  it("parses a valuation-v3 row with the basis and the ranked value", () => {
    const parsed = parseStockValuation(payload("valuation-v3", {
      metricCode: "PE", value: "13.18", applicability: "DEFINED", ownHistoryPercentile: "69.83",
      sectorPercentile: null, effectiveWeight: "0.571428571429", reasonCode: null,
      ownHistoryBasis: "FISCAL_YEAR", ownHistoryComparisonValue: "15.47",
    }));
    expect(parsed.ruleVersion).toBe("valuation-v3");
    expect(parsed.metrics[0].ownHistoryBasis).toBe("FISCAL_YEAR");
    expect(parsed.metrics[0].ownHistoryComparisonValue).toBe("15.47");
  });

  it("still refuses an unknown rule version and an unknown basis label", () => {
    expect(() => parseStockValuation(payload("valuation-v9", {
      metricCode: "PE", applicability: "MISSING", reasonCode: "MISSING_EPS",
    }))).toThrow(/ruleVersion/);
    expect(() => parseStockValuation(payload("valuation-v3", {
      metricCode: "PE", value: "13.18", applicability: "DEFINED", ownHistoryBasis: "QUARTER_TTM",
    }))).toThrow(/ownHistoryBasis/);
  });
});
