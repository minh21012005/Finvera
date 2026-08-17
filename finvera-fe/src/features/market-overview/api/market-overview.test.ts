import { describe, expect, it, vi } from "vitest";
import { getMarketOverview, MarketOverviewApiError, parseMarketOverview } from "./market-overview";

const validOverview = {
  contractVersion: "1.0",
  generatedAt: "2026-08-17T03:00:00Z",
  tradingDate: "2026-08-17",
  timezone: "Asia/Ho_Chi_Minh",
  dataStatus: "PARTIAL",
  session: { state: "OPEN", tradingDate: "2026-08-17", asOf: "2026-08-17T03:00:00Z", calendarVersion: "market-calendar-v1", venueStates: [] },
  indices: ["VN_INDEX", "VN30", "HNX_INDEX", "UPCOM_INDEX"].map((code) => ({
    code,
    displayName: code,
    venue: code === "HNX_INDEX" ? "HNX" : code === "UPCOM_INDEX" ? "UPCOM" : "HOSE",
    dataStatus: "CURRENT",
    direction: "UP",
    value: "1280.250000",
    absoluteChange: "5.250000",
    percentageChange: "0.411765",
    matchedVolume: 420000000,
    matchedValueVnd: "11250000000000.0000",
    unit: "INDEX_POINT",
    currency: "VND",
    tradingDate: "2026-08-17",
    asOf: "2026-08-17T03:00:00Z",
    source: { provider: "FINVERA_FIXTURE", dataset: "INDEX" },
    revision: 1,
    reasonCodes: [],
  })),
  breadth: { dataStatus: "UNAVAILABLE", advancing: null, declining: null, unchanged: null, eligible: null,
    unclassified: null, universeVersion: "breadth-universe-v1", tradingDate: null, asOf: null,
    source: { provider: "UNAVAILABLE", dataset: "BREADTH" }, reasonCodes: ["BREADTH_NOT_AVAILABLE"] },
  regime: {
    dataStatus: "UNAVAILABLE", ruleVersion: "market-regime-v1", label: null, score: null, confidence: null,
    confidenceMeaning: "ASSESSMENT_QUALITY_NOT_FORECAST_PROBABILITY", tradingDate: null, asOf: null, factors: [],
    source: { provider: "UNAVAILABLE", dataset: "REGIME" }, reasonCodes: ["REGIME_NOT_AVAILABLE"],
    disclaimerCode: "QUANTITATIVE_DECISION_SUPPORT_NOT_INVESTMENT_ADVICE",
  },
  warnings: [],
};

describe("market overview API client", () => {
  it("accepts the versioned same-origin response contract", () => {
    expect(parseMarketOverview(validOverview).indices).toHaveLength(4);
  });

  it("rejects numbers used in place of precision-safe decimal strings", () => {
    const malformed = structuredClone(validOverview) as Omit<typeof validOverview, "warnings"> & { warnings: unknown[] };
    malformed.indices[0].value = 1280.25 as never;

    expect(() => parseMarketOverview(malformed)).toThrow("value must be a decimal string or null");
  });

  it("rejects a warning outside the reviewed contract enums", () => {
    const malformed = structuredClone(validOverview) as Omit<typeof validOverview, "warnings"> & { warnings: unknown[] };
    malformed.warnings = [{ code: "MISSING_INDEX", severity: "NOTICE", section: "INDEX", subject: "VN_INDEX" }];

    expect(() => parseMarketOverview(malformed)).toThrow("warning severity is invalid");
  });

  it("uses only the versioned Spring same-origin endpoint", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(validOverview), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(getMarketOverview()).resolves.toMatchObject({ contractVersion: "1.0" });
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/market/overview", expect.objectContaining({
      credentials: "same-origin",
      headers: { Accept: "application/json" },
    }));
  });

  it("returns a safe typed error for a denied response", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 401 })));

    await expect(getMarketOverview()).rejects.toEqual(new MarketOverviewApiError(401));
  });
});
