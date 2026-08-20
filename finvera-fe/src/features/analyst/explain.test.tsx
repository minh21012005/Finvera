import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ExplainButton } from './components/ExplainButton';
import * as analystApi from './api/analyst';

vi.mock('./api/analyst', async () => {
  const actual = await vi.importActual<typeof import('./api/analyst')>('./api/analyst');
  return {
    ...actual,
    explainOutput: vi.fn(),
  };
});

describe('ExplainButton Component (User Story 3: P3)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders trigger button and opens modal with supplied evidence factors', async () => {
    const mockExplain = vi.mocked(analystApi.explainOutput);
    mockExplain.mockResolvedValueOnce({
      explanation: 'Tín hiệu mua kỹ thuật hình thành do RSI quá mua.',
      verified: true,
    });

    render(
      <ExplainButton
        outputType="SIGNAL"
        symbol="HPG"
        evidenceFactors={[
          { factorCode: 'RSI_14', description: 'Chỉ số RSI đạt 72.5' },
          { factorCode: 'MA20_CROSS', description: 'Cắt lên MA20' },
        ]}
      />
    );

    const triggerBtn = screen.getByText(/Giải thích AI/i);
    expect(triggerBtn).toBeDefined();

    fireEvent.click(triggerBtn);

    await waitFor(() => {
      expect(screen.getByText(/Giải Thích Kết Quả \(SIGNAL\)/i)).toBeDefined();
      expect(screen.getByText(/\[RSI_14\]:/i)).toBeDefined();
      expect(screen.getByText(/Chỉ số RSI đạt 72.5/i)).toBeDefined();
      expect(screen.getByText(/Tín hiệu mua kỹ thuật hình thành do RSI quá mua\./i)).toBeDefined();
      expect(screen.getByText(/\[Đã xác thực tính trung thực\]/i)).toBeDefined();
    });
  });

  it('renders unverified badge when faithfulness check fails on backend', async () => {
    const mockExplain = vi.mocked(analystApi.explainOutput);
    mockExplain.mockResolvedValueOnce({
      explanation: 'Hiện chưa có sẵn phần giải thích tự động.',
      verified: false,
    });

    render(
      <ExplainButton
        outputType="VALUATION_CLASSIFICATION"
        symbol="FPT"
        evidenceFactors={[
          { factorCode: 'PE_RATIO', description: 'P/E là 12.5' },
        ]}
      />
    );

    const triggerBtn = screen.getByText(/Giải thích AI/i);
    fireEvent.click(triggerBtn);

    await waitFor(() => {
      expect(screen.getByText(/\[Không thể giải thích tự động\]/i)).toBeDefined();
      expect(screen.getByText(/Hiện chưa có sẵn phần giải thích tự động\./i)).toBeDefined();
    });
  });
});
