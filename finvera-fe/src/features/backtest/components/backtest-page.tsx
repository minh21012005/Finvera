import { useCallback, useEffect, useState } from "react";
import {
  deleteAllBacktests,
  deleteBacktest,
  getBacktest,
  getEquity,
  getEvents,
  getTrades,
  listBacktests,
  type EntryEvent,
  type EquityPoint,
  type RunDetail,
  type RunSummary,
  type Trade,
} from "../api/backtest";
import { BacktestCreateForm } from "./backtest-create-form";
import { BacktestEquityChart } from "./backtest-equity-chart";
import { BacktestLedger } from "./backtest-ledger";
import { BacktestResult } from "./backtest-result";
import { History, BarChart3, Trash2 } from "lucide-react";

export function BacktestPage() {
  const [runs, setRuns] = useState<RunSummary[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [detail, setDetail] = useState<RunDetail | null>(null);
  const [trades, setTrades] = useState<Trade[]>([]);
  const [equity, setEquity] = useState<EquityPoint[]>([]);
  const [events, setEvents] = useState<EntryEvent[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null);
  const [confirmDeleteAll, setConfirmDeleteAll] = useState(false);

  const handleDelete = useCallback(async (id: string) => {
    try {
      setDeletingId(id);
      await deleteBacktest(id);
      setRuns((current) => current.filter((r) => r.id !== id));
      if (selected === id) {
        setSelected(null);
        setDetail(null);
        setTrades([]);
        setEquity([]);
        setEvents([]);
      }
      setConfirmDeleteId(null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không xóa được backtest");
    } finally {
      setDeletingId(null);
    }
  }, [selected]);

  const handleDeleteAll = useCallback(async () => {
    try {
      setDeletingId("ALL");
      await deleteAllBacktests();
      setRuns([]);
      setSelected(null);
      setDetail(null);
      setTrades([]);
      setEquity([]);
      setEvents([]);
      setConfirmDeleteAll(false);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không xóa được tất cả backtest");
    } finally {
      setDeletingId(null);
    }
  }, []);

  const loadList = useCallback(async () => {
    try {
      setRuns((await listBacktests()).items);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không tải được lịch sử backtest");
    }
  }, []);

  const load = useCallback(async (id: string) => {
    try {
      const next = await getBacktest(id);
      setDetail(next);
      setRuns((current) => current.map((run) => (run.id === next.id ? next : run)));
      if (next.status === "COMPLETED") {
        const [tradePage, equityPage, eventPage] = await Promise.all([
          getTrades(id),
          getEquity(id),
          getEvents(id),
        ]);
        setTrades(tradePage.items);
        setEquity(equityPage.items);
        setEvents(eventPage.items);
      } else if (next.status === "WITHHELD" || next.status === "FAILED") {
        setTrades([]);
        setEquity([]);
        setEvents([]);
      }
      setError(null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không tải được backtest");
    }
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => void loadList(), 0);
    return () => clearTimeout(timer);
  }, [loadList]);

  useEffect(() => {
    if (!selected) return;
    const loaded = detail?.id === selected;
    const active = !loaded || detail.status === "QUEUED" || detail.status === "RUNNING";
    if (!active) return;
    const first = setTimeout(() => void load(selected), 0);
    const timer = setInterval(() => void load(selected), 2_000);
    return () => {
      clearTimeout(first);
      clearInterval(timer);
    };
  }, [selected, load, detail?.id, detail?.status]);

  return (
    <main className="app-shell backtest-page space-y-6" aria-labelledby="backtest-title">
      <header className="page-header">
        <p className="eyebrow">FINVERA · QUANTITATIVE STRATEGY ENGINE</p>
        <h1 id="backtest-title" className="text-2xl font-bold text-white">
          Backtesting chiến lược
        </h1>
        <p className="text-xs text-slate-400 mt-1">
          Mô phỏng xác định trên dữ liệu lịch sử đã chốt; đây là công cụ nghiên cứu, không bảo đảm lợi nhuận.
        </p>
      </header>

      {error && (
        <p role="alert" className="p-3.5 rounded-lg bg-rose-950/40 border border-rose-800/60 text-xs text-rose-300">
          {error}
        </p>
      )}

      {/* Panel Tạo Backtest Rộng Rãi Toàn Chiều Ngang */}
      <BacktestCreateForm
        onCreated={(run) => {
          setRuns((current) => [run, ...current]);
          setDetail(null);
          setSelected(run.id);
        }}
      />

      <div className="grid grid-cols-1 xl:grid-cols-12 gap-6 items-start">
        {/* Cột trái: Lịch sử runs (4 cols) */}
        <div className="xl:col-span-4">
          <section className="panel bg-slate-900/60 border border-slate-800 rounded-xl p-5 shadow-lg">
            <div className="flex items-center justify-between mb-3 pb-3 border-b border-slate-800">
              <h2 className="text-sm font-bold text-white uppercase tracking-wider flex items-center gap-2">
                <History size={15} className="text-cyan-400" />
                <span>Lịch sử run</span>
              </h2>
              <div className="flex items-center gap-2">
                <span className="text-xs text-slate-400 font-mono">{runs.length} lượt</span>
                {runs.length > 0 && (
                  confirmDeleteAll ? (
                    <div className="flex items-center gap-1.5 bg-rose-950/80 border border-rose-700/80 px-2 py-0.5 rounded text-[11px]">
                      <span className="text-rose-300 font-semibold">Xóa hết?</span>
                      <button
                        type="button"
                        onClick={handleDeleteAll}
                        disabled={deletingId === "ALL"}
                        className="text-rose-300 font-bold hover:underline"
                      >
                        Có
                      </button>
                      <button
                        type="button"
                        onClick={() => setConfirmDeleteAll(false)}
                        className="text-slate-400 hover:text-white"
                      >
                        Hủy
                      </button>
                    </div>
                  ) : (
                    <button
                      type="button"
                      onClick={() => setConfirmDeleteAll(true)}
                      className="text-[11px] text-slate-500 hover:text-rose-400 hover:bg-rose-500/10 px-1.5 py-0.5 rounded transition-colors"
                      title="Xóa tất cả các lượt chạy"
                    >
                      Xóa tất cả
                    </button>
                  )
                )}
              </div>
            </div>

            {runs.length === 0 ? (
              <p className="text-xs text-slate-400 p-4 rounded-lg bg-slate-950/40 border border-slate-800/60 text-center">
                Chưa có backtest.
              </p>
            ) : (
              <ul className="space-y-2 max-h-[580px] overflow-y-auto pr-1">
                {runs.map((run) => {
                  const isSelected = selected === run.id;
                  const isConfirming = confirmDeleteId === run.id;
                  const isDeleting = deletingId === run.id;

                  return (
                    <li key={run.id} className="relative group">
                      <div
                        className={`flex items-center rounded-lg border transition-all ${
                          isSelected
                            ? "border-cyan-500/80 bg-cyan-950/30 text-cyan-200 shadow-md shadow-cyan-950/50"
                            : "border-slate-800 bg-slate-950/50 text-slate-300 hover:border-slate-700 hover:bg-slate-900/80"
                        }`}
                      >
                        <button
                          type="button"
                          onClick={() => {
                            setDetail(null);
                            setSelected(run.id);
                          }}
                          aria-current={isSelected}
                          className="flex-1 min-w-0 text-left p-3 text-xs font-mono"
                        >
                          {run.symbol} · {run.strategyCode} · {run.status} · {run.processedSessions}/{run.totalSessions ?? "?"}
                        </button>

                        <div className="pr-2 flex-shrink-0">
                          {isConfirming ? (
                            <div className="flex items-center gap-1.5 bg-rose-950/90 border border-rose-700 px-1.5 py-0.5 rounded text-[11px]">
                              <button
                                type="button"
                                onClick={() => void handleDelete(run.id)}
                                disabled={isDeleting}
                                className="text-rose-300 font-bold hover:underline"
                                title="Xác nhận xóa"
                              >
                                Xóa
                              </button>
                              <button
                                type="button"
                                onClick={() => setConfirmDeleteId(null)}
                                className="text-slate-400 hover:text-white"
                                title="Hủy"
                              >
                                ✕
                              </button>
                            </div>
                          ) : (
                            <button
                              type="button"
                              onClick={(e) => {
                                e.stopPropagation();
                                setConfirmDeleteId(run.id);
                              }}
                              disabled={isDeleting}
                              aria-label="Xóa lượt chạy"
                              title="Xóa lượt chạy này"
                              className="p-1.5 rounded text-slate-500 hover:text-rose-400 hover:bg-rose-500/10 transition-colors opacity-70 group-hover:opacity-100"
                            >
                              <Trash2 size={13} />
                            </button>
                          )}
                        </div>
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}
          </section>
        </div>

        {/* Cột phải: Kết quả, Biểu đồ & Sổ giao dịch */}
        <div className="xl:col-span-8 min-w-0">
          {detail ? (
            <>
              <BacktestResult run={detail} onDelete={() => void handleDelete(detail.id)} />
              {detail.status === "COMPLETED" && (
                <>
                  <BacktestEquityChart points={equity} />
                  <BacktestLedger trades={trades} events={events} />
                </>
              )}
            </>
          ) : (
            <div className="panel bg-slate-900/40 border border-slate-800/80 rounded-xl p-12 text-center flex flex-col items-center justify-center min-h-[360px]">
              <div className="w-12 h-12 rounded-full bg-cyan-950/40 border border-cyan-800/40 flex items-center justify-center text-cyan-400 mb-3">
                <BarChart3 size={24} />
              </div>
              <h3 className="text-sm font-semibold text-slate-200 mb-1">Chưa chọn lượt backtest</h3>
              <p className="text-xs text-slate-400 max-w-md">
                Chọn một lượt chạy từ danh sách bên trái hoặc tạo một backtest mới để xem kết quả chi tiết, đường vốn NAV và sổ giao dịch.
              </p>
            </div>
          )}
        </div>
      </div>
    </main>
  );
}
