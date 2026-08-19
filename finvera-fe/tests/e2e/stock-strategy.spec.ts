import { expect, test, type Page } from "@playwright/test";

test.describe("P3 strategy scan — screen for a strategy across the universe", () => {
  test("P3 finds exactly the triggering stocks with their own signal summary", async ({ page }) => {
    await installAuthenticatedOwnerSession(page);
    await page.route("**/api/v1/strategies/TREND_FOLLOWING/scan", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(scanResponse([
          match("FPT", "CTCP FPT"),
          match("VNM", "CTCP Vinamilk"),
        ])),
      });
    });

    await page.goto("/strategies");
    await page.getByRole("button", { name: /quét thị trường/i }).click();

    await expect(page.getByText(/2 mã/)).toBeVisible();
    await expect(page.getByRole("button", { name: "FPT" })).toBeVisible();
    await expect(page.getByRole("button", { name: "VNM" })).toBeVisible();
  });

  test("P3 shows a specific empty-result state, not an error", async ({ page }) => {
    await installAuthenticatedOwnerSession(page);
    await page.route("**/api/v1/strategies/MEAN_REVERSION/scan", async (route) => {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(scanResponse([])) });
    });

    await page.goto("/strategies");
    await page.getByRole("combobox").selectOption("MEAN_REVERSION");
    await page.getByRole("button", { name: /quét thị trường/i }).click();

    await expect(page.getByText(/Không có mã cổ phiếu nào đang kích hoạt/)).toBeVisible();
    await expect(page.getByRole("alert")).toHaveCount(0);
  });

  test("P3 discloses an insufficient-history exclusion, distinguishable from a genuine empty result", async ({ page }) => {
    await installAuthenticatedOwnerSession(page);
    await page.route("**/api/v1/strategies/BREAKOUT/scan", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(scanResponse([], { excludedForInsufficientHistoryCount: 7 })),
      });
    });

    await page.goto("/strategies");
    await page.getByRole("combobox").selectOption("BREAKOUT");
    await page.getByRole("button", { name: /quét thị trường/i }).click();

    await expect(page.getByText(/7 mã bị loại do chưa đủ dữ liệu lịch sử/)).toBeVisible();
  });

  test("P3 navigates to the existing stock detail page when a matched symbol is selected", async ({ page }) => {
    await installAuthenticatedOwnerSession(page);
    await page.route("**/api/v1/strategies/TREND_FOLLOWING/scan", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(scanResponse([match("FPT", "CTCP FPT")])),
      });
    });
    await page.route("**/api/v1/stocks/FPT", async (route) => {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(minimalOverview()) });
    });
    await page.route("**/api/v1/stocks/FPT/chart**", async (route) => {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(minimalChart()) });
    });
    await page.route("**/api/v1/stocks/FPT/technical", async (route) => {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(minimalTechnical()) });
    });
    await page.route("**/api/v1/stocks/FPT/fundamentals", async (route) => {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(minimalFundamentals()) });
    });
    await page.route("**/api/v1/stocks/FPT/valuation", async (route) => {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(minimalValuation()) });
    });
    await page.route("**/api/v1/stocks/FPT/signals", async (route) => {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(minimalSignals()) });
    });

    await page.goto("/strategies");
    await page.getByRole("button", { name: /quét thị trường/i }).click();
    await page.getByRole("button", { name: "FPT" }).click();

    await expect(page).toHaveURL(/\/stocks\/FPT$/);
    await expect(page.getByRole("heading", { name: "CTCP FPT" })).toBeVisible();
  });
});

function match(symbol: string, companyName: string) {
  return {
    symbol,
    companyName,
    exchange: "HOSE",
    signal: {
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
    },
  };
}

function scanResponse(matches: unknown[], overrides: Record<string, unknown> = {}) {
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
  await page.route("**/api/v1/auth/csrf", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ headerName: "X-CSRF-TOKEN", token: "fixture-csrf-token" }),
    });
  });
}

function meta(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    contractVersion: "1.0",
    symbol: "FPT",
    asOf: "2026-08-17T07:15:00Z",
    tradingDate: "2026-08-14",
    timezone: "Asia/Ho_Chi_Minh",
    dataStatus: "CURRENT",
    coherenceKey: "coh-e2e-strategy",
    sources: ["FINVERA_ACCEPTED"],
    reasonCodes: [],
    ...overrides,
  };
}

function minimalOverview() {
  return {
    meta: meta({ coherenceKey: "coh-overview-e2e-strategy" }),
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
      referencePrice: "122500.000000",
      absoluteChange: "1100.000000",
      percentageChange: "0.897959",
      direction: "UP",
      volume: 2270000,
      valueVnd: "280457200000.0000",
      marketCapVnd: "180703200000000.000000",
      applicability: "DEFINED",
      changeBasisReason: null,
    },
    session: { state: "OPEN", tradingDate: "2026-08-14", calendarVersion: "finvera-calendar-v1" },
  };
}

function minimalChart() {
  return { meta: meta({ coherenceKey: "coh-chart-e2e-strategy" }), window: "1M", adjustmentStatus: "ADJUSTED", bars: [] };
}

function minimalTechnical() {
  return {
    meta: meta({ coherenceKey: "coh-technical-e2e-strategy" }),
    ruleVersion: "technical-indicators-v1",
    adjustmentStatus: "ADJUSTED",
    disclaimerCode: "QUANTITATIVE_DECISION_SUPPORT",
    indicators: [],
  };
}

function minimalFundamentals() {
  return { meta: meta({ coherenceKey: "coh-fundamentals-e2e-strategy" }), period: null, metrics: [] };
}

function minimalValuation() {
  return {
    meta: meta({ coherenceKey: "coh-valuation-e2e-strategy" }),
    ruleVersion: "valuation-v1",
    published: false,
    classification: null,
    score: null,
    displayedScore: null,
    confidence: null,
    disclaimerCode: "QUANTITATIVE_DECISION_SUPPORT",
    basis: {
      usedOwnHistory: false,
      usedSector: false,
      sector: null,
      sectorScheme: null,
      sectorSchemeVersion: null,
      sectorConstituentCount: null,
      historyPointCount: null,
    },
    metrics: [],
  };
}

function minimalSignals() {
  return {
    symbol: "FPT",
    dataStatus: "CURRENT",
    evaluations: [
      "TREND_FOLLOWING", "MOMENTUM", "BREAKOUT", "PULLBACK", "MEAN_REVERSION",
      "MA_CROSSOVER", "MACD_BASED", "RSI_BASED",
    ].map((strategyCode) => ({ strategyCode, status: "NO_SIGNAL", reasonCode: null, signal: null })),
    disclaimerCode: "QUANTITATIVE_DECISION_SUPPORT",
    coherenceKey: "coh-signal-e2e-strategy",
    asOf: "2026-08-17T07:15:01Z",
  };
}
