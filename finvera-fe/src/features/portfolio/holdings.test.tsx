import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { HoldingsTable } from "./components/holdings-table";
import { TransactionForm } from "./components/transaction-form";
import { TransactionLedger } from "./components/transaction-ledger";
import type { Position, Transaction } from "./api/portfolio";

describe("HoldingsTable Component", () => {
  it("renders derived positions with explicit non-color signs and allocation", () => {
    const mockPositions: Position[] = [
      {
        instrumentSymbol: "FPT",
        quantity: "1000",
        averageCostBasis: "50000",
        currentPrice: "60000",
        currentPriceStatus: "DEFINED",
        unrealizedPL: "10000000",
        realizedPL: "2000000",
        allocation: "0.6",
      },
      {
        instrumentSymbol: "VNM",
        quantity: "500",
        averageCostBasis: "70000",
        currentPrice: "65000",
        currentPriceStatus: "DEFINED",
        unrealizedPL: "-2500000",
        realizedPL: "0",
        allocation: "0.325",
      },
    ];

    render(
      <HoldingsTable
        positions={mockPositions}
        cashBalance="7500000"
        totalValue="100000000"
      />,
    );

    expect(screen.getByText("FPT")).toBeInTheDocument();
    expect(screen.getByText("VNM")).toBeInTheDocument();

    // Check non-color sign indicators
    expect(screen.getByText("(+)")).toBeInTheDocument();
    expect(screen.getByText("(-)")).toBeInTheDocument();

    // Check allocations
    expect(screen.getByText("60.00%")).toBeInTheDocument();
    expect(screen.getByText("32.50%")).toBeInTheDocument();
  });

  it("renders empty state when there are no positions", () => {
    render(
      <HoldingsTable
        positions={[]}
        cashBalance="10000000"
        totalValue="10000000"
      />,
    );

    expect(
      screen.getByText(/Hiện chưa có cổ phiếu nào trong danh mục/i),
    ).toBeInTheDocument();
  });
});

describe("TransactionForm Component", () => {
  it("switches tabs between BUY, SELL, DEPOSIT, WITHDRAW", async () => {
    const user = userEvent.setup();
    const onSuccess = vi.fn();

    render(<TransactionForm portfolioId="pf-1" onSuccess={onSuccess} />);

    expect(screen.getByText("Mã cổ phiếu")).toBeInTheDocument();

    await user.click(screen.getByText("NẠP TIỀN"));
    expect(screen.getByText("Số tiền (VNĐ)")).toBeInTheDocument();
    expect(screen.queryByText("Mã cổ phiếu")).not.toBeInTheDocument();
  });
});

describe("TransactionLedger Component", () => {
  it("renders ledger rows and handles void trigger", async () => {
    const user = userEvent.setup();
    const mockTxs: Transaction[] = [
      {
        id: "tx-1",
        portfolioId: "pf-1",
        sequenceNo: 1,
        transactionType: "BUY",
        instrumentSymbol: "FPT",
        quantity: "100",
        price: "50000",
        fee: "5000",
        amount: null,
        currency: "VND",
        executedAt: "2026-08-01T10:00:00Z",
        entryAt: "2026-08-01T10:00:00Z",
        idempotencyKey: "k1",
      },
    ];

    render(
      <TransactionLedger
        portfolioId="pf-1"
        transactions={mockTxs}
        onVoidSuccess={vi.fn()}
      />,
    );

    expect(screen.getByText("MUA")).toBeInTheDocument();
    expect(screen.getByText("FPT")).toBeInTheDocument();

    // Click void button to show reason input
    const voidBtn = screen.getByRole("button", { name: /Hủy GD \(Void\)/i });
    await user.click(voidBtn);

    expect(screen.getByPlaceholderText("Lý do hủy...")).toBeInTheDocument();
  });
});
