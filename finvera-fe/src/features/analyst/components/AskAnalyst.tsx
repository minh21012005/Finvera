import React, { useState, useRef, useEffect } from 'react';
import {
  streamAskAnalyst,
  AskAnalystRequest,
  AnalystFinalResult,
  ToolCallEvent,
} from '../api/analyst';

export const AskAnalyst: React.FC = () => {
  const [question, setQuestion] = useState('');
  const [symbol, setSymbol] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  const [streamedText, setStreamedText] = useState('');
  const [toolCalls, setToolCalls] = useState<ToolCallEvent[]>([]);
  const [finalResult, setFinalResult] = useState<AnalystFinalResult | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const abortControllerRef = useRef<AbortController | null>(null);
  const responseEndRef = useRef<HTMLDivElement | null>(null);

  const suggestedQuestions = [
    { label: 'Giá và Kỹ thuật HPG', q: 'Phân tích giá và tín hiệu kỹ thuật của HPG hôm nay', sym: 'HPG' },
    { label: 'Chỉ số VN-INDEX & Độ rộng', q: 'Tổng quan thị trường và chỉ số VN-INDEX hiện tại', sym: '' },
    { label: 'Định giá P/E FPT', q: 'Định giá P/E và kết quả kinh doanh của FPT', sym: 'FPT' },
    { label: 'Hiệu suất danh mục', q: 'Đánh giá danh mục và tỷ trọng phân bổ tài sản', sym: '' },
  ];

  useEffect(() => {
    if (isStreaming && responseEndRef.current) {
      responseEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [streamedText, toolCalls, isStreaming]);

  const handleAsk = async (customQ?: string, customSym?: string) => {
    const q = (customQ !== undefined ? customQ : question).trim();
    if (!q || isStreaming) return;

    if (customQ !== undefined) setQuestion(customQ);
    if (customSym !== undefined) setSymbol(customSym);

    setIsStreaming(true);
    setStreamedText('');
    setToolCalls([]);
    setFinalResult(null);
    setErrorMsg(null);

    const controller = new AbortController();
    abortControllerRef.current = controller;

    const req: AskAnalystRequest = {
      question: q,
      symbol: (customSym !== undefined ? customSym : symbol).trim() || undefined,
    };

    try {
      await streamAskAnalyst(
        req,
        {
          onToolCall: (tc) => {
            // A tool call is reported twice — once "started", once with its terminal
            // status — replace the existing card by sequenceNo rather than appending a
            // second one for the same call.
            setToolCalls((prev) => {
              const idx = prev.findIndex((p) => p.sequenceNo === tc.sequenceNo);
              if (idx === -1) return [...prev, tc];
              const next = [...prev];
              next[idx] = tc;
              return next;
            });
          },
          onDelta: (delta) => {
            setStreamedText((prev) => prev + delta);
          },
          onFinal: (res) => {
            setFinalResult(res);
            setIsStreaming(false);
          },
          onError: (err) => {
            setErrorMsg(err.message);
            setIsStreaming(false);
          },
        },
        controller.signal
      );
    } catch (err: unknown) {
      if (err instanceof Error && err.name === 'AbortError') return;
      setErrorMsg(err instanceof Error ? err.message : 'Đã có lỗi xảy ra');
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
    <div className="flex flex-col h-full max-w-5xl mx-auto p-4 space-y-6">
      {/* Header */}
      <div className="flex flex-col space-y-1">
        <div className="flex items-center space-x-2">
          <span className="inline-flex items-center justify-center p-2 rounded-lg bg-emerald-500/10 text-emerald-400 font-bold">
            AI Analyst
          </span>
          <h1 className="text-xl font-bold text-slate-100">
            Trợ Lý Phân Tích Đa Công Cụ (Multi-Tool AI)
          </h1>
        </div>
        <p className="text-sm text-slate-400">
          Phân tích chuyên sâu dựa trên 9 công cụ dữ liệu tài chính thực tế và kiểm chứng số liệu minh bạch.
        </p>
      </div>

      {/* Suggested chips */}
      <div className="flex flex-wrap gap-2">
        {suggestedQuestions.map((s, idx) => (
          <button
            key={idx}
            type="button"
            disabled={isStreaming}
            onClick={() => handleAsk(s.q, s.sym)}
            className="text-xs px-3 py-1.5 rounded-full bg-slate-800/80 hover:bg-slate-700 text-slate-300 hover:text-white border border-slate-700/60 transition"
          >
            {s.label}
          </button>
        ))}
      </div>

      {/* Main Conversation / Output Area */}
      <div className="flex-1 min-h-[350px] bg-slate-900/60 rounded-xl border border-slate-800/80 p-5 overflow-y-auto space-y-6">
        {/* Empty state */}
        {!isStreaming && !streamedText && !finalResult && !errorMsg && (
          <div className="h-full flex flex-col items-center justify-center text-center text-slate-500 py-12">
            <div className="w-12 h-12 rounded-full bg-slate-800 flex items-center justify-center mb-3 text-slate-400">
              📊
            </div>
            <p className="font-medium text-slate-400">Chưa có câu hỏi nào được đưa ra</p>
            <p className="text-xs max-w-sm mt-1">
              Nhập câu hỏi về thị trường, cổ phiếu, chỉ số kỹ thuật hoặc danh mục để bắt đầu.
            </p>
          </div>
        )}

        {/* Error message */}
        {errorMsg && (
          <div className="p-4 rounded-lg bg-rose-500/10 border border-rose-500/30 text-rose-300 text-sm">
            <div className="flex items-center space-x-2 font-semibold">
              <span>[Lỗi]</span>
              <span>Không thể hoàn tất phân tích</span>
            </div>
            <p className="mt-1 text-xs text-rose-400">{errorMsg}</p>
          </div>
        )}

        {/* Tool Invocations Progress */}
        {toolCalls.length > 0 && (
          <div className="space-y-2">
            <div className="text-xs font-semibold text-slate-400 uppercase tracking-wider flex items-center space-x-2">
              <span>Công cụ được kích hoạt ({toolCalls.length})</span>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
              {toolCalls.map((tc) => {
                const statusStyle =
                  tc.status === 'SUCCEEDED'
                    ? { card: 'bg-slate-800/40 border-slate-700/60', badge: 'bg-emerald-500/20 text-emerald-300', label: '[Thành công]' }
                    : tc.status === 'FAILED'
                      ? { card: 'bg-rose-950/20 border-rose-800/40', badge: 'bg-rose-500/20 text-rose-300', label: '[Thất bại]' }
                      : { card: 'bg-slate-800/20 border-slate-700/40', badge: 'bg-amber-500/20 text-amber-300', label: '[Đang xử lý…]' };
                return (
                <div
                  key={tc.sequenceNo}
                  className={`p-3 rounded-lg border text-xs flex flex-col justify-between ${statusStyle.card}`}
                >
                  <div className="flex items-center justify-between">
                    <span className="font-mono font-bold text-slate-200">
                      #{tc.sequenceNo} {tc.toolName}
                    </span>
                    <span
                      className={`px-2 py-0.5 rounded text-[10px] font-semibold ${statusStyle.badge}`}
                    >
                      {statusStyle.label}
                    </span>
                  </div>
                  {tc.toolName === 'SCREENING' && tc.arguments?.filters ? (
                    <div className="mt-2 space-y-1">
                      <div className="text-slate-300 font-sans text-[11px] font-medium">
                        Bộ lọc đã chuyển đổi:
                      </div>
                      <div className="p-1.5 rounded bg-slate-950/60 font-mono text-[10px] text-indigo-300 overflow-x-auto">
                        {JSON.stringify(tc.arguments.filters, null, 2)}
                      </div>
                      {Boolean(tc.arguments?.ambiguityNote) && (
                        <div className="p-2 rounded bg-amber-500/10 border border-amber-500/30 text-amber-300 text-[10px]">
                          <strong>[Lưu ý mơ hồ]:</strong> {String(tc.arguments.ambiguityNote)}
                        </div>
                      )}
                    </div>
                  ) : (
                    <div className="mt-2 text-slate-400 font-mono text-[11px] truncate">
                      {JSON.stringify(tc.arguments)}
                    </div>
                  )}
                  <div className="mt-1 text-[10px] text-slate-500 text-right">
                    Độ trễ: {tc.latencyMs}ms
                  </div>
                </div>
                );
              })}
            </div>
          </div>
        )}

        {/* Streaming / Final Answer Area */}
        {(streamedText || finalResult) && (
          <div className="space-y-4 pt-2">
            <div className="prose prose-invert max-w-none text-slate-200 text-sm leading-relaxed whitespace-pre-wrap">
              {finalResult ? finalResult.answer : streamedText}
              {isStreaming && (
                <span className="inline-block w-2 h-4 ml-1 bg-emerald-400 animate-pulse align-middle" />
              )}
            </div>

            {/* Bound reached notice */}
            {finalResult?.toolCallBoundReached && (
              <div className="p-2.5 rounded bg-amber-500/10 border border-amber-500/30 text-amber-300 text-xs flex items-center space-x-2">
                <span>[Giới hạn 10 công cụ]</span>
                <span>Hệ thống đã đạt giới hạn tối đa 10 lượt gọi công cụ. Kết quả phân tích có thể chỉ phản ánh một phần dữ liệu.</span>
              </div>
            )}

            {/* Refusal Notice */}
            {finalResult?.refused && (
              <div className="p-2.5 rounded bg-blue-500/10 border border-blue-500/30 text-blue-300 text-xs flex items-center space-x-2">
                <span>[Từ chối / Ngoài phạm vi]</span>
                <span>Nội dung yêu cầu nằm ngoài phạm vi công cụ tài chính hỗ trợ.</span>
              </div>
            )}

            {/* Structured Claims Attribution Badges */}
            {finalResult && finalResult.structuredClaims && finalResult.structuredClaims.length > 0 && (
              <div className="space-y-2 mt-4 pt-4 border-t border-slate-800">
                <div className="text-xs font-semibold text-slate-400 uppercase tracking-wider flex items-center space-x-2">
                  <span>Dữ liệu đã được kiểm chứng ({finalResult.structuredClaims.length} số liệu)</span>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  {finalResult.structuredClaims.map((claim, idx) => (
                    <div
                      key={idx}
                      className="p-2.5 rounded-lg bg-slate-800/60 border border-emerald-500/30 flex flex-col justify-between space-y-1"
                    >
                      <div className="flex items-center justify-between text-xs">
                        <span className="font-semibold text-emerald-300">
                          {claim.claimText}
                        </span>
                        <span className="text-[10px] text-slate-400 font-mono">
                          [#{claim.sequenceNo} {claim.toolName} → {claim.sourceField}]
                        </span>
                      </div>
                      <div className="text-[10px] text-slate-400 flex items-center justify-end">
                        <span>Thời điểm: {claim.asOf ? new Date(claim.asOf).toLocaleTimeString('vi-VN') : 'N/A'}</span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Document Claims Citations Badges (US2: P2) */}
            {finalResult && finalResult.documentClaims && finalResult.documentClaims.length > 0 && (
              <div className="space-y-2 mt-3 pt-3 border-t border-slate-800/60">
                <div className="text-xs font-semibold text-slate-400 uppercase tracking-wider flex items-center space-x-2">
                  <span>Trích dẫn tài liệu & BCTC ({finalResult.documentClaims.length} văn bản)</span>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  {finalResult.documentClaims.map((docClaim, idx) => (
                    <div
                      key={idx}
                      className="p-2.5 rounded-lg bg-slate-800/60 border border-blue-500/30 flex flex-col justify-between space-y-1"
                    >
                      <div className="flex items-center justify-between text-xs">
                        <span className="font-semibold text-blue-300 truncate">
                          📄 {docClaim.sourceTitle || 'Tài liệu công bố'}
                        </span>
                        {docClaim.location && (
                          <span className="text-[10px] px-1.5 py-0.5 rounded bg-blue-500/10 text-blue-300 font-mono">
                            {docClaim.location}
                          </span>
                        )}
                      </div>
                      <div className="text-xs text-slate-300 line-clamp-2">
                        {docClaim.claimText}
                      </div>
                      <div className="text-[10px] text-slate-400 flex items-center justify-between mt-1">
                        <span className="font-mono text-[9px] text-slate-500 truncate max-w-[180px]">
                          {docClaim.source}
                        </span>
                        <span className="text-[10px] text-slate-400">
                          [{docClaim.sourceType === 'NEWS_ARTICLE' ? 'TIN TỨC' : 'TÀI LIỆU'}]
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}

        <div ref={responseEndRef} />
      </div>

      {/* Input bar */}
      <div className="flex flex-col sm:flex-row gap-2">
        <div className="w-full sm:w-28">
          <input
            type="text"
            placeholder="Mã (HPG...)"
            value={symbol}
            disabled={isStreaming}
            onChange={(e) => setSymbol(e.target.value.toUpperCase())}
            className="w-full px-3 py-2.5 rounded-lg bg-slate-900 border border-slate-700 text-slate-100 placeholder-slate-500 text-sm focus:outline-none focus:border-emerald-500"
          />
        </div>
        <div className="flex-1 flex gap-2">
          <input
            type="text"
            placeholder="Hỏi trợ lý phân tích (ví dụ: Phân tích kỹ thuật và định giá HPG)..."
            value={question}
            disabled={isStreaming}
            onChange={(e) => setQuestion(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                handleAsk();
              }
            }}
            className="flex-1 px-4 py-2.5 rounded-lg bg-slate-900 border border-slate-700 text-slate-100 placeholder-slate-500 text-sm focus:outline-none focus:border-emerald-500"
          />
          {isStreaming ? (
            <button
              type="button"
              onClick={handleStop}
              className="px-5 py-2.5 rounded-lg bg-rose-600 hover:bg-rose-500 text-white font-medium text-sm transition shadow-lg shadow-rose-600/20"
            >
              Dừng
            </button>
          ) : (
            <button
              type="button"
              onClick={() => handleAsk()}
              disabled={!question.trim()}
              className="px-5 py-2.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 disabled:bg-slate-800 disabled:text-slate-600 text-white font-medium text-sm transition shadow-lg shadow-emerald-600/20"
            >
              Gửi
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
