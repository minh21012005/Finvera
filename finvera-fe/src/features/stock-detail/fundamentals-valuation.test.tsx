import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { StockFundamentals } from "./components/stock-fundamentals";
import { StockValuation } from "./components/stock-valuation";
import type {
  StockFundamentals as StockFundamentalsData,
  StockValuation as StockValuationData,
} from "./api/stock-detail";

function mockFundamentals(overrides: Partial<StockFundamentalsData> = {}): StockFundamentalsData {
  return {
    meta: {
      contractVersion: "1.0",
      symbol: "FPT",
      asOf: "2026-08-17T07:15:00Z",
      tradingDate: "2026-08-14",
      timezone: "Asia/Ho_Chi_Minh",
      dataStatus: "CURRENT",
      coherenceKey: "coh-fundamentals-1",
      sources: ["FINVERA_ACCEPTED"],
      reasonCodes: [],
    },
    period: {
      label: "2026-Q2",
      periodType: "QUARTER",
      fiscalYear: 2026,
      fiscalQuarter: 2,
      periodStart: "2026-04-01",
      periodEnd: "2026-06-30",
      reportKind: "CONSOLIDATED",
      auditStatus: "REVIEWED",
      currency: "VND",
      restated: false,
    },
    metrics: [
      {
        metricCode: "REVENUE",
        value: "16250000000000.00",
        unit: "VND",
        displayPrecision: 2,
        applicability: "DEFINED",
        reasonCode: null,
      },
      {
        metricCode: "EPS",
        value: "1300.00",
        unit: "VND",
        displayPrecision: 2,
        applicability: "DEFINED",
        reasonCode: null,
      },
    ],
    ...overrides,
  };
}

function mockValuation(overrides: Partial<StockValuationData> = {}): StockValuationData {
  return {
    meta: {
      contractVersion: "1.0",
      symbol: "FPT",
      asOf: "2026-08-17T07:15:00Z",
      tradingDate: "2026-08-14",
      timezone: "Asia/Ho_Chi_Minh",
      dataStatus: "CURRENT",
      coherenceKey: "coh-valuation-1",
      sources: ["FINVERA_ACCEPTED"],
      reasonCodes: [],
    },
    ruleVersion: "valuation-v1",
    published: true,
    classification: "FAIR_VALUED",
    score: "48.25",
    displayedScore: 48,
    confidence: 72,
    disclaimerCode: "QUANTITATIVE_DECISION_SUPPORT",
    basis: {
      usedOwnHistory: true,
      usedSector: false,
      sector: null,
      sectorScheme: null,
      sectorSchemeVersion: null,
      sectorConstituentCount: null,
      historyPointCount: 600,
    },
    metrics: [
      {
        metricCode: "PE",
        value: "15.12",
        applicability: "DEFINED",
        ownHistoryPercentile: "48.25",
        sectorPercentile: null,
        effectiveWeight: "0.571428571429",
        reasonCode: null,
      },
      {
        metricCode: "PB",
        value: "2.42",
        applicability: "DEFINED",
        ownHistoryPercentile: "40.00",
        sectorPercentile: null,
        effectiveWeight: "0.428571428571",
        reasonCode: null,
      },
    ],
    ...overrides,
  };
}

describe("stock fundamentals section", () => {
  it("renders period information, audit status, and metric values", () => {
    render(<StockFundamentals fundamentals={mockFundamentals()} />);
    expect(screen.getByText(/2026-Q2/)).toBeVisible();
    expect(screen.getByText(/Hợp nhất/i)).toBeVisible();
    expect(screen.getByText(/Soát xét/i)).toBeVisible();
    expect(screen.getByText(/REVENUE|Doanh thu/i)).toBeVisible();
  });

  it("shows not applicable for negative earnings or missing metric, never 0", () => {
    const data = mockFundamentals({
      metrics: [
        {
          metricCode: "DIVIDEND_PER_SHARE",
          value: null,
          unit: "VND",
          displayPrecision: 2,
          applicability: "NOT_APPLICABLE",
          reasonCode: "NO_DIVIDEND",
        },
      ],
    });
    render(<StockFundamentals fundamentals={data} />);
    expect(screen.getByText(/Không áp dụng/i)).toBeVisible();
    expect(screen.queryByText(/^0([.,]0+)?$/)).not.toBeInTheDocument();
  });

  it("renders restated badge when filing is a restatement", () => {
    const data = mockFundamentals({
      period: {
        ...mockFundamentals().period!,
        restated: true,
      },
    });
    render(<StockFundamentals fundamentals={data} />);
    expect(screen.getByText(/Điều chỉnh|Restated/i)).toBeVisible();
  });
});

describe("stock valuation section", () => {
  it("renders published valuation with classification, score, and confidence", () => {
    render(<StockValuation valuation={mockValuation()} />);
    expect(screen.getByText(/Hợp lý|Fair/i)).toBeVisible();
    expect(screen.getByText(/Điểm đắt\/rẻ/i)).toHaveTextContent("48");
    expect(screen.getByText(/Độ hoàn thiện/i)).toHaveTextContent("72%");
  });

  it("discloses the used comparison basis", () => {
    render(<StockValuation valuation={mockValuation()} />);
    expect(screen.getByText(/Lịch sử riêng|Own History/i)).toBeVisible();
  });

  it("shows reason codes and never a guessed label when withheld", () => {
    const withheld = mockValuation({
      published: false,
      classification: null,
      score: null,
      displayedScore: null,
      confidence: null,
      meta: {
        ...mockValuation().meta,
        reasonCodes: ["NO_COMPARISON_BASIS"],
        dataStatus: "UNAVAILABLE",
      },
    });
    render(<StockValuation valuation={withheld} />);
    expect(screen.getByText(/Chưa đủ cơ sở so sánh|NO_COMPARISON_BASIS/i)).toBeVisible();
    expect(screen.queryByText(/Định giá thấp|Định giá cao|Định giá hợp lý/i)).not.toBeInTheDocument();
  });

  it("discloses the quantitative decision-support disclaimer without buy/sell instructions", () => {
    render(<StockValuation valuation={mockValuation()} />);
    expect(screen.getByText(/không phải.*khuyến nghị đầu tư/i)).toBeVisible();
    expect(screen.queryByText(/Mua|Bán/i)).not.toBeInTheDocument();
  });
});
