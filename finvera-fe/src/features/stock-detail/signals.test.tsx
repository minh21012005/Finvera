import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { StockSignals } from "./components/stock-signals";
import type { Signal, StockSignals as StockSignalsData, StrategyEvaluation } from "./api/stock-signals";

function signalsResponse(evaluations: StrategyEvaluation[]): StockSignalsData {
  return {
    symbol: "FPT",
    dataStatus: "CURRENT",
    evaluations,
    disclaimerCode: "QUANTITATIVE_DECISION_SUPPORT",
    coherenceKey: "coh-signal-1",
    asOf: "2026-08-14T08:15:01Z",
  };
}

function fullSignal(overrides: Partial<Signal> = {}): Signal {
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
    riskFactors: [
      { factorCode: "VOLATILITY", inputValue: "2.5", factorScore: 10, applicability: "DEFINED", reasonCode: null },
      { factorCode: "ATR", inputValue: "1.0", factorScore: 33, applicability: "DEFINED", reasonCode: null },
      { factorCode: "DRAWDOWN", inputValue: "0", factorScore: 0, applicability: "DEFINED", reasonCode: null },
      { factorCode: "LIQUIDITY", inputValue: "1.0", factorScore: 50, applicability: "DEFINED", reasonCode: null },
      { factorCode: "STOP_DISTANCE", inputValue: "4", factorScore: 8, applicability: "DEFINED", reasonCode: null },
      {
        factorCode: "MARKET_REGIME",
        inputValue: null,
        factorScore: null,
        applicability: "MISSING",
        reasonCode: "REGIME_UNAVAILABLE",
      },
    ],
    supportingEvidence: { trend: "UPTREND" },
    reasonCodes: [],
    asOfTradingDate: "2026-08-14",
    calculatedAt: "2026-08-14T08:15:00Z",
    ...overrides,
  };
}

describe("stock signals section", () => {
  it("shows a triggered signal's direction, entry zone, stop, targets, and risk/reward", () => {
    render(
      <StockSignals
        symbol="HPG"
        signals={signalsResponse([
          { strategyCode: "TREND_FOLLOWING", status: "SIGNAL", reasonCode: null, signal: fullSignal() },
        ])}
      />,
    );
    expect(screen.getByText(/Theo xu hướng/)).toBeVisible();
    expect(screen.getByText(/Mua \(LONG\)/)).toBeVisible();
    expect(screen.getByText(/99,5\b/)).toBeVisible();
    expect(screen.getByText(/100,5\b/)).toBeVisible();
    expect(screen.getByText(/96\b/)).toBeVisible();
    expect(screen.getByText(/1 : 2\b/)).toBeVisible();
  });

  it("shows a truthful no-signal state, not an error, for a strategy whose condition is not met", () => {
    render(
      <StockSignals
        symbol="HPG"
        signals={signalsResponse([
          { strategyCode: "MOMENTUM", status: "NO_SIGNAL", reasonCode: null, signal: null },
        ])}
      />,
    );
    expect(screen.getByText(/Động lượng/)).toBeVisible();
    expect(screen.getByText(/chưa được thỏa mãn/)).toBeVisible();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("shows an insufficient-history state distinct from no-signal", () => {
    render(
      <StockSignals
        symbol="HPG"
        signals={signalsResponse([
          {
            strategyCode: "BREAKOUT",
            status: "INSUFFICIENT_HISTORY",
            reasonCode: "INSUFFICIENT_HISTORY",
            signal: null,
          },
        ])}
      />,
    );
    expect(screen.getByText(/Bứt phá/)).toBeVisible();
    expect(screen.getAllByText(/Chưa đủ dữ liệu lịch sử/).length).toBeGreaterThanOrEqual(2);
  });

  it("every strategy shown at once still discloses its own correct state per strategy", () => {
    render(
      <StockSignals
        symbol="HPG"
        signals={signalsResponse([
          { strategyCode: "TREND_FOLLOWING", status: "SIGNAL", reasonCode: null, signal: fullSignal() },
          { strategyCode: "MOMENTUM", status: "NO_SIGNAL", reasonCode: null, signal: null },
          {
            strategyCode: "BREAKOUT",
            status: "INSUFFICIENT_HISTORY",
            reasonCode: "INSUFFICIENT_HISTORY",
            signal: null,
          },
        ])}
      />,
    );
    expect(screen.getByText(/Theo xu hướng/)).toBeVisible();
    expect(screen.getByText(/Động lượng/)).toBeVisible();
    expect(screen.getByText(/Bứt phá/)).toBeVisible();
    expect(document.querySelectorAll(".signal-grid > li")).toHaveLength(3);
  });

  it("shows direction and risk level as text/icon indicators independent of colour (NFR-003)", () => {
    render(
      <StockSignals
        symbol="HPG"
        signals={signalsResponse([
          { strategyCode: "TREND_FOLLOWING", status: "SIGNAL", reasonCode: null, signal: fullSignal() },
        ])}
      />,
    );
    // Direction is stated in words, not implied by a colour class alone.
    expect(screen.getByText(/Chiều: Mua \(LONG\)/)).toBeVisible();
    // Risk level carries both a text label and an icon glyph.
    expect(screen.getByText(/Rủi ro thấp/)).toBeVisible();
    expect(screen.getByText(/Độ mạnh tín hiệu: Mạnh/)).toBeVisible();
  });

  it("states a deterministic scenario, never a guarantee or instruction (FR-013)", () => {
    render(
      <StockSignals
        symbol="HPG"
        signals={signalsResponse([
          { strategyCode: "TREND_FOLLOWING", status: "SIGNAL", reasonCode: null, signal: fullSignal() },
        ])}
      />,
    );
    const disclaimers = screen.getAllByText(/không phải khuyến nghị đầu tư/i);
    expect(disclaimers.length).toBeGreaterThan(0);
    disclaimers.forEach((node) => expect(node).toBeVisible());
    expect(screen.queryByText(/^Mua ngay$|^Bán ngay$/i)).not.toBeInTheDocument();
  });
});
