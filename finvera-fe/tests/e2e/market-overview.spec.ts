import { expect, test, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

type OverviewMode = "complete" | "delayed" | "closed" | "missing";
type BreadthMode = "complete" | "partial" | "unavailable";
type RegimeMode = "complete" | "withheld" | "conflict" | "corrected" | "unavailable";

test.describe("P1 market overview", () => {
  test("P1 renders four complete index facts without contacting AI or a market provider", async ({ page }) => {
    const forbiddenRequests = forbidExternalRequests(page);
    await installMarketOverviewFixture(page, "complete");

    await page.goto("/");

    await expect(page.getByRole("heading", { name: "Tổng quan thị trường" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Chỉ số thị trường" })).toBeVisible();
    await expect(page.getByRole("article")).toHaveCount(4);
    await expect(page.getByRole("article", { name: /VN-Index: Tăng; Hiện tại/ })).toContainText("1.280,25");
    await expect(page.getByRole("article", { name: /HNX-Index: Giảm; Hiện tại/ })).toContainText("241,12");
    await expect(page.getByText(/Phiên giao dịch 2026-08-17/)).toBeVisible();
    await expect(page.getByText(/Cập nhật 10:00/)).toBeVisible();
    expect(forbiddenRequests).toEqual([]);
  });

  test("P1 labels delayed and closed snapshots explicitly", async ({ page }) => {
    await installMarketOverviewFixture(page, "delayed");
    await page.goto("/");
    await expect(page.getByText("Trạng thái dữ liệu: Chậm.")).toBeVisible();
    await expect(page.getByText("HOSE · Chậm").first()).toBeVisible();

    await installMarketOverviewFixture(page, "closed");
    await page.reload();
    await expect(page.getByText("Phiên đã đóng cửa · Trạng thái tổng thể: Hiện tại")).toBeVisible();
    await expect(page.getByText("HOSE · Hiện tại").first()).toBeVisible();
  });

  test("P1 preserves an explicitly unavailable missing index rather than showing zero", async ({ page }) => {
    await installMarketOverviewFixture(page, "missing");
    await page.goto("/");

    const upcom = page.getByRole("article", { name: /UPCoM-Index: Chưa xác định; Không có dữ liệu/ });
    await expect(upcom).toContainText("Không có dữ liệu: MISSING_INDEX");
    await expect(upcom).not.toContainText("0,00");
    await expect(page.getByRole("article")).toHaveCount(4);
  });

  test("P1 denies an unauthenticated owner session without exposing fallback values", async ({ page }) => {
    await page.route("**/api/v1/auth/session", async (route) => {
      await route.fulfill({ status: 401, contentType: "application/json", body: "{}" });
    });

    await page.goto("/");

    await expect(page.getByLabel("Username")).toBeVisible();
    await expect(page.getByLabel("Password")).toBeVisible();
    await expect(page.getByRole("article")).toHaveCount(0);
  });

  test("P1 has no automatically detectable accessibility violations for the complete overview", async ({ page }) => {
    await installMarketOverviewFixture(page, "complete");
    await page.goto("/");
    await expect(page.getByRole("heading", { name: "Tổng quan thị trường" })).toBeVisible();

    const results = await new AxeBuilder({ page }).analyze();
    expect(results.violations).toEqual([]);
  });
});

test.describe("P2 market breadth", () => {
  test("P2 presents reconciled complete breadth and explicitly labels partial coverage", async ({ page }) => {
    await installMarketOverviewFixture(page, "complete", "complete");
    await page.goto("/");

    const breadth = page.getByRole("region", { name: "Độ rộng thị trường" });
    await expect(breadth.locator("dt", { hasText: "Tăng giá" })).toBeVisible();
    await expect(breadth.locator("dd", { hasText: "612" })).toBeVisible();
    await expect(breadth.getByText(/Universe: breadth-universe-v1/)).toBeVisible();
    await expect(breadth.getByText(/Nguồn: FINVERA_ACCEPTED/)).toBeVisible();

    await installMarketOverviewFixture(page, "complete", "partial");
    await page.reload();
    const partialBreadth = page.getByRole("region", { name: "Độ rộng thị trường" });
    await expect(partialBreadth.getByRole("status")).toHaveText(/3 mã chưa phân loại/);
    await expect(partialBreadth.getByText(/MISSING_REFERENCE_PRICE/)).toBeVisible();
  });

  test("P2 reports unavailable breadth without fabricated counts", async ({ page }) => {
    await installMarketOverviewFixture(page, "complete", "unavailable");
    await page.goto("/");

    const breadth = page.getByRole("region", { name: "Độ rộng thị trường" });
    await expect(breadth.getByRole("status")).toHaveText(/Không có dữ liệu độ rộng: BREADTH_NOT_AVAILABLE/);
    await expect(breadth.getByText(/Universe: breadth-universe-v1/)).toBeVisible();
    await expect(breadth.getByText("612")).toHaveCount(0);
  });
});

test.describe("P3 market regime", () => {
  test("P3 replays the same deterministic regime facts and disclosure after reload", async ({ page }) => {
    await installMarketOverviewFixture(page, "complete", "complete", "complete");
    await page.goto("/");

    const regime = page.locator("section").filter({ hasText: "market-regime-v1" });
    await expect(regime).toContainText("EARLY_BULL");
    await expect(regime).toContainText("62/100");
    await expect(regime).toContainText("84/100");
    await expect(regime).toContainText("market-regime-v1");
    await expect(regime).toContainText("POSITIVE");
    await expect(regime).toContainText("QUANTITATIVE_DECISION_SUPPORT_NOT_INVESTMENT_ADVICE");

    await page.reload();
    const replay = page.locator("section").filter({ hasText: "market-regime-v1" });
    await expect(replay).toContainText("EARLY_BULL");
    await expect(replay).toContainText("62/100");
    await expect(replay).toContainText("84/100");
  });

  test("P3 withholds unavailable and conflicting assessments without inventing decision facts", async ({ page }) => {
    await installMarketOverviewFixture(page, "complete", "complete", "withheld");
    await page.goto("/");
    const withheld = page.locator("section").filter({ hasText: "MANDATORY_INPUT_UNAVAILABLE" });
    await expect(withheld.getByRole("status")).toContainText("MANDATORY_INPUT_UNAVAILABLE");
    await expect(withheld).not.toContainText("/100");
    await expect(withheld).not.toContainText("TREND");

    await installMarketOverviewFixture(page, "complete", "complete", "conflict");
    await page.reload();
    const conflict = page.locator("section").filter({ hasText: "SOURCE_CONFLICT" });
    await expect(conflict.getByRole("status")).toContainText("SOURCE_CONFLICT");
    await expect(conflict).not.toContainText("/100");
  });

  test("P3 displays a corrected assessment with its new as-of indication and no trading instruction", async ({ page }) => {
    await installMarketOverviewFixture(page, "complete", "complete", "corrected");
    await page.goto("/");

    const corrected = page.locator("section").filter({ hasText: "81/100" });
    await expect(corrected).toContainText("BULL");
    await expect(corrected).toContainText("81/100");
    await expect(corrected).toContainText("10:05 17/8/26");
    await expect(corrected).toContainText("QUANTITATIVE_DECISION_SUPPORT_NOT_INVESTMENT_ADVICE");
    await expect(corrected).not.toContainText(/Mua|Bán/i);
  });
});

async function installMarketOverviewFixture(
  page: Page, mode: OverviewMode, breadthMode: BreadthMode = "unavailable", regimeMode: RegimeMode = "unavailable",
): Promise<void> {
  await installAuthenticatedOwnerSession(page);
  await page.route("**/api/v1/market/overview", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(overviewFixture(mode, breadthMode, regimeMode)),
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

function overviewFixture(mode: OverviewMode, breadthMode: BreadthMode, regimeMode: RegimeMode) {
  const delayed = mode === "delayed";
  const closed = mode === "closed";
  const missing = mode === "missing";
  const observedAt = delayed ? "2026-08-17T02:45:00Z" : "2026-08-17T03:00:00Z";
  const indices = [
    index("VN_INDEX", "VN-Index", "HOSE", "UP", "1280.250000", "5.250000", "0.411765", observedAt),
    index("VN30", "VN30", "HOSE", "UP", "1342.800000", "4.400000", "0.328751", observedAt),
    index("HNX_INDEX", "HNX-Index", "HNX", "DOWN", "241.120000", "-0.880000", "-0.363636", observedAt),
    missing
      ? unavailableIndex()
      : index("UPCOM_INDEX", "UPCoM-Index", "UPCOM", "UNCHANGED", "98.420000", "0.000000", "0.000000", observedAt),
  ];

  if (delayed) {
    for (const marketIndex of indices) marketIndex.dataStatus = "DELAYED";
  }

  return {
    contractVersion: "1.0",
    generatedAt: observedAt,
    tradingDate: "2026-08-17",
    timezone: "Asia/Ho_Chi_Minh",
    dataStatus: missing ? "PARTIAL" : delayed ? "DELAYED" : "CURRENT",
    session: {
      state: closed ? "CLOSED" : "OPEN",
      tradingDate: "2026-08-17",
      asOf: observedAt,
      calendarVersion: "market-calendar-v1",
      venueStates: [],
    },
    indices,
    breadth: breadthFixture(breadthMode, observedAt),
    regime: regimeFixture(regimeMode, observedAt),
    warnings: [],
  };
}

function regimeFixture(mode: RegimeMode, asOf: string) {
  const unavailable = (dataStatus: "UNAVAILABLE" | "PARTIAL", reasonCodes: string[]) => ({
    dataStatus, ruleVersion: "market-regime-v1", label: null, score: null, confidence: null,
    confidenceMeaning: "ASSESSMENT_QUALITY_NOT_FORECAST_PROBABILITY", tradingDate: null, asOf: null, factors: [],
    source: { provider: "UNAVAILABLE", dataset: "REGIME" }, reasonCodes,
    disclaimerCode: "QUANTITATIVE_DECISION_SUPPORT_NOT_INVESTMENT_ADVICE",
  });
  if (mode === "unavailable") return unavailable("UNAVAILABLE", ["REGIME_NOT_AVAILABLE"]);
  if (mode === "withheld") return unavailable("UNAVAILABLE", ["MANDATORY_INPUT_UNAVAILABLE"]);
  if (mode === "conflict") return unavailable("PARTIAL", ["SOURCE_CONFLICT"]);
  const corrected = mode === "corrected";
  return {
    dataStatus: "CURRENT", ruleVersion: "market-regime-v1", label: corrected ? "BULL" : "EARLY_BULL",
    score: corrected ? 81 : 62, confidence: corrected ? 89 : 84,
    confidenceMeaning: "ASSESSMENT_QUALITY_NOT_FORECAST_PROBABILITY", tradingDate: "2026-08-17",
    asOf: corrected ? "2026-08-17T03:05:00Z" : asOf,
    factors: [{ code: "TREND", direction: "POSITIVE", descriptionCode: "REGIME_FACTOR_TREND_V1",
      normalizedScore: "70.0000", effectiveWeight: "0.350000", contribution: "24.500000", observations: [] }],
    source: { provider: "FINVERA_ACCEPTED", dataset: "REGIME" }, reasonCodes: [],
    disclaimerCode: "QUANTITATIVE_DECISION_SUPPORT_NOT_INVESTMENT_ADVICE",
  };
}

function breadthFixture(mode: BreadthMode, asOf: string) {
  if (mode === "unavailable") {
    return { dataStatus: "UNAVAILABLE", advancing: null, declining: null, unchanged: null, eligible: null,
      unclassified: null, universeVersion: "breadth-universe-v1", tradingDate: null, asOf: null,
      source: { provider: "UNAVAILABLE", dataset: "BREADTH" }, reasonCodes: ["BREADTH_NOT_AVAILABLE"] };
  }
  const partial = mode === "partial";
  return { dataStatus: partial ? "PARTIAL" : "CURRENT", advancing: 612, declining: 498, unchanged: 91,
    eligible: partial ? 1204 : 1201, unclassified: partial ? 3 : 0, universeVersion: "breadth-universe-v1",
    tradingDate: "2026-08-17", asOf, source: { provider: "FINVERA_ACCEPTED", dataset: "BREADTH" },
    reasonCodes: partial ? ["MISSING_REFERENCE_PRICE"] : [] };
}

function index(
  code: string, displayName: string, venue: string, direction: string, value: string, absoluteChange: string,
  percentageChange: string, asOf: string,
) {
  return {
    code, displayName, venue, dataStatus: "CURRENT", direction, value, absoluteChange, percentageChange,
    matchedVolume: 420000000, matchedValueVnd: "11250000000000.0000", unit: "INDEX_POINT", currency: "VND",
    tradingDate: "2026-08-17", asOf, source: { provider: "FINVERA_FIXTURE", dataset: "INDEX" }, revision: 1,
    reasonCodes: [],
  };
}

function unavailableIndex() {
  return {
    code: "UPCOM_INDEX", displayName: "UPCoM-Index", venue: "UPCOM", dataStatus: "UNAVAILABLE", direction: null,
    value: null, absoluteChange: null, percentageChange: null, matchedVolume: null, matchedValueVnd: null,
    unit: "INDEX_POINT", currency: "VND", tradingDate: null, asOf: null,
    source: { provider: "FINVERA_FIXTURE", dataset: "INDEX" }, revision: null, reasonCodes: ["MISSING_INDEX"],
  };
}
