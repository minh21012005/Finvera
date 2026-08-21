import { test, expect } from '@playwright/test';

test.describe('Feature 007: User Story 1 (P1) - Ask a Question and Get a Tool-Grounded Answer', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/analyst');
  });

  test('P1: the AI Analyst page is reachable and accepts a question', async ({ page }) => {
    // Regression test: this page existed, was fully built and unit-tested, but was
    // never wired into the app router — a real user had no way to reach it at all.
    const textarea = page.getByPlaceholderText(/Hỏi trợ lý phân tích/i);
    await expect(textarea).toBeVisible();

    await textarea.fill('Giá cổ phiếu HPG hôm nay bao nhiêu?');
    await page.getByRole('button', { name: /^Gửi$/i }).click();

    // Either a tool-grounded answer streams in, or the system states the question is
    // outside its current capability — either way, the request must complete, never
    // hang indefinitely (NFR-001/FR-005).
    await expect(
      page.locator('text=/Công cụ được kích hoạt|Từ chối|Dữ liệu đã được kiểm chứng/i').first()
    ).toBeVisible({ timeout: 20000 });
  });

  test('P1: navigating from the market overview reaches the AI Analyst page', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('button', { name: /AI Analyst →/i }).click();
    await expect(page.getByPlaceholderText(/Hỏi trợ lý phân tích/i)).toBeVisible();
  });
});
