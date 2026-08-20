import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { DocumentUpload } from "./components/document-upload";
import { DocumentList } from "./components/document-list";
import { RetrievalResults } from "./components/retrieval-results";
import type { Document } from "./api/documents";

// Mock the API layer
vi.mock("./api/documents", async (importOriginal) => {
  const actual = await importOriginal<typeof import("./api/documents")>();
  return {
    ...actual,
    submitDocument: vi.fn().mockResolvedValue({
      id: "doc-123",
      title: "Test Report",
      symbol: "FPT",
      documentType: "ANNUAL_REPORT",
      year: 2025,
      source: "FPT IR",
      publicationDate: "2026-03-15",
      ingestionStatus: "PENDING",
      submittedAt: new Date().toISOString(),
    }),
    deleteDocument: vi.fn().mockResolvedValue(undefined),
  };
});

vi.mock("./api/retrieve", async (importOriginal) => {
  const actual = await importOriginal<typeof import("./api/retrieve")>();
  return {
    ...actual,
    retrievePassages: vi.fn().mockResolvedValue({
      passages: [
        {
          sourceType: "DOCUMENT",
          sourceId: "doc-123",
          sourceTitle: "FPT Annual Report 2025",
          location: "Page 15",
          source: "FPT IR",
          publicationDate: "2026-03-15",
          excerpt: "Doanh thu năm 2025 tăng trưởng mạnh mẽ đạt 60.000 tỷ.",
          score: 0.88,
        },
      ],
      asOf: new Date().toISOString(),
    }),
  };
});

describe("Research Components (User Story 1)", () => {
  describe("DocumentUpload Component", () => {
    it("renders form inputs and allows mode switching", async () => {
      const user = userEvent.setup();
      const onSubmitted = vi.fn();
      render(<DocumentUpload onDocumentSubmitted={onSubmitted} />);

      expect(screen.getByText("Tải lên Tài liệu Nghiên cứu")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "File PDF" })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Dán Văn bản" })).toBeInTheDocument();

      // Switch to text mode
      await user.click(screen.getByRole("button", { name: "Dán Văn bản" }));
      expect(screen.getByPlaceholderText("Dán nội dung văn bản báo cáo vào đây...")).toBeInTheDocument();
    });

    it("shows error when submitting without required fields", async () => {
      const user = userEvent.setup();
      render(<DocumentUpload onDocumentSubmitted={vi.fn()} />);

      await user.click(screen.getByText("Tải lên & Bắt đầu Xử lý"));
      expect(screen.getByText("Vui lòng nhập tiêu đề tài liệu.")).toBeInTheDocument();
    });
  });

  describe("DocumentList Component", () => {
    const mockDocs: Document[] = [
      {
        id: "doc-1",
        title: "Báo cáo thường niên FPT 2025",
        symbol: "FPT",
        documentType: "ANNUAL_REPORT",
        year: 2025,
        source: "FPT IR",
        publicationDate: "2026-03-15",
        ingestionStatus: "READY",
        submittedAt: new Date().toISOString(),
      },
      {
        id: "doc-2",
        title: "Báo cáo Q4 Vinamilk",
        symbol: "VNM",
        documentType: "QUARTERLY_REPORT",
        year: 2025,
        quarter: 4,
        source: "VNM",
        publicationDate: "2026-01-20",
        ingestionStatus: "FAILED",
        ingestionFailureReason: "PDF corrupted",
        submittedAt: new Date().toISOString(),
      },
    ];

    it("renders document list with non-color status labels", () => {
      render(
        <DocumentList
          documents={mockDocs}
          loading={false}
          onDocumentDeleted={vi.fn()}
          onRefresh={vi.fn()}
          symbolFilter=""
          onSymbolFilterChange={vi.fn()}
          typeFilter=""
          onTypeFilterChange={vi.fn()}
        />,
      );

      expect(screen.getByText("Báo cáo thường niên FPT 2025")).toBeInTheDocument();
      expect(screen.getByText("● Sẵn sàng (READY)")).toBeInTheDocument();
      expect(screen.getByText("✕ Thất bại (FAILED)")).toBeInTheDocument();
      expect(screen.getByText("Lỗi: PDF corrupted")).toBeInTheDocument();
    });

    it("renders empty state when there are no documents", () => {
      render(
        <DocumentList
          documents={[]}
          loading={false}
          onDocumentDeleted={vi.fn()}
          onRefresh={vi.fn()}
          symbolFilter=""
          onSymbolFilterChange={vi.fn()}
          typeFilter=""
          onTypeFilterChange={vi.fn()}
        />,
      );

      expect(screen.getByText("Chưa có tài liệu nào trong kho lưu trữ của bạn.")).toBeInTheDocument();
    });
  });

  describe("RetrievalResults Component", () => {
    it("renders search input and displays ranked cited passages on search", async () => {
      const user = userEvent.setup();
      render(<RetrievalResults />);

      const input = screen.getByPlaceholderText(/Nhập câu hỏi hoặc từ khóa nghiên cứu/i);
      await user.type(input, "Doanh thu FPT");

      await user.click(screen.getByText("Tìm Trích dẫn"));

      expect(await screen.findByText(/Kết quả Đoạn Trích dẫn/i)).toBeInTheDocument();
      expect(screen.getByText("FPT Annual Report 2025")).toBeInTheDocument();
      expect(screen.getByText("Page 15")).toBeInTheDocument();
      expect(screen.getByText(/Doanh thu năm 2025 tăng trưởng mạnh mẽ/i)).toBeInTheDocument();
      expect(screen.getByText("Độ khớp: 88.0%")).toBeInTheDocument();
    });
  });
});
