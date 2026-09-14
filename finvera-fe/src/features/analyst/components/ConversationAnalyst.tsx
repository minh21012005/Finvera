import React, { useCallback, useEffect, useRef, useState } from 'react';
import { ArrowUp, Wrench } from 'lucide-react';
import { ConversationSidebar } from './ConversationSidebar';
import { ConversationTranscript } from './ConversationTranscript';
import { SupportedToolsModal } from './SupportedToolsModal';
import {
  deleteConversation, getConversationExchanges, listConversations, renameConversation,
  streamConversationAsk, type ConversationExchange, type ConversationSummary,
} from '../api/analyst';

const newRequestId = () => globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`;

export const ConversationAnalyst: React.FC = () => {
  const [conversations, setConversations] = useState<ConversationSummary[]>([]);
  const [selectedId, setSelectedId] = useState<string>();
  const [exchanges, setExchanges] = useState<ConversationExchange[]>([]);
  const [listCursor, setListCursor] = useState<string>();
  const [olderCursor, setOlderCursor] = useState<string>();
  const [listHasMore, setListHasMore] = useState(false);
  const [olderHasMore, setOlderHasMore] = useState(false);
  const [question, setQuestion] = useState('');
  const [symbol, setSymbol] = useState('');
  const [streamedText, setStreamedText] = useState('');
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string>();
  const [toolsModalOpen, setToolsModalOpen] = useState(false);
  const abortRef = useRef<AbortController | undefined>(undefined);
  const activeExchangeIdRef = useRef<string | undefined>(undefined);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const [streamedExchangeId, setStreamedExchangeId] = useState<string>();

  const loadList = useCallback(async (append = false) => {
    setLoading(true);
    try {
      const page = await listConversations(append ? listCursor : undefined);
      setConversations((old) => append ? [...old, ...page.items.filter((x) => !old.some((o) => o.id === x.id))] : page.items);
      setListCursor(page.nextCursor || undefined); setListHasMore(page.hasMore);
    } catch (e) { setError(e instanceof Error ? e.message : 'Không thể tải lịch sử'); }
    finally { setLoading(false); }
  }, [listCursor]);

  useEffect(() => {
    let active = true;
    void listConversations().then((page) => {
      if (!active) return;
      setConversations(page.items);
      setListCursor(page.nextCursor || undefined);
      setListHasMore(page.hasMore);
    }).catch((e: unknown) => {
      if (active) setError(e instanceof Error ? e.message : 'Không thể tải lịch sử');
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, []);

  const selectConversation = async (id: string) => {
    setSelectedId(id); setLoading(true); setError(undefined); setStreamedText('');
    try {
      const page = await getConversationExchanges(id);
      setExchanges(page.items); setOlderCursor(page.olderCursor || undefined); setOlderHasMore(page.hasMore);
    } catch (e) { setError(e instanceof Error ? e.message : 'Không thể mở hội thoại'); }
    finally { setLoading(false); }
  };

  const loadOlder = async () => {
    if (!selectedId || !olderCursor) return;
    setLoading(true); setError(undefined);
    try {
      const page = await getConversationExchanges(selectedId, olderCursor);
      setExchanges((old) => [...page.items.filter((x) => !old.some((o) => o.id === x.id)), ...old]);
      setOlderCursor(page.olderCursor || undefined); setOlderHasMore(page.hasMore);
    } catch (e) { setError(e instanceof Error ? e.message : 'Không thể tải các lượt cũ hơn'); }
    finally { setLoading(false); }
  };

  const send = async () => {
    const q = question.trim(); if (!q || sending) return;
    setSending(true); setQuestion(''); setError(undefined); setStreamedText('');
    const tempId = `pending-${newRequestId()}`;
    activeExchangeIdRef.current = tempId;
    setStreamedExchangeId(tempId);
    const pending: ConversationExchange = { id: tempId, sequenceNo: exchanges.length + 1, question: q,
      symbol: symbol.trim().toUpperCase() || undefined, status: 'PROCESSING', final: null,
      contextWindow: { ruleVersion: 'context-window-v1', includedExchanges: 0, omittedExchanges: 0 },
      createdAt: new Date().toISOString() };
    setExchanges((old) => [...old, pending]);
    const controller = new AbortController(); abortRef.current = controller;
    try {
      await streamConversationAsk({ conversationId: selectedId, clientRequestId: newRequestId(), question: q,
        symbol: symbol.trim().toUpperCase() || undefined }, {
        onAccepted: (event) => {
          activeExchangeIdRef.current = event.exchangeId;
          setStreamedExchangeId(event.exchangeId);
          setSelectedId(event.conversationId);
          setExchanges((old) => old.map((x) => x.id === tempId ? { ...x, id: event.exchangeId, contextWindow: event.contextWindow } : x));
        },
        onDelta: (delta) => setStreamedText((old) => old + delta),
        onFinal: (finalResult) => {
          const targetId = activeExchangeIdRef.current;
          setExchanges((old) => old.map((x) => x.id === targetId
            ? { ...x, status: 'COMPLETED', final: finalResult, completedAt: new Date().toISOString() } : x));
        },
        onTerminalError: (reasonCode) => {
          const targetId = activeExchangeIdRef.current;
          setExchanges((old) => old.map((x) => x.id === targetId
            ? { ...x, status: 'FAILED', failureCode: reasonCode } : x));
        },
        onError: (e) => {
          const targetId = activeExchangeIdRef.current;
          setExchanges((old) => old.map((x) => x.id === targetId && x.status === 'PROCESSING'
            ? { ...x, status: 'FAILED', failureCode: 'STREAM_INTERRUPTED' } : x));
          setError(e.message);
        },
      }, controller.signal);
      await loadList(false);
    } catch (e) {
      if (!(e instanceof Error && e.name === 'AbortError')) {
        const targetId = activeExchangeIdRef.current;
        setExchanges((old) => old.map((x) => x.id === targetId && x.status === 'PROCESSING'
          ? { ...x, status: 'FAILED', failureCode: 'STREAM_INTERRUPTED' } : x));
        setError(e instanceof Error ? e.message : 'Không thể gửi câu hỏi');
      }
    } finally {
      setSending(false); setStreamedText(''); setStreamedExchangeId(undefined);
      activeExchangeIdRef.current = undefined; abortRef.current = undefined;
    }
  };

  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
      const scrollHeight = textareaRef.current.scrollHeight;
      textareaRef.current.style.height = `${Math.min(Math.max(scrollHeight, 44), 180)}px`;
    }
  }, [question]);

  const handleSelectPrompt = (q: string, sym?: string) => {
    setQuestion(q);
    if (sym) setSymbol(sym);
    if (textareaRef.current) {
      textareaRef.current.focus();
    }
  };

  return (
    <>
      <div className="flex flex-col gap-4 lg:flex-row">
        <ConversationSidebar
          conversations={conversations}
          selectedId={selectedId}
          loading={loading}
          hasMore={listHasMore}
          onNew={() => {
            setSelectedId(undefined);
            setExchanges([]);
            setError(undefined);
          }}
          onSelect={(id) => void selectConversation(id)}
          onLoadMore={() => void loadList(true)}
          onRename={async (id, title) => {
            try {
              const updated = await renameConversation(id, title);
              setConversations((old) => old.map((x) => (x.id === id ? updated : x)));
            } catch (e) {
              setError(e instanceof Error ? e.message : 'Không thể đổi tên');
              throw e;
            }
          }}
          onDelete={async (id) => {
            try {
              await deleteConversation(id);
              setConversations((old) => old.filter((x) => x.id !== id));
              if (selectedId === id) {
                setSelectedId(undefined);
                setExchanges([]);
              }
              globalThis.document?.getElementById('new-conversation-button')?.focus();
            } catch (e) {
              setError(e instanceof Error ? e.message : 'Không thể xóa');
              throw e;
            }
          }}
        />
        <div className="min-w-0 flex-1 space-y-4">
          {error && (
            <div
              role="alert"
              className="rounded-xl border border-rose-800/60 bg-rose-950/40 p-3.5 text-xs text-rose-300 shadow-lg flex items-center gap-2"
            >
              <span>{error}</span>
            </div>
          )}

          <ConversationTranscript
            exchanges={exchanges}
            streamedText={streamedText}
            streamedExchangeId={streamedExchangeId}
            loading={loading}
            hasOlder={olderHasMore}
            onLoadOlder={() => void loadOlder()}
            onOpenTools={() => setToolsModalOpen(true)}
            onSelectQuery={handleSelectPrompt}
          />

          {/* Chatbot Input Container (ChatGPT / Gemini style) */}
          <div className="rounded-2xl border border-slate-800 bg-[#070e1a]/95 backdrop-blur-xl p-3 shadow-2xl transition-all focus-within:border-indigo-500/80 focus-within:ring-1 focus-within:ring-indigo-500/40">
            {/* Multiline auto-expanding textarea */}
            <div className="relative">
              <label className="sr-only" htmlFor="conversation-question">
                Câu hỏi
              </label>
              <textarea
                id="conversation-question"
                ref={textareaRef}
                rows={1}
                maxLength={2000}
                value={question}
                onChange={(e) => setQuestion(e.target.value)}
                disabled={sending}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    void send();
                  }
                }}
                placeholder="Hỏi AI Analyst…"
                className="w-full min-h-[44px] max-h-[180px] rounded-xl bg-transparent px-3 py-2 text-xs sm:text-sm text-slate-100 placeholder:text-slate-500 outline-none resize-none leading-relaxed overflow-y-auto"
              />
            </div>

            {/* Bottom Toolbar inside the input card */}
            <div className="flex items-center justify-between gap-2 pt-2 border-t border-slate-800/80 mt-1">
              {/* Left tools and symbol input */}
              <div className="flex items-center gap-2 flex-wrap">
                <button
                  type="button"
                  onClick={() => setToolsModalOpen(true)}
                  className="inline-flex items-center gap-1.5 rounded-lg border border-slate-800 bg-slate-900/90 hover:bg-slate-800 hover:border-slate-700 px-2.5 py-1 text-xs text-slate-300 hover:text-cyan-300 transition-all cursor-pointer shadow-sm"
                  title="Xem 11 công cụ hệ thống hỗ trợ"
                >
                  <Wrench size={12} className="text-cyan-400" />
                  <span className="font-semibold text-[11px]">11 Tools</span>
                </button>

                <div className="relative inline-flex items-center rounded-lg border border-slate-800 bg-slate-900/90 px-2 py-0.5 hover:border-slate-700 transition-colors">
                  <span
                    className="text-xs font-mono font-bold text-cyan-400 select-none mr-1"
                    aria-hidden="true"
                  >
                    #
                  </span>
                  <label htmlFor="conversation-symbol" className="sr-only">
                    Mã cổ phiếu
                  </label>
                  <input
                    id="conversation-symbol"
                    maxLength={20}
                    value={symbol}
                    onChange={(e) => setSymbol(e.target.value.toUpperCase())}
                    disabled={sending}
                    placeholder="FPT…"
                    style={{ border: 'none', outline: 'none', background: 'transparent', boxShadow: 'none', padding: 0 }}
                    className="w-14 bg-transparent border-0 outline-none p-0 text-xs font-mono font-bold text-cyan-300 placeholder:text-slate-600 uppercase"
                  />
                  {symbol && (
                    <button
                      type="button"
                      onClick={() => setSymbol('')}
                      className="text-slate-500 hover:text-slate-300 cursor-pointer text-xs ml-1 leading-none"
                      aria-label="Xóa mã cổ phiếu"
                    >
                      ✕
                    </button>
                  )}
                </div>
              </div>

              {/* Right actions */}
              <div className="flex items-center gap-2.5">
                <span className="hidden sm:inline text-[10px] text-slate-500 select-none">
                  Shift + Enter ↵ xuống dòng
                </span>

                {sending ? (
                  <button
                    type="button"
                    onClick={() => {
                      const targetId = activeExchangeIdRef.current;
                      abortRef.current?.abort();
                      setExchanges((old) =>
                        old.map((x) =>
                          x.id === targetId && x.status === 'PROCESSING'
                            ? { ...x, status: 'CANCELLED', failureCode: 'CLIENT_CANCELLED' }
                            : x
                        )
                      );
                      setSending(false);
                    }}
                    className="inline-flex items-center gap-1 rounded-xl bg-rose-600 hover:bg-rose-500 px-4 py-1.5 text-xs font-bold text-white transition-all cursor-pointer shadow-lg shadow-rose-600/20"
                  >
                    <span>Dừng</span>
                  </button>
                ) : (
                  <button
                    type="button"
                    disabled={!question.trim()}
                    onClick={() => void send()}
                    className="inline-flex items-center gap-1.5 rounded-xl bg-gradient-to-r from-indigo-600 to-cyan-600 hover:from-indigo-500 hover:to-cyan-500 px-4 py-1.5 text-xs font-bold text-white shadow-lg shadow-indigo-600/20 disabled:opacity-40 transition-all cursor-pointer"
                  >
                    <span>Gửi</span>
                    <ArrowUp size={13} />
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>

      <SupportedToolsModal
        isOpen={toolsModalOpen}
        onClose={() => setToolsModalOpen(false)}
        onSelectQuery={handleSelectPrompt}
      />
    </>
  );
};
