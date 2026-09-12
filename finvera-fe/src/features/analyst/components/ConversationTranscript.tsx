import React from 'react';
import type { ConversationExchange } from '../api/analyst';
import { LiteMarkdown } from '../format/lite-markdown';

interface Props { exchanges: ConversationExchange[]; streamedText: string; streamedExchangeId?: string; loading: boolean; hasOlder: boolean; onLoadOlder: () => void; }

const vietnameseTime = (value: string) => new Date(value).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' });

const stateLabel: Record<ConversationExchange['status'], string> = {
  PROCESSING: 'Đang xử lý', COMPLETED: 'Đã hoàn thành', FAILED: 'Không thể hoàn thành', CANCELLED: 'Đã dừng',
};

export const ConversationTranscript: React.FC<Props> = ({ exchanges, streamedText, streamedExchangeId, loading, hasOlder, onLoadOlder }) => (
  <section aria-label="Nội dung hội thoại" aria-live="polite" className="min-h-[480px] flex-1 overflow-y-auto rounded-xl border border-slate-800 bg-slate-900/60 p-4">
    {hasOlder && <button type="button" disabled={loading} onClick={onLoadOlder} className="mx-auto mb-4 block text-xs text-slate-300">Tải các lượt cũ hơn</button>}
    {loading && exchanges.length === 0 && <p className="text-sm text-slate-400">Đang tải nội dung…</p>}
    {!loading && exchanges.length === 0 && <div className="grid min-h-[360px] place-items-center text-center text-sm text-slate-400"><p>Hãy đặt câu hỏi đầu tiên để bắt đầu một nghiên cứu mới.</p></div>}
    <div className="space-y-5">
      {exchanges.map((exchange) => {
        const final = exchange.final;
        return <article key={exchange.id} className="space-y-3">
          <div className="ml-auto max-w-[85%] rounded-xl bg-emerald-700/30 px-4 py-3 text-sm text-slate-100">
            {exchange.question}
            {exchange.symbol && <span className="ml-2 text-xs text-emerald-300">#{exchange.symbol}</span>}
          </div>
          <div className="max-w-[92%] rounded-xl border border-slate-700 bg-slate-950/70 px-4 py-3 text-sm text-slate-200">
            <p className="mb-2 text-[11px] uppercase tracking-wide text-slate-400">{stateLabel[exchange.status]}</p>
            {exchange.status === 'PROCESSING' && exchange.id === streamedExchangeId && streamedText && <LiteMarkdown text={streamedText} />}
            {final && <>
              <LiteMarkdown text={final.answer} />
              {final.structuredClaims.length > 0 && <div className="mt-3 border-t border-slate-800 pt-2">
                <p className="text-xs font-semibold text-emerald-300">Số liệu đã kiểm chứng</p>
                {final.structuredClaims.map((claim, index) => <p className="mt-1 text-xs text-slate-300" key={`${claim.sequenceNo}-${index}`}>{claim.claimText} · {claim.sourceField} · {vietnameseTime(claim.asOf)}</p>)}
              </div>}
              {final.documentClaims.length > 0 && <div className="mt-3 border-t border-slate-800 pt-2">
                <p className="text-xs font-semibold text-blue-300">Nguồn tài liệu</p>
                {final.documentClaims.map((claim, index) => <p className="mt-1 text-xs text-slate-300" key={`${claim.sourceId}-${index}`}>{claim.sourceTitle} · {claim.location} · {claim.source}</p>)}
              </div>}
              <p className="mt-3 text-[11px] text-slate-500">Tạo lúc {vietnameseTime(exchange.completedAt || exchange.createdAt)}</p>
            </>}
            {(exchange.status === 'FAILED' || exchange.status === 'CANCELLED') && <p className="text-sm text-rose-300">{exchange.failureCode || 'Yêu cầu không hoàn thành. Bạn có thể gửi một câu hỏi mới.'}</p>}
          </div>
        </article>;
      })}
    </div>
  </section>
);
