import { expect, test, type Page } from "@playwright/test";

const DOC_ID = "00000000-0000-0000-0000-000000000001";

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

test.describe("P1 Research Documents and RAG Retrieval E2E Journeys", () => {
  test("P1 document submission, listing, deletion, and passage retrieval", async ({ page }) => {
    await installAuthAndCsrf(page);

    const documents = [
      {
        id: DOC_ID,
        title: "Báo cáo thường niên FPT 2025",
        symbol: "FPT",
        documentType: "ANNUAL_REPORT",
        year: 2025,
        quarter: null,
        source: "FPT IR",
        publicationDate: "2026-03-15",
        ingestionStatus: "READY",
        ingestionFailureReason: null,
        submittedAt: "2026-03-15T08:00:00Z",
        processedAt: "2026-03-15T08:02:00Z",
      },
    ];

    await page.route("**/api/v1/research/documents*", async (route) => {
      if (route.request().method() === "GET") {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            items: documents,
            totalCount: documents.length,
            limit: 20,
            offset: 0,
          }),
        });
        return;
      }
      if (route.request().method() === "POST") {
        const newDoc = {
          id: "00000000-0000-0000-0000-000000000002",
          title: "Báo cáo tài chính VNM 2025",
          symbol: "VNM",
          documentType: "FINANCIAL_REPORT",
          year: 2025,
          quarter: 4,
          source: "Vinamilk IR",
          publicationDate: "2026-02-10",
          ingestionStatus: "PENDING",
          ingestionFailureReason: null,
          submittedAt: new Date().toISOString(),
          processedAt: null,
        };
        documents.push(newDoc);
        await route.fulfill({
          status: 201,
          contentType: "application/json",
          body: JSON.stringify(newDoc),
        });
        return;
      }
      if (route.request().method() === "DELETE") {
        await route.fulfill({ status: 204 });
        return;
      }
      await route.fallback();
    });

    await page.route("**/api/v1/research/retrieve", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          passages: [
            {
              sourceType: "DOCUMENT",
              sourceId: DOC_ID,
              sourceTitle: "Báo cáo thường niên FPT 2025",
              location: "Page 15",
              source: "FPT IR",
              publicationDate: "2026-03-15",
              excerpt: "Doanh thu năm 2025 đạt 60.000 tỷ VND, vượt kế hoạch 12%.",
              score: 0.92,
            },
          ],
          asOf: new Date().toISOString(),
        }),
      });
    });

    // Navigate to /research
    await page.goto("/research");

    // Check header and document list
    await expect(page.getByText("Nghiên cứu & Dữ liệu RAG")).toBeVisible();
    await expect(page.getByText("Báo cáo thường niên FPT 2025")).toBeVisible();
    await expect(page.getByText("● Sẵn sàng (READY)")).toBeVisible();

    // Switch to text mode in upload form and submit new doc
    await page.getByRole("button", { name: "Dán Văn bản" }).click();
    await page.getByPlaceholder("vd: Báo cáo thường niên 2025 - CTCP FPT").fill("Báo cáo tài chính VNM 2025");
    await page.getByPlaceholder("FPT").fill("VNM");
    await page.getByPlaceholder("vd: FPT IR / CTCK SSI").fill("Vinamilk IR");
    await page.getByPlaceholder("Dán nội dung văn bản báo cáo vào đây...").fill("Nội dung báo cáo tài chính kiểm toán 2025.");
    await page.getByRole("button", { name: "Tải lên & Bắt đầu Xử lý" }).click();

    // Verify new document appears in list
    await expect(page.getByText("Báo cáo tài chính VNM 2025")).toBeVisible();

    // Switch to retrieval tab
    await page.getByRole("button", { name: "Truy vấn Trích dẫn" }).click();
    await expect(page.getByText("Truy vấn Trích dẫn từ Kho Dữ liệu (RAG)")).toBeVisible();

    // Search query
    await page.getByPlaceholder(/Nhập câu hỏi hoặc từ khóa nghiên cứu/i).fill("Doanh thu FPT năm 2025");
    await page.getByRole("button", { name: "Tìm Trích dẫn" }).click();

    // Check passage results
    await expect(page.getByText("Kết quả Đoạn Trích dẫn (1)")).toBeVisible();
    await expect(page.getByText("Page 15")).toBeVisible();
    await expect(page.getByText(/Doanh thu năm 2025 đạt 60.000 tỷ VND/i)).toBeVisible();
    await expect(page.getByText("Độ khớp: 92.0%")).toBeVisible();
  });
});
