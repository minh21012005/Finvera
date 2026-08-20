import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AskAnalyst } from './components/AskAnalyst';
import * as analystApi from './api/analyst';

vi.mock('./api/analyst', () => ({
  streamAskAnalyst: vi.fn(),
}));

describe('AskAnalyst Component (User Story 1: P1)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders input bar, suggested chips, and initial empty state', () => {
    render(<AskAnalyst />);
    expect(screen.getByPlaceholderText(/Hỏi trợ lý phân tích/i)).toBeDefined();
    expect(screen.getByPlaceholderText(/Mã/i)).toBeDefined();
    expect(screen.getByText(/Giá và Kỹ thuật HPG/i)).toBeDefined();
    expect(screen.getByText(/Chưa có câu hỏi nào được đưa ra/i)).toBeDefined();
  });

  it('renders tool calls progress and verified structured claims on success', async () => {
    const mockStream = vi.mocked(analystApi.streamAskAnalyst);
    mockStream.mockImplementation(async (_req, callbacks) => {
      callbacks.onToolCall?.({
        sequenceNo: 1,
        toolName: 'STOCK',
        arguments: { symbol: 'HPG' },
        status: 'SUCCEEDED',
        latencyMs: 150,
      });
      callbacks.onDelta?.('Giá HPG là 28500.');
      callbacks.onFinal?.({
        answer: 'Giá HPG là 28500.',
        structuredClaims: [
          {
            claimText: 'Giá 28500',
            toolName: 'STOCK',
            sourceField: 'price',
            claimedValue: '28500',
            asOf: '2026-08-20T10:00:00Z',
          },
        ],
        documentClaims: [],
        refused: false,
        toolCalls: [],
        toolCallBoundReached: false,
        ruleVersion: 'orchestration-v1',
      });
    });

    render(<AskAnalyst />);
    const input = screen.getByPlaceholderText(/Hỏi trợ lý phân tích/i);
    fireEvent.change(input, { target: { value: 'Giá HPG bao nhiêu?' } });

    const submitBtn = screen.getByText(/Gửi/i);
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByText(/Công cụ được kích hoạt/i)).toBeDefined();
      expect(screen.getByText(/#1 STOCK/i)).toBeDefined();
      expect(screen.getByText(/\[Thành công\]/i)).toBeDefined();
      expect(screen.getByText(/Giá HPG là 28500\./i)).toBeDefined();
      expect(screen.getByText(/Dữ liệu đã được kiểm chứng/i)).toBeDefined();
      expect(screen.getByText(/Giá 28500/i)).toBeDefined();
    });
  });

  it('renders bound reached disclosure notice when toolCallBoundReached is true', async () => {
    const mockStream = vi.mocked(analystApi.streamAskAnalyst);
    mockStream.mockImplementation(async (_req, callbacks) => {
      callbacks.onFinal?.({
        answer: 'Dữ liệu tổng hợp từ 10 công cụ.',
        structuredClaims: [],
        documentClaims: [],
        refused: false,
        toolCalls: [],
        toolCallBoundReached: true,
        ruleVersion: 'orchestration-v1',
      });
    });

    render(<AskAnalyst />);
    const input = screen.getByPlaceholderText(/Hỏi trợ lý phân tích/i);
    fireEvent.change(input, { target: { value: 'Phân tích tổng hợp' } });

    const submitBtn = screen.getByText(/Gửi/i);
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByText(/\[Giới hạn 10 công cụ\]/i)).toBeDefined();
    });
  });

  it('renders both structured claims and document citations distinctly for combined question (User Story 2: P2)', async () => {
    const mockStream = vi.mocked(analystApi.streamAskAnalyst);
    mockStream.mockImplementation(async (_req, callbacks) => {
      callbacks.onFinal?.({
        answer: 'Giá HPG là 28500. Kế hoạch doanh thu 150000 tỷ theo Báo cáo thường niên 2025.',
        structuredClaims: [
          {
            claimText: 'Giá 28500',
            toolName: 'STOCK',
            sourceField: 'price',
            claimedValue: '28500',
            asOf: '2026-08-20T10:00:00Z',
          },
        ],
        documentClaims: [
          {
            chunkId: '11111111-1111-1111-1111-111111111111',
            claimText: 'Theo Báo cáo thường niên 2025: Doanh thu 150.000 tỷ',
            title: 'Báo cáo thường niên 2025 HPG',
            source: 'DOCUMENT',
            pageNumber: 15,
          },
        ],
        refused: false,
        toolCalls: [],
        toolCallBoundReached: false,
        ruleVersion: 'orchestration-v1',
      });
    });

    render(<AskAnalyst />);
    const input = screen.getByPlaceholderText(/Hỏi trợ lý phân tích/i);
    fireEvent.change(input, { target: { value: 'Giá HPG và tài liệu BCTN 2025' } });

    const submitBtn = screen.getByText(/Gửi/i);
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByText(/Dữ liệu đã được kiểm chứng/i)).toBeDefined();
      expect(screen.getByText(/Giá 28500/i)).toBeDefined();
      expect(screen.getByText(/Trích dẫn tài liệu & BCTC/i)).toBeDefined();
      expect(screen.getByText(/Báo cáo thường niên 2025 HPG/i)).toBeDefined();
      expect(screen.getByText(/Trang 15/i)).toBeDefined();
    });
  });

  it('renders converted screening filters and ambiguity note (User Story 4: P4)', async () => {
    const mockStream = vi.mocked(analystApi.streamAskAnalyst);
    mockStream.mockImplementation(async (_req, callbacks) => {
      callbacks.onToolCall?.({
        sequenceNo: 1,
        toolName: 'SCREENING',
        arguments: {
          filters: {
            fundamental: { peMax: '10', roeMin: '15' },
          },
          ambiguityNote: 'Tiêu chí ngành chưa được chỉ định cụ thể, áp dụng toàn bộ thị trường.',
        },
        status: 'SUCCEEDED',
        latencyMs: 150,
      });

      callbacks.onFinal?.({
        answer: 'Đã tìm thấy 5 mã cổ phiếu thỏa mãn tiêu chí P/E dưới 10 và ROE trên 15%.',
        structuredClaims: [],
        documentClaims: [],
        refused: false,
        toolCalls: [
          {
            sequenceNo: 1,
            toolName: 'SCREENING',
            arguments: {
              filters: {
                fundamental: { peMax: '10', roeMin: '15' },
              },
              ambiguityNote: 'Tiêu chí ngành chưa được chỉ định cụ thể, áp dụng toàn bộ thị trường.',
            },
            status: 'SUCCEEDED',
            latencyMs: 150,
          },
        ],
        toolCallBoundReached: false,
        ruleVersion: 'orchestration-v1',
      });
    });

    render(<AskAnalyst />);
    const input = screen.getByPlaceholderText(/Hỏi trợ lý phân tích/i);
    fireEvent.change(input, { target: { value: 'Tìm các mã P/E dưới 10 và ROE trên 15%' } });

    const submitBtn = screen.getByText(/Gửi/i);
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByText(/Bộ lọc đã chuyển đổi:/i)).toBeDefined();
      expect(screen.getByText(/\[Lưu ý mơ hồ\]:/i)).toBeDefined();
      expect(screen.getByText(/Tiêu chí ngành chưa được chỉ định cụ thể/i)).toBeDefined();
      expect(screen.getByText(/Đã tìm thấy 5 mã cổ phiếu thỏa mãn/i)).toBeDefined();
    });
  });
});
