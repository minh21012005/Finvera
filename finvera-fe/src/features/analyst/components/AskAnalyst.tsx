import React, { useState, useRef, useEffect } from 'react';
import {
  streamAskAnalyst,
  AskAnalystRequest,
  AnalystFinalResult,
  ToolCallEvent,
} from '../api/analyst';
import { groupClaimsBySentence } from '../format/claim-grouping';
import { LiteMarkdown } from '../format/lite-markdown';
import { stripCitationTags } from '../format/citation-tags';

interface ToolCapability {
  name: string;
  badge: string;
  icon: string;
  title: string;
  desc: string;
  exampleQ: string;
  defaultSym?: string;
  accent: string;
  badgeColor: string;
}

const TOOL_CAPABILITIES: ToolCapability[] = [
  {
    name: 'MARKET',
    badge: 'Thị trường',
    icon: '🌐',
    title: 'Tổng quan & Độ rộng VN-INDEX',
    desc: 'Cung cấp điểm số chỉ số VN-INDEX, mức tăng giảm trong phiên, thống kê số mã tăng/giảm/tham chiếu và trạng thái thị trường (Regime).',
    exampleQ: 'Tổng quan chỉ số VN-INDEX và độ rộng thị trường phiên hôm nay',
    accent: 'border-blue-500/30 hover:border-blue-400/70 bg-blue-950/20 hover:bg-blue-900/30',
    badgeColor: 'bg-blue-500/20 text-blue-300 border-blue-500/30',
  },
  {
    name: 'STOCK',
    badge: 'Khớp lệnh',
    icon: '🏷️',
    title: 'Giá & Khối lượng Thời gian thực',
    desc: 'Cung cấp giá khớp lệnh hiện tại, % biến động tăng giảm và tình trạng dữ liệu của từng mã cổ phiếu.',
    exampleQ: 'Giá cổ phiếu HPG hôm nay biến động thế nào?',
    defaultSym: 'HPG',
    accent: 'border-emerald-500/30 hover:border-emerald-400/70 bg-emerald-950/20 hover:bg-emerald-900/30',
    badgeColor: 'bg-emerald-500/20 text-emerald-300 border-emerald-500/30',
  },
  {
    name: 'TECHNICAL',
    badge: 'Kỹ thuật',
    icon: '📐',
    title: 'Chỉ báo Kỹ thuật & Tín hiệu Chiến lược',
    desc: 'Tính toán các đường trung bình MA20, MA50, chỉ số sức mạnh tương quan RSI14 và kiểm tra các tín hiệu chiến lược đang kích hoạt.',
    exampleQ: 'Phân tích chỉ báo RSI, MA và tín hiệu kỹ thuật của SSI',
    defaultSym: 'SSI',
    accent: 'border-indigo-500/30 hover:border-indigo-400/70 bg-indigo-950/20 hover:bg-indigo-900/30',
    badgeColor: 'bg-indigo-500/20 text-indigo-300 border-indigo-500/30',
  },
  {
    name: 'FUNDAMENTAL',
    badge: 'Cơ bản',
    icon: '🏢',
    title: 'Tài chính Doanh nghiệp & Hiệu quả Sinh lời',
    desc: 'Cung cấp EPS theo quý, EPS 12 tháng gần nhất (TTM), tỷ suất sinh lời trên vốn (ROE) và tốc độ tăng trưởng doanh thu & lợi nhuận.',
    exampleQ: 'Chỉ số tài chính cơ bản, EPS và ROE của FPT',
    defaultSym: 'FPT',
    accent: 'border-cyan-500/30 hover:border-cyan-400/70 bg-cyan-950/20 hover:bg-cyan-900/30',
    badgeColor: 'bg-cyan-500/20 text-cyan-300 border-cyan-500/30',
  },
  {
    name: 'VALUATION',
    badge: 'Định giá',
    icon: '⚖️',
    title: 'Mô hình Định giá P/E & P/B',
    desc: 'Tính toán hệ số định giá P/E, P/B và phân loại đắt/rẻ định lượng (Undervalued, Fair Value, Overvalued) theo chuẩn so sánh lịch sử.',
    exampleQ: 'Định giá P/E và P/B của VCB hiện tại đắt hay rẻ so với lịch sử?',
    defaultSym: 'VCB',
    accent: 'border-violet-500/30 hover:border-violet-400/70 bg-violet-950/20 hover:bg-violet-900/30',
    badgeColor: 'bg-violet-500/20 text-violet-300 border-violet-500/30',
  },
  {
    name: 'PORTFOLIO',
    badge: 'Danh mục',
    icon: '💼',
    title: 'Quản lý Danh mục & Phân bổ Tài sản',
    desc: 'Cung cấp tổng giá trị tài sản, số dư tiền mặt, danh sách các vị thế nắm giữ, tỷ trọng phân bổ từng mã và lãi/lỗ chưa thực hiện.',
    exampleQ: 'Đánh giá danh mục, tỷ trọng phân bổ tài sản và lãi lỗ vị thế',
    accent: 'border-amber-500/30 hover:border-amber-400/70 bg-amber-950/20 hover:bg-amber-900/30',
    badgeColor: 'bg-amber-500/20 text-amber-300 border-amber-500/30',
  },
  {
    name: 'SCREENING',
    badge: 'Bộ lọc',
    icon: '🔍',
    title: 'Lọc Cổ phiếu theo Ngôn ngữ Tự nhiên',
    desc: 'Bộ lọc tìm kiếm cổ phiếu theo tiêu chí linh hoạt: P/E < 12, ROE > 18%, RSI, vốn hoá thị trường hoặc điểm bứt phá kỹ thuật.',
    exampleQ: 'Lọc các cổ phiếu có P/E dưới 12 và ROE trên 18%',
    accent: 'border-pink-500/30 hover:border-pink-400/70 bg-pink-950/20 hover:bg-pink-900/30',
    badgeColor: 'bg-pink-500/20 text-pink-300 border-pink-500/30',
  },
  {
    name: 'NEWS',
    badge: 'Tin tức',
    icon: '📰',
    title: 'Tin tức & Sự kiện Doanh nghiệp',
    desc: 'Tổng hợp danh sách các bài báo tài chính, sự kiện công bố thông tin và diễn biến tin tức mới nhất của các doanh nghiệp trên thị trường.',
    exampleQ: 'Cập nhật tin tức mới nhất về ngành thép và HPG',
    defaultSym: 'HPG',
    accent: 'border-orange-500/30 hover:border-orange-400/70 bg-orange-950/20 hover:bg-orange-900/30',
    badgeColor: 'bg-orange-500/20 text-orange-300 border-orange-500/30',
  },
  {
    name: 'RESEARCH_RAG',
    badge: 'Kho BCTC',
    icon: '📚',
    title: 'Tra cứu Thuyết minh BCTC & Báo cáo PDF',
    desc: 'Tra cứu sâu văn bản trong kho Báo cáo tài chính, Thuyết minh BCTC, Nghị quyết ĐHĐCĐ và các báo cáo phân tích chuyên sâu.',
    exampleQ: 'Tìm trong báo cáo tài chính về kế hoạch kinh doanh và triển vọng của FPT',
    defaultSym: 'FPT',
    accent: 'border-teal-500/30 hover:border-teal-400/70 bg-teal-950/20 hover:bg-teal-900/30',
    badgeColor: 'bg-teal-500/20 text-teal-300 border-teal-500/30',
  },
];

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
  const inputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    if (isStreaming && responseEndRef.current) {
      responseEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [streamedText, toolCalls, isStreaming]);

  // When user selects a template/card: populate input box for review/edit without auto-submitting
  const handleSelectPrompt = (q: string, sym: string) => {
    setQuestion(q);
    setSymbol(sym);
    inputRef.current?.focus();
  };

  const handleAsk = async () => {
    const q = question.trim();
    if (!q || isStreaming) return;

    setIsStreaming(true);
    setStreamedText('');
    setToolCalls([]);
    setFinalResult(null);
    setErrorMsg(null);

    const controller = new AbortController();
    abortControllerRef.current = controller;

    const req: AskAnalystRequest = {
      question: q,
      symbol: symbol.trim() || undefined,
    };

    try {
      await streamAskAnalyst(
        req,
        {
          onToolCall: (tc) => {
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
    <div className="flex flex-col h-full max-w-5xl mx-auto p-4 space-y-5">
      {/* Header */}
      <div className="flex flex-col space-y-1">
        <div className="flex items-center space-x-2">
          <span className="inline-flex items-center justify-center p-2 rounded-lg bg-emerald-500/10 text-emerald-400 font-bold text-sm">
            AI Analyst
          </span>
          <h1 className="text-xl font-bold text-slate-100">
            Trợ Lý Phân Tích Đa Công Cụ (Multi-Tool AI)
          </h1>
        </div>
        <p className="text-xs text-slate-400">
          Hệ thống tích hợp 9 công cụ dữ liệu tài chính chuyên sâu với cơ chế kiểm chứng số liệu minh bạch và bảo mật.
        </p>
      </div>

      {/* Main Conversation / Output Area */}
      <div className="flex-1 min-h-[420px] bg-slate-900/60 rounded-xl border border-slate-800/80 p-5 overflow-y-auto space-y-6">
        {/* Empty state: 9 Capabilities Board */}
        {!isStreaming && !streamedText && !finalResult && !errorMsg && (
          <div className="space-y-4 py-2">
            <div className="text-center space-y-1 pb-2">
              <div className="inline-flex items-center justify-center w-10 h-10 rounded-full bg-slate-800 text-lg mb-1">
                ⚡
              </div>
              <h2 className="text-sm font-semibold text-slate-200">
                Bản Đồ 9 Công Cụ Dữ Liệu Sẵn Sàng Truy Vấn
              </h2>
              <p className="text-xs text-slate-400 max-w-xl mx-auto">
                Nhấp vào bất kỳ công cụ nào bên dưới để chèn câu hỏi mẫu vào ô nhập liệu (bạn có thể chỉnh sửa trước khi bấm Gửi).
              </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3.5">
              {TOOL_CAPABILITIES.map((cap) => {
                const isSelected = question === cap.exampleQ;
                return (
                  <div
                    key={cap.name}
                    onClick={() => handleSelectPrompt(cap.exampleQ, cap.defaultSym || '')}
                    className={`cursor-pointer text-left p-4 rounded-xl border transition flex flex-col justify-between group ${
                      isSelected
                        ? 'border-emerald-500 bg-emerald-950/30 ring-1 ring-emerald-500/40'
                        : cap.accent
                    }`}
                  >
                    <div className="space-y-2">
                      <div className="flex items-center justify-between">
                        <span className="text-lg">{cap.icon}</span>
                        <span className={`text-[10px] px-2.5 py-0.5 rounded-full font-mono font-medium border ${cap.badgeColor}`}>
                          {cap.badge}
                        </span>
                      </div>
                      <div className="text-xs font-semibold text-slate-100 group-hover:text-emerald-300 transition">
                        {cap.title}
                      </div>
                      <p className="text-[11px] text-slate-300/80 leading-relaxed">
                        {cap.desc}
                      </p>
                    </div>

                    <div className="mt-3.5 pt-2.5 border-t border-slate-800/80 flex items-center justify-between text-[11px] text-slate-400 group-hover:text-emerald-400 transition">
                      <span className="italic">
                        "{cap.exampleQ}"
                      </span>
                      <span className="font-semibold text-xs ml-2 flex-shrink-0">→</span>
                    </div>
                  </div>
                );
              })}
            </div>
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
            {/* Header / Coverage badge */}
            {finalResult && (
              <div className="flex items-center justify-between pb-1">
                <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                  Kết quả phân tích AI
                </span>
                {finalResult.claimCoverage === 'FULL' && (
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-medium bg-emerald-500/15 text-emerald-300 border border-emerald-500/30">
                    <span>✓</span> Số liệu đã kiểm chứng đầy đủ
                  </span>
                )}
                {finalResult.claimCoverage === 'PARTIAL' && (
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-medium bg-amber-500/15 text-amber-300 border border-amber-500/30">
                    <span>⚠</span> Đã lược bỏ phần chưa đủ bằng chứng
                  </span>
                )}
                {finalResult.claimCoverage === 'NONE' && (
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-medium bg-orange-500/15 text-orange-300 border border-orange-500/40">
                    <span>⚠</span> Câu trả lời chưa có số liệu được kiểm chứng tự động
                  </span>
                )}
              </div>
            )}

            <div className="prose prose-invert max-w-none text-slate-200 text-sm leading-relaxed">
              <LiteMarkdown text={finalResult ? finalResult.answer : stripCitationTags(streamedText)} />
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

            {/* Degraded mode notice */}
            {finalResult?.synthesisMode === 'OFFLINE_TEMPLATE' && !finalResult.refused && (
              <div role="status" className="p-2.5 rounded bg-amber-500/10 border border-amber-500/30 text-amber-300 text-xs flex items-center space-x-2">
                <span>[Chế độ suy giảm]</span>
                <span>
                  Mô hình AI tạm thời không khả dụng — câu trả lời được lập theo mẫu trực tiếp từ dữ liệu công cụ
                  {finalResult.plannerMode === 'KEYWORD_FALLBACK' ? ', công cụ được chọn theo từ khoá' : ''}. Số liệu vẫn được kiểm chứng.
                </span>
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
                  {groupClaimsBySentence(finalResult.structuredClaims).map((group, idx) => (
                    <div
                      key={idx}
                      className="p-2.5 rounded-lg bg-slate-800/60 border border-emerald-500/30 flex flex-col justify-between space-y-1"
                    >
                      <div className="text-xs font-semibold text-emerald-300">{group.claimText}</div>
                      <div className="text-[10px] text-slate-400 font-mono">
                        [#{group.sequenceNo} {group.toolName} → {group.sourceFields.join(', ')}]
                      </div>
                      <div className="text-[10px] text-slate-400 flex items-center justify-end">
                        <span>Thời điểm: {group.asOf ? new Date(group.asOf).toLocaleTimeString('vi-VN') : 'N/A'}</span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Document Claims Citations Badges */}
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
            ref={inputRef}
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
              onClick={handleAsk}
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
