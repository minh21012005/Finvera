import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { SupportedToolsModal } from './components/SupportedToolsModal';
import { ConversationAnalyst } from './components/ConversationAnalyst';

vi.mock('./api/analyst', async () => {
  const actual = await vi.importActual<typeof import('./api/analyst')>('./api/analyst');
  return {
    ...actual,
    listConversations: vi.fn().mockResolvedValue({ items: [], hasMore: false }),
    getConversationExchanges: vi.fn().mockResolvedValue({ items: [], hasMore: false }),
    streamConversationAsk: vi.fn(),
  };
});

describe('Supported Tools Showcase', () => {
  it('renders all 11 tools and allows filtering and selecting a sample query', () => {
    const onSelect = vi.fn();
    const onClose = vi.fn();

    render(<SupportedToolsModal isOpen={true} onClose={onClose} onSelectQuery={onSelect} />);

    expect(screen.getByText('Công Cụ Phân Tích Finvera AI Hỗ Trợ')).toBeDefined();
    expect(screen.getByText('11 TOOLS SẴN SÀNG')).toBeDefined();

    // Check specific tools
    expect(screen.getByText('Phân tích Kỹ thuật Định lượng')).toBeDefined();
    expect(screen.getByText('Quét Chiến lược Quant Engine')).toBeDefined();
    expect(screen.getByText('Hybrid RAG Nghiên cứu Tài liệu')).toBeDefined();

    // Click a sample query
    const sampleBtn = screen.getByText(/Tổng quan diễn biến thị trường và thanh khoản VN-Index hôm nay/);
    fireEvent.click(sampleBtn);

    expect(onSelect).toHaveBeenCalledWith(
      'Tổng quan diễn biến thị trường và thanh khoản VN-Index hôm nay',
      undefined
    );
    expect(onClose).toHaveBeenCalled();
  });

  it('filters tools by search keyword', () => {
    render(<SupportedToolsModal isOpen={true} onClose={vi.fn()} onSelectQuery={vi.fn()} />);

    const searchInput = screen.getByPlaceholderText('Tìm kiếm công cụ hoặc mẫu lệnh…');
    fireEvent.change(searchInput, { target: { value: 'Breakout' } });

    expect(screen.getByText('Quét Chiến lược Quant Engine')).toBeDefined();
    expect(screen.queryByText('Hồ sơ & Thị giá Cổ phiếu')).toBeNull();
  });

  it('populates question textarea when clicking a starter prompt in the empty state', async () => {
    render(<ConversationAnalyst />);

    // Starter prompt cards should be visible in empty state
    expect(await screen.findByText('Bắt đầu nghiên cứu cùng Finvera AI Analyst')).toBeDefined();
    const starterCard = screen.getByText(/Phân tích kỹ thuật các chỉ báo RSI, MACD/);
    fireEvent.click(starterCard);

    const textarea = screen.getByPlaceholderText('Hỏi AI Analyst…') as HTMLTextAreaElement;
    expect(textarea.value).toContain('Phân tích kỹ thuật các chỉ báo RSI, MACD');

    const symbolInput = screen.getByPlaceholderText('FPT…') as HTMLInputElement;
    expect(symbolInput.value).toBe('FPT');
  });

  it('opens tools modal from the toolbar button', async () => {
    render(<ConversationAnalyst />);

    const toolsBtn = await screen.findByTitle('Xem 11 công cụ hệ thống hỗ trợ');
    fireEvent.click(toolsBtn);

    expect(screen.getByText('Công Cụ Phân Tích Finvera AI Hỗ Trợ')).toBeDefined();
    fireEvent.click(screen.getByRole('button', { name: 'Đóng' }));
    expect(screen.queryByText('Công Cụ Phân Tích Finvera AI Hỗ Trợ')).toBeNull();
  });
});
