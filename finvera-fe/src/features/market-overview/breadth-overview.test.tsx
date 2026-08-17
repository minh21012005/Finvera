import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { BreadthOverview } from "./components/breadth-overview";
import type { MarketBreadth } from "./api/market-overview";

const complete: MarketBreadth = {
  dataStatus: "CURRENT", advancing: 612, declining: 498, unchanged: 91, eligible: 1201, unclassified: 0,
  universeVersion: "breadth-universe-v1", tradingDate: "2026-08-17", asOf: "2026-08-17T03:00:00Z",
  source: { provider: "FINVERA_ACCEPTED", dataset: "BREADTH" }, reasonCodes: [],
};

describe("BreadthOverview", () => {
  it("presents complete counts, provenance, and status text without relying on color", () => {
    render(<BreadthOverview breadth={complete} />);

    expect(screen.getByRole("heading", { name: /Độ rộng thị trường/i })).toBeVisible();
    expect(screen.getByText("Tăng giá")).toBeVisible();
    expect(screen.getByText("612")).toBeVisible();
    expect(screen.getByText(/Universe: breadth-universe-v1/i)).toBeVisible();
    expect(screen.getByText(/Nguồn: FINVERA_ACCEPTED/i)).toBeVisible();
    expect(screen.getByLabelText(/Độ rộng thị trường: Hiện tại/i)).toBeVisible();
  });

  it("makes partial coverage and unclassified securities explicit", () => {
    render(<BreadthOverview breadth={{ ...complete, dataStatus: "PARTIAL", unclassified: 3,
      eligible: 1204, reasonCodes: ["MISSING_REFERENCE_PRICE"] }} />);

    expect(screen.getByRole("status")).toHaveTextContent(/3 mã chưa phân loại/i);
    expect(screen.getByText(/MISSING_REFERENCE_PRICE/)).toBeVisible();
    expect(screen.getByLabelText(/Độ rộng thị trường: Một phần/i)).toBeVisible();
  });

  it("does not invent counts when breadth is unavailable", () => {
    render(<BreadthOverview breadth={{ ...complete, dataStatus: "UNAVAILABLE", advancing: null, declining: null,
      unchanged: null, eligible: null, unclassified: null, tradingDate: null, asOf: null,
      source: { provider: "UNAVAILABLE", dataset: "BREADTH" }, reasonCodes: ["BREADTH_NOT_AVAILABLE"] }} />);

    expect(screen.getByRole("status")).toHaveTextContent(/Không có dữ liệu độ rộng/i);
    expect(screen.queryByText("612")).not.toBeInTheDocument();
    expect(screen.getByLabelText(/Độ rộng thị trường: Không có dữ liệu/i)).toBeVisible();
  });
});
