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
        className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded-md bg-indigo-500/10 hover:bg-indigo-500/20 text-indigo-300 border border-indigo-500/30 transition shadow-sm"
      >
        <span>💡</span>
        <span>{label}</span>
      </button>

      {isOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
          <div className="bg-slate-900 border border-slate-800 rounded-xl max-w-lg w-full p-5 space-y-4 shadow-2xl">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <div className="flex items-center gap-2">
                <span className="text-base font-bold text-slate-100">
                  Giải Thích Kết Quả ({outputType})
                </span>
                {symbol && (
                  <span className="px-2 py-0.5 rounded bg-slate-800 text-xs font-mono text-slate-300">
                    {symbol}
                  </span>
                )}
              </div>
              <button
                type="button"
                onClick={() => setIsOpen(false)}
                className="text-slate-400 hover:text-slate-200 text-lg leading-none"
              >
                ✕
              </button>
            </div>

            {/* Supplied evidence factors */}
            <div className="space-y-1.5">
              <div className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">
                Các Yếu Tố Bằng Chứng Được Cung Cấp ({evidenceFactors.length})
              </div>
              <div className="space-y-1 bg-slate-950/60 p-2.5 rounded-lg border border-slate-800 text-xs">
                {evidenceFactors.map((f, idx) => (
                  <div key={idx} className="flex items-start gap-2 text-slate-300">
                    <span className="font-mono text-indigo-400 font-semibold shrink-0">
                      [{f.factorCode}]:
                    </span>
                    <span>{f.description}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Content / Result Area */}
            <div className="space-y-3 pt-1">
              {isLoading && (
                <div className="py-6 flex flex-col items-center justify-center text-slate-400 space-y-2 text-xs">
                  <div className="w-5 h-5 border-2 border-indigo-400 border-t-transparent rounded-full animate-spin" />
                  <span>Đang tổng hợp và kiểm tra tính trung thực bằng chứng...</span>
                </div>
              )}

              {errorMsg && (
                <div className="p-3 rounded-lg bg-rose-500/10 border border-rose-500/30 text-rose-300 text-xs">
                  <strong>[Lỗi]:</strong> {errorMsg}
                </div>
              )}

              {result && !isLoading && (
                <div className="space-y-2.5">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-semibold text-slate-400 uppercase">
                      Lời Giải Thích Chi Tiết
                    </span>
                    <span
                      className={`text-[10px] px-2 py-0.5 rounded font-semibold ${
                        result.verified
                          ? 'bg-emerald-500/20 text-emerald-300'
                          : 'bg-amber-500/20 text-amber-300'
                      }`}
                    >
                      {result.verified
                        ? '[Đã xác thực tính trung thực]'
                        : '[Không thể giải thích tự động]'}
                    </span>
                  </div>
                  <div className="p-3.5 rounded-lg bg-slate-800/60 border border-slate-700 text-slate-200 text-xs leading-relaxed whitespace-pre-wrap">
                    {result.explanation}
                  </div>
                </div>
              )}
            </div>

            <div className="flex justify-end pt-2">
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
