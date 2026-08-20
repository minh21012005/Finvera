import React, { useState, useRef, useEffect } from 'react';
import { streamAskQuestion, AskRequest, AskFinalResult, AskCitation } from '../api/ask';
import type { DocumentType } from '../api/documents';

export const AskPanel: React.FC = () => {
  const [query, setQuery] = useState('');
  const [symbol, setSymbol] = useState('');
  const [documentType, setDocumentType] = useState<DocumentType | ''>('');
  const [showFilters, setShowFilters] = useState(false);

  const [isStreaming, setIsStreaming] = useState(false);
  const [streamedText, setStreamedText] = useState('');
  const [finalResult, setFinalResult] = useState<AskFinalResult | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [selectedCitation, setSelectedCitation] = useState<AskCitation | null>(null);

  const abortControllerRef = useRef<AbortController | null>(null);
  const responseEndRef = useRef<HTMLDivElement | null>(null);

  const suggestedQuestions = [
    'Doanh thu và tăng trưởng của FPT năm 2025?',
    'Kế hoạch sản xuất kinh doanh của HPG trong năm nay?',
    'Các yếu tố rủi ro chính được công bố trong báo cáo thường niên?',
  ];

  useEffect(() => {
    if (isStreaming && responseEndRef.current) {
      responseEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [streamedText, isStreaming]);

  const handleAsk = async (questionText?: string) => {
    const q = (questionText || query).trim();
    if (!q || isStreaming) {
      return;
    }

    if (questionText) {
      setQuery(questionText);
    }

    setIsStreaming(true);
    setStreamedText('');
    setFinalResult(null);
    setErrorMsg(null);
    setSelectedCitation(null);

    const controller = new AbortController();
    abortControllerRef.current = controller;

    const req: AskRequest = {
      query: q,
      filters: {
        symbol: symbol.trim() || undefined,
        documentType: documentType || undefined,
      },
    };

    try {
      await streamAskQuestion(
        req,
        {
          onDelta: (delta) => {
            setStreamedText((prev) => prev + delta);
          },
          onFinal: (res) => {
            setFinalResult(res);
            setStreamedText(res.answer);
            setIsStreaming(false);
          },
          onError: (err) => {
            setErrorMsg(err.message);
            setIsStreaming(false);
          },
        },
        controller.signal
      );
    } catch {
      setIsStreaming(false);
    }
  };

  const handleStop = () => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }
    setIsStreaming(false);
  };

  return (
    <div className="space-y-6">
      {/* Header & Description */}
      <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-6 shadow-xl backdrop-blur-md">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-xl font-bold text-slate-100 flex items-center gap-2">
              <span className="p-2 rounded-lg bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
                💬
              </span>
              Hỏi Đáp Trợ Lý Phân Tích (AI Analyst RAG)
            </h2>
            <p className="text-sm text-slate-400 mt-1">
              Đặt câu hỏi ngữ nghĩa dựa trên kho tài liệu đã tiếp nhận của bạn. Toàn bộ câu trả lời được kiểm chứng trích dẫn nguồn thực tế, không suy diễn số liệu tài chính sai lệch.
            </p>
          </div>
          <button
            type="button"
            onClick={() => setShowFilters(!showFilters)}
            className="text-xs px-3 py-1.5 rounded-lg border border-slate-700 bg-slate-800/80 text-slate-300 hover:text-white hover:bg-slate-700 transition"
          >
            {showFilters ? 'Ẩn bộ lọc ▲' : 'Bộ lọc ngữ cảnh ▼'}
          </button>
        </div>

        {/* Collapsible Filter Bar */}
        {showFilters && (
          <div className="mt-4 pt-4 border-t border-slate-800 grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">
                Mã Cổ Phiếu
              </label>
              <input
                type="text"
                value={symbol}
                onChange={(e) => setSymbol(e.target.value.toUpperCase())}
                placeholder="VD: FPT, VNM, HPG..."
                className="w-full bg-slate-950/70 border border-slate-700/80 rounded-xl px-3.5 py-2 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-indigo-500 transition"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">
                Loại Tài Liệu
              </label>
              <select
                value={documentType}
                onChange={(e) => setDocumentType(e.target.value as any)}
                className="w-full bg-slate-950/70 border border-slate-700/80 rounded-xl px-3.5 py-2 text-sm text-slate-100 focus:outline-none focus:border-indigo-500 transition"
              >
                <option value="">Tất cả loại tài liệu</option>
                <option value="FINANCIAL_REPORT">Báo cáo tài chính</option>
                <option value="ANNUAL_REPORT">Báo cáo thường niên</option>
                <option value="EARNINGS_PRESENTATION">Thuyết trình kết quả kinh doanh</option>
                <option value="RESEARCH_REPORT">Báo cáo phân tích</option>
                <option value="OTHER">Tài liệu khác</option>
              </select>
            </div>
          </div>
        )}

        {/* Query Input Form */}
        <div className="mt-5">
          <div className="relative">
            <textarea
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  handleAsk();
                }
              }}
              rows={3}
              maxLength={2000}
              placeholder="Nhập câu hỏi của bạn (VD: Lợi nhuận gộp quý 4/2025 tăng trưởng bao nhiêu %? Định hướng kinh doanh công nghệ số ra sao?)..."
              className="w-full bg-slate-950 border border-slate-700/80 rounded-xl p-4 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 focus:border-indigo-500 transition resize-none shadow-inner"
            />
            <div className="absolute right-3 bottom-3 flex items-center gap-3">
              <span className="text-xs text-slate-500">{query.length}/2000</span>
              {isStreaming ? (
                <button
                  type="button"
                  onClick={handleStop}
                  className="px-4 py-2 bg-red-600/80 hover:bg-red-500 text-white rounded-lg text-xs font-semibold shadow transition flex items-center gap-1.5"
                >
                  <span className="w-2 h-2 rounded-full bg-white animate-pulse"></span>
                  Dừng trả lời
                </button>
              ) : (
                <button
                  type="button"
                  onClick={() => handleAsk()}
                  disabled={!query.trim()}
                  className="px-4 py-2 bg-gradient-to-r from-indigo-600 to-indigo-500 hover:from-indigo-500 hover:to-indigo-400 disabled:opacity-50 disabled:cursor-not-allowed text-white rounded-lg text-xs font-semibold shadow-md transition flex items-center gap-1.5"
                >
                  <span>Gửi câu hỏi</span>
                  <span>➔</span>
                </button>
              )}
            </div>
          </div>

          {/* Suggested Prompts */}
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <span className="text-xs text-slate-500">Gợi ý:</span>
            {suggestedQuestions.map((s, idx) => (
              <button
                key={idx}
                type="button"
                onClick={() => handleAsk(s)}
                disabled={isStreaming}
                className="text-xs bg-slate-800/80 hover:bg-slate-700 border border-slate-700/60 rounded-lg px-2.5 py-1 text-slate-300 transition"
              >
                {s}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Error Alert */}
      {errorMsg && (
        <div className="p-4 bg-red-950/40 border border-red-800/60 rounded-xl text-red-300 text-sm flex items-start gap-3">
          <span className="text-red-400 font-bold">✕</span>
          <div>
            <div className="font-semibold">Không thể tải phản hồi</div>
            <div className="text-xs text-red-400/90 mt-0.5">{errorMsg}</div>
          </div>
        </div>
      )}

      {/* Response Panel */}
      {(streamedText || isStreaming || finalResult) && (
        <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-6 shadow-xl backdrop-blur-md space-y-5">
          <div className="flex items-center justify-between pb-3 border-b border-slate-800">
            <div className="flex items-center gap-2">
              <span className="p-1.5 rounded-lg bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 text-xs">
                🤖
              </span>
              <span className="text-sm font-semibold text-slate-200">Câu Trả Lời Của Finvera AI Analyst</span>
              {isStreaming && (
                <span className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full bg-indigo-500/20 text-indigo-300 border border-indigo-500/30">
                  <span className="w-1.5 h-1.5 rounded-full bg-indigo-400 animate-ping"></span>
                  Đang tổng hợp dữ liệu...
                </span>
              )}
              {finalResult && finalResult.refused && (
                <span className="text-xs px-2 py-0.5 rounded-full bg-amber-500/20 text-amber-300 border border-amber-500/30">
                  ⚠️ Từ chối / Ngoài phạm vi tài liệu
                </span>
              )}
            </div>

            {finalResult && (
              <button
                type="button"
                onClick={() => navigator.clipboard.writeText(finalResult.answer)}
                className="text-xs px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 border border-slate-700 transition"
              >
                📋 Sao chép
              </button>
            )}
          </div>

          {/* Streamed Answer Text */}
          <div className="prose prose-invert max-w-none text-sm text-slate-200 leading-relaxed whitespace-pre-wrap">
            {streamedText}
            {isStreaming && <span className="inline-block w-2 h-4 ml-1 bg-indigo-400 animate-pulse align-middle"></span>}
          </div>

          {/* Refusal Notice Banner */}
          {finalResult && finalResult.refused && (
            <div className="p-4 bg-amber-950/30 border border-amber-800/40 rounded-xl text-xs text-amber-200/90 leading-relaxed">
              <div className="font-semibold text-amber-300 mb-1">ℹ️ Ghi chú kiểm chứng & an toàn:</div>
              Hệ thống không tìm thấy đủ đoạn trích có độ tin cậy trong kho tài liệu hoặc câu hỏi yêu cầu tính toán định lượng chuyên sâu (được xử lý tại engine tài chính tất định). Finvera tuyệt đối tuân thủ nguyên tắc không tự sinh số liệu tài chính giả định.
            </div>
          )}

          {/* Verified Citations List */}
          {finalResult && !finalResult.refused && finalResult.citations.length > 0 && (
            <div className="mt-6 pt-5 border-t border-slate-800">
              <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-3 flex items-center gap-2">
                <span>📚</span> Nguồn Trích Dẫn Đã Được Kiểm Chứng ({finalResult.citations.length})
              </h3>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                {finalResult.citations.map((c, idx) => (
                  <div
                    key={idx}
                    onClick={() => setSelectedCitation(c)}
                    className="p-3.5 rounded-xl bg-slate-950/70 border border-slate-800/80 hover:border-indigo-500/50 hover:bg-slate-900/90 cursor-pointer transition shadow-sm group"
                  >
                    <div className="flex items-center justify-between mb-1.5">
                      <span className="text-xs font-semibold text-indigo-400 group-hover:text-indigo-300 truncate max-w-[200px]">
                        {c.sourceTitle}
                      </span>
                      <span className="text-[11px] px-2 py-0.5 rounded bg-slate-800 text-slate-400 border border-slate-700">
                        {c.location}
                      </span>
                    </div>
                    <p className="text-xs text-slate-300 line-clamp-2 italic">
                      "{c.claimText}"
                    </p>
                    <div className="mt-2 text-[11px] text-slate-500 flex items-center justify-between">
                      <span>Nguồn: {c.source}</span>
                      <span className="text-indigo-400 group-hover:underline">Chi tiết ➔</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          <div ref={responseEndRef} />
        </div>
      )}

      {/* Citation Detail Modal */}
      {selectedCitation && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-lg w-full p-6 shadow-2xl space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <h4 className="text-sm font-bold text-slate-100">Chi Tiết Đoạn Trích Dẫn</h4>
              <button
                type="button"
                onClick={() => setSelectedCitation(null)}
                className="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800 transition"
              >
                ✕
              </button>
            </div>
            <div className="space-y-2 text-xs">
              <div>
                <span className="text-slate-400 font-semibold">Tài liệu:</span>{' '}
                <span className="text-slate-200">{selectedCitation.sourceTitle}</span>
              </div>
              <div>
                <span className="text-slate-400 font-semibold">Vị trí:</span>{' '}
                <span className="text-indigo-400 font-semibold">{selectedCitation.location}</span>
              </div>
              <div>
                <span className="text-slate-400 font-semibold">Nguồn phát hành:</span>{' '}
                <span className="text-slate-200">{selectedCitation.source}</span>
              </div>
              <div>
                <span className="text-slate-400 font-semibold">Loại nguồn:</span>{' '}
                <span className="text-slate-200">{selectedCitation.sourceType === 'DOCUMENT' ? 'Tài liệu nghiên cứu' : 'Tin tức thị trường'}</span>
              </div>
            </div>
            <div className="p-3.5 bg-slate-950 border border-slate-800 rounded-xl text-xs text-slate-200 italic leading-relaxed">
              "{selectedCitation.claimText}"
            </div>
            <div className="flex justify-end">
              <button
                type="button"
                onClick={() => setSelectedCitation(null)}
                className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg text-xs font-semibold transition"
              >
                Đóng
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
