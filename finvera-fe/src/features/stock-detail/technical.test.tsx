import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { StockTechnical } from "./components/stock-technical";
import type { IndicatorResult, StockTechnical as StockTechnicalData } from "./api/stock-detail";

function technical(indicators: IndicatorResult[], dataStatus: StockTechnicalData["meta"]["dataStatus"] = "CURRENT"): StockTechnicalData {
  return {
    meta: {
      contractVersion: "1.0",
      symbol: "FPT",
      asOf: "2026-08-17T07:15:00Z",
      tradingDate: "2026-08-14",
      timezone: "Asia/Ho_Chi_Minh",
      dataStatus,
      coherenceKey: "coh-technical-1",
      sources: ["FINVERA_ACCEPTED"],
      reasonCodes: [],
    },
    ruleVersion: "technical-indicators-v1",
    adjustmentStatus: "ADJUSTED",
    disclaimerCode: "QUANTITATIVE_DECISION_SUPPORT",
    indicators,
  };
}

function definedIndicator(overrides: Partial<IndicatorResult> = {}): IndicatorResult {
  return {
    indicatorCode: "MA20",
    applicability: "DEFINED",
    requiredBars: 20,
    availableBars: 20,
    windowStartDate: "2026-07-17",
    windowEndDate: "2026-08-14",
    reasonCode: null,
    components: [
      { componentCode: "VALUE", value: "41491.75", unit: "VND", displayPrecision: 2, applicability: "DEFINED", reasonCode: null },
    ],
    ...overrides,
  };
}

describe("stock technical section", () => {
  it("renders a full result with value, window, and rule-version context", () => {
    render(<StockTechnical technical={technical([definedIndicator()])} />);
    expect(screen.getByText(/MA20/)).toBeVisible();
    expect(screen.getByText(/41\.491,75/)).toBeVisible();
  });

  it("shows a specific unavailable reason for an under-lookback indicator, not a blank or zero", () => {
    const insufficient = definedIndicator({
      indicatorCode: "MA200",
      applicability: "MISSING",
      requiredBars: 200,
      availableBars: 199,
      windowStartDate: null,
      windowEndDate: null,
      reasonCode: "INSUFFICIENT_HISTORY",
      components: [],
    });
    render(<StockTechnical technical={technical([insufficient])} />);
    expect(screen.getByText(/INSUFFICIENT_HISTORY/)).toBeVisible();
    expect(screen.getByText(/cần 200 phiên/)).toBeVisible();
    expect(screen.getByText(/hiện có/)).toHaveTextContent("199");
    expect(screen.queryByText(/^0$/)).not.toBeInTheDocument();
  });

  it("marks status by text as well as class so meaning does not depend on color alone", () => {
    const insufficient = definedIndicator({ applicability: "MISSING", reasonCode: "INSUFFICIENT_HISTORY", components: [] });
    render(<StockTechnical technical={technical([definedIndicator(), insufficient])} />);
    const cards = screen.getAllByRole("listitem");
    expect(cards.some((card) => card.className.includes("defined"))).toBe(true);
    expect(cards.some((card) => card.className.includes("missing"))).toBe(true);
    // The textual reason is present independent of any color/class.
    expect(screen.getByText(/Không có dữ liệu/i)).toBeVisible();
  });

  it("shows a zero-denominator component as not applicable, never as zero", () => {
    const relativeVolume = definedIndicator({
      indicatorCode: "RELATIVE_VOLUME",
      applicability: "NOT_APPLICABLE",
      reasonCode: "NOT_APPLICABLE",
      components: [
        { componentCode: "VALUE", value: null, unit: "RATIO", displayPrecision: 2, applicability: "NOT_APPLICABLE", reasonCode: "NOT_APPLICABLE" },
      ],
    });
    render(<StockTechnical technical={technical([relativeVolume])} />);
    expect(screen.getByText(/Không áp dụng/i)).toBeVisible();
    expect(screen.queryByText(/^0([.,]0+)?$/)).not.toBeInTheDocument();
  });

  it("discloses the quantitative decision-support disclaimer and never suggests a buy/sell action", () => {
    render(<StockTechnical technical={technical([definedIndicator()])} />);
    expect(screen.getByText(/không phải khuyến nghị đầu tư/i)).toBeVisible();
    expect(screen.queryByText(/Mua|Bán/i)).not.toBeInTheDocument();
  });
});
