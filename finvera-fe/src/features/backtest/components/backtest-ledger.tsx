import type { EntryEvent, Trade } from "../api/backtest";
import { ListFilter, XCircle } from "lucide-react";

export function BacktestLedger({ trades, events }: { trades: Trade[]; events: EntryEvent[] }) {
  return (
    <section className="panel bg-slate-900/70 border border-slate-800 rounded-xl p-6 shadow-xl mb-6 space-y-6">
      {/* Sổ giao dịch */}
      <div>
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-sm font-bold text-slate-200 uppercase tracking-wider flex items-center gap-2">
            <ListFilter size={16} className="text-cyan-400" />
            <span>Sổ giao dịch</span>
          </h2>
          <span className="text-xs font-mono text-slate-400">
            {trades.length} lệnh đã thực hiện
          </span>
        </div>

        {trades.length === 0 ? (
          <div className="p-4 rounded-lg bg-slate-950/40 border border-slate-800 text-xs text-slate-400 text-center">
            <p>Không có giao dịch nào được thực hiện trong chu kỳ backtest này.</p>
          </div>
        ) : (
          <div className="overflow-x-auto rounded-lg border border-slate-800">
            <table className="terminal-data-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Signal</th>
                  <th>Vào</th>
                  <th>Ra</th>
                  <th>SL</th>
                  <th>P/L</th>
                  <th>Lý do thoát</th>
                </tr>
              </thead>
              <tbody>
                {trades.map((t) => {
                  const pnlNum = Number(t.netPnlVnd);
                  const isProfit = pnlNum > 0;
                  const isLoss = pnlNum < 0;
                  return (
                    <tr key={t.sequence}>
                      <td className="font-mono text-xs text-slate-400">{t.sequence}</td>
                      <td className="font-mono text-xs text-slate-300">{t.signalDate}</td>
                      <td className="font-mono text-xs text-slate-300">{t.entryDate}</td>
                      <td className="font-mono text-xs text-slate-300">{t.exitDate}</td>
                      <td className="font-mono text-xs text-slate-200">{t.quantity}</td>
                      <td className={`font-mono text-xs font-bold ${
                        isProfit ? "text-emerald-400" : isLoss ? "text-rose-400" : "text-slate-300"
                      }`}>
                        {t.netPnlVnd}
                      </td>
                      <td className="text-xs text-slate-400">{t.exitReason}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Tín hiệu không vào lệnh */}
      <div className="pt-4 border-t border-slate-800">
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-sm font-bold text-slate-200 uppercase tracking-wider flex items-center gap-2">
            <XCircle size={15} className="text-amber-400" />
            <span>Tín hiệu không vào lệnh</span>
          </h3>
          <span className="text-xs font-mono text-slate-400">
            {events.length} rejected events
          </span>
        </div>

        {events.length === 0 ? (
          <p className="text-xs text-slate-400 p-3 rounded-lg bg-slate-950/40 border border-slate-800/80">Không có.</p>
        ) : (
          <ul className="space-y-2">
            {events.map((e) => (
              <li
                key={e.sequence}
                className="p-2.5 rounded-lg bg-slate-950/40 border border-slate-800 text-xs font-mono text-slate-300"
              >
                {e.signalDate}: {e.outcome} — {e.reasonCode}
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
