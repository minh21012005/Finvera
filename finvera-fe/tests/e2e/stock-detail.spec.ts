import { expect, test, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

type OverviewMode = "complete" | "delayed" | "stale" | "closed" | "missing-reference";
type ChartMode = "complete" | "unavailable";

test.describe("P1 stock detail — current price and recent history", () => {
  test("P1 renders a complete overview and ascending chart without contacting AI or a market provider", async ({ page }) => {
    const forbiddenRequests = forbidExternalRequests(page);
    await installStockDetailFixtures(page, "complete", "complete");

    await page.goto("/stocks/FPT");

    await expect(page.getByRole("heading", { name: "CTCP FPT" })).toBeVisible();
    await expect(page.getByText(/123\.600/)).toBeVisible();
    await expect(page.getByText(/Tăng/i)).toBeVisible();
    await expect(page.getByRole("img", { name: /biểu đồ giá/i })).toBeVisible();
    await expect(page.getByText(/đã điều chỉnh/i)).toBeVisible();
    expect(forbiddenRequests).toEqual([]);
  });

  test("P1 identifies the closed market and keeps the latest accepted session visible", async ({ page }) => {
    await installStockDetailFixtures(page, "closed", "complete");
    await page.goto("/stocks/FPT");
    await expect(page.getByText(/đã đóng cửa/i)).toBeVisible();
    await expect(page.getByText("2026-08-14", { exact: true })).toBeVisible();
  });

  test("P1 labels a delayed or stale snapshot and never claims it is live", async ({ page }) => {
    await installStockDetailFixtures(page, "delayed", "complete");
    await page.goto("/stocks/FPT");
    await expect(page.getByText("Chậm", { exact: true })).toBeVisible();

    await installStockDetailFixtures(page, "stale", "complete");
    await page.reload();
    await expect(page.getByText("Cũ", { exact: true })).toBeVisible();
  });

  test("P1 shows a specific not-found state for an unsupported symbol without fabricating data", async ({ page }) => {
    await installAuthenticatedOwnerSession(page);
    await page.route("**/api/v1/stocks/ZZZZZ", async (route) => {
      await route.fulfill({
        status: 404,
        contentType: "application/problem+json",
        body: JSON.stringify({ type: "about:blank", title: "Stock not supported", status: 404, reasonCode: "STOCK_NOT_SUPPORTED" }),
      });
    });

    await page.goto("/stocks/ZZZZZ");

    await expect(page.getByRole("alert")).toContainText("ZZZZZ");
    await expect(page.getByRole("heading", { name: "CTCP FPT" })).toHaveCount(0);
  });

  test("P1 keeps the overview fully usable when the chart section alone is unavailable", async ({ page }) => {
    await installStockDetailFixtures(page, "complete", "unavailable");
    await page.goto("/stocks/FPT");

    await expect(page.getByRole("heading", { name: "CTCP FPT" })).toBeVisible();
    await expect(page.getByText(/123\.600/)).toBeVisible();
    await expect(page.getByText("Không có dữ liệu biểu đồ")).toBeVisible();
  });

  test("P1 shows change fields as unavailable with a reason rather than inferring or zeroing them", async ({ page }) => {
    await installStockDetailFixtures(page, "missing-reference", "complete");
    await page.goto("/stocks/FPT");

    await expect(page.getByText(/REFERENCE_PRICE_UNAVAILABLE/).first()).toBeVisible();
    await expect(page.getByText(/123\.600/)).toBeVisible();
  });

  test("P1 is reachable from the market overview symbol search", async ({ page }) => {
    await installMarketOverviewSearchFixture(page);
    await installStockDetailFixtures(page, "complete", "complete");

    await page.route("**/api/v1/market/overview", async (route) => {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(minimalMarketOverview()) });
    });

    await page.goto("/");
    await page.getByLabel("Tra cứu mã cổ phiếu").fill("FPT");
    await page.getByRole("button", { name: /FPT — CTCP FPT/ }).click();

    await expect(page).toHaveURL(/\/stocks\/FPT$/);
    await expect(page.getByRole("heading", { name: "CTCP FPT" })).toBeVisible();
  });

  test("P1 disclaims technical/valuation output as decision support, not investment advice", async ({ page }) => {
    await installStockDetailFixtures(page, "complete", "complete");
    await page.goto("/stocks/FPT");
    await expect(page.getByText(/không phải khuyến nghị đầu tư/i)).toBeVisible();
    await expect(page.getByText(/Mua|Bán/i)).toHaveCount(0);
  });

  test("P1 has no automatically detectable accessibility violations for the complete stock detail page", async ({ page }) => {
    await installStockDetailFixtures(page, "complete", "complete");
    await page.goto("/stocks/FPT");
    await expect(page.getByRole("heading", { name: "CTCP FPT" })).toBeVisible();

    const results = await new AxeBuilder({ page }).analyze();
    expect(results.violations).toEqual([]);
  });
});

async function installStockDetailFixtures(page: Page, overviewMode: OverviewMode, chartMode: ChartMode): Promise<void> {
  await installAuthenticatedOwnerSession(page);
  await page.route("**/api/v1/stocks/FPT", async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(overviewFixture(overviewMode)) });
  });
  await page.route("**/api/v1/stocks/FPT/chart**", async (route) => {
    if (chartMode === "unavailable") {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(chartFixture(false)) });
      return;
    }
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(chartFixture(true)) });
  });
}

async function installMarketOverviewSearchFixture(page: Page): Promise<void> {
  await page.route("**/api/v1/stocks?query=**", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        contractVersion: "1.0",
        results: [{ symbol: "FPT", companyName: "CTCP FPT", exchange: "HOSE", sector: "Information Technology", listingStatus: "LISTED" }],
      }),
    });
  });
}

async function installAuthenticatedOwnerSession(page: Page): Promise<void> {
  await page.route("**/api/v1/auth/session", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        subject: "00000000-0000-0000-0000-000000000001",
        username: "fixture-owner",
        authenticatedAt: "2026-08-17T02:55:00Z",
        expiresAt: "2026-08-17T10:55:00Z",
      }),
    });
  });
}

function forbidExternalRequests(page: Page): string[] {
  const requests: string[] = [];
  page.on("request", (request) => {
    const host = new URL(request.url()).hostname;
    if (host !== "127.0.0.1" && host !== "localhost") requests.push(request.url());
  });
  return requests;
}

function overviewFixture(mode: OverviewMode) {
  const dataStatus = mode === "delayed" ? "DELAYED" : mode === "stale" ? "STALE" : mode === "missing-reference" ? "PARTIAL" : "CURRENT";
  const tradingDate = mode === "stale" || mode === "closed" ? "2026-08-14" : "2026-08-17";
  const missingReference = mode === "missing-reference";
  return {
    meta: {
      contractVersion: "1.0",
      symbol: "FPT",
      asOf: "2026-08-17T07:15:00Z",
      tradingDate,
      timezone: "Asia/Ho_Chi_Minh",
      dataStatus,
      coherenceKey: "coh-fpt-1",
      sources: ["FINVERA_ACCEPTED"],
      reasonCodes: missingReference ? ["REFERENCE_PRICE_UNAVAILABLE"] : [],
    },
    profile: {
      symbol: "FPT",
      companyName: "CTCP FPT",
      companyNameEn: "FPT Corporation",
      exchange: "HOSE",
      sector: "Information Technology",
      sectorScheme: "finvera-sector-v1",
      listingStatus: "LISTED",
      sharesOutstanding: 1462000000,
    },
    price: {
      currency: "VND",
      last: "123600.000000",
      referencePrice: missingReference ? null : "122500.000000",
      absoluteChange: missingReference ? null : "1100.000000",
      percentageChange: missingReference ? null : "0.897959",
      direction: missingReference ? "UNCHANGED" : "UP",
      volume: 2270000,
      valueVnd: "280457200000.0000",
      marketCapVnd: "180703200000000.000000",
      applicability: missingReference ? "MISSING" : "DEFINED",
      changeBasisReason: missingReference ? "REFERENCE_PRICE_UNAVAILABLE" : null,
    },
    session: {
      state: mode === "closed" ? "CLOSED" : "OPEN",
      tradingDate,
      calendarVersion: "finvera-calendar-v1",
    },
  };
}

function chartFixture(withBars: boolean) {
  return {
    meta: {
      contractVersion: "1.0",
      symbol: "FPT",
      asOf: "2026-08-17T07:15:00Z",
      tradingDate: withBars ? "2026-08-14" : null,
      timezone: "Asia/Ho_Chi_Minh",
      dataStatus: withBars ? "CURRENT" : "UNAVAILABLE",
      coherenceKey: "coh-chart-1",
      sources: ["FINVERA_ACCEPTED"],
      reasonCodes: withBars ? [] : ["PROVIDER_UNAVAILABLE"],
    },
    window: "1M",
    adjustmentStatus: withBars ? "ADJUSTED" : "UNKNOWN",
    bars: withBars
      ? [
          { tradingDate: "2026-08-13", open: "122000.000000", high: "123000.000000", low: "121500.000000", close: "122500.000000", volume: 2000000 },
          { tradingDate: "2026-08-14", open: "122500.000000", high: "123800.000000", low: "122300.000000", close: "123600.000000", volume: 2270000 },
        ]
      : [],
  };
}

function minimalMarketOverview() {
  return {
    contractVersion: "1.0",
    generatedAt: "2026-08-17T03:00:00Z",
    tradingDate: "2026-08-17",
    timezone: "Asia/Ho_Chi_Minh",
    dataStatus: "UNAVAILABLE",
    session: { state: "OPEN", tradingDate: "2026-08-17", asOf: "2026-08-17T03:00:00Z", calendarVersion: "market-calendar-v1", venueStates: [] },
    indices: ["VN_INDEX", "VN30", "HNX_INDEX", "UPCOM_INDEX"].map((code) => ({
      code, displayName: code, venue: "HOSE", dataStatus: "UNAVAILABLE", direction: null, value: null,
      absoluteChange: null, percentageChange: null, matchedVolume: null, matchedValueVnd: null,
      unit: "INDEX_POINT", currency: "VND", tradingDate: null, asOf: null,
      source: { provider: "UNAVAILABLE", dataset: "INDEX" }, revision: null, reasonCodes: ["MISSING_INDEX"],
    })),
    breadth: {
      dataStatus: "UNAVAILABLE", advancing: null, declining: null, unchanged: null, eligible: null,
      unclassified: null, universeVersion: "breadth-universe-v1", tradingDate: null, asOf: null,
      source: { provider: "UNAVAILABLE", dataset: "BREADTH" }, reasonCodes: ["BREADTH_NOT_AVAILABLE"],
    },
    regime: {
      dataStatus: "UNAVAILABLE", ruleVersion: "market-regime-v1", label: null, score: null, confidence: null,
      confidenceMeaning: "ASSESSMENT_QUALITY_NOT_FORECAST_PROBABILITY", tradingDate: null, asOf: null, factors: [],
      source: { provider: "UNAVAILABLE", dataset: "REGIME" }, reasonCodes: ["REGIME_NOT_AVAILABLE"],
      disclaimerCode: "QUANTITATIVE_DECISION_SUPPORT_NOT_INVESTMENT_ADVICE",
    },
    warnings: [],
  };
}
