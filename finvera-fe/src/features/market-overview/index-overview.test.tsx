import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { IndexOverview } from "./components/index-overview";
import { formatDecimal, formatVnd } from "./format/market-format";
import type { MarketOverview } from "./api/market-overview";

const overview: MarketOverview = {
  contractVersion: "1.0",
  generatedAt: "2026-08-17T03:00:00Z",
  tradingDate: "2026-08-17",
  timezone: "Asia/Ho_Chi_Minh",
  dataStatus: "PARTIAL",
  session: { state: "OPEN", tradingDate: "2026-08-17", asOf: "2026-08-17T03:00:00Z", calendarVersion: "market-calendar-v1", venueStates: [] },
  indices: [
    index("VN_INDEX", "VN-Index", "UP", "CURRENT"),
    index("VN30", "VN30-Index", "DOWN", "DELAYED"),
    index("HNX_INDEX", "HNX-Index", "UNCHANGED", "CURRENT"),
    index("UPCOM_INDEX", "UPCoM-Index", null, "UNAVAILABLE"),
  ],
  breadth: { dataStatus: "UNAVAILABLE", advancing: null, declining: null, unchanged: null, eligible: null,
    unclassified: null, universeVersion: "breadth-universe-v1", tradingDate: null, asOf: null,
    source: { provider: "UNAVAILABLE", dataset: "BREADTH" }, reasonCodes: ["BREADTH_NOT_AVAILABLE"] },
  regime: {},
  warnings: [],
};

function index(
  code: MarketOverview["indices"][number]["code"],
  displayName: string,
  direction: MarketOverview["indices"][number]["direction"],
  dataStatus: MarketOverview["indices"][number]["dataStatus"],
): MarketOverview["indices"][number] {
  const unavailable = dataStatus === "UNAVAILABLE";
  return {
    code,
    displayName,
    venue: code === "HNX_INDEX" ? "HNX" : code === "UPCOM_INDEX" ? "UPCOM" : "HOSE",
    dataStatus,
    direction,
    value: unavailable ? null : "1280.250000",
    absoluteChange: unavailable ? null : direction === "DOWN" ? "-5.250000" : "5.250000",
    percentageChange: unavailable ? null : direction === "DOWN" ? "-0.411765" : "0.411765",
    matchedVolume: unavailable ? null : 420000000,
    matchedValueVnd: unavailable ? null : "11250000000000.0000",
    unit: "INDEX_POINT",
    currency: "VND",
    tradingDate: unavailable ? null : "2026-08-17",
    asOf: unavailable ? null : "2026-08-17T03:00:00Z",
    source: { provider: unavailable ? "UNAVAILABLE" : "FINVERA_FIXTURE", dataset: "INDEX" },
    revision: unavailable ? null : 1,
    reasonCodes: unavailable ? ["MISSING_INDEX"] : [],
  };
}

describe("market overview index cards", () => {
  it("formats decimal strings and VND in Vietnamese locale without recomputing values", () => {
    expect(formatDecimal("1280.250000")).toBe("1.280,25");
    expect(formatVnd("11250000000000.0000")).toContain("VND");
    expect(formatDecimal(null)).toBe("Không có dữ liệu");
  });

  it("renders exactly four stable cards with textual direction and status", () => {
    render(<IndexOverview overview={overview} />);

    const cards = screen.getAllByRole("article");
    expect(cards).toHaveLength(4);
    expect(cards.map((card) => card.getAttribute("aria-label"))).toEqual([
      expect.stringContaining("VN-Index"),
      expect.stringContaining("VN30-Index"),
      expect.stringContaining("HNX-Index"),
      expect.stringContaining("UPCoM-Index"),
    ]);
    expect(screen.getByText(/Tăng/i)).toBeVisible();
    expect(screen.getByText(/Giảm/i)).toBeVisible();
    expect(screen.getByText(/Chậm/i)).toBeVisible();
  });

  it("keeps unavailable facts empty and explains the reason without a zero placeholder", () => {
    render(<IndexOverview overview={overview} />);

    const upcom = screen.getByRole("article", { name: /UPCoM-Index/i });
    expect(upcom).toHaveTextContent("Không có dữ liệu");
    expect(upcom).toHaveTextContent("MISSING_INDEX");
    expect(upcom).not.toHaveTextContent("0 VND");
  });
});
