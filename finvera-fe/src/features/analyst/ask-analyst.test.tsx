import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AskAnalyst } from './components/AskAnalyst';
import * as analystApi from './api/analyst';

vi.mock('./api/analyst', () => ({
  streamAskAnalyst: vi.fn(),
}));

// jsdom does not implement scrollIntoView; AskAnalyst calls it on every tool-call/delta
// update while streaming, which every test here triggers.
Element.prototype.scrollIntoView = vi.fn();

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
            sequenceNo: 1,
            toolName: 'STOCK',
            sourceField: 'Giá',
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
      expect(screen.getAllByText(/#1 STOCK/i).length).toBeGreaterThan(0);
      expect(screen.getByText(/\[Thành công\]/i)).toBeDefined();
      expect(screen.getByText(/Giá HPG là 28500\./i)).toBeDefined();
      expect(screen.getByText(/Dữ liệu đã được kiểm chứng/i)).toBeDefined();
      expect(screen.getByText(/Giá 28500/i)).toBeDefined();
    });
  });

  it('renders a STARTED tool call as in-progress, never as failed', async () => {
    const mockStream = vi.mocked(analystApi.streamAskAnalyst);
    mockStream.mockImplementation(async (_req, callbacks) => {
      callbacks.onToolCall?.({
        sequenceNo: 1,
        toolName: 'STOCK',
        arguments: { symbol: 'HPG' },
        status: 'STARTED',
        latencyMs: 0,
      });
    });

    render(<AskAnalyst />);
    const input = screen.getByPlaceholderText(/Hỏi trợ lý phân tích/i);
    fireEvent.change(input, { target: { value: 'Giá HPG bao nhiêu?' } });
    fireEvent.click(screen.getByText(/Gửi/i));

    await waitFor(() => {
      expect(screen.getByText(/Đang xử lý/i)).toBeDefined();
      expect(screen.queryByText(/\[Thất bại\]/i)).toBeNull();
    });
  });

  it('replaces the STARTED card with its terminal status rather than duplicating it', async () => {
    const mockStream = vi.mocked(analystApi.streamAskAnalyst);
    mockStream.mockImplementation(async (_req, callbacks) => {
      callbacks.onToolCall?.({
        sequenceNo: 1,
        toolName: 'STOCK',
        arguments: { symbol: 'HPG' },
        status: 'STARTED',
        latencyMs: 0,
      });
      callbacks.onToolCall?.({
        sequenceNo: 1,
        toolName: 'STOCK',
        arguments: { symbol: 'HPG' },
        status: 'SUCCEEDED',
        latencyMs: 120,
      });
    });

    render(<AskAnalyst />);
    const input = screen.getByPlaceholderText(/Hỏi trợ lý phân tích/i);
    fireEvent.change(input, { target: { value: 'Giá HPG bao nhiêu?' } });
    fireEvent.click(screen.getByText(/Gửi/i));

    await waitFor(() => {
      expect(screen.getAllByText(/#1 STOCK/i)).toHaveLength(1);
      expect(screen.getByText(/\[Thành công\]/i)).toBeDefined();
      expect(screen.queryByText(/Đang xử lý/i)).toBeNull();
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
            sequenceNo: 1,
            toolName: 'STOCK',
            sourceField: 'Giá',
            asOf: '2026-08-20T10:00:00Z',
          },
        ],
        documentClaims: [
          {
            claimText: 'Theo Báo cáo thường niên 2025: Doanh thu 150.000 tỷ',
            sourceType: 'DOCUMENT',
            sourceId: '22222222-2222-2222-2222-222222222222',
            sourceTitle: 'Báo cáo thường niên 2025 HPG',
            location: 'Page 15',
            source: 'HPG Investor Relations',
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
      expect(screen.getByText(/Page 15/i)).toBeDefined();
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
