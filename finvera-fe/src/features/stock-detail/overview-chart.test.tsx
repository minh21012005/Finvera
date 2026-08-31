import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { StockOverview } from "./components/stock-overview";
import { StockChart } from "./components/stock-chart";
import { formatDecimal, formatDate, formatPercent, formatVnd } from "./format/stock-format";
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
    expect(formatDate("2026-08-17")).toBe("17/08/2026");
    expect(formatDate(null)).toBe("Không có dữ liệu");
  });
});

describe("stock overview card", () => {
  it("shows session price limits, foreign room, and a textual at-limit cue (Feature 008 US3)", () => {
    render(
      <StockOverview
        overview={overview({
          last: "36900.000000",
          ceilingPrice: "36900.000000",
          floorPrice: "32100.000000",
          foreignRoom: 12345,
          limitState: "AT_CEILING",
        })}
      />,
    );
    expect(screen.getByText(/Trần \/ Sàn/)).toBeVisible();
    expect(screen.getByText(/36\.900.*\/.*32\.100/)).toBeVisible();
    expect(screen.getByRole("status", { name: "" })).toBeDefined();
    expect(screen.getByText(/đang ở giá trần/)).toBeVisible();
    expect(screen.getByText("12.345")).toBeVisible();
  });

  it("renders limits as unavailable text rather than zero when no live frame exists", () => {
    render(<StockOverview overview={overview()} />);
    const cells = screen.getAllByText("Không có dữ liệu");
    expect(cells.length).toBeGreaterThanOrEqual(2); // Trần/Sàn and Room
    expect(screen.queryByText(/đang ở giá/)).not.toBeInTheDocument();
  });

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
    expect(screen.getByTitle("REFERENCE_PRICE_UNAVAILABLE")).toHaveTextContent(/Thiếu giá tham chiếu/);
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

  it("updates the latest bar and current price guideline dynamically when livePrice is supplied", () => {
    const bars: StockChartData["bars"] = [
      { tradingDate: "2026-08-13", open: "21000.00", high: "21500.00", low: "20800.00", close: "21200.00", volume: 1000000 },
      { tradingDate: "2026-08-14", open: "21200.00", high: "21400.00", low: "20900.00", close: "21100.00", volume: 1500000 },
    ];
    render(<StockChart chart={chart(bars)} livePrice="22500.00" />);
    expect(screen.getByRole("img", { name: /biểu đồ giá/i })).toBeInTheDocument();
    // The latest close and price guideline badge should reflect 22,500
    expect(screen.getAllByText("22.500").length).toBeGreaterThan(0);
  });

  it("synthesizes and renders the current session candle when liveTradingDate is after the last historical date", () => {
    const bars: StockChartData["bars"] = [
      { tradingDate: "2026-08-13", open: "21000.00", high: "21500.00", low: "20800.00", close: "21200.00", volume: 1000000 },
      { tradingDate: "2026-08-14", open: "21200.00", high: "21400.00", low: "20900.00", close: "21100.00", volume: 1500000 },
    ];
    render(
      <StockChart
        chart={chart(bars)}
        livePrice="22000.00"
        liveTradingDate="2026-08-17"
        liveReferencePrice="21100.00"
        liveVolume={500000}
      />
    );
    expect(screen.getByRole("img", { name: /biểu đồ giá/i })).toBeInTheDocument();
    // Active session status bar reflects the current trading session date (17/08/2026)
    expect(screen.getByText("17/08/2026")).toBeInTheDocument();
    // Latest close reflects 22,000
    expect(screen.getAllByText("22.000").length).toBeGreaterThan(0);
  });

  it("renders authentic open, high, and low values when liveOpenPrice, liveHighPrice, and liveLowPrice are supplied", () => {
    const bars: StockChartData["bars"] = [
      { tradingDate: "2026-08-13", open: "21000.00", high: "21500.00", low: "20800.00", close: "21200.00", volume: 1000000 },
      { tradingDate: "2026-08-14", open: "21200.00", high: "21400.00", low: "20900.00", close: "21100.00", volume: 1500000 },
    ];
    render(
      <StockChart
        chart={chart(bars)}
        livePrice="22000.00"
        liveTradingDate="2026-08-17"
        liveReferencePrice="21100.00"
        liveOpenPrice="21300.00"
        liveHighPrice="22800.00"
        liveLowPrice="20900.00"
        liveVolume={500000}
      />
    );
    expect(screen.getByRole("img", { name: /biểu đồ giá/i })).toBeInTheDocument();
    // Status bar displays High (22.800) and Low (20.900)
    expect(screen.getAllByText("22.800").length).toBeGreaterThan(0);
    expect(screen.getAllByText("20.900").length).toBeGreaterThan(0);
  });

  // ---------------------------------------------------------------------------
  // R-016 regression guards.
  //
  // The removed heuristic rescaled every bar by 1000 in whichever direction a
  // majority vote on `close >= 1000` suggested. Both directions are reproduced
  // here against the status bar, which renders the latest bar's O/H/L/C.
  // research.md R-015 rejected UI-side and magnitude-inferred normalization;
  // ARCHITECTURE.md section 6 forbids the client computing an authoritative value.
  // ---------------------------------------------------------------------------

  it("does not multiply a sub-1000 VND latest bar by 1000 when older bars are larger", () => {
    // A real collapse (ACM/FTM-style): the majority of the window is >= 1000 VND
    // while the current price is 400 VND. The old heuristic voted
    // "predominantly large" and rendered 400 VND as 400.000 VND.
    const bars: StockChartData["bars"] = [
      { tradingDate: "2026-04-21", open: "5000.000000", high: "5000.000000", low: "5000.000000", close: "5000.000000", volume: 100000 },
      { tradingDate: "2026-05-21", open: "3000.000000", high: "3000.000000", low: "3000.000000", close: "3000.000000", volume: 100000 },
      { tradingDate: "2026-06-22", open: "1500.000000", high: "1500.000000", low: "1500.000000", close: "1500.000000", volume: 100000 },
      { tradingDate: "2026-07-21", open: "900.000000", high: "900.000000", low: "900.000000", close: "900.000000", volume: 100000 },
      { tradingDate: "2026-08-21", open: "500.000000", high: "500.000000", low: "400.000000", close: "400.000000", volume: 100000 },
    ];
    render(<StockChart chart={chart(bars)} />);
    expect(screen.getAllByText(/^400,00$/).length).toBeGreaterThan(0);
    expect(screen.queryByText("400.000")).not.toBeInTheDocument();
    expect(screen.queryByText("500.000")).not.toBeInTheDocument();
  });

  it("does not divide a four-digit latest bar by 1000 when older bars are sub-1000", () => {
    // The mirror-image failure: a penny stock rallying past 1000 VND. The old
    // heuristic voted "predominantly small" and rendered 1.200 VND as 1,20.
    const bars: StockChartData["bars"] = [
      { tradingDate: "2026-05-21", open: "400.000000", high: "400.000000", low: "400.000000", close: "400.000000", volume: 100000 },
      { tradingDate: "2026-06-22", open: "500.000000", high: "500.000000", low: "500.000000", close: "500.000000", volume: 100000 },
      { tradingDate: "2026-07-21", open: "600.000000", high: "600.000000", low: "600.000000", close: "600.000000", volume: 100000 },
      { tradingDate: "2026-08-21", open: "1100.000000", high: "1200.000000", low: "1100.000000", close: "1200.000000", volume: 100000 },
    ];
    render(<StockChart chart={chart(bars)} />);
    expect(screen.getAllByText(/^1\.200$/).length).toBeGreaterThan(0);
    expect(screen.queryByText("1,20")).not.toBeInTheDocument();
    expect(screen.queryByText("1,10")).not.toBeInTheDocument();
  });

  it("does not rescale a sub-1000 VND live quote forming the current session candle", () => {
    const bars: StockChartData["bars"] = [
      { tradingDate: "2026-05-21", open: "5000.000000", high: "5000.000000", low: "5000.000000", close: "5000.000000", volume: 100000 },
      { tradingDate: "2026-06-22", open: "3000.000000", high: "3000.000000", low: "3000.000000", close: "3000.000000", volume: 100000 },
      { tradingDate: "2026-07-21", open: "1500.000000", high: "1500.000000", low: "1500.000000", close: "1500.000000", volume: 100000 },
      { tradingDate: "2026-08-21", open: "900.000000", high: "900.000000", low: "900.000000", close: "900.000000", volume: 100000 },
    ];
    render(
      <StockChart
        chart={chart(bars)}
        livePrice="400.000000"
        liveTradingDate="2026-08-24"
        liveReferencePrice="900.000000"
        liveVolume={30000}
      />
    );
    expect(screen.getAllByText(/^400,00$/).length).toBeGreaterThan(0);
    expect(screen.queryByText("400.000")).not.toBeInTheDocument();
  });

  it("preserves the server's declared decimal precision instead of re-serializing it", () => {
    // The old code rebuilt every value with `Number#toFixed(2)`, so a scale-6
    // server decimal silently became a scale-2 client-computed number.
    const bars: StockChartData["bars"] = [
      { tradingDate: "2026-08-20", open: "21000.000000", high: "21500.000000", low: "20800.000000", close: "21200.000000", volume: 1000000 },
      { tradingDate: "2026-08-21", open: "21200.000000", high: "21400.000000", low: "20900.000000", close: "21100.000000", volume: 1500000 },
    ];
    render(<StockChart chart={chart(bars)} livePrice="21350.000000" />);
    // 21.350 VND, not 21,35 and not 21.350.000
    expect(screen.getAllByText(/^21\.350$/).length).toBeGreaterThan(0);
    expect(screen.queryByText("21,35")).not.toBeInTheDocument();
  });
});
