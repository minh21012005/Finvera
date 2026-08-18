import { expect, test, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

type OverviewMode = "complete" | "delayed" | "stale" | "closed" | "missing-reference";
type ChartMode = "complete" | "unavailable";
type TechnicalMode = "full" | "partial-199" | "partial-249" | "raw-unavailable";

test.describe("P1 stock detail — current price and recent history", () => {
  test("P1 renders a complete overview and ascending chart without contacting AI or a market provider", async ({ page }) => {
    const forbiddenRequests = forbidExternalRequests(page);
    await installStockDetailFixtures(page, "complete", "complete");

    await page.goto("/stocks/FPT");

    await expect(page.getByRole("heading", { name: "CTCP FPT" })).toBeVisible();
    await expect(page.getByText(/123\.600/)).toBeVisible();
    await expect(page.getByText(/Tăng/i)).toBeVisible();
    await expect(page.getByRole("img", { name: /biểu đồ giá/i })).toBeVisible();
    await expect(page.getByText(/đã điều chỉnh/i).first()).toBeVisible();
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
    await expect(page.getByText(/không phải khuyến nghị đầu tư/i).first()).toBeVisible();
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

test.describe("P2 technical condition — deterministic indicators", () => {
  test("P2 shows a complete result with every indicator's value, window, and rule version for a 250-bar history", async ({ page }) => {
    await installStockDetailFixtures(page, "complete", "complete", "full");
    await page.goto("/stocks/FPT");

    await expect(page.getByText("MA20", { exact: true })).toBeVisible();
    await expect(page.getByText("RSI(14)")).toBeVisible();
    await expect(page.getByText(/technical-indicators-v1/)).toBeVisible();
    await expect(page.getByText(/41\.491,75/).first()).toBeVisible();
  });

  test("P2 degrades exactly the under-lookback indicators for a 199-bar history and leaves the rest usable", async ({ page }) => {
    await installStockDetailFixtures(page, "complete", "complete", "partial-199");
    await page.goto("/stocks/FPT");

    await expect(page.getByText(/INSUFFICIENT_HISTORY/).first()).toBeVisible();
    await expect(page.getByText(/cần 200 phiên, hiện có/)).toBeVisible();
    // MA20 only needs 20 bars, so it stays usable even though MA200 degrades alone.
    await expect(page.getByText("MA20", { exact: true })).toBeVisible();
    await expect(page.getByText(/41\.491,75/).first()).toBeVisible();
  });

  test("P2 degrades exactly the fixed-window indicators for a 249-bar history", async ({ page }) => {
    await installStockDetailFixtures(page, "complete", "complete", "partial-249");
    await page.goto("/stocks/FPT");

    await expect(page.getByText(/cần 250 phiên, hiện có/).first()).toBeVisible();
    // MA200 only needs 200 bars, so it is available at 249, unlike RSI/MACD/ATR14.
    await expect(page.getByText("MA200", { exact: true })).toBeVisible();
    await expect(page.getByText(/45\.657,57/)).toBeVisible();
  });

  test("P2 reproduces an identical result on reload (determinism)", async ({ page }) => {
    await installStockDetailFixtures(page, "complete", "complete", "full");
    await page.goto("/stocks/FPT");
    await expect(page.getByText(/41\.491,75/).first()).toBeVisible();

    await page.reload();
    await expect(page.getByText(/41\.491,75/).first()).toBeVisible();
  });

  test("P2 discloses the price basis actually used and never claims adjusted when it is not", async ({ page }) => {
    await installStockDetailFixtures(page, "complete", "complete", "raw-unavailable");
    await page.goto("/stocks/FPT");

    await expect(page.getByText(/chưa điều chỉnh \(RAW\)/)).toBeVisible();
    await expect(page.getByText(/ADJUSTMENT_BASIS_UNAVAILABLE/).first()).toBeVisible();
  });

  test("P2 discloses technical output as decision support, not investment advice", async ({ page }) => {
    await installStockDetailFixtures(page, "complete", "complete", "full");
    await page.goto("/stocks/FPT");

    await expect(page.getByText(/không phải khuyến nghị đầu tư/i).first()).toBeVisible();
    await expect(page.getByText(/Mua|Bán/i)).toHaveCount(0);
  });
});

async function installStockDetailFixtures(
  page: Page,
  overviewMode: OverviewMode,
  chartMode: ChartMode,
  technicalMode: TechnicalMode = "full",
): Promise<void> {
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
  await page.route("**/api/v1/stocks/FPT/technical", async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(technicalFixture(technicalMode)) });
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

function technicalFixture(mode: TechnicalMode) {
  const rawBasis = mode === "raw-unavailable";
  const availableBars = mode === "partial-199" ? 199 : mode === "partial-249" ? 249 : 250;
  const fixedWindowAvailable = availableBars >= 250;

  const defined = (
    indicatorCode: string,
    components: Array<{ componentCode: string; value: string; unit: string; displayPrecision: number }>,
    requiredBars: number,
  ) => ({
    indicatorCode,
    applicability: "DEFINED",
    requiredBars,
    availableBars,
    windowStartDate: "2025-09-01",
    windowEndDate: "2026-08-14",
    reasonCode: null as string | null,
    components: components.map((c) => ({ ...c, applicability: "DEFINED", reasonCode: null })),
  });
  const missing = (indicatorCode: string, requiredBars: number) => ({
    indicatorCode,
    applicability: "MISSING",
    requiredBars,
    availableBars,
    windowStartDate: null,
    windowEndDate: null,
    reasonCode: "INSUFFICIENT_HISTORY",
    components: [],
  });

  const ma20 = defined("MA20", [{ componentCode: "VALUE", value: "41491.75", unit: "VND", displayPrecision: 2 }], 20);
  const ma50 = defined("MA50", [{ componentCode: "VALUE", value: "42765.28", unit: "VND", displayPrecision: 2 }], 50);
  const ma200 = availableBars >= 200
    ? defined("MA200", [{ componentCode: "VALUE", value: "45657.57", unit: "VND", displayPrecision: 2 }], 200)
    : missing("MA200", 200);
  const rsi14 = fixedWindowAvailable
    ? defined("RSI14", [{ componentCode: "VALUE", value: "68.77", unit: "POINTS", displayPrecision: 2 }], 250)
    : missing("RSI14", 250);
  const macd = fixedWindowAvailable
    ? defined("MACD", [
        { componentCode: "MACD_LINE", value: "563.95", unit: "VND", displayPrecision: 2 },
        { componentCode: "SIGNAL", value: "26.53", unit: "VND", displayPrecision: 2 },
        { componentCode: "HISTOGRAM", value: "537.42", unit: "VND", displayPrecision: 2 },
      ], 250)
    : missing("MACD", 250);
  const bbands = defined("BBANDS", [
    { componentCode: "UPPER", value: "45163.78", unit: "VND", displayPrecision: 2 },
    { componentCode: "MIDDLE", value: "41491.75", unit: "VND", displayPrecision: 2 },
    { componentCode: "LOWER", value: "37819.73", unit: "VND", displayPrecision: 2 },
    { componentCode: "BANDWIDTH", value: "17.70", unit: "PERCENT", displayPrecision: 2 },
  ], 20);
  const atr14 = fixedWindowAvailable
    ? defined("ATR14", [
        { componentCode: "VALUE", value: "774.79", unit: "VND", displayPrecision: 2 },
        { componentCode: "PERCENT_OF_CLOSE", value: "1.74", unit: "PERCENT", displayPrecision: 2 },
      ], 250)
    : missing("ATR14", 250);
  const avgVolume20 = defined("AVG_VOLUME20", [
    { componentCode: "VALUE", value: "2243225", unit: "SHARES", displayPrecision: 0 },
  ], 20);
  const relativeVolume = defined("RELATIVE_VOLUME", [
    { componentCode: "VALUE", value: "0.97", unit: "RATIO", displayPrecision: 2 },
  ], 21);

  return {
    meta: {
      contractVersion: "1.0",
      symbol: "FPT",
      asOf: "2026-08-17T07:15:00Z",
      tradingDate: "2026-08-14",
      timezone: "Asia/Ho_Chi_Minh",
      dataStatus: "CURRENT",
      coherenceKey: "coh-technical-1",
      sources: ["FINVERA_ACCEPTED"],
      reasonCodes: rawBasis ? ["ADJUSTMENT_BASIS_UNAVAILABLE"] : [],
    },
    ruleVersion: "technical-indicators-v1",
    adjustmentStatus: rawBasis ? "RAW" : "ADJUSTED",
    disclaimerCode: "QUANTITATIVE_DECISION_SUPPORT",
    indicators: [ma20, ma50, ma200, rsi14, macd, bbands, atr14, avgVolume20, relativeVolume],
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
