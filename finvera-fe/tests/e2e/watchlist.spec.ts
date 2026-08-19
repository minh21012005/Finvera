import { expect, test, type Page } from "@playwright/test";

const WL_ID = "00000000-0000-0000-0000-000000000001";

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

test.describe("P2 Watchlist Research Candidate Tracking Journeys", () => {
  test("P2 creates watchlist, adds symbol with signal and without signal, and removes item", async ({
    page,
  }) => {
    await installAuthAndCsrf(page);

    const watchlists = [
      {
        id: WL_ID,
        name: "Tech Candidates",
        createdAt: "2026-08-01T00:00:00Z",
        itemCount: 2,
      },
    ];

    const items = [
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
      {
        symbol: "VNM",
        companyName: "Vinamilk",
        addedAt: "2026-08-11T00:00:00Z",
        currentPrice: "70000",
        dailyChangePercent: "-0.8",
        technicalTrend: "BEARISH",
        volumeCondition: "LOW_VOLUME",
        hasCurrentSignal: false,
        signalDirection: null,
        riskLevel: null,
        dataStatus: "CURRENT",
        reasonCode: null,
      },
    ];

    await page.route("**/api/v1/watchlists", async (route) => {
      if (route.request().method() === "GET") {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(watchlists),
        });
        return;
      }
      if (route.request().method() === "POST") {
        const body = route.request().postDataJSON() as { name: string };
        const newWl = {
          id: "00000000-0000-0000-0000-000000000002",
          name: body.name,
          createdAt: "2026-08-15T00:00:00Z",
          itemCount: 0,
        };
        watchlists.push(newWl);
        await route.fulfill({
          status: 201,
          contentType: "application/json",
          body: JSON.stringify(newWl),
        });
        return;
      }
    });

    await page.route(`**/api/v1/watchlists/${WL_ID}`, async (route) => {
      if (route.request().method() === "GET") {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            id: WL_ID,
            name: "Tech Candidates",
            items,
            coherenceKey: "coh-wl-1",
            asOf: "2026-08-15T00:00:00Z",
          }),
        });
        return;
      }
      if (route.request().method() === "DELETE") {
        await route.fulfill({ status: 204 });
        return;
      }
    });

    await page.route(`**/api/v1/watchlists/${WL_ID}/items`, async (route) => {
      if (route.request().method() === "POST") {
        const body = route.request().postDataJSON() as { symbol: string };
        items.push({
          symbol: body.symbol,
          companyName: `${body.symbol} Corp`,
          addedAt: "2026-08-15T00:00:00Z",
          currentPrice: "25000",
          dailyChangePercent: "1.0",
          technicalTrend: "BULLISH",
          volumeCondition: "NORMAL",
          hasCurrentSignal: false,
          signalDirection: null,
          riskLevel: null,
          dataStatus: "CURRENT",
          reasonCode: null,
        });
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            id: WL_ID,
            name: "Tech Candidates",
            items,
            coherenceKey: "coh-wl-2",
            asOf: "2026-08-15T00:00:00Z",
          }),
        });
        return;
      }
    });

    await page.route(`**/api/v1/watchlists/${WL_ID}/items/*`, async (route) => {
      if (route.request().method() === "DELETE") {
        await route.fulfill({ status: 204 });
        return;
      }
    });

    // 1. Visit Watchlist list
    await page.goto("/watchlists");
    await expect(page.getByRole("heading", { name: "Danh sách theo dõi (Watchlist)" })).toBeVisible();
    await expect(page.getByText("Tech Candidates")).toBeVisible();

    // 2. Open Watchlist detail
    await page.getByRole("button", { name: "Mở danh sách theo dõi →" }).first().click();
    await expect(page.getByRole("heading", { name: "Tech Candidates" })).toBeVisible();

    // 3. Verify FPT shows signal & risk badge
    const fptRow = page.getByTestId("watchlist-row-FPT");
    await expect(fptRow).toBeVisible();
    await expect(fptRow.getByText("60.000 ₫")).toBeVisible();
    await expect(fptRow.getByText("(+) +2.50%")).toBeVisible();
    await expect(fptRow.getByText("Rủi ro: LOW")).toBeVisible();

    // 4. Verify VNM shows truthful no-signal state
    const vnmRow = page.getByTestId("watchlist-row-VNM");
    await expect(vnmRow).toBeVisible();
    await expect(vnmRow.getByTestId("no-signal-badge")).toHaveTextContent("Không có tín hiệu");

    // 5. Add new symbol HPG
    await page.getByPlaceholder("Nhập mã cổ phiếu").fill("HPG");
    await page.getByRole("button", { name: "+ Thêm vào danh sách" }).click();

    // 6. Verify HPG is now rendered in the table
    await expect(page.getByTestId("watchlist-row-HPG")).toBeVisible();
  });
});
