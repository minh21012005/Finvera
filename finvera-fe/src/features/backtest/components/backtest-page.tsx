import { useCallback, useEffect, useState } from "react";
import { getBacktest, getEquity, getEvents, getTrades, listBacktests, type EntryEvent, type EquityPoint, type RunDetail, type RunSummary, type Trade } from "../api/backtest";
import { BacktestCreateForm } from "./backtest-create-form";
import { BacktestEquityChart } from "./backtest-equity-chart";
import { BacktestLedger } from "./backtest-ledger";
import { BacktestResult } from "./backtest-result";

export function BacktestPage() {
  const [runs, setRuns] = useState<RunSummary[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [detail, setDetail] = useState<RunDetail | null>(null);
  const [trades, setTrades] = useState<Trade[]>([]);
  const [equity, setEquity] = useState<EquityPoint[]>([]);
  const [events, setEvents] = useState<EntryEvent[]>([]);
  const [error, setError] = useState<string | null>(null);

  const loadList = useCallback(async () => {
    try { setRuns((await listBacktests()).items); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Không tải được lịch sử backtest"); }
  }, []);

  const load = useCallback(async (id: string) => {
    try {
      const next = await getBacktest(id);
      setDetail(next);
      setRuns((current) => current.map((run) => run.id === next.id ? next : run));
      if (next.status === "COMPLETED") {
        const [tradePage, equityPage, eventPage] = await Promise.all([getTrades(id), getEquity(id), getEvents(id)]);
        setTrades(tradePage.items); setEquity(equityPage.items); setEvents(eventPage.items);
      } else if (next.status === "WITHHELD" || next.status === "FAILED") {
        setTrades([]); setEquity([]); setEvents([]);
      }
      setError(null);
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Không tải được backtest"); }
  }, []);

  useEffect(() => { const timer = setTimeout(() => void loadList(), 0); return () => clearTimeout(timer); }, [loadList]);
  useEffect(() => {
    if (!selected) return;
    const loaded = detail?.id === selected;
    const active = !loaded || detail.status === "QUEUED" || detail.status === "RUNNING";
    if (!active) return;
    const first = setTimeout(() => void load(selected), 0);
    const timer = setInterval(() => void load(selected), 2_000);
    return () => { clearTimeout(first); clearInterval(timer); };
  }, [selected, load, detail?.id, detail?.status]);

  return <main className="app-shell backtest-page" aria-labelledby="backtest-title">
    <header className="page-header"><h1 id="backtest-title">Backtesting chiến lược</h1><p>Mô phỏng xác định trên dữ liệu lịch sử đã chốt; đây là công cụ nghiên cứu, không bảo đảm lợi nhuận.</p></header>
    <BacktestCreateForm onCreated={(run) => { setRuns((current) => [run, ...current]); setDetail(null); setSelected(run.id); }} />
    {error && <p role="alert">{error}</p>}
    <section><h2>Lịch sử run</h2>{runs.length === 0 ? <p>Chưa có backtest.</p> : <ul>{runs.map((run) => <li key={run.id}><button onClick={() => { setDetail(null); setSelected(run.id); }} aria-current={selected === run.id}>{run.symbol} · {run.strategyCode} · {run.status} · {run.processedSessions}/{run.totalSessions ?? "?"}</button></li>)}</ul>}</section>
    {detail && <><BacktestResult run={detail} />{detail.status === "COMPLETED" && <><BacktestEquityChart points={equity} /><BacktestLedger trades={trades} events={events} /></>}</>}
  </main>;
}
