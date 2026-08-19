import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { StrategyPicker } from "./components/strategy-picker";
import { StrategyScanResults } from "./components/strategy-scan-results";
import type { ScanMatch, ScanResponse } from "./api/stock-strategy";
import type { Signal } from "../stock-detail/api/stock-signals";

function baseSignal(overrides: Partial<Signal> = {}): Signal {
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
    riskFactors: [],
    supportingEvidence: {},
    reasonCodes: [],
    asOfTradingDate: "2026-08-14",
    calculatedAt: "2026-08-14T08:15:00Z",
    ...overrides,
  };
}

function match(overrides: Partial<ScanMatch> = {}): ScanMatch {
  return { symbol: "FPT", companyName: "CTCP FPT", exchange: "HOSE", signal: baseSignal(), ...overrides };
}

function scanResponse(matches: ScanMatch[], overrides: Partial<ScanResponse> = {}): ScanResponse {
  return {
    strategyCode: "TREND_FOLLOWING",
    matches,
    totalMatchCount: matches.length,
    limit: 50,
    offset: 0,
    excludedForInsufficientHistoryCount: 0,
    calculatedAt: "2026-08-14T08:15:01Z",
    ...overrides,
  };
}

describe("strategy picker", () => {
  it("submits the chosen strategy code", async () => {
    const onSubmit = vi.fn();
    render(<StrategyPicker onSubmit={onSubmit} submitting={false} />);

    await userEvent.selectOptions(screen.getByLabelText("Chiến lược"), "MOMENTUM");
    await userEvent.click(screen.getByRole("button", { name: /quét thị trường/i }));

    expect(onSubmit).toHaveBeenCalledWith("MOMENTUM");
  });

  it("disables submission while a scan is in progress", () => {
    render(<StrategyPicker onSubmit={vi.fn()} submitting={true} />);
    expect(screen.getByRole("button", { name: /đang quét/i })).toBeDisabled();
  });
});

describe("strategy scan results", () => {
  it("shows exactly the triggering stocks with their own signal summary", () => {
    render(<StrategyScanResults result={scanResponse([match(), match({ symbol: "VNM", companyName: "CTCP Vinamilk" })])} />);
    expect(screen.getByText("FPT")).toBeVisible();
    expect(screen.getByText("VNM")).toBeVisible();
    expect(screen.getByText(/2 mã/)).toBeVisible();
  });

  it("shows a specific empty-result state rather than an error or a blank table", () => {
    render(<StrategyScanResults result={scanResponse([])} />);
    expect(screen.getByText(/Không có mã cổ phiếu nào đang kích hoạt/)).toBeVisible();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("discloses insufficient-history exclusions distinguishably from a genuine empty result", () => {
    render(<StrategyScanResults result={scanResponse([], { excludedForInsufficientHistoryCount: 4 })} />);
    expect(screen.getByText(/4 mã bị loại do chưa đủ dữ liệu lịch sử/)).toBeVisible();
  });

  it("never suggests a guaranteed outcome", () => {
    render(<StrategyScanResults result={scanResponse([match()])} />);
    expect(screen.getByText(/không phải khuyến nghị đầu tư/i)).toBeVisible();
  });
});
