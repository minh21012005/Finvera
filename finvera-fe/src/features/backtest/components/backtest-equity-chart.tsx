import type { EquityPoint } from "../api/backtest";
import { TrendingUp, Table } from "lucide-react";

export function BacktestEquityChart({ points }: { points: EquityPoint[] }) {
  if (!points.length) {
    return <p className="text-xs text-slate-400 p-4">Không có điểm equity.</p>;
  }

  const values = points.map((x) => Number(x.totalEquityVnd));
  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = max - min || 1;

  const w = 100;
  const h = 100;
  const padY = 6;
  const usableH = h - padY * 2;

  const pointsCoords = values.map((v, i) => {
    const x = (i * w) / Math.max(1, values.length - 1);
    const y = h - padY - ((v - min) * usableH) / span;
    return { x, y };
  });

  const linePath = pointsCoords
    .map((p, i) => `${i ? "L" : "M"} ${p.x.toFixed(2)} ${p.y.toFixed(2)}`)
    .join(" ");

  const areaPath = `${linePath} L 100 100 L 0 100 Z`;

  const startVal = values[0] ?? 0;
  const endVal = values[values.length - 1] ?? 0;
  const returnRate = startVal > 0 ? ((endVal - startVal) / startVal) * 100 : 0;
  const isUp = returnRate >= 0;

  return (
    <section className="panel bg-slate-900/70 border border-slate-800 rounded-xl p-6 shadow-xl mb-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mb-4">
        <div>
          <h2 className="text-sm font-bold text-slate-200 uppercase tracking-wider flex items-center gap-2">
            <TrendingUp size={16} className="text-cyan-400" />
            <span>Đường vốn</span>
          </h2>
          <p className="text-xs text-slate-400 mt-0.5">
            Diễn biến NAV qua {points.length} phiên ({points[0]?.tradingDate} → {points[points.length - 1]?.tradingDate})
          </p>
        </div>
        <div className="flex items-center gap-4 text-xs font-mono">
          <div className="bg-slate-950/60 border border-slate-800/80 px-3 py-1.5 rounded-lg">
            <span className="text-slate-400 mr-1.5">NAV cuối:</span>
            <span className={`font-bold ${isUp ? "text-emerald-400" : "text-rose-400"}`}>
              {endVal.toLocaleString("vi-VN")} VND ({isUp ? "+" : ""}{returnRate.toFixed(2)}%)
            </span>
          </div>
        </div>
      </div>

      <div className="backtest-chart-box relative overflow-hidden rounded-xl border border-slate-800 bg-[#070c14] p-4">
        <svg
          role="img"
          aria-label="Biểu đồ đường vốn"
          viewBox="0 0 100 100"
          preserveAspectRatio="none"
          className="w-full h-56 sm:h-64 overflow-visible"
        >
          <defs>
            <linearGradient id="equityGradient" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#00d2e0" stopOpacity="0.25" />
              <stop offset="100%" stopColor="#00d2e0" stopOpacity="0.0" />
            </linearGradient>
          </defs>

          {/* Grid lines */}
          <line x1="0" y1="25" x2="100" y2="25" stroke="#162438" strokeDasharray="2,2" strokeWidth="0.5" />
          <line x1="0" y1="50" x2="100" y2="50" stroke="#162438" strokeDasharray="2,2" strokeWidth="0.5" />
          <line x1="0" y1="75" x2="100" y2="75" stroke="#162438" strokeDasharray="2,2" strokeWidth="0.5" />

          {/* Fill Area under curve */}
          <path d={areaPath} fill="url(#equityGradient)" />

          {/* Line */}
          <path
            d={linePath}
            fill="none"
            stroke="#00d2e0"
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </div>

      <details className="mt-4 rounded-lg border border-slate-800 bg-slate-950/40 overflow-hidden">
        <summary className="cursor-pointer p-3 text-xs font-semibold text-slate-300 hover:text-white select-none transition-colors flex items-center gap-2">
          <Table size={14} className="text-slate-400" />
          <span>Xem dữ liệu đường vốn dạng bảng</span>
        </summary>
        <div className="overflow-x-auto max-h-72 border-t border-slate-800">
          <table className="terminal-data-table">
            <thead>
              <tr>
                <th>Ngày</th>
                <th>Equity</th>
                <th>Tiền mặt</th>
              </tr>
            </thead>
            <tbody>
              {points.map((p) => (
                <tr key={p.tradingDate}>
                  <td className="text-slate-300 font-mono text-xs">{p.tradingDate}</td>
                  <td className="text-cyan-300 font-mono text-xs font-semibold">{p.totalEquityVnd}</td>
                  <td className="text-slate-400 font-mono text-xs">{p.cashVnd}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </section>
  );
}
