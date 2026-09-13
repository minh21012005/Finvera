import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

test("position sizing manual journey is keyboard usable and exposes the binding cap", async ({ page }) => {
  await page.route("**/api/v1/auth/session", (route) => route.fulfill({ status: 200, contentType: "application/json",
    body: JSON.stringify({ subject: "owner", username: "owner", authenticatedAt: "2026-09-12T00:00:00Z", expiresAt: "2026-09-13T00:00:00Z" }) }));
  await page.route("**/api/v1/auth/csrf", (route) => route.fulfill({ status: 200, contentType: "application/json",
    body: JSON.stringify({ token: "csrf-token-for-e2e", headerName: "X-CSRF-TOKEN" }) }));
  await page.route("**/api/v1/portfolios", (route) => route.fulfill({ status: 200, contentType: "application/json", body: "[]" }));
  await page.route("**/api/v1/position-sizing/calculate", async (route) => {
    expect(route.request().headers()["x-csrf-token"]).toBe("csrf-token-for-e2e");
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({
      status: "CALCULATED", symbol: "FPT", mode: "MANUAL", quantity: 900, rawPermittedQuantity: 998,
      lotSize: 100, roundingRemainder: 98, capitalBaseVnd: "100000000", availableCashVnd: "100000000",
      resolvedEntryPriceVnd: "50000", resolvedStopPriceVnd: "47000", effectiveEntryPriceVnd: "50000",
      effectiveStopPriceVnd: "47000", costsExcluded: true, riskBudgetVnd: "5000000", acquisitionUnitCostVnd: "50000",
      stopNetProceedsPerShareVnd: "47000", lossPerShareVnd: "3000", requiredCapitalVnd: "45000000",
      estimatedLossAtStopVnd: "2700000", remainingCashVnd: "55000000", projectedSymbolMarketValueVnd: null,
      projectedSymbolExposureRate: null, projectedDeploymentRate: null,
      constraints: [{ code: "SYMBOL_CONCENTRATION", applicability: "APPLIED", rawQuantity: 998, binding: true },
        { code: "TOTAL_DEPLOYMENT", applicability: "NOT_APPLIED", rawQuantity: null, binding: false }],
      inputEvidence: [{ field: "entryPriceVnd", value: "50000", source: "OWNER_ENTERED", unit: "VND_PER_SHARE", asOf: null, coherenceKey: null }],
      reasonCodes: [], warnings: ["COSTS_EXCLUDED"], sizingRuleVersion: "position-sizing-v1",
      marketRuleVersion: "market-lot-v1", calculatedAt: "2026-09-12T08:00:00Z",
    }) });
  });

  await page.goto("/position-sizing");
  await expect(page.getByRole("heading", { name: "Tính quy mô vị thế" })).toBeVisible();
  await page.getByLabel("Vốn cơ sở (VND)").fill("100000000");
  await page.getByLabel("Tiền có thể dùng (VND)").fill("100000000");
  await page.getByLabel("Rủi ro tối đa (VND)").fill("5000000");
  await page.getByLabel("Giá vào (VND/cp)").fill("50000");
  await page.getByLabel("Giá dừng lỗ (VND/cp)").fill("47000");
  await page.getByLabel(/Loại trừ toàn bộ chi phí/).check();
  await page.getByRole("button", { name: "Tính quy mô" }).press("Enter");
  await expect(page.getByRole("heading", { name: /900 cổ phiếu/ })).toBeVisible();
  await expect(page.getByText(/SYMBOL_CONCENTRATION/).locator("..")).toContainText("ĐANG GIỚI HẠN");
  const accessibility = await new AxeBuilder({ page }).include("main").analyze();
  expect(accessibility.violations).toEqual([]);
});
