import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { PortfolioAnalyticsView } from "./components/portfolio-analytics-view";

describe("PortfolioAnalyticsView Component", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation((url: string) => {
        if (url.includes("/analytics")) {
          return Promise.resolve({
            ok: true,
            json: () =>
              Promise.resolve({
                periodFrom: "2026-08-01",
                periodTo: "2026-08-15",
                periodClampedToInception: true,
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
        return Promise.reject(new Error(`Unhandled: ${url}`));
      }),
    );
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("renders returns, max drawdown, risk exposure, concentration, and clamped notice", async () => {
    render(<PortfolioAnalyticsView portfolioId="pf-1" />);

    expect(screen.getByText("Đang tính toán phân tích hiệu quả danh mục…")).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText("(+) +15.00%")).toBeInTheDocument();
    });

    expect(screen.getByText("(+) +5.00%")).toBeInTheDocument();
    expect(screen.getByText("(-) -2.00%")).toBeInTheDocument();
    expect(screen.getByText("25/100")).toBeInTheDocument();
    expect(screen.getByText("LOW")).toBeInTheDocument();
    expect(screen.getByText("100.00%")).toBeInTheDocument();
    expect(screen.getByText("FPT")).toBeInTheDocument();
    expect(screen.getByText("Công nghệ")).toBeInTheDocument();
    expect(screen.getByTestId("clamped-inception-banner")).toBeInTheDocument();
  });
});
