import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { RegimeOverview } from "./components/regime-overview";
import type { MarketRegime } from "./api/market-overview";

const complete: MarketRegime = {
  dataStatus: "CURRENT",
  ruleVersion: "market-regime-v1",
  label: "EARLY_BULL",
  score: 62,
  confidence: 84,
  confidenceMeaning: "ASSESSMENT_QUALITY_NOT_FORECAST_PROBABILITY",
  tradingDate: "2026-08-17",
  asOf: "2026-08-17T03:00:00Z",
  factors: [
    {
      code: "TREND",
      direction: "POSITIVE",
      descriptionCode: "REGIME_FACTOR_TREND_V1",
      normalizedScore: "70.0000",
      effectiveWeight: "0.350000",
      contribution: "24.500000",
      observations: [],
    },
  ],
  source: { provider: "FINVERA_ACCEPTED", dataset: "REGIME" },
  reasonCodes: [],
  disclaimerCode: "QUANTITATIVE_DECISION_SUPPORT_NOT_INVESTMENT_ADVICE",
};

describe("RegimeOverview", () => {
  it("presents the deterministic label, score, confidence quality, timing, version, and factor direction without color-only meaning", () => {
    render(<RegimeOverview regime={complete} />);

    expect(screen.getByRole("heading", { name: /Trạng thái thị trường/i })).toBeVisible();
    expect(screen.getByText("EARLY_BULL")).toBeVisible();
    expect(screen.getByText("62/100")).toBeVisible();
    expect(screen.getByLabelText("Chất lượng đánh giá: 84/100")).toBeVisible();
    expect(screen.getByText(/market-regime-v1/i)).toBeVisible();
    expect(screen.getByText(/POSITIVE/i)).toBeVisible();
    expect(screen.getByText(/QUANTITATIVE_DECISION_SUPPORT_NOT_INVESTMENT_ADVICE/i)).toBeVisible();
  });

  it("makes permitted renormalization observable through the reason and effective factor weight", () => {
    render(<RegimeOverview regime={{
      ...complete,
      dataStatus: "PARTIAL",
      reasonCodes: ["RENORMALIZED_MISSING_VOLATILITY"],
      factors: [{ ...complete.factors[0], effectiveWeight: "0.388889" }],
    }} />);

    expect(screen.getByRole("status")).toHaveTextContent(/RENORMALIZED_MISSING_VOLATILITY/);
    expect(screen.getByRole("listitem")).toHaveTextContent("0.388889");
  });

  it("withholds label, score, confidence, and factors when the assessment is unavailable", () => {
    render(<RegimeOverview regime={{
      ...complete,
      dataStatus: "UNAVAILABLE",
      label: null,
      score: null,
      confidence: null,
      tradingDate: null,
      asOf: null,
      factors: [],
      source: { provider: "UNAVAILABLE", dataset: "REGIME" },
      reasonCodes: ["MANDATORY_INPUT_UNAVAILABLE"],
    }} />);

    expect(screen.getByRole("status")).toHaveTextContent(/MANDATORY_INPUT_UNAVAILABLE/);
    expect(screen.queryByText(/\/100/)).not.toBeInTheDocument();
    expect(screen.queryByText("TREND")).not.toBeInTheDocument();
  });
});
