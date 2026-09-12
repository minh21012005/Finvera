import React, { useState } from 'react';
import type { ConversationSummary } from '../api/analyst';

interface Props {
  conversations: ConversationSummary[];
  selectedId?: string;
  loading: boolean;
  hasMore: boolean;
  onNew: () => void;
  onSelect: (id: string) => void;
  onLoadMore: () => void;
  onRename: (id: string, title: string) => Promise<void>;
  onDelete: (id: string) => Promise<void>;
}

export const ConversationSidebar: React.FC<Props> = ({ conversations, selectedId, loading, hasMore,
  onNew, onSelect, onLoadMore, onRename, onDelete }) => {
  const [editingId, setEditingId] = useState<string>();
  const [title, setTitle] = useState('');

  return (
    <aside aria-label="Lịch sử hội thoại" className="w-full lg:w-72 shrink-0 rounded-xl border border-slate-800 bg-slate-950/60 p-3">
      <button id="new-conversation-button" type="button" onClick={onNew} className="w-full rounded-lg bg-emerald-600 px-3 py-2 text-sm font-semibold text-white hover:bg-emerald-500">
        + Cuộc trò chuyện mới
      </button>
      <div className="mt-3 space-y-2" role="list">
        {loading && conversations.length === 0 && <p className="px-2 text-xs text-slate-400">Đang tải lịch sử…</p>}
        {!loading && conversations.length === 0 && <p className="px-2 text-xs text-slate-400">Chưa có cuộc trò chuyện.</p>}
        {conversations.map((item) => (
          <div key={item.id} role="listitem" className={`rounded-lg border p-2 ${selectedId === item.id ? 'border-emerald-500 bg-emerald-950/30' : 'border-slate-800 bg-slate-900/70'}`}>
            {editingId === item.id ? (
              <form onSubmit={(event) => { event.preventDefault(); void onRename(item.id, title).then(() => setEditingId(undefined)).catch(() => undefined); }}>
                <label className="sr-only" htmlFor={`title-${item.id}`}>Tên cuộc trò chuyện</label>
                <input id={`title-${item.id}`} autoFocus maxLength={120} value={title} onChange={(e) => setTitle(e.target.value)}
                  className="w-full rounded border border-slate-600 bg-slate-950 px-2 py-1 text-sm text-white" />
                <div className="mt-2 flex gap-2">
                  <button className="text-xs text-emerald-300" type="submit">Lưu</button>
                  <button className="text-xs text-slate-400" type="button" onClick={() => setEditingId(undefined)}>Hủy</button>
                </div>
              </form>
            ) : (
              <>
                <button type="button" onClick={() => onSelect(item.id)} className="w-full text-left">
                  <span className="block truncate text-sm font-medium text-slate-100">{item.title}</span>
                  <span className="text-[11px] text-slate-400">{item.exchangeCount} lượt · {new Date(item.lastActivityAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}</span>
                  {item.processing && <span className="mt-1 block text-[11px] text-amber-300">Đang xử lý</span>}
                </button>
                <div className="mt-1 flex gap-3">
                  <button type="button" className="text-[11px] text-slate-400 hover:text-white" onClick={() => { setEditingId(item.id); setTitle(item.title); }}>Đổi tên</button>
                  <button type="button" className="text-[11px] text-rose-400 hover:text-rose-300" onClick={() => {
                    if (window.confirm('Xóa toàn bộ cuộc trò chuyện này?')) void onDelete(item.id).catch(() => undefined);
                  }}>Xóa</button>
                </div>
              </>
            )}
          </div>
        ))}
      </div>
      {hasMore && <button type="button" disabled={loading} onClick={onLoadMore} className="mt-3 w-full text-xs text-slate-300 hover:text-white">Tải thêm</button>}
    </aside>
  );
};
