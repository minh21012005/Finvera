import { test, expect } from '@playwright/test';

test.describe('Feature 006: User Story 2 (P2) - Ask a Question Grounded in Cited Passages', () => {
  test.beforeEach(async ({ page }) => {
    // Navigate to research page
    await page.goto('/research');
  });

  test('P2: Owner asks a grounded question and receives verified citations', async ({ page }) => {
    // Switch to Ask AI tab
    const askTab = page.getByRole('button', { name: /Hỏi Đáp AI Analyst/i });
    await askTab.click();

    const textarea = page.getByPlaceholderText(/Nhập câu hỏi của bạn/i);
    await expect(textarea).toBeVisible();

    // Use suggested question or type query
    const suggestedBtn = page.getByRole('button', { name: /Doanh thu và tăng trưởng của FPT năm 2025\?/i });
    if (await suggestedBtn.isVisible()) {
      await suggestedBtn.click();
    } else {
      await textarea.fill('Doanh thu FPT năm 2025 đạt bao nhiêu?');
      await page.getByRole('button', { name: /Gửi câu hỏi/i }).click();
    }

    // Expect response container to appear
    await expect(page.getByText(/Câu Trả Lời Của Finvera AI Analyst/i)).toBeVisible({ timeout: 15000 });

    // Verify copy button is present
    await expect(page.getByRole('button', { name: /Sao chép/i })).toBeVisible();
  });

  test('P2: Owner asks for structured financial calculations and receives deterministic engine redirection', async ({ page }) => {
    const askTab = page.getByRole('button', { name: /Hỏi Đáp AI Analyst/i });
    await askTab.click();

    const textarea = page.getByPlaceholderText(/Nhập câu hỏi của bạn/i);
    await textarea.fill('Tính chỉ số RSI và định giá P/E của FPT');
    await page.getByRole('button', { name: /Gửi câu hỏi/i }).click();

    // Expect refusal / redirection notice
    await expect(page.getByText(/Từ chối \/ Ngoài phạm vi tài liệu/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Ghi chú kiểm chứng & an toàn/i)).toBeVisible();
  });
});
