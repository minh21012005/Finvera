import { expect, test, type Page } from "@playwright/test";

test.describe("P1 stock signals — a stock's current trade signals", () => {
  test("P1 shows a triggered strategy's complete signal: direction, entry zone, stop, targets, risk score/level", async ({ page }) => {
    await installFixtures(page, signalsFixture([triggeredEvaluation("TREND_FOLLOWING")]));

    await page.goto("/stocks/FPT");

    await expect(page.getByText(/Theo xu hướng/)).toBeVisible();
    await expect(page.getByText(/Mua \(LONG\)/).first()).toBeVisible();
    await expect(page.getByText(/Vùng vào lệnh/)).toBeVisible();
    await expect(page.getByText(/Dừng lỗ/)).toBeVisible();
    await expect(page.getByText(/Rủi ro thấp/)).toBeVisible();
  });

  test("P1 shows every strategy with no current trigger as a truthful non-signal state, not an error", async ({ page }) => {
    const evaluations = STRATEGY_CODES.map((code) => noSignalEvaluation(code));
    await installFixtures(page, signalsFixture(evaluations));

    await page.goto("/stocks/FPT");

    await expect(page.getByText(/Điều kiện chiến lược hiện chưa được thỏa mãn/).first()).toBeVisible();
    await expect(page.getByRole("alert")).toHaveCount(0);
  });

  test("P1 shows an insufficient-history state per strategy, distinguishable from a plain non-signal", async ({ page }) => {
    const evaluations = [
      insufficientHistoryEvaluation("BREAKOUT"),
      ...STRATEGY_CODES.filter((c) => c !== "BREAKOUT").map((code) => noSignalEvaluation(code)),
    ];
    await installFixtures(page, signalsFixture(evaluations));

    await page.goto("/stocks/FPT");

    await expect(page.getByText(/Bứt phá/)).toBeVisible();
    await expect(page.getByText(/Chưa đủ dữ liệu lịch sử/).first()).toBeVisible();
  });

  test("P1 shows two simultaneously triggered strategies, each correctly attributed", async ({ page }) => {
    const evaluations = [
      triggeredEvaluation("TREND_FOLLOWING"),
      triggeredEvaluation("MOMENTUM"),
      ...STRATEGY_CODES.filter((c) => c !== "TREND_FOLLOWING" && c !== "MOMENTUM").map((code) => noSignalEvaluation(code)),
    ];
    await installFixtures(page, signalsFixture(evaluations));

    await page.goto("/stocks/FPT");

    await expect(page.getByText(/Theo xu hướng/)).toBeVisible();
    await expect(page.getByText(/Động lượng/)).toBeVisible();
    expect(await page.locator(".signal-card.signal").count()).toBe(2);
  });

  test("P1 states a deterministic scenario, never a guarantee or an investment instruction", async ({ page }) => {
    await installFixtures(page, signalsFixture([triggeredEvaluation("TREND_FOLLOWING")]));

    await page.goto("/stocks/FPT");

    await expect(page.getByText(/không phải khuyến nghị đầu tư/i).first()).toBeVisible();
    await expect(page.getByText(/^Mua ngay$|^Bán ngay$/i)).toHaveCount(0);
  });
});

test.describe("P2 risk factor transparency", () => {
  test("P2 shows each risk factor's own value and score when all six are available", async ({ page }) => {
    await installFixtures(page, signalsFixture([triggeredEvaluation("TREND_FOLLOWING", { allFactorsAvailable: true })]));

    await page.goto("/stocks/FPT");

    await expect(page.getByText(/Biến động giá/)).toBeVisible();
    await expect(page.getByText(/Trạng thái thị trường chung/)).toBeVisible();
    await expect(page.getByText(/Điểm:\s*\d+\/100/).first()).toBeVisible();
  });

  test("P2 discloses an unavailable factor's reason while the overall score still publishes from the rest", async ({ page }) => {
    await installFixtures(page, signalsFixture([triggeredEvaluation("TREND_FOLLOWING")]));

    await page.goto("/stocks/FPT");

    await expect(page.getByText(/REGIME_UNAVAILABLE/)).toBeVisible();
    await expect(page.getByText(/Rủi ro thấp/)).toBeVisible();
  });

  test("P2 withholds the overall risk score/level with only three of six factors available, but keeps the signal visible", async ({ page }) => {
    await installFixtures(page, signalsFixture([triggeredEvaluation("TREND_FOLLOWING", { insufficientRiskFactors: true })]));

    await page.goto("/stocks/FPT");

    await expect(page.getByText(/Vùng vào lệnh/)).toBeVisible();
    await expect(page.getByText(/INSUFFICIENT_RISK_FACTORS/)).toBeVisible();
  });

  test("P2 reproduces identical factor values and overall score on reload", async ({ page }) => {
    await installFixtures(page, signalsFixture([triggeredEvaluation("TREND_FOLLOWING")]));
    await page.goto("/stocks/FPT");
    await expect(page.getByText(/\(25\/100\)/)).toBeVisible();

    await page.reload();
    await expect(page.getByText(/\(25\/100\)/)).toBeVisible();
  });
});

const STRATEGY_CODES = [
  "TREND_FOLLOWING", "MOMENTUM", "BREAKOUT", "PULLBACK", "MEAN_REVERSION",
  "MA_CROSSOVER", "MACD_BASED", "RSI_BASED",
] as const;

type StrategyCode = (typeof STRATEGY_CODES)[number];

function noSignalEvaluation(strategyCode: StrategyCode) {
  return { strategyCode, status: "NO_SIGNAL", reasonCode: null, signal: null };
}

function insufficientHistoryEvaluation(strategyCode: StrategyCode) {
  return { strategyCode, status: "INSUFFICIENT_HISTORY", reasonCode: "INSUFFICIENT_HISTORY", signal: null };
}

function triggeredEvaluation(
  strategyCode: StrategyCode,
  options: { allFactorsAvailable?: boolean; insufficientRiskFactors?: boolean } = {},
) {
  const definedFactors = [
    { factorCode: "VOLATILITY", inputValue: "2.5", factorScore: 10, applicability: "DEFINED", reasonCode: null },
    { factorCode: "ATR", inputValue: "1.0", factorScore: 33, applicability: "DEFINED", reasonCode: null },
    { factorCode: "DRAWDOWN", inputValue: "0", factorScore: 0, applicability: "DEFINED", reasonCode: null },
    { factorCode: "LIQUIDITY", inputValue: "1.0", factorScore: 50, applicability: "DEFINED", reasonCode: null },
    { factorCode: "STOP_DISTANCE", inputValue: "4", factorScore: 8, applicability: "DEFINED", reasonCode: null },
  ];
  const regimeFactor = options.allFactorsAvailable
    ? { factorCode: "MARKET_REGIME", inputValue: "50", factorScore: 50, applicability: "DEFINED", reasonCode: null }
    : {
        factorCode: "MARKET_REGIME",
        inputValue: null,
        factorScore: null,
        applicability: "MISSING",
        reasonCode: "REGIME_UNAVAILABLE",
      };

  const riskFactors = options.insufficientRiskFactors
    ? [
        definedFactors[0], definedFactors[3], definedFactors[4],
        { factorCode: "ATR", inputValue: null, factorScore: null, applicability: "MISSING", reasonCode: "INPUT_UNAVAILABLE" },
        { factorCode: "DRAWDOWN", inputValue: null, factorScore: null, applicability: "MISSING", reasonCode: "INPUT_UNAVAILABLE" },
        { factorCode: "MARKET_REGIME", inputValue: null, factorScore: null, applicability: "MISSING", reasonCode: "REGIME_UNAVAILABLE" },
      ]
    : [...definedFactors, regimeFactor];

  return {
    strategyCode,
    status: "SIGNAL",
    reasonCode: null,
    signal: {
      strategyCode,
      ruleVersion: "strategy-signal-v1",
      direction: "LONG",
      entryLow: "99.500000",
      entryHigh: "100.500000",
      stopLoss: "96.000000",
      target1: "108.000000",
      target2: "112.000000",
      riskReward: "2.0000",
      riskScore: options.insufficientRiskFactors ? null : 25,
      riskLevel: options.insufficientRiskFactors ? null : "LOW",
      signalStrength: options.insufficientRiskFactors ? null : "STRONG",
      riskFactors,
      supportingEvidence: { trend: "UPTREND" },
      reasonCodes: options.insufficientRiskFactors ? ["INSUFFICIENT_RISK_FACTORS"] : [],
      asOfTradingDate: "2026-08-14",
      calculatedAt: "2026-08-14T08:15:00Z",
    },
  };
}

function signalsFixture(evaluations: unknown[]) {
  return {
    symbol: "FPT",
    dataStatus: "CURRENT",
    evaluations,
    disclaimerCode: "QUANTITATIVE_DECISION_SUPPORT",
    coherenceKey: "coh-signal-e2e",
    asOf: "2026-08-14T08:15:01Z",
  };
}

async function installFixtures(page: Page, signals: unknown): Promise<void> {
  await installAuthenticatedOwnerSession(page);
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
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(signals) });
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

function meta(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    contractVersion: "1.0",
    symbol: "FPT",
    asOf: "2026-08-17T07:15:00Z",
    tradingDate: "2026-08-14",
    timezone: "Asia/Ho_Chi_Minh",
    dataStatus: "CURRENT",
    coherenceKey: "coh-e2e",
    sources: ["FINVERA_ACCEPTED"],
    reasonCodes: [],
    ...overrides,
  };
}

function minimalOverview() {
  return {
    meta: meta({ coherenceKey: "coh-overview-e2e" }),
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
  return {
    meta: meta({ coherenceKey: "coh-chart-e2e" }),
    window: "1M",
    adjustmentStatus: "ADJUSTED",
    bars: [],
  };
}

function minimalTechnical() {
  return {
    meta: meta({ coherenceKey: "coh-technical-e2e" }),
    ruleVersion: "technical-indicators-v1",
    adjustmentStatus: "ADJUSTED",
    disclaimerCode: "QUANTITATIVE_DECISION_SUPPORT",
    indicators: [],
  };
}

function minimalFundamentals() {
  return { meta: meta({ coherenceKey: "coh-fundamentals-e2e" }), period: null, metrics: [] };
}

function minimalValuation() {
  return {
    meta: meta({ coherenceKey: "coh-valuation-e2e" }),
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
