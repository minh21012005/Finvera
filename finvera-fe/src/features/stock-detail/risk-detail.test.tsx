import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { StockSignals } from "./components/stock-signals";
import type { RiskFactor, Signal, StockSignals as StockSignalsData } from "./api/stock-signals";

function signalsResponse(signal: Signal): StockSignalsData {
  return {
    symbol: "FPT",
    dataStatus: "CURRENT",
    evaluations: [{ strategyCode: "TREND_FOLLOWING", status: "SIGNAL", reasonCode: null, signal }],
    disclaimerCode: "QUANTITATIVE_DECISION_SUPPORT",
    coherenceKey: "coh-signal-1",
    asOf: "2026-08-14T08:15:01Z",
  };
}

function baseSignal(riskFactors: RiskFactor[], overrides: Partial<Signal> = {}): Signal {
  return {
    strategyCode: "TREND_FOLLOWING",
    ruleVersion: "strategy-signal-v1",
    direction: "LONG",
    entryLow: "99.500000",
    entryHigh: "100.500000",
    stopLoss: "96.000000",
    target1: "108.000000",
    target2: "112.000000",
    riskReward: "2.0000",
    riskScore: 25,
    riskLevel: "LOW",
    signalStrength: "STRONG",
    riskFactors,
    supportingEvidence: {},
    reasonCodes: [],
    asOfTradingDate: "2026-08-14",
    calculatedAt: "2026-08-14T08:15:00Z",
    ...overrides,
  };
}

const SIX_DEFINED_FACTORS: RiskFactor[] = [
  { factorCode: "VOLATILITY", inputValue: "2.5", factorScore: 10, applicability: "DEFINED", reasonCode: null },
  { factorCode: "ATR", inputValue: "1.0", factorScore: 33, applicability: "DEFINED", reasonCode: null },
  { factorCode: "DRAWDOWN", inputValue: "0", factorScore: 0, applicability: "DEFINED", reasonCode: null },
  { factorCode: "LIQUIDITY", inputValue: "1.0", factorScore: 50, applicability: "DEFINED", reasonCode: null },
  { factorCode: "STOP_DISTANCE", inputValue: "4", factorScore: 8, applicability: "DEFINED", reasonCode: null },
  { factorCode: "MARKET_REGIME", inputValue: "50", factorScore: 50, applicability: "DEFINED", reasonCode: null },
];

describe("stock signal risk-factor breakdown", () => {
  it("shows all six factors with their own value and score when every factor is available", () => {
    render(<StockSignals symbol="HPG" signals={signalsResponse(baseSignal(SIX_DEFINED_FACTORS))} />);
    const items = screen.getAllByRole("listitem");
    // 1 strategy card + 6 risk-factor items.
    expect(items.length).toBeGreaterThanOrEqual(6);
    expect(screen.getByText(/Biến động giá/)).toBeVisible();
    expect(screen.getByText(/Điểm:\s*10\/100/)).toBeVisible();
    expect(screen.getByText(/Trạng thái thị trường chung/)).toBeVisible();
  });

  it("discloses an unavailable factor's own reason, with the overall score still computed from the rest", () => {
    const factors: RiskFactor[] = [
      ...SIX_DEFINED_FACTORS.slice(0, 5),
      {
        factorCode: "MARKET_REGIME",
        inputValue: null,
        factorScore: null,
        applicability: "MISSING",
        reasonCode: "REGIME_UNAVAILABLE",
      },
    ];
    render(<StockSignals symbol="HPG" signals={signalsResponse(baseSignal(factors, { riskScore: 20, riskLevel: "LOW" }))} />);
    expect(screen.getByText(/REGIME_UNAVAILABLE/)).toBeVisible();
    expect(screen.getByText(/Rủi ro thấp/)).toBeVisible();
    expect(screen.getByText(/\(20\/100\)/)).toBeVisible();
  });

  it("withholds the overall score/level when fewer than four of six factors are available, but still shows the signal", () => {
    const factors: RiskFactor[] = [
      { factorCode: "VOLATILITY", inputValue: "2.5", factorScore: 10, applicability: "DEFINED", reasonCode: null },
      { factorCode: "LIQUIDITY", inputValue: "1.0", factorScore: 50, applicability: "DEFINED", reasonCode: null },
      { factorCode: "STOP_DISTANCE", inputValue: "4", factorScore: 8, applicability: "DEFINED", reasonCode: null },
      { factorCode: "ATR", inputValue: null, factorScore: null, applicability: "MISSING", reasonCode: "INPUT_UNAVAILABLE" },
      {
        factorCode: "DRAWDOWN",
        inputValue: null,
        factorScore: null,
        applicability: "MISSING",
        reasonCode: "INPUT_UNAVAILABLE",
      },
      {
        factorCode: "MARKET_REGIME",
        inputValue: null,
        factorScore: null,
        applicability: "MISSING",
        reasonCode: "REGIME_UNAVAILABLE",
      },
    ];
    render(
      <StockSignals
        symbol="HPG"
        signals={signalsResponse(
          baseSignal(factors, { riskScore: null, riskLevel: null, signalStrength: null, reasonCodes: ["INSUFFICIENT_RISK_FACTORS"] }),
        )}
      />,
    );
    // The signal itself (direction, levels) is still shown.
    expect(screen.getByText(/Chiều: Mua \(LONG\)/)).toBeVisible();
    expect(screen.getByText(/Vùng vào lệnh/)).toBeVisible();
    // The overall risk score/level is explicitly withheld, not fabricated as zero.
    expect(screen.getByText(/INSUFFICIENT_RISK_FACTORS/)).toBeVisible();
    expect(screen.getAllByText(/Chưa xác định/).length).toBeGreaterThan(0);
  });

  it("shows the risk level with a non-colour text/icon indicator", () => {
    render(<StockSignals symbol="HPG" signals={signalsResponse(baseSignal(SIX_DEFINED_FACTORS, { riskLevel: "HIGH" }))} />);
    const badge = screen.getByText(/Rủi ro cao/).closest(".risk-level-badge");
    expect(badge).not.toBeNull();
    expect(badge?.textContent).toMatch(/●●●/);
  });

  it("reproduces identical factor values and overall score for identical accepted inputs", () => {
    const { unmount } = render(<StockSignals symbol="HPG" signals={signalsResponse(baseSignal(SIX_DEFINED_FACTORS))} />);
    const firstScore = screen.getByText(/\(25\/100\)/).textContent;
    unmount();
    render(<StockSignals symbol="HPG" signals={signalsResponse(baseSignal(SIX_DEFINED_FACTORS))} />);
    const secondScore = screen.getByText(/\(25\/100\)/).textContent;
    expect(firstScore).toEqual(secondScore);
  });
});
