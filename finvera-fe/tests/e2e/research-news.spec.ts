import { test, expect } from '@playwright/test';

test.describe('Feature 006: User Story 3 (P3) - Submit and Understand News Articles', () => {
  test.beforeEach(async ({ page }) => {
    // Navigate to research page
    await page.goto('/research');
  });

  test('P3: Owner submits a news article and views classification metadata', async ({ page }) => {
    // Switch to News tab
    const newsTab = page.getByRole('button', { name: /Tin Tức Thị Trường/i });
    await newsTab.click();

    // Fill submit form
    const titleInput = page.getByLabelText(/Tiêu đề bài báo/i);
    await expect(titleInput).toBeVisible();

    await titleInput.fill('FPT bứt phá doanh thu xuất khẩu phần mềm năm 2025');
    await page.getByLabelText(/Nguồn \/ Nhà xuất bản/i).fill('VnExpress');
    await page.getByLabelText(/Mã cổ phiếu/i).fill('FPT');
    await page.getByLabelText(/Nội dung bài viết/i).fill(
      'Khối công nghệ và chuyển đổi số ghi nhận mức tăng trưởng doanh thu 28% so với cùng kỳ năm trước.'
    );

    // Click submit button
    const submitBtn = page.getByRole('button', { name: /Gửi Bài Viết/i });
    await submitBtn.click();

    // Expect success alert or article in list
    await expect(page.getByText(/Đã gửi bài báo thành công!/i)).toBeVisible({ timeout: 10000 });
  });

  test('P3: Owner filters news by symbol and category', async ({ page }) => {
    const newsTab = page.getByRole('button', { name: /Tin Tức Thị Trường/i });
    await newsTab.click();

    // Filter by symbol
    const symbolInput = page.getByPlaceholderText(/Lọc mã CP/i);
    await expect(symbolInput).toBeVisible();
    await symbolInput.fill('FPT');

    // Filter by category
    const categorySelect = page.locator('select').first();
    await expect(categorySelect).toBeVisible();
    await categorySelect.selectOption('COMPANY');

    // Expect articles list to react
    await expect(page.getByText(/bài báo/i)).toBeVisible();
  });
});
