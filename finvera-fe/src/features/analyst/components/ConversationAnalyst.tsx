import React, { useCallback, useEffect, useRef, useState } from 'react';
import { ConversationSidebar } from './ConversationSidebar';
import { ConversationTranscript } from './ConversationTranscript';
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
  const abortRef = useRef<AbortController | undefined>(undefined);
  const activeExchangeIdRef = useRef<string | undefined>(undefined);
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

  return <div className="flex flex-col gap-4 lg:flex-row">
    <ConversationSidebar conversations={conversations} selectedId={selectedId} loading={loading} hasMore={listHasMore}
      onNew={() => { setSelectedId(undefined); setExchanges([]); setError(undefined); }} onSelect={(id) => void selectConversation(id)}
      onLoadMore={() => void loadList(true)} onRename={async (id, title) => {
        try { const updated=await renameConversation(id, title); setConversations((old) => old.map((x) => x.id===id ? updated : x)); }
        catch (e) { setError(e instanceof Error ? e.message : 'Không thể đổi tên'); throw e; }
      }}
      onDelete={async (id) => {
        try {
          await deleteConversation(id); setConversations((old) => old.filter((x) => x.id!==id));
          if (selectedId===id) { setSelectedId(undefined); setExchanges([]); }
          globalThis.document?.getElementById('new-conversation-button')?.focus();
        } catch (e) { setError(e instanceof Error ? e.message : 'Không thể xóa'); throw e; }
      }} />
    <div className="min-w-0 flex-1 space-y-3">
      {error && <div role="alert" className="rounded-lg border border-rose-700 bg-rose-950/30 p-3 text-sm text-rose-300">{error}</div>}
      <ConversationTranscript exchanges={exchanges} streamedText={streamedText} streamedExchangeId={streamedExchangeId}
        loading={loading} hasOlder={olderHasMore} onLoadOlder={() => void loadOlder()} />
      <div className="flex flex-col gap-2 sm:flex-row">
        <label className="sr-only" htmlFor="conversation-symbol">Mã cổ phiếu</label>
        <input id="conversation-symbol" maxLength={20} value={symbol} onChange={(e) => setSymbol(e.target.value.toUpperCase())} disabled={sending}
          placeholder="Mã (FPT…)" className="rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-white sm:w-28" />
        <label className="sr-only" htmlFor="conversation-question">Câu hỏi</label>
        <input id="conversation-question" maxLength={2000} value={question} onChange={(e) => setQuestion(e.target.value)} disabled={sending}
          onKeyDown={(e) => { if (e.key==='Enter') { e.preventDefault(); void send(); } }} placeholder="Hỏi AI Analyst…"
          className="min-w-0 flex-1 rounded-lg border border-slate-700 bg-slate-900 px-4 py-2 text-sm text-white" />
        {sending ? <button type="button" onClick={() => { const targetId=activeExchangeIdRef.current; abortRef.current?.abort(); setExchanges((old) => old.map((x) => x.id===targetId && x.status==='PROCESSING' ? { ...x, status:'CANCELLED', failureCode:'CLIENT_CANCELLED' } : x)); setSending(false); }}
          className="rounded-lg bg-rose-600 px-4 py-2 text-sm text-white">Dừng</button>
          : <button type="button" disabled={!question.trim()} onClick={() => void send()} className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">Gửi</button>}
      </div>
    </div>
  </div>;
};
