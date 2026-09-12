import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ConversationAnalyst } from './components/ConversationAnalyst';
import * as api from './api/analyst';

vi.mock('./api/analyst', async () => {
  const actual = await vi.importActual<typeof import('./api/analyst')>('./api/analyst');
  return { ...actual, listConversations: vi.fn(), getConversationExchanges: vi.fn(),
    streamConversationAsk: vi.fn(), renameConversation: vi.fn(), deleteConversation: vi.fn() };
});

const summary = { id: 'conversation-1', title: 'Phân tích FPT', titleSource: 'AUTO' as const,
  createdAt: '2026-09-12T01:00:00Z', updatedAt: '2026-09-12T01:00:00Z',
  lastActivityAt: '2026-09-12T01:00:00Z', exchangeCount: 1, processing: false };

describe('AI conversation history', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.listConversations).mockResolvedValue({ items: [summary], hasMore: false });
    vi.mocked(api.getConversationExchanges).mockResolvedValue({ conversation: summary, items: [], hasMore: false });
  });

  it('loads, selects, and reopens an owned conversation', async () => {
    render(<ConversationAnalyst />);
    expect(await screen.findByText('Phân tích FPT')).toBeDefined();
    fireEvent.click(screen.getByText('Phân tích FPT'));
    await waitFor(() => expect(api.getConversationExchanges).toHaveBeenCalledWith('conversation-1'));
  });

  it('reopens the exact stored answer, evidence, and as-of timestamp', async () => {
    vi.mocked(api.getConversationExchanges).mockResolvedValue({ conversation: summary, hasMore: false, items: [{
      id: 'exchange-evidence', sequenceNo: 1, question: 'Giá FPT?', status: 'COMPLETED',
      final: { answer: 'Giá đóng cửa đã lưu', refused: false, toolCalls: [], toolCallBoundReached: false,
        ruleVersion: 'orchestration-v1', claimCoverage: 'FULL', structuredClaims: [{ sequenceNo: 1,
          claimText: 'FPT đóng cửa 150.000 đồng', toolName: 'GET_STOCK_OVERVIEW', sourceField: 'closePrice', asOf: '2026-09-11T08:00:00Z' }],
        documentClaims: [{ claimText: 'Doanh thu tăng', sourceId: 'doc-1', sourceTitle: 'Báo cáo FPT',
          sourceType: 'DOCUMENT', location: 'trang 3', source: 'FPT' }] },
      contextWindow: { ruleVersion: 'context-window-v1', includedExchanges: 0, omittedExchanges: 0 },
      createdAt: '2026-09-12T01:00:00Z', completedAt: '2026-09-12T01:01:00Z',
    }] });

    render(<ConversationAnalyst />);
    fireEvent.click(await screen.findByText('Phân tích FPT'));
    expect(await screen.findByText('Giá đóng cửa đã lưu')).toBeDefined();
    expect(screen.getByText(/FPT đóng cửa 150.000 đồng/)).toHaveTextContent('closePrice');
    expect(screen.getByText(/Báo cáo FPT/)).toHaveTextContent('trang 3');
  });

  it('prepends an older page once and keeps chronological order', async () => {
    const newer = { id: 'exchange-2', sequenceNo: 2, question: 'Câu mới', status: 'COMPLETED' as const,
      final: { answer: 'Trả lời mới', structuredClaims: [], documentClaims: [], refused: false,
        toolCalls: [], toolCallBoundReached: false, ruleVersion: 'orchestration-v1', claimCoverage: 'FULL' as const },
      contextWindow: { ruleVersion: 'context-window-v1', includedExchanges: 1, omittedExchanges: 0 },
      createdAt: '2026-09-12T02:00:00Z', completedAt: '2026-09-12T02:01:00Z' };
    const older = { ...newer, id: 'exchange-1', sequenceNo: 1, question: 'Câu cũ',
      final: { ...newer.final, answer: 'Trả lời cũ' }, createdAt: '2026-09-12T01:00:00Z' };
    vi.mocked(api.getConversationExchanges)
      .mockResolvedValueOnce({ conversation: summary, items: [newer], olderCursor: 'older-page', hasMore: true })
      .mockResolvedValueOnce({ conversation: summary, items: [older], hasMore: false });

    render(<ConversationAnalyst />);
    fireEvent.click(await screen.findByText('Phân tích FPT'));
    fireEvent.click(await screen.findByRole('button', { name: 'Tải các lượt cũ hơn' }));

    await screen.findByText('Trả lời cũ');
    const questions = screen.getAllByText(/^Câu (cũ|mới)$/).map((node) => node.textContent);
    expect(questions).toEqual(['Câu cũ', 'Câu mới']);
    expect(api.getConversationExchanges).toHaveBeenLastCalledWith('conversation-1', 'older-page');
  });

  it('creates a durable exchange from accepted through final', async () => {
    vi.mocked(api.listConversations).mockResolvedValue({ items: [], hasMore: false });
    vi.mocked(api.streamConversationAsk).mockImplementation(async (_request, callbacks) => {
      callbacks.onAccepted?.({ type: 'accepted', conversationId: 'new-conversation', exchangeId: 'exchange-1',
        created: true, title: 'Giá FPT', contextWindow: { ruleVersion: 'context-window-v1', includedExchanges: 0, omittedExchanges: 0 } });
      callbacks.onDelta?.('FPT đang được phân tích.');
      callbacks.onFinal?.({ answer: 'Kết quả FPT', structuredClaims: [], documentClaims: [], refused: false,
        toolCalls: [], toolCallBoundReached: false, ruleVersion: 'orchestration-v1', claimCoverage: 'FULL' });
    });
    render(<ConversationAnalyst />);
    const input = await screen.findByPlaceholderText('Hỏi AI Analyst…');
    fireEvent.change(input, { target: { value: 'Giá FPT?' } });
    fireEvent.click(screen.getByRole('button', { name: 'Gửi' }));
    expect(await screen.findByText('Kết quả FPT')).toBeDefined();
    expect(api.streamConversationAsk).toHaveBeenCalledWith(expect.objectContaining({ question: 'Giá FPT?' }), expect.anything(), expect.anything());
  });

  it('shows failed state honestly without presenting a completed answer', async () => {
    vi.mocked(api.listConversations).mockResolvedValue({ items: [], hasMore: false });
    vi.mocked(api.streamConversationAsk).mockImplementation(async (_request, callbacks) => {
      callbacks.onAccepted?.({ type: 'accepted', conversationId: 'new-conversation', exchangeId: 'exchange-1',
        created: true, title: 'FPT', contextWindow: { ruleVersion: 'context-window-v1', includedExchanges: 0, omittedExchanges: 0 } });
      callbacks.onTerminalError?.('ANALYST_UNAVAILABLE', true);
    });
    render(<ConversationAnalyst />);
    fireEvent.change(await screen.findByPlaceholderText('Hỏi AI Analyst…'), { target: { value: 'Phân tích FPT' } });
    fireEvent.click(screen.getByRole('button', { name: 'Gửi' }));
    expect(await screen.findByText('ANALYST_UNAVAILABLE')).toBeDefined();
    expect(screen.queryByText('Đã hoàn thành')).toBeNull();
  });

  it('marks only the active exchange failed when its stream is interrupted', async () => {
    vi.mocked(api.listConversations).mockResolvedValue({ items: [], hasMore: false });
    vi.mocked(api.streamConversationAsk).mockImplementation(async (_request, callbacks) => {
      callbacks.onAccepted?.({ type: 'accepted', conversationId: 'new-conversation', exchangeId: 'exchange-1',
        created: true, title: 'FPT', contextWindow: { ruleVersion: 'context-window-v1', includedExchanges: 0, omittedExchanges: 0 } });
      const error = new Error('Mất kết nối');
      callbacks.onError?.(error);
      throw error;
    });
    render(<ConversationAnalyst />);
    fireEvent.change(await screen.findByPlaceholderText('Hỏi AI Analyst…'), { target: { value: 'Phân tích FPT' } });
    fireEvent.click(screen.getByRole('button', { name: 'Gửi' }));
    expect(await screen.findByText('STREAM_INTERRUPTED')).toBeDefined();
    expect(screen.queryByText('Đang xử lý')).toBeNull();
  });

  it('does not attach a late final event to another selected processing exchange', async () => {
    const other = { ...summary, id: 'conversation-2', title: 'HPG', processing: true };
    const otherPending = { id: 'other-exchange', sequenceNo: 1, question: 'Phân tích HPG', status: 'PROCESSING' as const,
      final: null, contextWindow: { ruleVersion: 'context-window-v1', includedExchanges: 0, omittedExchanges: 0 },
      createdAt: '2026-09-12T02:00:00Z' };
    vi.mocked(api.listConversations).mockResolvedValue({ items: [summary, other], hasMore: false });
    vi.mocked(api.getConversationExchanges)
      .mockResolvedValueOnce({ conversation: summary, items: [], hasMore: false })
      .mockResolvedValueOnce({ conversation: other, items: [otherPending], hasMore: false });
    let callbacks: api.ConversationCallbacks | undefined;
    let finishStream: (() => void) | undefined;
    vi.mocked(api.streamConversationAsk).mockImplementation(async (_request, handlers) => {
      callbacks = handlers;
      handlers.onAccepted?.({ type: 'accepted', conversationId: 'conversation-1', exchangeId: 'active-exchange',
        created: false, title: 'FPT', contextWindow: { ruleVersion: 'context-window-v1', includedExchanges: 0, omittedExchanges: 0 } });
      await new Promise<void>((resolve) => { finishStream = resolve; });
    });

    render(<ConversationAnalyst />);
    fireEvent.click(await screen.findByText('Phân tích FPT'));
    await waitFor(() => expect(api.getConversationExchanges).toHaveBeenCalledWith('conversation-1'));
    fireEvent.change(screen.getByPlaceholderText('Hỏi AI Analyst…'), { target: { value: 'Câu tiếp theo' } });
    fireEvent.click(screen.getByRole('button', { name: 'Gửi' }));
    fireEvent.click(await screen.findByText('HPG'));
    expect(await screen.findByText('Phân tích HPG')).toBeDefined();

    await act(async () => {
      callbacks?.onDelta?.('Không được hiển thị ở HPG');
      callbacks?.onFinal?.({ answer: 'Kết quả FPT', structuredClaims: [], documentClaims: [], refused: false,
        toolCalls: [], toolCallBoundReached: false, ruleVersion: 'orchestration-v1', claimCoverage: 'FULL' });
      finishStream?.();
    });
    expect(screen.getAllByText('Đang xử lý').length).toBeGreaterThan(0);
    expect(screen.queryByText('Kết quả FPT')).toBeNull();
    expect(screen.queryByText('Không được hiển thị ở HPG')).toBeNull();
  });

  it('cancels the local stream and labels the exchange as stopped', async () => {
    vi.mocked(api.listConversations).mockResolvedValue({ items: [], hasMore: false });
    vi.mocked(api.streamConversationAsk).mockImplementation(async (_request, callbacks, signal) => {
      callbacks.onAccepted?.({ type: 'accepted', conversationId: 'new-conversation', exchangeId: 'exchange-1',
        created: true, title: 'FPT', contextWindow: { ruleVersion: 'context-window-v1', includedExchanges: 0, omittedExchanges: 0 } });
      await new Promise<void>((_resolve, reject) => signal?.addEventListener('abort', () => reject(new DOMException('Stopped', 'AbortError'))));
    });
    render(<ConversationAnalyst />);
    fireEvent.change(await screen.findByPlaceholderText('Hỏi AI Analyst…'), { target: { value: 'Phân tích FPT' } });
    fireEvent.click(screen.getByRole('button', { name: 'Gửi' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Dừng' }));
    expect(await screen.findByText('Đã dừng')).toBeDefined();
    expect(screen.getByText('CLIENT_CANCELLED')).toBeDefined();
  });

  it('renames and deletes only after successful API responses', async () => {
    vi.mocked(api.renameConversation).mockResolvedValue({ ...summary, title: 'Nghiên cứu dài hạn', titleSource: 'OWNER' });
    vi.mocked(api.deleteConversation).mockResolvedValue();
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    render(<ConversationAnalyst />);
    expect(await screen.findByText('Phân tích FPT')).toBeDefined();

    fireEvent.click(screen.getByRole('button', { name: 'Đổi tên' }));
    const titleInput=screen.getByLabelText('Tên cuộc trò chuyện');
    fireEvent.change(titleInput, { target: { value: 'Nghiên cứu dài hạn' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    expect(await screen.findByText('Nghiên cứu dài hạn')).toBeDefined();

    fireEvent.click(screen.getByRole('button', { name: 'Xóa' }));
    await waitFor(() => expect(api.deleteConversation).toHaveBeenCalledWith('conversation-1'));
    await waitFor(() => expect(screen.queryByText('Nghiên cứu dài hạn')).toBeNull());
    expect(document.activeElement).toBe(screen.getByRole('button', { name: /Cuộc trò chuyện mới/ }));
  });

  it('keeps a conversation visible and reports a failed delete', async () => {
    vi.mocked(api.deleteConversation).mockRejectedValue(new Error('Conversation is busy'));
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    render(<ConversationAnalyst />);
    expect(await screen.findByText('Phân tích FPT')).toBeDefined();
    fireEvent.click(screen.getByRole('button', { name: 'Xóa' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Conversation is busy');
    expect(screen.getByText('Phân tích FPT')).toBeDefined();
  });
});
