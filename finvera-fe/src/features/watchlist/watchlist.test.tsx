import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { WatchlistItemTable } from "./components/watchlist-item-table";
import type { WatchlistItem } from "./api/watchlist";

describe("Watchlist Component Suite", () => {
  it("renders watchlist items with live price, non-color change signs, and signal/risk badges", () => {
    const mockItems: WatchlistItem[] = [
      {
        symbol: "FPT",
        companyName: "Tập đoàn FPT",
        addedAt: "2026-08-10T00:00:00Z",
        currentPrice: "60000",
        dailyChangePercent: "2.5",
        technicalTrend: "BULLISH",
        volumeCondition: "NORMAL",
        hasCurrentSignal: true,
        signalDirection: "BULLISH",
        riskLevel: "LOW",
        dataStatus: "CURRENT",
        reasonCode: null,
      },
    ];

    render(<WatchlistItemTable items={mockItems} onRemove={vi.fn()} />);

    expect(screen.getByText("FPT")).toBeInTheDocument();
    expect(screen.getByText("Tập đoàn FPT")).toBeInTheDocument();
    expect(screen.getByText("60.000 ₫")).toBeInTheDocument();
    expect(screen.getByText("(+) +2.50%")).toBeInTheDocument();
    expect(screen.getByText("Tăng (Bullish)")).toBeInTheDocument();
    expect(screen.getByText("Bình thường")).toBeInTheDocument();
    expect(screen.getByText("Rủi ro: LOW")).toBeInTheDocument();
  });

  it("renders a specific truthful no-signal state when symbol has no current signal", () => {
    const mockItems: WatchlistItem[] = [
      {
        symbol: "VNM",
        companyName: "Vinamilk",
        addedAt: "2026-08-11T00:00:00Z",
        currentPrice: "70000",
        dailyChangePercent: "-1.2",
        technicalTrend: "BEARISH",
        volumeCondition: "LOW_VOLUME",
        hasCurrentSignal: false,
        signalDirection: null,
        riskLevel: null,
        dataStatus: "CURRENT",
        reasonCode: null,
      },
    ];

    render(<WatchlistItemTable items={mockItems} onRemove={vi.fn()} />);

    expect(screen.getByText("VNM")).toBeInTheDocument();
    expect(screen.getByText("(-) -1.20%")).toBeInTheDocument();
    expect(screen.getByTestId("no-signal-badge")).toHaveTextContent("Không có tín hiệu");
  });

  it("renders stated insufficient-history reason when history is lacking", () => {
    const mockItems: WatchlistItem[] = [
      {
        symbol: "NEW",
        companyName: "New Stock",
        addedAt: "2026-08-12T00:00:00Z",
        currentPrice: "15000",
        dailyChangePercent: null,
        technicalTrend: null,
        volumeCondition: null,
        hasCurrentSignal: false,
        signalDirection: null,
        riskLevel: null,
        dataStatus: "PARTIAL",
        reasonCode: "INSUFFICIENT_HISTORY",
      },
    ];

    render(<WatchlistItemTable items={mockItems} onRemove={vi.fn()} />);

    expect(screen.getByText("NEW")).toBeInTheDocument();
    expect(screen.getByText("Chưa đủ dữ liệu")).toBeInTheDocument();
  });

  it("calls onRemove with symbol when delete button is clicked", async () => {
    const user = userEvent.setup();
    const onRemoveMock = vi.fn().mockResolvedValue(undefined);

    const mockItems: WatchlistItem[] = [
      {
        symbol: "HPG",
        companyName: "Tập đoàn Hòa Phát",
        addedAt: "2026-08-10T00:00:00Z",
        currentPrice: "28000",
        dailyChangePercent: "0",
        technicalTrend: "BULLISH",
        volumeCondition: "NORMAL",
        hasCurrentSignal: false,
        signalDirection: null,
        riskLevel: null,
        dataStatus: "CURRENT",
        reasonCode: null,
      },
    ];

    render(<WatchlistItemTable items={mockItems} onRemove={onRemoveMock} />);

    const deleteBtn = screen.getByRole("button", { name: "Xóa HPG khỏi danh sách" });
    await user.click(deleteBtn);

    expect(onRemoveMock).toHaveBeenCalledWith("HPG");
  });
});
