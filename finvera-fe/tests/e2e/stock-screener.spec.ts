import { expect, test, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

test.describe("P1 stock screener — Market and Price filters", () => {
  test("P1 renders a combined Market/Price match with its qualifying values", async ({ page }) => {
    await installScreenerFixtures(page, screenResponse({
      matches: [match({ symbol: "FPT", matchedValues: { exchange: "HOSE", price: "123600.000000" } })],
      totalMatchCount: 1,
      categoryDisclosures: [disclosure("MARKET"), disclosure("PRICE")],
    }));

    await page.goto("/screener");
    await page.getByLabel(/sàn giao dịch/i).fill("HOSE");
    await page.getByLabel(/^giá tối thiểu$/i).fill("100000");
    await page.getByLabel(/^giá tối đa$/i).fill("150000");
    await page.getByRole("button", { name: /lọc cổ phiếu/i }).click();

    await expect(page.getByText(/kết quả \(1 mã\)/i)).toBeVisible();
    await expect(page.getByRole("button", { name: "FPT" })).toBeVisible();
    await expect(page.getByText(/123\.600/)).toBeVisible();
  });

  test("P1 shows a specific empty-result state rather than an error or a blank table", async ({ page }) => {
    await installScreenerFixtures(page, screenResponse({ matches: [], totalMatchCount: 0, categoryDisclosures: [] }));

    await page.goto("/screener");
    await page.getByRole("button", { name: /lọc cổ phiếu/i }).click();

    await expect(page.getByRole("status")).toContainText(/không có mã cổ phiếu nào/i);
  });

  test("P1 discloses a category with excluded candidates rather than silently omitting them", async ({ page }) => {
    await installScreenerFixtures(page, screenResponse({
      matches: [],
      totalMatchCount: 0,
      categoryDisclosures: [{ category: "PRICE", status: "PARTIAL", reasonCode: "PRICE_UNAVAILABLE", excludedCount: 2 }],
    }));

    await page.goto("/screener");
    await page.getByLabel(/^giá tối thiểu$/i).fill("1");
    await page.getByRole("button", { name: /lọc cổ phiếu/i }).click();

    await expect(page.getByText(/2 mã bị loại/i)).toBeVisible();
    await expect(page.getByText(/PRICE_UNAVAILABLE/)).toBeVisible();
  });

  test("P1 navigates to the existing stock detail page when a result is selected", async ({ page }) => {
    await installScreenerFixtures(page, screenResponse({
      matches: [match({ symbol: "FPT" })],
      totalMatchCount: 1,
    }));
    await page.route("**/api/v1/stocks/FPT", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(minimalOverview()),
      });
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

    await page.goto("/screener");
    await page.getByRole("button", { name: /lọc cổ phiếu/i }).click();
    await page.getByRole("button", { name: "FPT" }).click();

    await expect(page).toHaveURL(/\/stocks\/FPT$/);
  });

  test("P1 rejects a contradictory filter range with a specific message, not a silent empty result", async ({ page }) => {
    await installAuthenticatedOwnerSession(page);
    await page.route("**/api/v1/screener/executions", async (route) => {
      await route.fulfill({
        status: 400,
        contentType: "application/problem+json",
        body: JSON.stringify({ type: "about:blank", title: "Invalid filter range", status: 400, reasonCode: "INVALID_FILTER_RANGE" }),
      });
    });

    await page.goto("/screener");
    await page.getByLabel(/^giá tối thiểu$/i).fill("999999");
    await page.getByLabel(/^giá tối đa$/i).fill("1");
    await page.getByRole("button", { name: /lọc cổ phiếu/i }).click();

    await expect(page.getByRole("alert")).toContainText(/khoảng lọc không hợp lệ/i);
  });

  test("P1 has no automatically detectable accessibility violations", async ({ page }) => {
    await installScreenerFixtures(page, screenResponse({
      matches: [match({ symbol: "FPT" })],
      totalMatchCount: 1,
      categoryDisclosures: [disclosure("PRICE")],
    }));

    await page.goto("/screener");
    await page.getByRole("button", { name: /lọc cổ phiếu/i }).click();
    await expect(page.getByRole("button", { name: "FPT" })).toBeVisible();

    const results = await new AxeBuilder({ page }).analyze();
    expect(results.violations).toEqual([]);
  });
});

test.describe("P2 stock screener — Technical filters", () => {
  test("P2 matches on a Technical-only screen using the persisted indicator values", async ({ page }) => {
    await installScreenerFixtures(page, screenResponse({
      matches: [match({ symbol: "FPT", matchedValues: { rsi: "68.420000", trend: "UPTREND" } })],
      totalMatchCount: 1,
      categoryDisclosures: [disclosure("TECHNICAL")],
    }));

    await page.goto("/screener");
    await page.getByLabel(/rsi tối thiểu/i).fill("60");
    await page.getByLabel(/rsi tối đa/i).fill("100");
    await page.getByLabel(/xu hướng/i).selectOption("UPTREND");
    await page.getByRole("button", { name: /lọc cổ phiếu/i }).click();

    await expect(page.getByRole("button", { name: "FPT" })).toBeVisible();
    await expect(page.getByText(/68,42/)).toBeVisible();
  });

  test("P2 matches a three-category intersection (Market, Price, Technical) in one screen", async ({ page }) => {
    await installScreenerFixtures(page, screenResponse({
      matches: [match({ symbol: "FPT", matchedValues: { exchange: "HOSE", price: "123600.000000", rsi: "68.420000" } })],
      totalMatchCount: 1,
      categoryDisclosures: [disclosure("MARKET"), disclosure("PRICE"), disclosure("TECHNICAL")],
    }));

    await page.goto("/screener");
    await page.getByLabel(/sàn giao dịch/i).fill("HOSE");
    await page.getByLabel(/^giá tối thiểu$/i).fill("100000");
    await page.getByLabel(/rsi tối thiểu/i).fill("60");
    await page.getByRole("button", { name: /lọc cổ phiếu/i }).click();

    await expect(page.getByRole("button", { name: "FPT" })).toBeVisible();
  });

  test("P2 reload-determinism: an identical screen run twice renders the identical result", async ({ page }) => {
    await installScreenerFixtures(page, screenResponse({
      matches: [match({ symbol: "FPT", matchedValues: { rsi: "68.420000" } })],
      totalMatchCount: 1,
    }));

    await page.goto("/screener");
    await page.getByLabel(/rsi tối thiểu/i).fill("60");
    await page.getByRole("button", { name: /lọc cổ phiếu/i }).click();
    await expect(page.getByRole("button", { name: "FPT" })).toBeVisible();

    await page.getByRole("button", { name: /lọc cổ phiếu/i }).click();
    await expect(page.getByRole("button", { name: "FPT" })).toBeVisible();
    await expect(page.getByText(/kết quả \(1 mã\)/i)).toBeVisible();
  });
});

test.describe("P3 stock screener — Fundamental and Valuation filters", () => {
  test("P3 matches a full four-category screen and shows the revenue-growth qualifying value", async ({ page }) => {
    await installScreenerFixtures(page, screenResponse({
      matches: [match({
        symbol: "FPT",
        matchedValues: {
          exchange: "HOSE", price: "123600.000000", rsi: "68.420000", revenueGrowthPercent: "23.486600",
        },
      })],
      totalMatchCount: 1,
      categoryDisclosures: [disclosure("MARKET"), disclosure("PRICE"), disclosure("TECHNICAL"), disclosure("FUNDAMENTAL")],
    }));

    await page.goto("/screener");
    await page.getByLabel(/sàn giao dịch/i).fill("HOSE");
    await page.getByLabel(/^giá tối thiểu$/i).fill("100000");
    await page.getByLabel(/rsi tối thiểu/i).fill("60");
    await page.getByLabel(/tăng trưởng dt % tối thiểu/i).fill("0");
    await page.getByRole("button", { name: /lọc cổ phiếu/i }).click();

    await expect(page.getByRole("button", { name: "FPT" })).toBeVisible();
    await expect(page.getByText(/23,4866/)).toBeVisible();
  });

  test("P3 discloses a withheld-valuation category rather than a silent zero-match result", async ({ page }) => {
    await installScreenerFixtures(page, screenResponse({
      matches: [],
      totalMatchCount: 0,
      categoryDisclosures: [{ category: "FUNDAMENTAL", status: "PARTIAL", reasonCode: "VALUATION_WITHHELD", excludedCount: 1 }],
    }));

    await page.goto("/screener");
    await page.getByLabel(/^p\/e tối thiểu$/i).fill("0");
    await page.getByRole("button", { name: /lọc cổ phiếu/i }).click();

    await expect(page.getByText(/VALUATION_WITHHELD/)).toBeVisible();
  });

  test("P3 presents fundamental/valuation results as quantitative filtering output, not investment advice", async ({ page }) => {
    await installScreenerFixtures(page, screenResponse({
      matches: [match({ symbol: "FPT", matchedValues: { pe: "12.300000" } })],
      totalMatchCount: 1,
    }));

    await page.goto("/screener");
    await page.getByLabel(/^p\/e tối đa$/i).fill("20");
    await page.getByRole("button", { name: /lọc cổ phiếu/i }).click();

    await expect(page.getByRole("note")).toContainText(/không phải khuyến nghị đầu tư/i);
  });
});

// ── Fixtures and helpers ─────────────────────────────────────────────────

async function installScreenerFixtures(page: Page, response: unknown): Promise<void> {
  await installAuthenticatedOwnerSession(page);
  await page.route("**/api/v1/screener/executions", async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(response) });
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
  // executeScreen fetches a CSRF token before its POST (SEC-002 lineage —
  // Spring Security's CSRF filter applies to every state-changing HTTP
  // method regardless of this endpoint's own read-only semantics).
  await page.route("**/api/v1/auth/csrf", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ token: "fixture-csrf-token-0123456789", headerName: "X-CSRF-TOKEN" }),
    });
  });
}

function screenResponse(overrides: {
  matches: ReturnType<typeof match>[];
  totalMatchCount: number;
  categoryDisclosures?: ReturnType<typeof disclosure>[];
}) {
  return {
    ruleVersion: "screener-v1",
    matches: overrides.matches,
    totalMatchCount: overrides.totalMatchCount,
    limit: 50,
    offset: 0,
    categoryDisclosures: overrides.categoryDisclosures ?? [],
    coherenceKey: "coh-screen-e2e-1",
    calculatedAt: "2026-08-19T07:00:00Z",
  };
}

function match(overrides: { symbol: string; matchedValues?: Record<string, string> }) {
  return {
    symbol: overrides.symbol,
    companyName: `CTCP ${overrides.symbol}`,
    exchange: "HOSE",
    sectorName: "Information Technology",
    matchedValues: overrides.matchedValues ?? {},
    dataStatus: "CURRENT",
    asOfTradingDate: "2026-08-17",
  };
}

function disclosure(category: "MARKET" | "PRICE" | "TECHNICAL" | "FUNDAMENTAL") {
  return { category, status: "CURRENT", reasonCode: null, excludedCount: 0 };
}

function minimalOverview() {
  return {
    meta: {
      contractVersion: "1.0", symbol: "FPT", asOf: "2026-08-17T07:15:00Z", tradingDate: "2026-08-17",
      timezone: "Asia/Ho_Chi_Minh", dataStatus: "CURRENT", coherenceKey: "coh-fpt-1",
      sources: ["FINVERA_ACCEPTED"], reasonCodes: [],
    },
    profile: {
      symbol: "FPT", companyName: "CTCP FPT", companyNameEn: "FPT Corporation", exchange: "HOSE",
      sector: "Information Technology", sectorScheme: "finvera-sector-v1", listingStatus: "LISTED",
      sharesOutstanding: 1462000000,
    },
    price: {
      currency: "VND", last: "123600.000000", referencePrice: "122500.000000", absoluteChange: "1100.000000",
      percentageChange: "0.897959", direction: "UP", volume: 2270000, valueVnd: "280457200000.0000",
      marketCapVnd: "180703200000000.000000", applicability: "DEFINED", changeBasisReason: null,
    },
    session: { state: "CLOSED", tradingDate: "2026-08-17", calendarVersion: "v1" },
  };
}

function minimalChart() {
  return {
    meta: {
      contractVersion: "1.0", symbol: "FPT", asOf: "2026-08-17T07:15:00Z", tradingDate: "2026-08-17",
      timezone: "Asia/Ho_Chi_Minh", dataStatus: "CURRENT", coherenceKey: "coh-fpt-chart-1",
      sources: ["FINVERA_ACCEPTED"], reasonCodes: [],
    },
    window: "1M", adjustmentStatus: "ADJUSTED", bars: [],
  };
}

function minimalTechnical() {
  return {
    meta: {
      contractVersion: "1.0", symbol: "FPT", asOf: "2026-08-17T07:15:00Z", tradingDate: "2026-08-17",
      timezone: "Asia/Ho_Chi_Minh", dataStatus: "CURRENT", coherenceKey: "coh-fpt-tech-1",
      sources: ["FINVERA_ACCEPTED"], reasonCodes: [],
    },
    ruleVersion: "technical-indicators-v1", adjustmentStatus: "ADJUSTED",
    disclaimerCode: "QUANTITATIVE_DECISION_SUPPORT", indicators: [],
  };
}

function minimalFundamentals() {
  return {
    meta: {
      contractVersion: "1.0", symbol: "FPT", asOf: "2026-08-17T07:15:00Z", tradingDate: "2026-08-17",
      timezone: "Asia/Ho_Chi_Minh", dataStatus: "CURRENT", coherenceKey: "coh-fpt-fund-1",
      sources: ["FINVERA_ACCEPTED"], reasonCodes: [],
    },
    period: null, metrics: [],
  };
}

function minimalValuation() {
  return {
    meta: {
      contractVersion: "1.0", symbol: "FPT", asOf: "2026-08-17T07:15:00Z", tradingDate: "2026-08-17",
      timezone: "Asia/Ho_Chi_Minh", dataStatus: "UNAVAILABLE", coherenceKey: "coh-fpt-val-1",
      sources: ["FINVERA_ACCEPTED"], reasonCodes: ["NO_COMPARISON_BASIS"],
    },
    ruleVersion: "valuation-v1", published: false, classification: null, score: null, displayedScore: null,
    confidence: null, disclaimerCode: "QUANTITATIVE_DECISION_SUPPORT",
    basis: {
      usedOwnHistory: false, usedSector: false, sector: null, sectorScheme: null, sectorSchemeVersion: null,
      sectorConstituentCount: null, historyPointCount: null,
    },
    metrics: [],
  };
}
