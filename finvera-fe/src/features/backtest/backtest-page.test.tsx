import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { BacktestPage } from "./components/backtest-page";
import * as api from "./api/backtest";

vi.mock("./api/backtest", async () => {
  const actual = await vi.importActual<typeof import("./api/backtest")>("./api/backtest");
  return { ...actual, listBacktests: vi.fn(), getBacktest: vi.fn(), getTrades: vi.fn(),
    getEquity: vi.fn(), getEvents: vi.fn(), createBacktest: vi.fn() };
});

const run: api.RunSummary = {
  id: "00000000-0000-0000-0000-000000000001", status: "COMPLETED", strategyCode: "RSI_BASED",
  symbol: "FPT", startDate: "2026-01-02", endDate: "2026-02-02", processedSessions: 20,
  totalSessions: 20, reasonCode: null, createdAt: "2026-02-03T00:00:00Z",
  completedAt: "2026-02-03T00:00:01Z",
};

function detail(status: api.RunStatus = "COMPLETED", reasonCode: string | null = null): api.RunDetail {
  return { ...run, status, reasonCode, completedAt: status === "QUEUED" || status === "RUNNING" ? null : run.completedAt,
    dataCutoffAcceptedAt: run.createdAt,
    assumptions: { initialCapitalVnd: "100000000", riskPerTrancheRate: "0.01",
      maxAggregateOpenRiskRate: "0.04", costs: { excluded: true },
      strategyRuleVersion: "strategy-signal-v1", sizingRuleVersion: "position-sizing-v1",
      engineRuleVersion: "backtest-engine-v1", metricsRuleVersion: "backtest-metrics-v1",
      pyramidingRuleVersion: "pyramiding-v1", entryTiming: "NEXT_ELIGIBLE_SESSION_OPEN",
      sameBarPriority: "STOP_FIRST", maxOpenTranches: 4, pyramidStepAtr: "0.5" },
    metrics: status === "COMPLETED" ? [{ code: "TOTAL_RETURN", value: "0.1", unit: "RATE",
      availability: "DEFINED", reasonCode: null, ruleVersion: "backtest-metrics-v1" }] : [],
    evidence: [{ key: "marketDataSources", value: "VNSTOCK_VCI", unit: "TEXT" }],
    warnings: ["COSTS_EXCLUDED"] };
}

describe("BacktestPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.listBacktests).mockResolvedValue({ items: [run], total: 1, limit: 100, offset: 0 });
    vi.mocked(api.getBacktest).mockResolvedValue(detail());
    vi.mocked(api.getTrades).mockResolvedValue({ items: [], total: 0, limit: 500, offset: 0 });
    vi.mocked(api.getEquity).mockResolvedValue({ items: [{ tradingDate: "2026-02-02", cashVnd: "110000000",
      openPositionValueVnd: "0", totalEquityVnd: "110000000", openTrancheCount: 0,
      dailyReturnRate: "0.01" }], total: 1, limit: 500, offset: 0 });
    vi.mocked(api.getEvents).mockResolvedValue({ items: [], total: 0, limit: 500, offset: 0 });
  });

  afterEach(() => vi.useRealTimers());

  it("shows terminal metrics, warnings and an accessible equity table", async () => {
    render(<BacktestPage />);
    fireEvent.click(await screen.findByRole("button", { name: /FPT/ }));
    expect(await screen.findByText("TOTAL_RETURN")).toBeVisible();
    expect(screen.getByText(/COSTS_EXCLUDED/)).toBeVisible();
    expect(screen.getByText("VNSTOCK_VCI")).toBeVisible();
    expect(screen.getByText("backtest-engine-v1")).toBeVisible();
    expect(screen.getByText("Loại trừ theo lựa chọn của người dùng")).toBeVisible();
    expect(screen.getByRole("img", { name: "Biểu đồ đường vốn" })).toBeVisible();
    fireEvent.click(screen.getByText("Xem dữ liệu đường vốn dạng bảng"));
    expect(screen.getAllByText("110000000")).toHaveLength(2);
  });

  it("polls an active run and stops after it becomes terminal", async () => {
    vi.mocked(api.getBacktest).mockResolvedValueOnce(detail("RUNNING")).mockResolvedValueOnce(detail());
    render(<BacktestPage />);
    const button = await screen.findByRole("button", { name: /FPT/ });
    vi.useFakeTimers();
    fireEvent.click(button);
    await act(async () => { await vi.advanceTimersByTimeAsync(0); });
    expect(screen.getByText("RUNNING")).toBeVisible();
    await act(async () => { await vi.advanceTimersByTimeAsync(2_000); });
    expect(screen.getByText("COMPLETED")).toBeVisible();
    const callsAtCompletion = vi.mocked(api.getBacktest).mock.calls.length;
    await act(async () => { await vi.advanceTimersByTimeAsync(4_000); });
    expect(api.getBacktest).toHaveBeenCalledTimes(callsAtCompletion);
  });

  it.each([
    ["WITHHELD", "UNSUPPORTED_CORPORATE_ACTION"],
    ["FAILED", "WORKER_ATTEMPTS_EXHAUSTED"],
  ] as const)("renders %s reason without requesting a result ledger", async (status, reasonCode) => {
    vi.mocked(api.getBacktest).mockResolvedValue(detail(status, reasonCode));
    render(<BacktestPage />);
    fireEvent.click(await screen.findByRole("button", { name: /FPT/ }));

    expect(await screen.findByText(new RegExp(reasonCode))).toBeVisible();
    expect(api.getTrades).not.toHaveBeenCalled();
    expect(api.getEquity).not.toHaveBeenCalled();
    expect(api.getEvents).not.toHaveBeenCalled();
  });

  it("reloads a persisted run when the owner revisits it", async () => {
    render(<BacktestPage />);
    const button = await screen.findByRole("button", { name: /FPT/ });
    fireEvent.click(button);
    await screen.findByText("TOTAL_RETURN");
    fireEvent.click(button);

    await waitFor(() => expect(api.getBacktest).toHaveBeenCalledTimes(2));
  });

  it("shows an owner-session failure without leaking response details", async () => {
    vi.mocked(api.listBacktests).mockRejectedValue(new api.BacktestApiError(401, "AUTHENTICATION_REQUIRED"));
    render(<BacktestPage />);

    expect(await screen.findByRole("alert")).toHaveTextContent("AUTHENTICATION_REQUIRED");
    expect(screen.getByText("Chưa có backtest.")).toBeVisible();
  });

  it("shows the empty history state", async () => {
    vi.mocked(api.listBacktests).mockResolvedValue({ items: [], total: 0, limit: 100, offset: 0 });
    render(<BacktestPage />);
    expect(await screen.findByText("Chưa có backtest.")).toBeVisible();
  });
});
