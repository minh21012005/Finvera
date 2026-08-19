import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { getPortfolioAnalytics, PortfolioAnalyticsApiError } from "./portfolio-analytics";

describe("Portfolio Analytics API Client", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation((url: string) => {
        if (url.includes("/api/v1/portfolios/pf-1/analytics")) {
          return Promise.resolve({
            ok: true,
            json: () =>
              Promise.resolve({
                periodFrom: "2026-08-01",
                periodTo: "2026-08-15",
                periodClampedToInception: false,
                returnSinceInception: "0.15",
                returnOverPeriod: "0.05",
                returnMethodDisclosureCode: "NET_CONTRIBUTED_CAPITAL_METHOD",
                maxDrawdown: "0.02",
                performanceHistory: [
                  {
                    date: "2026-08-01",
                    totalValue: "100000000",
                    dataStatus: "CURRENT",
                    reasonCode: null,
                  },
                ],
                stockConcentration: [{ key: "FPT", percentage: "0.6" }],
                sectorConcentration: [{ key: "Công nghệ", percentage: "0.6" }],
                riskExposure: {
                  riskExposureScore: 25,
                  riskExposureLevel: "LOW",
                  coverageRatio: "1",
                  reasonCode: null,
                },
                benchmark: {
                  portfolioReturn: "0.05",
                  benchmarkReturn: "0.02",
                  benchmarkSymbol: "VNINDEX",
                },
                asOf: "2026-08-15T10:00:00Z",
              }),
          });
        }
        if (url.includes("/api/v1/portfolios/pf-toolong/analytics")) {
          return Promise.resolve({
            ok: false,
            status: 422,
            json: () =>
              Promise.resolve({
                status: 422,
                reasonCode: "PERIOD_TOO_LONG",
                detail: "Period exceeds 730 days",
              }),
          });
        }
        return Promise.reject(new Error(`Unhandled URL: ${url}`));
      }),
    );
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("getPortfolioAnalytics fetches analytics with params", async () => {
    const res = await getPortfolioAnalytics("pf-1", "2026-08-01", "2026-08-15");
    expect(res.returnSinceInception).toBe("0.15");
    expect(res.returnMethodDisclosureCode).toBe("NET_CONTRIBUTED_CAPITAL_METHOD");
    expect(res.riskExposure.riskExposureLevel).toBe("LOW");
    expect(res.benchmark.benchmarkSymbol).toBe("VNINDEX");
  });

  it("getPortfolioAnalytics throws PortfolioAnalyticsApiError on 422", async () => {
    await expect(getPortfolioAnalytics("pf-toolong")).rejects.toThrow(PortfolioAnalyticsApiError);
  });
});
