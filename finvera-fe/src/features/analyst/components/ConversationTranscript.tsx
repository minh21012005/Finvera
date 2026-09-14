import React from 'react';
import type { ConversationExchange } from '../api/analyst';
import { LiteMarkdown } from '../format/lite-markdown';
import { Bot, User, Sparkles, ShieldCheck, FileText, AlertCircle } from 'lucide-react';

interface Props {
  exchanges: ConversationExchange[];
  streamedText: string;
  streamedExchangeId?: string;
  loading: boolean;
  hasOlder: boolean;
  onLoadOlder: () => void;
}

const vietnameseTime = (value: string) =>
  new Date(value).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' });

const stateLabel: Record<ConversationExchange['status'], string> = {
  PROCESSING: 'Đang xử lý',
  COMPLETED: 'Đã hoàn thành',
  FAILED: 'Không thể hoàn thành',
  CANCELLED: 'Đã dừng',
};

export const ConversationTranscript: React.FC<Props> = ({
  exchanges,
  streamedText,
  streamedExchangeId,
  loading,
  hasOlder,
  onLoadOlder,
}) => (
  <section
    aria-label="Nội dung hội thoại"
    aria-live="polite"
    className="min-h-[540px] flex-1 overflow-y-auto rounded-2xl border border-slate-800 bg-[#060b13]/90 backdrop-blur-md p-5 shadow-2xl flex flex-col justify-between"
  >
    <div className="space-y-6 flex-1">
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
        <div className="grid min-h-[380px] place-items-center text-center p-8">
          <div className="max-w-md space-y-3">
            <div className="w-12 h-12 rounded-2xl bg-indigo-950/60 border border-indigo-800/60 flex items-center justify-center text-indigo-400 mx-auto shadow-inner">
              <Sparkles size={22} />
            </div>
            <h3 className="text-base font-bold text-slate-100">Bắt đầu nghiên cứu cùng AI Copilot</h3>
            <p className="text-xs text-slate-400 leading-relaxed">
              Hãy đặt câu hỏi đầu tiên để bắt đầu một nghiên cứu mới.
            </p>
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
                      <div className="prose prose-invert max-w-none text-xs sm:text-sm leading-relaxed text-slate-200">
                        <LiteMarkdown text={final.answer} />
                      </div>

                      {/* Structured grounded claims */}
                      {final.structuredClaims.length > 0 && (
                        <div className="mt-4 rounded-xl border border-emerald-900/30 bg-emerald-950/20 p-3.5">
                          <p className="text-xs font-bold text-emerald-400 flex items-center gap-1.5 mb-2">
                            <ShieldCheck size={14} />
                            <span>Số liệu đã kiểm chứng</span>
                          </p>
                          <div className="space-y-1.5">
                            {final.structuredClaims.map((claim, index) => (
                              <p
                                className="text-xs text-slate-300 font-mono bg-slate-950/50 rounded-lg p-2 border border-emerald-900/20"
                                key={`${claim.sequenceNo}-${index}`}
                              >
                                {claim.claimText} · {claim.sourceField} · {vietnameseTime(claim.asOf)}
                              </p>
                            ))}
                          </div>
                        </div>
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
