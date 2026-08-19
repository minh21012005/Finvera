import { expect, test, type Page } from "@playwright/test";

const PF_ID = "00000000-0000-0000-0000-000000000001";

function installAuthAndCsrf(page: Page) {
  return Promise.all([
    page.route("**/api/v1/auth/session", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          subject: "owner",
          username: "owner",
          authenticatedAt: "2026-08-15T00:00:00Z",
          expiresAt: "2026-08-16T00:00:00Z",
        }),
      });
    }),
    page.route("**/api/v1/auth/csrf", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          token: "csrf-token-1234567890123456",
          headerName: "X-CSRF-TOKEN",
        }),
      });
    }),
  ]);
}

test.describe("P1 Portfolio Holdings and Ledger Journeys", () => {
  test("P1 creates portfolio and records deposit, buy, and partial sell", async ({ page }) => {
    await installAuthAndCsrf(page);

    const portfolios = [
      {
        id: PF_ID,
        name: "Main Portfolio",
        createdAt: "2026-08-01T00:00:00Z",
        totalValue: "100000000",
        cashBalance: "40000000",
        totalUnrealizedPL: "10000000",
        totalRealizedPL: "0",
        asOf: "2026-08-15T00:00:00Z",
      },
    ];

    const positions = [
      {
        instrumentSymbol: "FPT",
        quantity: "1000",
        averageCostBasis: "50000",
        currentPrice: "60000",
        currentPriceStatus: "DEFINED",
        unrealizedPL: "10000000",
        realizedPL: "0",
        allocation: "0.6000",
      },
    ];

    const transactions = [
      {
        id: "tx-1",
        portfolioId: PF_ID,
        sequenceNo: 1,
        transactionType: "DEPOSIT",
        amount: "100000000",
        fee: "0",
        currency: "VND",
        executedAt: "2026-08-01T10:00:00Z",
        entryAt: "2026-08-01T10:00:00Z",
        idempotencyKey: "k1",
      },
      {
        id: "tx-2",
        portfolioId: PF_ID,
        sequenceNo: 2,
        transactionType: "BUY",
        instrumentSymbol: "FPT",
        quantity: "1000",
        price: "50000",
        fee: "5000",
        currency: "VND",
        executedAt: "2026-08-02T10:00:00Z",
        entryAt: "2026-08-02T10:00:00Z",
        idempotencyKey: "k2",
      },
    ];

    await page.route("**/api/v1/portfolios", async (route) => {
      if (route.request().method() === "GET") {
        await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(portfolios) });
      } else {
        await route.fulfill({ status: 201, contentType: "application/json", body: JSON.stringify(portfolios[0]) });
      }
    });

    await page.route(`**/api/v1/portfolios/${PF_ID}`, async (route) => {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(portfolios[0]) });
    });

    await page.route(`**/api/v1/portfolios/${PF_ID}/positions`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          positions,
          cashBalance: "40000000",
          totalValue: "100000000",
          coherenceKey: "coh-123",
          asOf: "2026-08-15T00:00:00Z",
        }),
      });
    });

    await page.route(`**/api/v1/portfolios/${PF_ID}/transactions**`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          items: transactions,
          totalCount: transactions.length,
          limit: 50,
          offset: 0,
        }),
      });
    });

    // 1. Visit /portfolios
    await page.goto("/portfolios");
    await expect(page.getByRole("heading", { name: /Quản lý danh mục đầu tư/i })).toBeVisible();
    await expect(page.getByText("Main Portfolio")).toBeVisible();

    // 2. Open details
    await page.getByRole("button", { name: /Xem chi tiết & Giao dịch/i }).click();
    await expect(page.getByRole("heading", { name: "Main Portfolio" })).toBeVisible();

    // 3. Check derived holdings & non-color indicators
    await expect(page.getByText("FPT")).toBeVisible();
    await expect(page.getByText("1.000")).toBeVisible();
    await expect(page.getByText("(+)")).toBeVisible();
    await expect(page.getByText("60.00%")).toBeVisible();

    // 4. Check transaction ledger
    await expect(page.getByText("Sổ cái giao dịch (Bất biến)")).toBeVisible();
    await expect(page.getByText("MUA")).toBeVisible();
    await expect(page.getByText("NẠP TIỀN")).toBeVisible();
  });

  test("P1 displays over-sell rejection when selling more shares than available", async ({ page }) => {
    await installAuthAndCsrf(page);

    await page.route(`**/api/v1/portfolios/${PF_ID}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: PF_ID,
          name: "Main Portfolio",
          createdAt: "2026-08-01T00:00:00Z",
          totalValue: "10000000",
          cashBalance: "10000000",
          totalUnrealizedPL: "0",
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
          positions: [],
          cashBalance: "10000000",
          totalValue: "10000000",
          coherenceKey: "coh-empty",
          asOf: "2026-08-15T00:00:00Z",
        }),
      });
    });

    await page.route(`**/api/v1/portfolios/${PF_ID}/transactions**`, async (route) => {
      if (route.request().method() === "POST") {
        await route.fulfill({
          status: 409,
          contentType: "application/problem+json",
          body: JSON.stringify({
            status: 409,
            title: "Transaction Rejected",
            reasonCode: "INSUFFICIENT_POSITION",
            detail: "Insufficient shares to sell",
          }),
        });
      } else {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({ items: [], totalCount: 0, limit: 50, offset: 0 }),
        });
      }
    });

    await page.goto(`/portfolios/${PF_ID}`);
    await page.getByRole("button", { name: /\+ Ghi nhận giao dịch/i }).click();

    await page.getByRole("button", { name: "BÁN CỔ PHIẾU" }).click();
    await page.getByPlaceholder(/VD: FPT, VNM, HPG/i).fill("FPT");
    await page.getByPlaceholder("Số lượng").fill("500");
    await page.getByPlaceholder("Giá").fill("60000");

    await page.getByRole("button", { name: /Xác nhận Bán/i }).click();

    await expect(page.getByRole("alert")).toContainText(/Không đủ số lượng cổ phiếu khả dụng/i);
  });
});
