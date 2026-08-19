import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ScreenerFilters } from "./components/screener-filters";
import { buildScreenRequest } from "./components/screener-filters-model";
import { ScreenerResults } from "./components/screener-results";
import type { ScreenResponse } from "./api/stock-screener";

function response(overrides: Partial<ScreenResponse> = {}): ScreenResponse {
  return {
    ruleVersion: "screener-v1",
    matches: [
      {
        symbol: "FPT",
        companyName: "CTCP FPT",
        exchange: "HOSE",
        sectorName: "Information Technology",
        matchedValues: { price: "123600.000000", rsi: "68.420000" },
        dataStatus: "CURRENT",
        asOfTradingDate: "2026-08-17",
      },
    ],
    totalMatchCount: 1,
    limit: 50,
    offset: 0,
    categoryDisclosures: [{ category: "TECHNICAL", status: "CURRENT", reasonCode: null, excludedCount: 0 }],
    coherenceKey: "coh-screen-1",
    calculatedAt: "2026-08-19T07:00:00Z",
    ...overrides,
  };
}

describe("ScreenerFilters", () => {
  it("submits an empty request when no filter fields are filled", async () => {
    const user = userEvent.setup();
    let submitted: unknown = null;
    render(<ScreenerFilters onSubmit={(r) => (submitted = r)} submitting={false} />);

    await user.click(screen.getByRole("button", { name: /lọc cổ phiếu/i }));

    expect(submitted).toEqual({ market: undefined, price: undefined, technical: undefined, fundamental: undefined });
  });

  it("builds a request carrying only the fields the owner actually filled in", async () => {
    const user = userEvent.setup();
    let submitted: unknown = null;
    render(<ScreenerFilters onSubmit={(r) => (submitted = r)} submitting={false} />);

    await user.type(screen.getByLabelText(/giá tối thiểu/i), "50000");
    await user.type(screen.getByLabelText(/giá tối đa/i), "150000");
    await user.type(screen.getByLabelText(/rsi tối thiểu/i), "60");
    await user.click(screen.getByRole("button", { name: /lọc cổ phiếu/i }));

    expect(submitted).toMatchObject({
      price: { priceMin: "50000", priceMax: "150000" },
      technical: { rsiMin: "60" },
    });
  });

  it("disables the submit button while a screen is running", () => {
    render(<ScreenerFilters onSubmit={() => {}} submitting={true} />);
    expect(screen.getByRole("button", { name: /đang lọc/i })).toBeDisabled();
  });
});

describe("buildScreenRequest", () => {
  it("normalizes the exchange field to uppercase", () => {
    const request = buildScreenRequest({
      exchange: "hose",
      marketCapMin: "",
      marketCapMax: "",
      priceMin: "",
      priceMax: "",
      priceChangePercentMin: "",
      priceChangePercentMax: "",
      rsiMin: "",
      rsiMax: "",
      macdSignal: "",
      maRelationship: "",
      volumeMin: "",
      volumeMax: "",
      relativeVolumeMin: "",
      relativeVolumeMax: "",
      breakout: "",
      trend: "",
      revenueGrowthPercentMin: "",
      revenueGrowthPercentMax: "",
      earningsGrowthPercentMin: "",
      earningsGrowthPercentMax: "",
      roeMin: "",
      roeMax: "",
      roaMin: "",
      roaMax: "",
      peMin: "",
      peMax: "",
      pbMin: "",
      pbMax: "",
      debtToEquityMin: "",
      debtToEquityMax: "",
    });
    expect(request.market?.exchange).toEqual(["HOSE"]);
  });
});

describe("ScreenerResults", () => {
  it("shows an explicit empty state rather than an empty table when there are no matches", () => {
    render(<ScreenerResults result={response({ matches: [], totalMatchCount: 0, categoryDisclosures: [] })} />);
    expect(screen.getByRole("status")).toHaveTextContent(/không có mã cổ phiếu nào/i);
  });

  it("renders matched values and a non-color status indicator for each match", () => {
    render(<ScreenerResults result={response()} />);
    expect(screen.getByRole("button", { name: "FPT" })).toBeInTheDocument();
    // dataStatusLabel renders as text, not color alone; it appears once per
    // category disclosure and once per match row here.
    expect(screen.getAllByText(/hiện tại/i).length).toBeGreaterThan(0);
  });

  it("discloses a degraded category with its exclusion count and reason", () => {
    render(
      <ScreenerResults
        result={response({
          categoryDisclosures: [
            { category: "FUNDAMENTAL", status: "UNAVAILABLE", reasonCode: "INSUFFICIENT_HISTORY", excludedCount: 3 },
          ],
        })}
      />,
    );
    expect(screen.getByText(/cơ bản/i)).toBeInTheDocument();
    expect(screen.getByText(/3 mã bị loại/i)).toBeInTheDocument();
  });

  it("presents results as quantitative filtering output, not investment advice", () => {
    render(<ScreenerResults result={response()} />);
    expect(screen.getByRole("note")).toHaveTextContent(/không phải khuyến nghị đầu tư/i);
  });
});
