import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { PositionSizingPage } from "./components/position-sizing-page";
import * as api from "./api/position-sizing";
import * as portfolioApi from "../portfolio/api/portfolio";
import * as signalsApi from "../stock-detail/api/stock-signals";

vi.mock("./api/position-sizing", async () => {
  const actual = await vi.importActual<typeof import("./api/position-sizing")>("./api/position-sizing");
  return { ...actual, calculatePositionSize: vi.fn() };
});
vi.mock("../portfolio/api/portfolio", () => ({ listPortfolios: vi.fn().mockResolvedValue([]) }));
vi.mock("../stock-detail/api/stock-signals", () => ({ getStockSignals: vi.fn() }));

describe("PositionSizingPage", () => {
  beforeEach(() => {
    vi.mocked(api.calculatePositionSize).mockReset();
    vi.mocked(portfolioApi.listPortfolios).mockResolvedValue([]);
    vi.mocked(signalsApi.getStockSignals).mockReset();
  });
  it("requires explicit complete costs unless exclusion is chosen", () => {
    render(<PositionSizingPage />);
    expect(screen.getByLabelText("Phí mua")).toBeRequired();
    fireEvent.click(screen.getByLabelText(/Loại trừ toàn bộ/));
    expect(screen.queryByLabelText("Phí mua")).not.toBeInTheDocument();
  });
  it("supports percentage risk, conditionally requires cap bases, and renders withholding", async () => {
    vi.mocked(api.calculatePositionSize).mockResolvedValue({ status: "WITHHELD", symbol: "FPT", mode: "MANUAL",
      quantity: null, rawPermittedQuantity: 99, lotSize: 100, roundingRemainder: 99, capitalBaseVnd: "1000000",
      availableCashVnd: "99000", resolvedEntryPriceVnd: "1000", resolvedStopPriceVnd: "900",
      effectiveEntryPriceVnd: "1000", effectiveStopPriceVnd: "900", costsExcluded: true, riskBudgetVnd: "10000",
      acquisitionUnitCostVnd: "1000", stopNetProceedsPerShareVnd: "900", lossPerShareVnd: "100",
      requiredCapitalVnd: "0", estimatedLossAtStopVnd: "0", remainingCashVnd: "99000",
      projectedSymbolMarketValueVnd: null, projectedSymbolExposureRate: null, projectedDeploymentRate: null,
      constraints: [{ code: "RISK_BUDGET", applicability: "APPLIED", rawQuantity: 100, binding: false }],
      inputEvidence: [], reasonCodes: ["BELOW_STANDARD_LOT"], warnings: ["COSTS_EXCLUDED"],
      sizingRuleVersion: "position-sizing-v1", marketRuleVersion: "market-lot-v1",
      calculatedAt: "2026-09-13T08:00:00Z" });
    render(<PositionSizingPage />);
    fireEvent.change(screen.getByLabelText("Kiểu rủi ro"), { target: { value: "PERCENT" } });
    expect(screen.getByLabelText("Tỷ lệ rủi ro")).toBeRequired();
    fireEvent.change(screen.getByLabelText("Tỷ trọng tối đa một mã"), { target: { value: "0.2" } });
    expect(screen.getByLabelText("Giá trị danh mục")).toBeRequired();
    expect(screen.getByLabelText("Giá trị mã hiện có")).toBeRequired();
    expect(screen.getByLabelText("Giá trị đang giải ngân")).not.toBeRequired();
    fireEvent.change(screen.getByLabelText("Vốn cơ sở (VND)"), { target: { value: "1000000" } });
    fireEvent.change(screen.getByLabelText("Tiền có thể dùng (VND)"), { target: { value: "99000" } });
    fireEvent.change(screen.getByLabelText("Tỷ lệ rủi ro"), { target: { value: "0.01" } });
    fireEvent.change(screen.getByLabelText("Giá vào (VND/cp)"), { target: { value: "1000" } });
    fireEvent.change(screen.getByLabelText("Giá dừng lỗ (VND/cp)"), { target: { value: "900" } });
    fireEvent.change(screen.getByLabelText("Giá trị danh mục"), { target: { value: "1000000" } });
    fireEvent.change(screen.getByLabelText("Giá trị mã hiện có"), { target: { value: "0" } });
    fireEvent.click(screen.getByLabelText(/Loại trừ toàn bộ/));
    fireEvent.click(screen.getByRole("button", { name: "Tính quy mô" }));
    expect(await screen.findByText("BELOW_STANDARD_LOT")).toBeInTheDocument();
    expect(screen.getByText(/Không công bố số lượng/)).toBeInTheDocument();
  });
  it("shows the binding constraint and non-colour status", async () => {
    vi.mocked(api.calculatePositionSize).mockResolvedValue({ status: "CALCULATED", symbol: "FPT", mode: "MANUAL",
      quantity: 900, rawPermittedQuantity: 998, lotSize: 100, roundingRemainder: 98, capitalBaseVnd: "100000000",
      availableCashVnd: "100000000", resolvedEntryPriceVnd: "50000", resolvedStopPriceVnd: "47000",
      effectiveEntryPriceVnd: "50000", effectiveStopPriceVnd: "47000", costsExcluded: true, riskBudgetVnd: "5000000",
      acquisitionUnitCostVnd: "50000", stopNetProceedsPerShareVnd: "47000", lossPerShareVnd: "3000",
      requiredCapitalVnd: "45000000", estimatedLossAtStopVnd: "2700000", remainingCashVnd: "55000000",
      projectedSymbolMarketValueVnd: null, projectedSymbolExposureRate: null, projectedDeploymentRate: null,
      constraints: [{ code: "SYMBOL_CONCENTRATION", applicability: "APPLIED", rawQuantity: 998, binding: true }],
      inputEvidence: [], reasonCodes: [], warnings: ["COSTS_EXCLUDED"], sizingRuleVersion: "position-sizing-v1",
      marketRuleVersion: "market-lot-v1", calculatedAt: "2026-09-12T08:00:00Z" });
    render(<PositionSizingPage />);
    fireEvent.change(screen.getByLabelText("Vốn cơ sở (VND)"), { target: { value: "100000000" } });
    fireEvent.change(screen.getByLabelText("Tiền có thể dùng (VND)"), { target: { value: "100000000" } });
    fireEvent.change(screen.getByLabelText("Rủi ro tối đa (VND)"), { target: { value: "5000000" } });
    fireEvent.change(screen.getByLabelText("Giá vào (VND/cp)"), { target: { value: "50000" } });
    fireEvent.change(screen.getByLabelText("Giá dừng lỗ (VND/cp)"), { target: { value: "47000" } });
    fireEvent.click(screen.getByLabelText(/Loại trừ toàn bộ/));
    fireEvent.click(screen.getByRole("button", { name: "Tính quy mô" }));
    await waitFor(() => expect(screen.getByText(/900 cổ phiếu/)).toBeInTheDocument());
    expect(screen.getByText(/SYMBOL_CONCENTRATION/).closest("li")).toHaveTextContent("ĐANG GIỚI HẠN");
    expect(screen.getByText("Giá vào sau trượt giá (VND/cp)")).toBeInTheDocument();
  });

  it("loads an owner portfolio and removes manual capital fields in portfolio mode", async () => {
    vi.mocked(portfolioApi.listPortfolios).mockResolvedValue([{ id: "00000000-0000-0000-0000-000000000007",
      name: "Dài hạn", createdAt: "2026-09-01T00:00:00Z", totalValue: "100000000", cashBalance: "30000000",
      totalUnrealizedPL: "0", totalRealizedPL: "0", dataStatus: "CURRENT", reasonCodes: [],
      asOf: "2026-09-13T07:00:00Z" }]);
    render(<PositionSizingPage />);
    fireEvent.change(screen.getByLabelText("Chế độ"), { target: { value: "PORTFOLIO" } });
    expect(await screen.findByRole("option", { name: /Dài hạn · CURRENT/ })).toBeInTheDocument();
    expect(screen.queryByLabelText("Vốn cơ sở (VND)")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Danh mục")).toBeRequired();
  });

  it("imports an exact current signal, requires confirmation, and can return to manual prices", async () => {
    vi.mocked(signalsApi.getStockSignals).mockResolvedValue({ symbol: "FPT", dataStatus: "CURRENT",
      evaluations: [{ strategyCode: "MOMENTUM", status: "SIGNAL", reasonCode: null, signal: {
        strategyCode: "MOMENTUM", ruleVersion: "strategy-signal-v1", direction: "LONG",
        entryLow: "49000", entryHigh: "51000", stopLoss: "46000", target1: "55000", target2: "60000",
        riskReward: "2", riskScore: 20, riskLevel: "LOW", signalStrength: "STRONG", riskFactors: [],
        supportingEvidence: {}, reasonCodes: [], asOfTradingDate: "2026-09-12",
        calculatedAt: "2026-09-13T07:00:00Z" }}], disclaimerCode: "DECISION_SUPPORT_ONLY",
      coherenceKey: "signal:7", asOf: "2026-09-13T07:00:00Z" });
    render(<PositionSizingPage />);
    fireEvent.click(screen.getByRole("button", { name: "Nạp tín hiệu" }));
    expect(await screen.findByText(/MOMENTUM: vùng 49000–51000/)).toBeInTheDocument();
    expect(screen.getByLabelText(/Tôi xác nhận/)).toBeRequired();
    expect(screen.queryByLabelText("Giá vào (VND/cp)")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Chuyển sang nhập tay" }));
    expect(screen.getByLabelText("Giá vào (VND/cp)")).toBeRequired();
    expect(screen.getByText(/chỉ được giữ làm ngữ cảnh, không tham gia tính toán/)).toBeInTheDocument();
  });
});
