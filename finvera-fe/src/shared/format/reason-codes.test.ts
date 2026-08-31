import { describe, expect, it } from "vitest";
import {
  NO_REASON_GIVEN,
  REASON_CODE_LABELS,
  applicabilityNote,
  dataStatusLabel,
  describeReasonCodes,
  isKnownReasonCode,
  reasonCodeLabel,
} from "./reason-codes";

/**
 * Inventory of every UI-reachable code (specs/014-reason-code-presentation/research.md R-002).
 * A code added to a backend emitter must be added here AND to the dictionary.
 */
const INVENTORY = [
  // market — index
  "MISSING_INDEX", "MISSING_REFERENCE_LEVEL", "MISSING_INDEX_LEVEL", "NO_ACCEPTED_INDEX_DATA",
  "MULTIPLE_ACCEPTED_SOURCES", "VNSTOCK_PRIVATE_PACKAGE",
  // market — breadth
  "UNRESOLVED_IDENTITY", "MISSING_PRICE", "MISSING_REFERENCE_PRICE", "MISSING_PRIOR_CLOSE", "BREADTH_NOT_AVAILABLE",
  "NO_ACTIVE_COMMON_EQUITY_UNIVERSE", "NO_DAILY_BAR_HISTORY", "NO_DAILY_BAR_HISTORY_FOR_LATEST_SESSION",
  "PROVIDER_AGGREGATE_BREADTH",
  // market — regime
  "MANDATORY_INPUT_UNAVAILABLE", "REQUIRED_INPUT_NOT_TIMELY_AVAILABLE", "INSUFFICIENT_COMPONENT_COMPLETENESS",
  "TREND_COMPONENT_UNAVAILABLE", "AGGREGATE_BREADTH_COMPONENT_UNAVAILABLE", "SOURCE_CONFLICT", "REGIME_NOT_AVAILABLE",
  "REGIME_UNAVAILABLE", "ASSESSMENT_QUALITY_NOT_FORECAST_PROBABILITY",
  "QUANTITATIVE_DECISION_SUPPORT_NOT_INVESTMENT_ADVICE", "QUANTITATIVE_DECISION_SUPPORT",
  // market — live / provider
  "TCBS_STREAM_RECEIVE_TIME", "TCBS_STREAM_ORDERING_UNAVAILABLE", "LIVE_OVERLAY_DISABLED", "PROVIDER_AUTH_REQUIRED",
  "PROVIDER_CONNECTIVITY_FAILED", "TCBS_THESIS_LIVE_QUOTE",
  // stock overview / price
  "PRICE_UNAVAILABLE", "PRICE_STALE", "PRICE_DELAYED", "REFERENCE_PRICE_UNAVAILABLE", "REFERENCE_PRICE_INVALID",
  "PROFILE_UNAVAILABLE", "PRICE_LIMITS_UNAVAILABLE", "AT_CEILING", "AT_FLOOR", "ADJUSTMENT_BASIS_UNAVAILABLE",
  // technical
  "INSUFFICIENT_HISTORY", "NOT_APPLICABLE", "MISSING", "VOLUME_UNAVAILABLE", "NO_BARS_AVAILABLE",
  // fundamentals
  "FUNDAMENTALS_UNAVAILABLE", "FUNDAMENTALS_DELAYED", "FUNDAMENTALS_STALE", "ANNUAL_BASIS", "PROVIDER_TRAILING_EPS",
  "NO_DATA", "NOT_REPORTED", "NEGATIVE_OR_ZERO_PRIOR_EPS", "NEGATIVE_OR_ZERO_PRIOR_REVENUE", "PROVIDER_REPORTED",
  "kbs-trailing-ratio-as-annualized-v1", "kbs-ebitda-margin-x-net-revenue-v1", "kbs-fcf-ocf-plus-capex-v1",
  "kbs-fcf-ocf-plus-capex-v2", "kbs-yearly-statement-labels-mirrored-v1",
  "vci-trailing-eps-parent-profit-over-shares-v1", "vci-bvps-parent-equity-over-shares-v1",
  "vci-roe-parent-profit-over-average-equity-v1", "vci-roe-parent-profit-over-average-equity-v1-end",
  "vci-roa-net-profit-over-average-assets-v1", "vci-roa-net-profit-over-average-assets-v1-end", "vci-margin-v1",
  "vci-debt-to-equity-v1", "vci-fcf-ocf-plus-capex-v1", "vci-ebitda-operating-profit-plus-da-v1",
  "vci-current-ratio-v1",
  "vci-quick-ratio-v1",
  "vci-cash-ratio-v1",
  "vci-debt-to-assets-v1",
  "vci-liabilities-to-equity-v1",
  "vci-equity-to-assets-v1",
  "vci-interest-coverage-v1",
  "vci-asset-turnover-v1",
  "vci-asset-turnover-v1-end",
  "vci-inventory-turnover-v1",
  "vci-inventory-turnover-v1-end",
  "vci-receivables-turnover-v1",
  "vci-receivables-turnover-v1-end",
  "vci-roce-v1",
  "vci-roce-v1-end",
  "vci-balance-growth-yoy-v1",
  "vci-nim-earning-assets-v1",
  "vci-nim-earning-assets-v1-end",
  "vci-cir-v1",
  "vci-dps-cash-dividends-over-shares-v1",
  "vci-ldr-v1", "SOURCE_SUPERSEDED", "SOURCE_RETIRED",
  // valuation
  "HISTORY_BASIS_INSUFFICIENT", "SECTOR_BASIS_INSUFFICIENT", "NO_COMPARISON_BASIS", "CORE_METRIC_UNAVAILABLE",
  "INSUFFICIENT_METRIC_COVERAGE", "REDUCED_METRIC_SET", "HISTORY_SHARES_OUTSTANDING_HELD_CURRENT", "MISSING_EPS",
  "NEGATIVE_OR_ZERO_EPS", "MISSING_BVPS", "NEGATIVE_OR_ZERO_BVPS", "MISSING_EBITDA", "NEGATIVE_OR_ZERO_EBITDA",
  "MISSING_EV_INPUTS", "PE_NOT_DEFINED", "MISSING_GROWTH", "NEGATIVE_OR_ZERO_GROWTH", "MISSING_DIVIDEND",
  "MISSING_REVENUE",
  "NEGATIVE_OR_ZERO_REVENUE",
  "MISSING_MARKET_CAP_INPUTS", "ZERO_PRICE",
  "SHARES_OUTSTANDING_UNAVAILABLE", "SHARES_OUTSTANDING_UNVERIFIED", "OWN_HISTORY", "SECTOR",
  // screener
  "SECTOR_UNCLASSIFIED", "SHARES_OUTSTANDING_MISSING", "VALUATION_WITHHELD", "NO_CANDIDATES",
  // signal / risk
  "SIGNAL", "NO_SIGNAL", "WITHHELD", "INSUFFICIENT_RISK_FACTORS", "INPUT_UNAVAILABLE", "TRAILING_AVERAGE_ATR_ZERO",
  "HIGHEST_CLOSE_INVALID",
  // portfolio / watchlist
  "POSITION_PRICE_UNAVAILABLE", "POSITION_PRICE_DELAYED", "POSITION_PRICE_STALE", "NO_POSITIONS",
  "NO_SIGNALS_FOR_POSITIONS", "BENCHMARK_UNAVAILABLE", "PARTIAL_DATA_GAP", "NET_CONTRIBUTED_CAPITAL_METHOD",
  "MISSING_SYMBOL",
  // analyst tool bridge
  "UNKNOWN_SYMBOL", "NO_FUNDAMENTAL_REPORT", "NO_VALUATION",
];

describe("reason-code-presentation-v1", () => {
  it("SC-001: every inventoried code has wording that is not the code itself", () => {
    const missing = INVENTORY.filter((code) => !isKnownReasonCode(code) || reasonCodeLabel(code) === code);
    expect(missing).toEqual([]);
  });

  it("the dictionary has no entry outside the inventory (keeps the two lists in step)", () => {
    const extra = Object.keys(REASON_CODE_LABELS).filter((code) => !INVENTORY.includes(code));
    expect(extra).toEqual([]);
  });

  it("no wording is an empty string or a bare identifier", () => {
    for (const [code, label] of Object.entries(REASON_CODE_LABELS)) {
      expect(label.trim().length, code).toBeGreaterThan(3);
      expect(/^[A-Z][A-Z0-9_]*$/.test(label), code).toBe(false);
    }
  });

  it("SC-002: an unknown code is shown as itself, never hidden", () => {
    expect(reasonCodeLabel("RENORMALIZED_MISSING_VOLATILITY")).toBe("RENORMALIZED_MISSING_VOLATILITY");
    expect(describeReasonCodes(["NO_COMPARISON_BASIS", "SOME_FUTURE_CODE"])).toBe(
      "Chưa đủ cơ sở so sánh (lịch sử riêng lẫn ngành); SOME_FUTURE_CODE",
    );
  });

  it("SC-002: an empty list where a reason is expected is an explicit sentence", () => {
    expect(describeReasonCodes([])).toBe(NO_REASON_GIVEN);
    expect(describeReasonCodes(null)).toBe(NO_REASON_GIVEN);
    expect(describeReasonCodes(undefined, "—")).toBe("—");
  });

  it("P-4: applicability notes carry the wording, not the code", () => {
    expect(applicabilityNote("DEFINED", null)).toBeNull();
    expect(applicabilityNote("NOT_APPLICABLE", "NEGATIVE_OR_ZERO_EPS")).toBe("Không áp dụng — EPS âm hoặc bằng 0");
    expect(applicabilityNote("MISSING", "INSUFFICIENT_HISTORY")).toBe("Không có dữ liệu — Chưa đủ lịch sử dữ liệu");
    expect(applicabilityNote("MISSING", null)).toBe("Không có dữ liệu");
    expect(applicabilityNote("MISSING", "BRAND_NEW")).toBe("Không có dữ liệu — BRAND_NEW");
  });

  it("P-5: data-status wording with a raw fallback", () => {
    expect(dataStatusLabel("PARTIAL")).toBe("Một phần");
    expect(dataStatusLabel("UNAVAILABLE")).toBe("Không có dữ liệu");
    expect(dataStatusLabel("FROZEN")).toBe("FROZEN");
  });
});
