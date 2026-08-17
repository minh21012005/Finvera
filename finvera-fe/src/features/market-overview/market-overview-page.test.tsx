import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { MarketOverviewApiError, type MarketOverview } from "./api/market-overview";
import { MarketOverviewPage } from "./market-overview-page";

const { getMarketOverview } = vi.hoisted(() => ({ getMarketOverview: vi.fn() }));
vi.mock("./api/market-overview", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api/market-overview")>()),
  getMarketOverview,
}));

const overview: MarketOverview = {
  contractVersion: "1.0", generatedAt: "2026-08-17T03:00:00Z", tradingDate: "2026-08-17",
  timezone: "Asia/Ho_Chi_Minh", dataStatus: "PARTIAL",
  session: { state: "OPEN", tradingDate: "2026-08-17", asOf: "2026-08-17T03:00:00Z", calendarVersion: "market-calendar-v1", venueStates: [] },
  indices: ["VN_INDEX", "VN30", "HNX_INDEX", "UPCOM_INDEX"].map((code) => ({
    code: code as MarketOverview["indices"][number]["code"], displayName: code, venue: "HOSE" as const,
    dataStatus: "UNAVAILABLE" as const, direction: null, value: null, absoluteChange: null,
    percentageChange: null, matchedVolume: null, matchedValueVnd: null, unit: "INDEX_POINT" as const,
    currency: "VND" as const, tradingDate: null, asOf: null, source: { provider: "UNAVAILABLE", dataset: "INDEX" },
    revision: null, reasonCodes: ["MISSING_INDEX"],
  })),
  breadth: { dataStatus: "UNAVAILABLE", advancing: null, declining: null, unchanged: null, eligible: null,
    unclassified: null, universeVersion: "breadth-universe-v1", tradingDate: null, asOf: null,
    source: { provider: "UNAVAILABLE", dataset: "BREADTH" }, reasonCodes: ["BREADTH_NOT_AVAILABLE"] },
  regime: {
    dataStatus: "UNAVAILABLE", ruleVersion: "market-regime-v1", label: null, score: null, confidence: null,
    confidenceMeaning: "ASSESSMENT_QUALITY_NOT_FORECAST_PROBABILITY", tradingDate: null, asOf: null, factors: [],
    source: { provider: "UNAVAILABLE", dataset: "REGIME" }, reasonCodes: ["REGIME_NOT_AVAILABLE"],
    disclaimerCode: "QUANTITATIVE_DECISION_SUPPORT_NOT_INVESTMENT_ADVICE",
  }, warnings: [],
};

describe("MarketOverviewPage", () => {
  beforeEach(() => getMarketOverview.mockReset());

  it("shows a loading state then an explicitly degraded overview", async () => {
    getMarketOverview.mockResolvedValue(overview);
    render(<MarketOverviewPage />);

    expect(screen.getByText(/Đang tải/i)).toBeVisible();
    expect(await screen.findByRole("heading", { name: /Tổng quan thị trường/i })).toBeVisible();
    expect(screen.getAllByText(/Một phần/i)).not.toHaveLength(0);
    expect(screen.getAllByRole("article")).toHaveLength(4);
  });

  it("shows a private-session error and permits a retry", async () => {
    getMarketOverview.mockRejectedValueOnce(new MarketOverviewApiError(401)).mockResolvedValueOnce(overview);
    const user = userEvent.setup();
    render(<MarketOverviewPage />);

    expect(await screen.findByRole("alert")).toHaveTextContent(/Phiên đăng nhập/i);
    await user.click(screen.getByRole("button", { name: /Thử lại/i }));
    expect(await screen.findByRole("heading", { name: /Tổng quan thị trường/i })).toBeVisible();
    expect(getMarketOverview).toHaveBeenCalledTimes(2);
  });
});
