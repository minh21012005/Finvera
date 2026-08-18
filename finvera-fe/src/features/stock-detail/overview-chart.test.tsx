import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { StockOverview } from "./components/stock-overview";
import { StockChart } from "./components/stock-chart";
import { formatDecimal, formatPercent, formatVnd } from "./format/stock-format";
import type { StockChart as StockChartData, StockOverview as StockOverviewData } from "./api/stock-detail";

function overview(overrides: Partial<StockOverviewData["price"]> = {}, dataStatus: StockOverviewData["meta"]["dataStatus"] = "CURRENT"): StockOverviewData {
  return {
    meta: {
      contractVersion: "1.0",
      symbol: "FPT",
      asOf: "2026-08-17T07:15:00Z",
      tradingDate: "2026-08-17",
      timezone: "Asia/Ho_Chi_Minh",
      dataStatus,
      coherenceKey: "coh-1",
      sources: ["FINVERA_ACCEPTED"],
      reasonCodes: [],
    },
    profile: {
      symbol: "FPT",
      companyName: "CTCP FPT",
      companyNameEn: "FPT Corporation",
      exchange: "HOSE",
      sector: "Information Technology",
      sectorScheme: "finvera-sector-v1",
      listingStatus: "LISTED",
      sharesOutstanding: 1462000000,
    },
    price: {
      currency: "VND",
      last: "123600.000000",
      referencePrice: "122500.000000",
      absoluteChange: "1100.000000",
      percentageChange: "0.897959",
      direction: "UP",
      volume: 2270000,
      valueVnd: "280457200000.0000",
      marketCapVnd: "180703200000000.000000",
      applicability: "DEFINED",
      changeBasisReason: null,
      ...overrides,
    },
    session: { state: "OPEN", tradingDate: "2026-08-17", calendarVersion: "finvera-calendar-v1" },
  };
}

function chart(bars: StockChartData["bars"] = [], adjustmentStatus: StockChartData["adjustmentStatus"] = "ADJUSTED"): StockChartData {
  return {
    meta: {
      contractVersion: "1.0",
      symbol: "FPT",
      asOf: "2026-08-17T07:15:00Z",
      tradingDate: bars.at(-1)?.tradingDate ?? null,
      timezone: "Asia/Ho_Chi_Minh",
      dataStatus: bars.length === 0 ? "UNAVAILABLE" : "CURRENT",
      coherenceKey: "coh-chart-1",
      sources: ["FINVERA_ACCEPTED"],
      reasonCodes: [],
    },
    window: "1M",
    adjustmentStatus,
    bars,
  };
}

describe("stock overview formatting", () => {
  it("formats VND decimals, percentages, and missing values in Vietnamese locale", () => {
    expect(formatDecimal("123600.000000")).toBe("123.600");
    expect(formatVnd("280457200000.0000")).toContain("VND");
    expect(formatPercent("0.897959")).toBe("+0,89%");
    expect(formatPercent("-3.100000")).toBe("−3,1%");
    expect(formatDecimal(null)).toBe("Không có dữ liệu");
  });
});

describe("stock overview card", () => {
  it("renders price, change, and a non-color direction indicator", () => {
    render(<StockOverview overview={overview()} />);
    expect(screen.getByText(/CTCP FPT/)).toBeVisible();
    expect(screen.getByText(/123\.600/)).toBeVisible();
    expect(screen.getByText(/Tăng/i)).toBeVisible();
    expect(screen.getByText("↑")).toBeVisible();
  });

  it("shows change fields as unavailable with a reason rather than zero when the basis is missing", () => {
    render(
      <StockOverview
        overview={overview(
          {
            referencePrice: null,
            absoluteChange: null,
            percentageChange: null,
            direction: "UNCHANGED",
            applicability: "MISSING",
            changeBasisReason: "REFERENCE_PRICE_UNAVAILABLE",
          },
          "PARTIAL",
        )}
      />,
    );
    expect(screen.getByText(/REFERENCE_PRICE_UNAVAILABLE/)).toBeVisible();
    expect(screen.queryByText("0 VND")).not.toBeInTheDocument();
  });

  it("labels the freshness state visibly and never claims live for a stale snapshot", () => {
    render(<StockOverview overview={overview({}, "STALE")} />);
    expect(screen.getByText(/Cũ/)).toBeVisible();
  });
});

describe("stock chart", () => {
  it("renders an ascending series and discloses the adjustment status", () => {
    const bars: StockChartData["bars"] = [
      { tradingDate: "2026-08-13", open: "122000.000000", high: "123000.000000", low: "121500.000000", close: "122500.000000", volume: 2000000 },
      { tradingDate: "2026-08-14", open: "122500.000000", high: "123800.000000", low: "122300.000000", close: "123600.000000", volume: 2270000 },
    ];
    render(<StockChart chart={chart(bars)} />);
    expect(screen.getByRole("img", { name: /biểu đồ giá/i })).toBeInTheDocument();
    expect(screen.getByText(/đã điều chỉnh/i)).toBeVisible();
  });

  it("shows an explicit unavailable state instead of an empty chart when there are no bars", () => {
    render(<StockChart chart={chart([])} />);
    expect(screen.getByText(/Không có dữ liệu biểu đồ/i)).toBeVisible();
  });
});
