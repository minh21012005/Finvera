import React, { useState } from 'react';
import type { ConversationSummary } from '../api/analyst';
import { Plus, Pencil, Trash2, Check, MessageSquare, Clock } from 'lucide-react';

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

export const ConversationSidebar: React.FC<Props> = ({
  conversations,
  selectedId,
  loading,
  hasMore,
  onNew,
  onSelect,
  onLoadMore,
  onRename,
  onDelete,
}) => {
  const [editingId, setEditingId] = useState<string>();
  const [title, setTitle] = useState('');

  return (
    <aside
      aria-label="Lịch sử hội thoại"
      className="w-full lg:w-80 shrink-0 rounded-2xl border border-slate-800 bg-slate-950/80 backdrop-blur-md p-4 flex flex-col shadow-2xl"
    >
      <div className="mb-4">
        <button
          id="new-conversation-button"
          type="button"
          onClick={onNew}
          className="w-full rounded-xl bg-gradient-to-r from-indigo-600 via-indigo-500 to-cyan-600 hover:from-indigo-500 hover:to-cyan-500 px-4 py-2.5 text-sm font-bold text-white shadow-lg shadow-indigo-600/20 hover:shadow-indigo-600/30 transition-all cursor-pointer flex items-center justify-center gap-2 select-none"
        >
          <Plus size={16} className="stroke-[2.5]" />
          <span>+ Cuộc trò chuyện mới</span>
        </button>
      </div>

      <div className="flex items-center justify-between px-1 mb-2">
        <span className="text-[11px] font-bold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
          <MessageSquare size={13} className="text-indigo-400" />
          <span>Lịch sử nghiên cứu</span>
        </span>
        <span className="text-[11px] font-mono text-slate-500">{conversations.length}</span>
      </div>

      <div className="space-y-2 flex-1 overflow-y-auto pr-1" role="list">
        {loading && conversations.length === 0 && (
          <div className="px-3 py-6 text-center text-xs text-slate-400">
            <p>Đang tải lịch sử…</p>
          </div>
        )}
        {!loading && conversations.length === 0 && (
          <div className="px-3 py-8 text-center text-xs text-slate-500 rounded-xl border border-dashed border-slate-800/80 bg-slate-900/30">
            <p>Chưa có cuộc trò chuyện.</p>
          </div>
        )}
        {conversations.map((item) => {
          const isSelected = selectedId === item.id;
          return (
            <div
              key={item.id}
              role="listitem"
              className={`group rounded-xl border p-3 transition-all ${
                isSelected
                  ? 'border-indigo-500/80 bg-indigo-950/30 shadow-md shadow-indigo-950/50'
                  : 'border-slate-800/80 bg-slate-900/50 hover:border-slate-700 hover:bg-slate-900/80'
              }`}
            >
              {editingId === item.id ? (
                <form
                  onSubmit={(event) => {
                    event.preventDefault();
                    void onRename(item.id, title)
                      .then(() => setEditingId(undefined))
                      .catch(() => undefined);
                  }}
                  className="space-y-2"
                >
                  <label className="sr-only" htmlFor={`title-${item.id}`}>
                    Tên cuộc trò chuyện
                  </label>
                  <input
                    id={`title-${item.id}`}
                    autoFocus
                    maxLength={120}
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    className="w-full rounded-lg border border-indigo-500/80 bg-slate-950 px-3 py-1.5 text-xs text-white outline-none focus:ring-1 focus:ring-indigo-500 font-medium"
                  />
                  <div className="flex items-center justify-end gap-2">
                    <button
                      type="button"
                      onClick={() => setEditingId(undefined)}
                      className="rounded px-2 py-1 text-xs text-slate-400 hover:bg-slate-800 hover:text-white transition-colors"
                    >
                      Hủy
                    </button>
                    <button
                      type="submit"
                      className="rounded bg-indigo-600 hover:bg-indigo-500 px-3 py-1 text-xs font-semibold text-white transition-colors flex items-center gap-1"
                    >
                      <Check size={12} />
                      <span>Lưu</span>
                    </button>
                  </div>
                </form>
              ) : (
                <>
                  <button
                    type="button"
                    onClick={() => onSelect(item.id)}
                    className="w-full text-left cursor-pointer"
                  >
                    <span className="block truncate text-xs font-semibold text-slate-100 group-hover:text-indigo-300 transition-colors">
                      {item.title}
                    </span>
                    <span className="mt-1 flex items-center gap-1.5 text-[11px] text-slate-400">
                      <Clock size={11} className="text-slate-500 shrink-0" />
                      <span>
                        {item.exchangeCount} lượt ·{' '}
                        {new Date(item.lastActivityAt).toLocaleString('vi-VN', {
                          timeZone: 'Asia/Ho_Chi_Minh',
                        })}
                      </span>
                    </span>
                    {item.processing && (
                      <span className="mt-1.5 inline-flex items-center gap-1 text-[11px] font-medium text-amber-300">
                        <span className="w-1.5 h-1.5 rounded-full bg-amber-400 animate-pulse" />
                        Đang xử lý
                      </span>
                    )}
                  </button>

                  <div className="mt-2.5 flex items-center justify-between border-t border-slate-800/60 pt-2 text-[11px]">
                    <button
                      type="button"
                      className="text-slate-400 hover:text-indigo-300 transition-colors cursor-pointer flex items-center gap-1"
                      onClick={() => {
                        setEditingId(item.id);
                        setTitle(item.title);
                      }}
                    >
                      <Pencil size={11} />
                      <span>Đổi tên</span>
                    </button>
                    <button
                      type="button"
                      className="text-rose-400/80 hover:text-rose-300 transition-colors cursor-pointer flex items-center gap-1"
                      onClick={() => {
                        if (window.confirm('Xóa toàn bộ cuộc trò chuyện này?')) {
                          void onDelete(item.id).catch(() => undefined);
                        }
                      }}
                    >
                      <Trash2 size={11} />
                      <span>Xóa</span>
                    </button>
                  </div>
                </>
              )}
            </div>
          );
        })}
      </div>

      {hasMore && (
        <button
          type="button"
          disabled={loading}
          onClick={onLoadMore}
          className="mt-3 w-full rounded-xl border border-slate-800 bg-slate-900/50 hover:bg-slate-900 hover:border-slate-700 py-2 text-xs font-semibold text-slate-300 hover:text-white transition-all cursor-pointer disabled:opacity-50"
        >
          Tải thêm
        </button>
      )}
    </aside>
  );
};
