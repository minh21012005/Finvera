import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { NewsSubmit } from "./components/news-submit";
import { NewsList } from "./components/news-list";
import * as newsApi from "./api/news";

vi.mock("./api/news", () => ({
  submitNewsArticle: vi.fn(),
  listNewsArticles: vi.fn(),
  deleteNewsArticle: vi.fn(),
}));

describe("NewsSubmit Component", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders form inputs and submits successfully", async () => {
    const onSubmitted = vi.fn();
    vi.mocked(newsApi.submitNewsArticle).mockResolvedValue({
      id: "news-1",
      title: "FPT công bố lợi nhuận",
      source: "VnExpress",
      publishedAt: new Date().toISOString(),
      categoryApplicability: "MISSING",
      sentimentApplicability: "MISSING",
      impactApplicability: "MISSING",
      ingestionStatus: "PENDING",
      submittedAt: new Date().toISOString(),
    });

    render(<NewsSubmit onSubmitted={onSubmitted} />);

    fireEvent.change(screen.getByLabelText(/Tiêu đề bài báo/i), {
      target: { value: "FPT công bố lợi nhuận quý 4" },
    });
    fireEvent.change(screen.getByLabelText(/Nguồn \/ Nhà xuất bản/i), {
      target: { value: "VnExpress" },
    });
    fireEvent.change(screen.getByLabelText(/Nội dung bài viết/i), {
      target: { value: "Nội dung bài viết chi tiết về FPT..." },
    });

    fireEvent.click(screen.getByRole("button", { name: /Gửi Bài Viết/i }));

    await waitFor(() => {
      expect(newsApi.submitNewsArticle).toHaveBeenCalledWith(
        expect.objectContaining({
          title: "FPT công bố lợi nhuận quý 4",
          source: "VnExpress",
          body: "Nội dung bài viết chi tiết về FPT...",
        })
      );
      expect(onSubmitted).toHaveBeenCalledTimes(1);
    });

    expect(
      screen.getByText(/Đã gửi bài báo thành công!/i)
    ).toBeInTheDocument();
  });

  it("handles submit error gracefully", async () => {
    vi.mocked(newsApi.submitNewsArticle).mockRejectedValue(
      new Error("Lỗi trùng lặp bài báo (409)")
    );

    render(<NewsSubmit />);

    fireEvent.change(screen.getByLabelText(/Tiêu đề bài báo/i), {
      target: { value: "Tiêu đề trùng" },
    });
    fireEvent.change(screen.getByLabelText(/Nguồn \/ Nhà xuất bản/i), {
      target: { value: "VnExpress" },
    });
    fireEvent.change(screen.getByLabelText(/Nội dung bài viết/i), {
      target: { value: "Nội dung..." },
    });

    fireEvent.click(screen.getByRole("button", { name: /Gửi Bài Viết/i }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("Lỗi trùng lặp bài báo (409)");
    });
  });
});

describe("NewsList Component", () => {
  const mockArticles: newsApi.NewsArticle[] = [
    {
      id: "art-1",
      title: "FPT đạt doanh thu kỷ lục 1 tỷ USD",
      symbol: "FPT",
      source: "VnExpress",
      sourceUrl: "https://vnexpress.net/fpt",
      publishedAt: "2026-02-15T08:30:00Z",
      category: "COMPANY",
      categoryApplicability: "DEFINED",
      sentiment: "POSITIVE",
      sentimentApplicability: "DEFINED",
      impactLevel: "HIGH",
      impactApplicability: "DEFINED",
      sector: "Technology",
      ingestionStatus: "READY",
      submittedAt: "2026-02-15T09:00:00Z",
    },
    {
      id: "art-2",
      title: "Báo cáo vĩ mô tháng 1/2026",
      source: "SBV",
      publishedAt: "2026-01-31T10:00:00Z",
      category: "MACRO",
      categoryApplicability: "DEFINED",
      sentiment: "NEUTRAL",
      sentimentApplicability: "DEFINED",
      impactLevel: "MEDIUM",
      impactApplicability: "DEFINED",
      ingestionStatus: "READY",
      submittedAt: "2026-01-31T10:10:00Z",
    },
    {
      id: "art-3",
      title: "Tin đồn sáp nhập doanh nghiệp chưa xác thực",
      source: "Diễn đàn F319",
      publishedAt: "2026-02-10T14:00:00Z",
      categoryApplicability: "MISSING",
      sentimentApplicability: "MISSING",
      impactApplicability: "MISSING",
      ingestionStatus: "PROCESSING",
      submittedAt: "2026-02-10T14:05:00Z",
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders articles with category, sentiment, and undetermined states", async () => {
    vi.mocked(newsApi.listNewsArticles).mockResolvedValue({
      items: mockArticles,
      totalCount: 3,
      limit: 50,
      offset: 0,
    });

    render(<NewsList />);

    await waitFor(() => {
      expect(screen.getByText("FPT đạt doanh thu kỷ lục 1 tỷ USD")).toBeInTheDocument();
      expect(screen.getByText("Báo cáo vĩ mô tháng 1/2026")).toBeInTheDocument();
      expect(screen.getByText("Tin đồn sáp nhập doanh nghiệp chưa xác thực")).toBeInTheDocument();
    });

    // Classification badges
    expect(screen.getAllByText("Doanh nghiệp").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("Tích cực").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("Tác động lớn")).toBeInTheDocument();

    // Undetermined state
    expect(screen.getByText("Chưa phân loại")).toBeInTheDocument();
    expect(screen.getByText("Chưa xác định")).toBeInTheDocument();
    expect(screen.getByText("Đang phân tích...")).toBeInTheDocument();
  });

  it("filters articles by symbol and category", async () => {
    vi.mocked(newsApi.listNewsArticles).mockResolvedValue({
      items: [mockArticles[0]],
      totalCount: 1,
      limit: 50,
      offset: 0,
    });

    render(<NewsList />);

    const symbolInput = screen.getByPlaceholderText(/Lọc mã CP/i);
    fireEvent.change(symbolInput, { target: { value: "FPT" } });

    await waitFor(() => {
      expect(newsApi.listNewsArticles).toHaveBeenCalledWith(
        expect.objectContaining({
          symbol: "FPT",
        })
      );
    });
  });

  it("deletes an article upon confirmation", async () => {
    vi.mocked(newsApi.listNewsArticles).mockResolvedValue({
      items: [mockArticles[0]],
      totalCount: 1,
      limit: 50,
      offset: 0,
    });
    vi.mocked(newsApi.deleteNewsArticle).mockResolvedValue();

    // Mock confirm
    vi.spyOn(window, "confirm").mockReturnValue(true);

    render(<NewsList />);

    await waitFor(() => {
      expect(screen.getByText("FPT đạt doanh thu kỷ lục 1 tỷ USD")).toBeInTheDocument();
    });

    const deleteBtn = screen.getByTitle(/Xóa bài báo/i);
    fireEvent.click(deleteBtn);

    await waitFor(() => {
      expect(newsApi.deleteNewsArticle).toHaveBeenCalledWith("art-1");
    });
  });
});
