import React, { useState, useMemo } from 'react';
import type { ConversationExchange, PublicStructuredClaim } from '../api/analyst';
import { LiteMarkdown } from '../format/lite-markdown';
import {
  Bot,
  User,
  Sparkles,
  ShieldCheck,
  FileText,
  AlertCircle,
  Wrench,
  ArrowUpRight,
  TrendingUp,
  FileSpreadsheet,
  Zap,
  GitCompare,
  BarChart3,
  BookOpen,
  ChevronDown,
  ChevronUp,
} from 'lucide-react';

interface Props {
  exchanges: ConversationExchange[];
  streamedText: string;
  streamedExchangeId?: string;
  loading: boolean;
  hasOlder: boolean;
  onLoadOlder: () => void;
  onOpenTools?: () => void;
  onSelectQuery?: (query: string, symbol?: string) => void;
}

const vietnameseTime = (value: string) =>
  new Date(value).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' });

const METRIC_ACRONYMS = new Set(['ROE', 'ROA', 'RSI', 'EPS', 'TTM', 'VND', 'USD', 'TOP', 'MAX', 'MIN', 'AVG', 'SMA', 'EMA', 'MAC', 'BOS', 'COT']);

const CollapsibleClaims: React.FC<{ claims: PublicStructuredClaim[] }> = ({ claims }) => {
  const [expanded, setExpanded] = useState(claims.length <= 4);

  const grouped = useMemo(() => {
    const map = new Map<string, PublicStructuredClaim[]>();
    for (const claim of claims) {
      const matches = claim.claimText.match(/\b([A-Z]{3})\b/g);
      let symbol: string | null = null;
      if (matches) {
        for (const m of matches) {
          if (!METRIC_ACRONYMS.has(m)) {
            symbol = m;
            break;
          }
        }
      }
      const groupKey = symbol || (claim.toolName === 'PORTFOLIO' ? 'Danh mục' : (claim.toolName === 'MARKET' ? 'Thị trường' : 'Chung'));
      if (!map.has(groupKey)) map.set(groupKey, []);
      map.get(groupKey)!.push(claim);
    }
    return map;
  }, [claims]);

  return (
    <div className="mt-4 rounded-xl border border-emerald-900/30 bg-emerald-950/20 p-3.5 transition-all">
      <button
        type="button"
        onClick={() => setExpanded((prev) => !prev)}
        className="w-full text-xs font-bold text-emerald-400 flex items-center justify-between gap-1.5 focus:outline-none hover:text-emerald-300"
      >
        <span className="flex items-center gap-1.5">
          <ShieldCheck size={14} />
          <span>Số liệu đã kiểm chứng ({claims.length} số liệu đối soát)</span>
        </span>
        <span className="flex items-center gap-1 text-[11px] font-normal text-emerald-500/80 bg-emerald-950/60 px-2 py-0.5 rounded border border-emerald-900/40">
          {expanded ? 'Thu gọn' : 'Xem chi tiết'}
          {expanded ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
        </span>
      </button>

      {expanded && (
        <div className="mt-3 space-y-3 pt-2 border-t border-emerald-900/30">
          {Array.from(grouped.entries()).map(([groupName, items]) => (
            <div key={groupName} className="space-y-1.5">
              <div className="text-[11px] font-bold text-emerald-300 uppercase tracking-wider">
                {groupName} ({items.length})
              </div>
              <div className="space-y-1.5">
                {items.map((claim, index) => (
                  <p
                    className="text-xs text-slate-300 font-mono bg-slate-950/50 rounded-lg p-2 border border-emerald-900/20"
                    key={`${claim.sequenceNo}-${index}`}
                  >
                    {claim.claimText} · <span className="text-slate-500">{claim.sourceField}</span> · {vietnameseTime(claim.asOf)}
                  </p>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

const stateLabel: Record<ConversationExchange['status'], string> = {
  PROCESSING: 'Đang xử lý',
  COMPLETED: 'Đã hoàn thành',
  FAILED: 'Không thể hoàn thành',
  CANCELLED: 'Đã dừng',
};

const STARTER_PROMPTS = [
  {
    icon: TrendingUp,
    iconColor: 'text-cyan-400',
    iconBg: 'bg-cyan-500/10 border-cyan-500/30',
    title: 'Phân tích Kỹ thuật & Xu hướng',
    query: 'Phân tích kỹ thuật các chỉ báo RSI, MACD và các đường MA của FPT',
    symbol: 'FPT',
  },
  {
    icon: FileSpreadsheet,
    iconColor: 'text-amber-400',
    iconBg: 'bg-amber-500/10 border-amber-500/30',
    title: 'BCTC & Chỉ số Sinh lời',
    query: 'Tình hình doanh thu, tăng trưởng lợi nhuận và ROE của VCB qua các quý gần nhất',
    symbol: 'VCB',
  },
  {
    icon: Zap,
    iconColor: 'text-rose-400',
    iconBg: 'bg-rose-500/10 border-rose-500/30',
    title: 'Quét Chiến lược Quant Engine',
    query: 'Quét các cổ phiếu đang có tín hiệu Breakout vượt nền giá hôm nay',
  },
  {
    icon: GitCompare,
    iconColor: 'text-sky-400',
    iconBg: 'bg-sky-500/10 border-sky-500/30',
    title: 'So sánh Đa Cổ phiếu',
    query: 'So sánh các chỉ số tài chính và định giá giữa HPG, NKG và HSG',
  },
  {
    icon: BarChart3,
    iconColor: 'text-emerald-400',
    iconBg: 'bg-emerald-500/10 border-emerald-500/30',
    title: 'Độ rộng & Dòng tiền Thị trường',
    query: 'Tổng quan diễn biến thị trường và thanh khoản VN-Index hôm nay',
  },
  {
    icon: BookOpen,
    iconColor: 'text-purple-400',
    iconBg: 'bg-purple-500/10 border-purple-500/30',
    title: 'Hybrid RAG Nghiên cứu Tài liệu',
    query: 'Kế hoạch mở rộng chuỗi và định hướng kinh doanh của MWG trong tài liệu ĐHCĐ',
    symbol: 'MWG',
  },
];

export const ConversationTranscript: React.FC<Props> = ({
  exchanges,
  streamedText,
  streamedExchangeId,
  loading,
  hasOlder,
  onLoadOlder,
  onOpenTools,
  onSelectQuery,
}) => (
  <section
    aria-label="Nội dung hội thoại"
    aria-live="polite"
    className="min-h-[540px] flex-1 overflow-y-auto rounded-2xl border border-slate-800 bg-[#060b13]/90 backdrop-blur-md p-5 shadow-2xl flex flex-col justify-between"
  >
    <div className="space-y-4 flex-1">
      {/* Transcript Top Bar */}
      <div className="flex items-center justify-between pb-3 border-b border-slate-800/80">
        <div className="flex items-center gap-2">
          <span className="inline-block w-2 h-2 rounded-full bg-cyan-400 shadow-[0_0_8px_#00d2e0]" />
          <span className="text-xs font-bold text-slate-300">Hội thoại AI Financial Analyst</span>
        </div>
        {onOpenTools && (
          <button
            type="button"
            onClick={onOpenTools}
            className="flex items-center gap-1.5 rounded-lg border border-slate-800 bg-slate-900/90 hover:bg-slate-800 hover:border-slate-700 px-3 py-1.5 text-xs font-semibold text-cyan-400 hover:text-cyan-300 transition-all cursor-pointer shadow-sm"
          >
            <Wrench size={13} />
            <span>11 Công cụ hỗ trợ</span>
          </button>
        )}
      </div>

      {hasOlder && (
        <div className="text-center pb-2">
          <button
            type="button"
            disabled={loading}
            onClick={onLoadOlder}
            className="rounded-full border border-slate-800 bg-slate-900/80 hover:bg-slate-800 px-4 py-1.5 text-xs text-slate-300 hover:text-white transition-all cursor-pointer shadow-sm"
          >
            Tải các lượt cũ hơn
          </button>
        </div>
      )}

      {loading && exchanges.length === 0 && (
        <div className="flex flex-col items-center justify-center py-20 text-center text-xs text-slate-400 space-y-2">
          <div className="w-8 h-8 rounded-full border-2 border-indigo-500 border-t-transparent animate-spin" />
          <p>Đang tải nội dung…</p>
        </div>
      )}

      {!loading && exchanges.length === 0 && (
        <div className="py-6 px-2 sm:px-4 max-w-3xl mx-auto space-y-6">
          <div className="text-center space-y-3">
            <div className="w-12 h-12 rounded-2xl bg-gradient-to-br from-indigo-900/50 to-cyan-900/50 border border-indigo-700/50 flex items-center justify-center text-cyan-300 mx-auto shadow-inner">
              <Sparkles size={22} />
            </div>
            <h3 className="text-lg font-bold text-white tracking-tight">
              Bắt đầu nghiên cứu cùng Finvera AI Analyst
            </h3>
            <p className="text-xs text-slate-400 max-w-lg mx-auto leading-relaxed">
              Trợ lý định lượng chuyên sâu cho thị trường chứng khoán Việt Nam. Chọn gợi ý nhanh bên dưới hoặc mở danh mục 11 công cụ để khám phá.
            </p>
            {onOpenTools && (
              <div className="pt-1">
                <button
                  type="button"
                  onClick={onOpenTools}
                  className="inline-flex items-center gap-2 rounded-xl bg-slate-900/90 hover:bg-slate-800 border border-indigo-500/30 hover:border-indigo-500/60 px-4 py-2 text-xs font-semibold text-cyan-300 hover:text-cyan-200 transition-all cursor-pointer shadow-lg shadow-indigo-950/40"
                >
                  <Wrench size={14} />
                  <span>Khám phá 11 Công cụ AI hỗ trợ</span>
                  <ArrowUpRight size={14} className="text-slate-400" />
                </button>
              </div>
            )}
          </div>

          {/* Quick Prompt Starters Grid */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2 items-stretch">
            {STARTER_PROMPTS.map((starter, i) => {
              const StarterIcon = starter.icon;
              return (
                <button
                  key={i}
                  type="button"
                  onClick={() => onSelectQuery?.(starter.query, starter.symbol)}
                  className="h-full min-h-[96px] flex flex-col justify-between text-left rounded-xl border border-slate-800/80 bg-slate-900/40 hover:bg-indigo-950/30 hover:border-indigo-600/50 p-3.5 transition-all cursor-pointer group shadow-sm hover:shadow-md"
                >
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-2">
                      <div
                        className={`w-6 h-6 rounded-lg border flex items-center justify-center shrink-0 ${starter.iconBg}`}
                      >
                        <StarterIcon size={13} className={starter.iconColor} />
                      </div>
                      <span className="text-[11px] font-bold text-slate-200 group-hover:text-cyan-300 transition-colors">
                        {starter.title}
                      </span>
                    </div>
                    <ArrowUpRight
                      size={13}
                      className="text-slate-600 group-hover:text-cyan-400 transition-colors shrink-0"
                    />
                  </div>
                  <p className="text-xs text-slate-300 leading-relaxed pl-8 break-words font-normal">
                    "{starter.query}"
                  </p>
                </button>
              );
            })}
          </div>
        </div>
      )}

      <div className="space-y-6">
        {exchanges.map((exchange) => {
          const final = exchange.final;
          const isProcessing = exchange.status === 'PROCESSING';
          const isFailedOrCancelled =
            exchange.status === 'FAILED' || exchange.status === 'CANCELLED';

          return (
            <article key={exchange.id} className="space-y-4">
              {/* User question bubble */}
              <div className="flex justify-end items-start gap-2.5">
                <div className="max-w-[85%] rounded-2xl bg-gradient-to-r from-slate-900/90 to-indigo-950/60 border border-indigo-900/40 px-4 py-3 text-sm text-slate-100 shadow-lg">
                  <div className="flex items-center justify-between gap-3 mb-1">
                    <span className="text-[11px] font-bold text-indigo-400 uppercase tracking-wide">
                      Bạn
                    </span>
                    {exchange.symbol && (
                      <span className="rounded bg-indigo-900/70 border border-indigo-700/60 px-1.5 py-0.5 text-[11px] font-mono font-bold text-indigo-300">
                        #{exchange.symbol}
                      </span>
                    )}
                  </div>
                  <p className="whitespace-pre-wrap leading-relaxed">{exchange.question}</p>
                </div>
                <div className="w-7 h-7 rounded-lg bg-slate-800 border border-slate-700 flex items-center justify-center text-slate-300 shrink-0 mt-1">
                  <User size={14} />
                </div>
              </div>

              {/* Assistant response bubble */}
              <div className="flex justify-start items-start gap-2.5">
                <div className="w-7 h-7 rounded-lg bg-gradient-to-br from-indigo-600 to-cyan-600 flex items-center justify-center text-white shrink-0 mt-1 shadow-md shadow-indigo-600/30">
                  <Bot size={15} />
                </div>
                <div className="max-w-[92%] flex-1 rounded-2xl border border-slate-800 bg-slate-950/70 backdrop-blur-sm p-4 text-sm text-slate-200 shadow-xl">
                  {/* Status header */}
                  <div className="flex items-center justify-between pb-2 mb-3 border-b border-slate-800/80">
                    <span className="text-[11px] font-mono uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
                      {isProcessing && <span className="w-1.5 h-1.5 rounded-full bg-amber-400 animate-pulse" />}
                      {stateLabel[exchange.status]}
                    </span>
                    <span className="text-[10px] font-mono text-slate-500">
                      FINVERA HYBRID RAG
                    </span>
                  </div>

                  {/* Streaming output */}
                  {isProcessing && exchange.id === streamedExchangeId && streamedText && (
                    <div className="prose prose-invert max-w-none text-xs sm:text-sm leading-relaxed text-slate-200">
                      <LiteMarkdown text={streamedText} />
                    </div>
                  )}

                  {/* Final answer */}
                  {final && (
                    <>
                      {final.answer?.trim() ? (
                        <div className="prose prose-invert max-w-none text-xs sm:text-sm leading-relaxed text-slate-200">
                          <LiteMarkdown text={final.answer} />
                        </div>
                      ) : (
                        <div className="my-2 p-3 rounded-xl bg-amber-950/30 border border-amber-800/40 text-amber-300 text-xs flex items-center gap-2">
                          <AlertCircle size={14} className="shrink-0 text-amber-400" />
                          <p className="text-xs text-amber-200">
                            Hệ thống không nhận được nội dung diễn giải từ mô hình cho yêu cầu này. Quý nhà đầu tư có thể xem các số liệu đã đối soát bên dưới hoặc gửi lại câu hỏi.
                          </p>
                        </div>
                      )}

                      {/* Structured grounded claims */}
                      {final.structuredClaims.length > 0 && (
                        <CollapsibleClaims claims={final.structuredClaims} />
                      )}

                      {/* Document citation claims */}
                      {final.documentClaims.length > 0 && (
                        <div className="mt-3 rounded-xl border border-blue-900/30 bg-blue-950/20 p-3.5">
                          <p className="text-xs font-bold text-blue-400 flex items-center gap-1.5 mb-2">
                            <FileText size={14} />
                            <span>Nguồn tài liệu</span>
                          </p>
                          <div className="space-y-1.5">
                            {final.documentClaims.map((claim, index) => (
                              <p
                                className="text-xs text-slate-300 bg-slate-950/50 rounded-lg p-2 border border-blue-900/20"
                                key={`${claim.sourceId}-${index}`}
                              >
                                {claim.sourceTitle} · {claim.location} · {claim.source}
                              </p>
                            ))}
                          </div>
                        </div>
                      )}

                      <p className="mt-3 text-[11px] text-slate-500 font-mono">
                        Tạo lúc {vietnameseTime(exchange.completedAt || exchange.createdAt)}
                      </p>
                    </>
                  )}

                  {/* Failure / Cancelled */}
                  {isFailedOrCancelled && (
                    <div className="mt-2 p-3 rounded-xl bg-rose-950/30 border border-rose-800/40 text-rose-300 text-xs flex items-center gap-2">
                      <AlertCircle size={14} className="shrink-0" />
                      <p className="text-sm text-rose-300">
                        {exchange.failureCode || 'Yêu cầu không hoàn thành. Bạn có thể gửi một câu hỏi mới.'}
                      </p>
                    </div>
                  )}
                </div>
              </div>
            </article>
          );
        })}
      </div>
    </div>
  </section>
);
