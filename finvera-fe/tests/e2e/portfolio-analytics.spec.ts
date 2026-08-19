import { expect, test, type Page } from "@playwright/test";

const PF_ID = "00000000-0000-0000-0000-000000000001";

async function installAuthAndCsrf(page: Page) {
  await page.route("**/api/v1/auth/session", async (route) => {
    if (route.request().method() === "GET") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          authenticated: true,
          ownerId: "00000000-0000-0000-0000-000000000001",
          username: "owner",
        }),
      });
      return;
    }
    await route.fulfill({ status: 204 });
  });

  await page.route("**/api/v1/auth/csrf", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        token: "csrf-token-test-12345",
        headerName: "X-CSRF-TOKEN",
      }),
    });
  });
}

test.describe("P3 Portfolio Analytics and Benchmark Comparison Journeys", () => {
  test("P3 reviews return, drawdown, concentration, risk exposure, and VN-Index comparison", async ({
    page,
  }) => {
    await installAuthAndCsrf(page);

    await page.route(`**/api/v1/portfolios/${PF_ID}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: PF_ID,
          name: "Main Growth Portfolio",
          createdAt: "2026-08-01T00:00:00Z",
          totalValue: "110000000",
          cashBalance: "50000000",
          totalUnrealizedPL: "10000000",
          totalRealizedPL: "0",
          asOf: "2026-08-15T00:00:00Z",
        }),
      });
    });

    await page.route(`**/api/v1/portfolios/${PF_ID}/positions`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          positions: [
            {
              instrumentSymbol: "FPT",
              quantity: "1000",
              averageCostBasis: "50000",
              currentPrice: "60000",
              currentPriceStatus: "DEFINED",
              unrealizedPL: "10000000",
              realizedPL: "0",
              allocation: "0.5455",
            },
          ],
          cashBalance: "50000000",
          totalValue: "110000000",
          coherenceKey: "coh-pos-1",
          asOf: "2026-08-15T00:00:00Z",
        }),
      });
    });

    await page.route(`**/api/v1/portfolios/${PF_ID}/transactions*`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          items: [],
          totalElements: 0,
          limit: 200,
          offset: 0,
        }),
      });
    });

    await page.route(`**/api/v1/portfolios/${PF_ID}/analytics*`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          periodFrom: "2026-08-01",
          periodTo: "2026-08-15",
          periodClampedToInception: true,
          returnSinceInception: "0.10",
          returnOverPeriod: "0.10",
          returnMethodDisclosureCode: "NET_CONTRIBUTED_CAPITAL_METHOD",
          maxDrawdown: "0.02",
          performanceHistory: [
            {
              date: "2026-08-01",
              totalValue: "100000000",
              dataStatus: "CURRENT",
              reasonCode: null,
            },
            {
              date: "2026-08-15",
              totalValue: "110000000",
              dataStatus: "CURRENT",
              reasonCode: null,
            },
          ],
          stockConcentration: [{ key: "FPT", percentage: "0.5455" }],
          sectorConcentration: [{ key: "Công nghệ Thông tin", percentage: "0.5455" }],
          riskExposure: {
            riskExposureScore: 25,
            riskExposureLevel: "LOW",
            coverageRatio: "0.5455",
            reasonCode: null,
          },
          benchmark: {
            portfolioReturn: "0.10",
            benchmarkReturn: "0.045",
            benchmarkSymbol: "VNINDEX",
          },
          asOf: "2026-08-15T10:00:00Z",
        }),
      });
    });

    // 1. Visit portfolio detail
    await page.goto(`/portfolios/${PF_ID}`);
    await expect(page.getByRole("heading", { name: "Main Growth Portfolio" })).toBeVisible();

    // 2. Switch to Analytics tab
    await page.getByRole("button", { name: "Phân tích & Hiệu quả đầu tư" }).click();

    // 3. Verify Return, Drawdown, Benchmark cards
    await expect(page.getByText("(+) +10.00%").first()).toBeVisible();
    await expect(page.getByText("(-) -2.00%")).toBeVisible();
    await expect(page.getByText("VNINDEX")).toBeVisible();

    // 4. Verify Clamped to Inception banner
    await expect(page.getByTestId("clamped-inception-banner")).toBeVisible();

    // 5. Verify Risk Exposure & Concentration
    await expect(page.getByText("25/100")).toBeVisible();
    await expect(page.getByText("LOW")).toBeVisible();
    await expect(page.getByText("FPT").first()).toBeVisible();
    await expect(page.getByText("Công nghệ Thông tin")).toBeVisible();
  });
});
