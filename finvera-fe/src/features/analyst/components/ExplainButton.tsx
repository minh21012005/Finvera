import React, { useState } from 'react';
import { explainOutput, ExplainRequest, ExplainResponse, EvidenceFactor } from '../api/analyst';

export interface ExplainButtonProps {
  outputType: 'SIGNAL' | 'INDICATOR_READING' | 'VALUATION_CLASSIFICATION' | 'RISK_FACTOR';
  symbol?: string;
  evidenceFactors: EvidenceFactor[];
  label?: string;
}

export const ExplainButton: React.FC<ExplainButtonProps> = ({
  outputType,
  symbol,
  evidenceFactors,
  label = 'Giải thích AI',
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [result, setResult] = useState<ExplainResponse | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const handleExplain = async () => {
    setIsLoading(true);
    setErrorMsg(null);

    const req: ExplainRequest = {
      outputType,
      symbol,
      evidenceFactors,
    };

    try {
      const res = await explainOutput(req);
      setResult(res);
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Lỗi khi yêu cầu giải thích.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleOpen = () => {
    setIsOpen(true);
    if (!result && !isLoading) {
      handleExplain();
    }
  };

  return (
    <>
      <button
        type="button"
        onClick={handleOpen}
        className="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-medium rounded-md bg-indigo-500/15 hover:bg-indigo-500/25 text-indigo-300 border border-indigo-500/30 transition shadow-sm"
      >
        <span>💡</span>
        <span>{label}</span>
      </button>

      {isOpen && (
        <div
          className="fixed inset-0 z-[1000] flex items-center justify-center p-4 sm:p-6 bg-slate-950/60 backdrop-blur-sm overflow-hidden transition-all"
          onClick={() => setIsOpen(false)}
        >
          <div
            className="bg-slate-900/98 border border-slate-700/80 rounded-xl max-w-2xl w-full max-h-[82vh] flex flex-col shadow-[0_20px_60px_-15px_rgba(0,0,0,0.8)] ring-1 ring-white/5 overflow-hidden my-auto"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
          >
            {/* Modal Header (Fixed at top) */}
            <div className="px-6 py-4 border-b border-slate-800 flex items-center justify-between shrink-0 bg-slate-900">
              <div className="flex items-center gap-2.5">
                <span className="text-base font-bold text-slate-100">
                  Giải Thích Kết Quả ({outputType})
                </span>
                {symbol && (
                  <span className="px-2 py-0.5 rounded bg-indigo-950/80 border border-indigo-800/50 text-xs font-mono font-semibold text-indigo-300">
                    {symbol}
                  </span>
                )}
              </div>
              <button
                type="button"
                onClick={() => setIsOpen(false)}
                className="text-slate-400 hover:text-slate-200 p-1 rounded-lg hover:bg-slate-800 transition"
                aria-label="Đóng modal"
              >
                ✕
              </button>
            </div>

            {/* Modal Scrollable Body */}
            <div className="px-6 py-4 space-y-4 overflow-y-auto flex-1 overscroll-contain">
              {/* Supplied evidence factors */}
              <div className="space-y-2">
                <div className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">
                  Các Yếu Tố Bằng Chứng Được Cung Cấp ({evidenceFactors.length})
                </div>
                <div className="space-y-1.5 bg-slate-950/70 p-3 rounded-lg border border-slate-800 text-xs">
                  {evidenceFactors.map((f, idx) => (
                    <div key={idx} className="flex items-start gap-2 text-slate-300">
                      <span className="font-mono text-indigo-400 font-semibold shrink-0">
                        [{f.factorCode}]:
                      </span>
                      <span className="leading-snug">{f.description}</span>
                    </div>
                  ))}
                </div>
              </div>

              {/* Content / Result Area */}
              <div className="space-y-2.5 pt-1">
                {isLoading && (
                  <div className="py-8 flex flex-col items-center justify-center text-slate-400 space-y-2 text-xs">
                    <div className="w-6 h-6 border-2 border-indigo-400 border-t-transparent rounded-full animate-spin" />
                    <span>Đang kết nối Gemini LLM và kiểm tra tính trung thực bằng chứng...</span>
                  </div>
                )}

                {errorMsg && (
                  <div className="p-3.5 rounded-lg bg-rose-500/10 border border-rose-500/30 text-rose-300 text-xs">
                    <strong>[Lỗi]:</strong> {errorMsg}
                  </div>
                )}

                {result && !isLoading && (
                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-semibold text-slate-400 uppercase">
                        Lời Giải Thích Chi Tiết
                      </span>
                      <span
                        className={`text-[11px] px-2.5 py-0.5 rounded font-semibold ${
                          result.verified
                            ? 'bg-emerald-500/15 text-emerald-300 border border-emerald-500/30'
                            : 'bg-amber-500/15 text-amber-300 border border-amber-500/30'
                        }`}
                      >
                        {result.verified
                          ? '[Đã xác thực tính trung thực]'
                          : '[Không thể giải thích tự động]'}
                      </span>
                    </div>
                    <div className="p-4 rounded-lg bg-slate-950/60 border border-slate-800 text-slate-200 text-xs leading-relaxed whitespace-pre-wrap">
                      {result.explanation}
                    </div>
                  </div>
                )}
              </div>
            </div>

            {/* Modal Footer (Fixed at bottom) */}
            <div className="px-6 py-3.5 border-t border-slate-800 flex items-center justify-between shrink-0 bg-slate-900">
              <span className="text-[11px] text-slate-500">
                Finvera AI Assistant · Grounded Explanations
              </span>
              <button
                type="button"
                onClick={() => setIsOpen(false)}
                className="px-4 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-medium transition"
              >
                Đóng
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};
