import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AskPanel } from './components/ask-panel';
import * as askApi from './api/ask';

vi.mock('./api/ask', () => ({
  streamAskQuestion: vi.fn(),
}));

describe('AskPanel Component (User Story 2: P2)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders input area, submit button and suggested questions', () => {
    render(<AskPanel />);
    expect(screen.getByPlaceholderText(/Nhập câu hỏi của bạn/i)).toBeDefined();
    expect(screen.getByText(/Gửi câu hỏi/i)).toBeDefined();
    expect(screen.getByText(/Doanh thu và tăng trưởng của FPT năm 2025\?/i)).toBeDefined();
  });

  it('renders streaming answer and verified citations on success', async () => {
    const mockStreamAsk = vi.mocked(askApi.streamAskQuestion);
    mockStreamAsk.mockImplementation(async (_req, callbacks) => {
      callbacks.onDelta?.('Doanh thu ');
      callbacks.onDelta?.('FPT tăng trưởng mạnh.');
      callbacks.onFinal?.({
        answer: 'Doanh thu FPT tăng trưởng mạnh.',
        citations: [
          {
            claimText: 'Doanh thu FPT tăng trưởng mạnh',
            sourceType: 'DOCUMENT',
            sourceId: '11111111-1111-1111-1111-111111111111',
            sourceTitle: 'BCTC Q4 2025',
            location: 'Page 12',
            source: 'FPT IR',
          },
        ],
        refused: false,
      });
    });

    render(<AskPanel />);
    const textarea = screen.getByPlaceholderText(/Nhập câu hỏi của bạn/i);
    fireEvent.change(textarea, { target: { value: 'Doanh thu FPT?' } });

    const submitBtn = screen.getByText(/Gửi câu hỏi/i);
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByText(/Câu Trả Lời Của Finvera AI Analyst/i)).toBeDefined();
      expect(screen.getByText(/Doanh thu FPT tăng trưởng mạnh\./i)).toBeDefined();
      expect(screen.getByText(/BCTC Q4 2025/i)).toBeDefined();
      expect(screen.getByText(/Page 12/i)).toBeDefined();
    });
  });

  it('renders refusal banner when answer is refused due to lack of evidence', async () => {
    const mockStreamAsk = vi.mocked(askApi.streamAskQuestion);
    mockStreamAsk.mockImplementation(async (_req, callbacks) => {
      callbacks.onFinal?.({
        answer: 'Không tìm thấy thông tin phù hợp trong kho tài liệu.',
        citations: [],
        refused: true,
      });
    });

    render(<AskPanel />);
    const textarea = screen.getByPlaceholderText(/Nhập câu hỏi của bạn/i);
    fireEvent.change(textarea, { target: { value: 'Chỉ số RSI của VNM?' } });

    const submitBtn = screen.getByText(/Gửi câu hỏi/i);
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByText(/Từ chối \/ Ngoài phạm vi tài liệu/i)).toBeDefined();
      expect(screen.getByText(/Ghi chú kiểm chứng & an toàn/i)).toBeDefined();
    });
  });

  it('renders error notice when streaming fails with network error', async () => {
    const mockStreamAsk = vi.mocked(askApi.streamAskQuestion);
    mockStreamAsk.mockImplementation(async (_req, callbacks) => {
      callbacks.onError?.(new Error('Dịch vụ RAG hiện không khả dụng'));
      throw new Error('Dịch vụ RAG hiện không khả dụng');
    });

    render(<AskPanel />);
    const textarea = screen.getByPlaceholderText(/Nhập câu hỏi của bạn/i);
    fireEvent.change(textarea, { target: { value: 'Kế hoạch kinh doanh?' } });

    const submitBtn = screen.getByText(/Gửi câu hỏi/i);
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByText(/Không thể tải phản hồi/i)).toBeDefined();
      expect(screen.getByText(/Dịch vụ RAG hiện không khả dụng/i)).toBeDefined();
    });
  });
});
