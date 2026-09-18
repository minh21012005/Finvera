import { useMemo, useRef, useState } from "react";
import type { EquityPoint } from "../api/backtest";
import { TrendingUp, Table, Calendar, DollarSign, Layers } from "lucide-react";

// Canvas geometry - standard proportional coordinate system matching stock-chart.tsx
const WIDTH = 1000;
const HEIGHT = 320;
const MARGIN_LEFT = 24;
const MARGIN_RIGHT = 86;
const TOP = 20;
const BOTTOM = 275;
const PLOT_WIDTH = WIDTH - MARGIN_LEFT - MARGIN_RIGHT; // 890
const PLOT_HEIGHT = BOTTOM - TOP; // 255
const TIME_AXIS_Y = 302;

type TimeRange = "3M" | "6M" | "1Y" | "ALL";

function formatNavCompact(val: number): string {
  if (val >= 1_000_000_000) return `${(val / 1_000_000_000).toFixed(2)} tỷ`;
  if (val >= 1_000_000) return `${(val / 1_000_000).toFixed(2)} tr`;
  return val.toLocaleString("vi-VN");
}

function calculateNicePriceTicks(min: number, max: number, targetCount = 5): number[] {
  const range = max - min;
  if (range <= 0) return [min];
  const rawStep = range / (targetCount - 1);
  const magnitude = Math.pow(10, Math.floor(Math.log10(rawStep)));
  const norm = rawStep / magnitude;
  let niceStep = magnitude;
  if (norm >= 5) niceStep = 5 * magnitude;
  else if (norm >= 2) niceStep = 2 * magnitude;

  const first = Math.ceil(min / niceStep) * niceStep;
  const last = Math.floor(max / niceStep) * niceStep;
  const ticks: number[] = [];
  for (let t = first; t <= last + niceStep * 0.5; t += niceStep) {
    if (t >= min && t <= max) ticks.push(t);
  }
  return ticks.length >= 2 ? ticks : [min, (min + max) / 2, max];
}

export function BacktestEquityChart({ points }: { points: EquityPoint[] }) {
  const [range, setRange] = useState<TimeRange>("ALL");
  const [hoverIdx, setHoverIdx] = useState<number | null>(null);
  const svgRef = useRef<SVGSVGElement | null>(null);

  const visiblePoints = useMemo(() => {
    if (!points.length || range === "ALL" || points.length <= 1) return points;
    const lastDate = new Date(points[points.length - 1].tradingDate);
    const cutoff = new Date(lastDate);
    if (range === "3M") cutoff.setMonth(cutoff.getMonth() - 3);
    else if (range === "6M") cutoff.setMonth(cutoff.getMonth() - 6);
    else if (range === "1Y") cutoff.setFullYear(cutoff.getFullYear() - 1);
    const cutoffStr = cutoff.toISOString().slice(0, 10);
    const filtered = points.filter((p) => p.tradingDate >= cutoffStr);
    return filtered.length >= 2 ? filtered : points;
  }, [points, range]);

  if (!points.length) {
    return <p className="text-xs text-slate-400 p-4">Không có điểm equity.</p>;
  }

  const values = visiblePoints.map((x) => Number(x.totalEquityVnd));
  const min = Math.min(...values);
  const max = Math.max(...values);
  const isFlat = max === min;
  const span = isFlat ? 1 : max - min;

  // Scale functions
  const xCoord = (idx: number) =>
    MARGIN_LEFT + (idx * PLOT_WIDTH) / Math.max(1, visiblePoints.length - 1);
  const yCoord = (val: number) =>
    isFlat ? (TOP + BOTTOM) / 2 : BOTTOM - ((val - min) * PLOT_HEIGHT) / span;

  // Generate SVG polyline path
  const pointsCoords = values.map((v, i) => ({
    x: xCoord(i),
    y: yCoord(v),
  }));

  const linePoints = pointsCoords
    .map((p) => `${p.x.toFixed(1)},${p.y.toFixed(1)}`)
    .join(" ");

  const areaPath = pointsCoords.length > 0
    ? `M ${pointsCoords[0].x.toFixed(1)},${BOTTOM} L ${pointsCoords
        .map((p) => `${p.x.toFixed(1)},${p.y.toFixed(1)}`)
        .join(" L ")} L ${pointsCoords[pointsCoords.length - 1].x.toFixed(1)},${BOTTOM} Z`
    : "";

  // Performance calculations
  const startVal = Number(points[0]?.totalEquityVnd ?? 0);
  const endVal = Number(points[points.length - 1]?.totalEquityVnd ?? 0);
  const totalReturnPct = startVal > 0 ? ((endVal - startVal) / startVal) * 100 : 0;
  const isUp = totalReturnPct >= 0;

  // Active / Hovered state
  const activeIdx = hoverIdx !== null ? hoverIdx : visiblePoints.length - 1;
  const activePoint = visiblePoints[activeIdx];
  const activeCoord = pointsCoords[activeIdx];
  const activeVal = values[activeIdx] ?? 0;
  const activeReturnPct = startVal > 0 ? ((activeVal - startVal) / startVal) * 100 : 0;

  const handleMouseMove = (e: React.MouseEvent<SVGSVGElement>) => {
    if (!svgRef.current || visiblePoints.length === 0) return;
    const rect = svgRef.current.getBoundingClientRect();
    if (rect.width <= 0) return;
    const svgX = ((e.clientX - rect.left) / rect.width) * WIDTH;
    if (svgX < MARGIN_LEFT || svgX > WIDTH - MARGIN_RIGHT) {
      setHoverIdx(null);
      return;
    }
    const ratio = (svgX - MARGIN_LEFT) / PLOT_WIDTH;
    const idx = Math.min(
      visiblePoints.length - 1,
      Math.max(0, Math.round(ratio * (visiblePoints.length - 1)))
    );
    setHoverIdx(idx);
  };

  // Price ticks for Y-Axis
  const priceTicks = calculateNicePriceTicks(min, max, 5);

  // Time grid ticks (5-6 evenly-spaced dates across visible range)
  const timeTickCount = Math.min(5, visiblePoints.length);
  const timeTickIndices = timeTickCount <= 1
    ? (visiblePoints.length > 0 ? [0] : [])
    : Array.from({ length: timeTickCount }, (_, i) =>
        Math.round(i * ((visiblePoints.length - 1) / (timeTickCount - 1)))
      );

  return (
    <section className="panel bg-[#0d121c] border border-slate-800 rounded-xl p-5 shadow-2xl mb-6">
      {/* Top Header Row */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mb-4 pb-3 border-b border-slate-800/80">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-sm font-bold text-slate-100 uppercase tracking-wider flex items-center gap-2">
              <TrendingUp size={16} className="text-cyan-400" />
              <span>Đường vốn NAV</span>
            </h2>
            <span className="px-2 py-0.5 rounded bg-cyan-950/60 border border-cyan-800/50 text-[11px] font-mono font-bold text-cyan-300">
              {visiblePoints.length} / {points.length} phiên
            </span>
          </div>
          <p className="text-xs text-slate-400 mt-1">
            {visiblePoints[0]?.tradingDate} → {visiblePoints[visiblePoints.length - 1]?.tradingDate}
          </p>
        </div>

        {/* Right tools: Time range buttons & Overview Return */}
        <div className="flex items-center gap-3">
          <div className="flex items-center rounded-lg bg-slate-950/80 border border-slate-800 p-0.5 text-xs font-mono">
            {(["3M", "6M", "1Y", "ALL"] as const).map((r) => (
              <button
                key={r}
                type="button"
                onClick={() => setRange(r)}
                className={`px-2.5 py-1 rounded transition-colors ${
                  range === r
                    ? "bg-cyan-500 text-slate-950 font-bold shadow"
                    : "text-slate-400 hover:text-white"
                }`}
              >
                {r === "ALL" ? "Tất cả" : r}
              </button>
            ))}
          </div>

          <div className="hidden md:flex items-center gap-1.5 bg-slate-950/80 border border-slate-800 px-3 py-1.5 rounded-lg text-xs font-mono">
            <span className="text-slate-400">Toàn chu kỳ:</span>
            <span className={`font-bold ${isUp ? "text-emerald-400" : "text-rose-400"}`}>
              {isUp ? "+" : ""}{totalReturnPct.toFixed(2)}%
            </span>
          </div>
        </div>
      </div>

      {/* Institutional Top HUD Bar (TradingView / TCBS Style) */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-2 mb-3 text-xs font-mono">
        <div className="bg-[#121824] border border-slate-800/90 px-3 py-2 rounded-lg flex items-center gap-2.5 shadow-sm">
          <Calendar size={15} className="text-cyan-400 shrink-0" />
          <div className="min-w-0">
            <span className="text-[10px] text-slate-500 block uppercase">Phiên giao dịch</span>
            <span className="font-bold text-slate-200 truncate block">{activePoint?.tradingDate}</span>
          </div>
        </div>

        <div className="bg-[#121824] border border-slate-800/90 px-3 py-2 rounded-lg flex items-center gap-2.5 shadow-sm">
          <DollarSign size={15} className="text-emerald-400 shrink-0" />
          <div className="min-w-0">
            <span className="text-[10px] text-slate-500 block uppercase">
              NAV ({activeReturnPct >= 0 ? "+" : ""}{activeReturnPct.toFixed(2)}%)
            </span>
            <span className={`font-bold truncate block ${activeReturnPct >= 0 ? "text-emerald-300" : "text-rose-300"}`}>
              {activeVal.toLocaleString("vi-VN")} VND
            </span>
          </div>
        </div>

        <div className="bg-[#121824] border border-slate-800/90 px-3 py-2 rounded-lg flex items-center gap-2.5 shadow-sm">
          <div className="w-2.5 h-2.5 rounded-full bg-slate-400 shrink-0" />
          <div className="min-w-0">
            <span className="text-[10px] text-slate-500 block uppercase">Tiền mặt</span>
            <span className="font-bold text-slate-300 truncate block">
              {Number(activePoint?.cashVnd ?? 0).toLocaleString("vi-VN")} VND
            </span>
          </div>
        </div>

        <div className="bg-[#121824] border border-slate-800/90 px-3 py-2 rounded-lg flex items-center gap-2.5 shadow-sm">
          <Layers size={15} className="text-amber-400 shrink-0" />
          <div className="min-w-0">
            <span className="text-[10px] text-slate-500 block uppercase">
              Vị thế ({activePoint?.openTrancheCount ?? 0} tranche)
            </span>
            <span className="font-bold text-amber-300 truncate block">
              {Number(activePoint?.openPositionValueVnd ?? 0).toLocaleString("vi-VN")} VND
            </span>
          </div>
        </div>
      </div>

      {/* SVG Canvas - Institutional Pro Standard matching stock-chart.tsx */}
      <div className="chart-svg-wrapper relative">
        <svg
          ref={svgRef}
          role="img"
          aria-label="Biểu đồ đường vốn"
          viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
          width="100%"
          height="100%"
          preserveAspectRatio="none"
          className="chart-svg cursor-crosshair"
          onMouseMove={handleMouseMove}
          onMouseLeave={() => setHoverIdx(null)}
        >
          <defs>
            <linearGradient id="equityAreaGradient" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#00d2e0" stopOpacity="0.22" />
              <stop offset="100%" stopColor="#00d2e0" stopOpacity="0.0" />
            </linearGradient>
          </defs>

          {/* Background Plot Panel */}
          <rect
            x={MARGIN_LEFT}
            y={TOP}
            width={PLOT_WIDTH}
            height={PLOT_HEIGHT}
            fill="#10141d"
            stroke="#1e2638"
            strokeWidth="1"
          />

          {/* Horizontal Price Grid Lines & Round Labels */}
          {priceTicks.map((val, idx) => {
            const y = Math.round(yCoord(val));
            return (
              <g key={idx} className="price-grid-line">
                <line
                  x1={MARGIN_LEFT}
                  x2={WIDTH - MARGIN_RIGHT}
                  y1={y}
                  y2={y}
                  stroke="#1c2436"
                  strokeDasharray="2 3"
                  strokeWidth="1"
                  shapeRendering="crispEdges"
                />
                <text
                  x={WIDTH - MARGIN_RIGHT + 8}
                  y={y + 4}
                  fill="#64748b"
                  fontSize="10"
                  fontFamily="JetBrains Mono, monospace"
                  textAnchor="start"
                >
                  {formatNavCompact(val)}
                </text>
              </g>
            );
          })}

          {/* Vertical Time Grid Lines & Milestone Labels */}
          {timeTickIndices.map((idx) => {
            const p = visiblePoints[idx];
            if (!p) return null;
            const x = Math.round(xCoord(idx));
            const parts = p.tradingDate.split("-");
            const dateStr = parts.length === 3 ? `${parts[2]}/${parts[1]}` : p.tradingDate;
            return (
              <g key={p.tradingDate} className="time-grid-line">
                <line
                  x1={x}
                  x2={x}
                  y1={TOP}
                  y2={BOTTOM}
                  stroke="#1c2436"
                  strokeDasharray="2 3"
                  strokeWidth="1"
                  shapeRendering="crispEdges"
                />
                <text
                  x={x}
                  y={TIME_AXIS_Y}
                  fill="#64748b"
                  fontSize="10"
                  fontFamily="JetBrains Mono, monospace"
                  textAnchor="middle"
                >
                  {dateStr}
                </text>
              </g>
            );
          })}

          {/* Area Fill under curve */}
          {areaPath && <path d={areaPath} fill="url(#equityAreaGradient)" />}

          {/* Crisp Glowing Equity Polyline */}
          <polyline
            points={linePoints}
            fill="none"
            stroke="#00d2e0"
            strokeWidth="1.6"
            strokeLinecap="round"
            strokeLinejoin="round"
          />

          {/* Interactive Crosshair & Floating Badges */}
          {hoverIdx !== null && activeCoord && (
            <g className="chart-crosshair">
              {/* Vertical crosshair */}
              <line
                x1={Math.round(activeCoord.x)}
                x2={Math.round(activeCoord.x)}
                y1={TOP}
                y2={BOTTOM}
                stroke="#38bdf8"
                strokeDasharray="3 3"
                strokeWidth="1"
                shapeRendering="crispEdges"
              />

              {/* Horizontal crosshair */}
              <line
                x1={MARGIN_LEFT}
                x2={WIDTH - MARGIN_RIGHT}
                y1={Math.round(activeCoord.y)}
                y2={Math.round(activeCoord.y)}
                stroke="#38bdf8"
                strokeDasharray="3 3"
                strokeWidth="1"
                shapeRendering="crispEdges"
              />

              {/* Glowing active point marker */}
              <circle
                cx={activeCoord.x}
                cy={activeCoord.y}
                r="3.5"
                fill="#00d2e0"
                stroke="#0f172a"
                strokeWidth="1.5"
              />

              {/* Floating Y-Axis NAV Badge */}
              <rect
                x={WIDTH - MARGIN_RIGHT + 4}
                y={Math.max(TOP, Math.min(BOTTOM - 18, Math.round(activeCoord.y) - 9))}
                width={MARGIN_RIGHT - 8}
                height={18}
                rx={3}
                fill="#0284c7"
              />
              <text
                x={WIDTH - MARGIN_RIGHT + 4 + (MARGIN_RIGHT - 8) / 2}
                y={Math.max(TOP, Math.min(BOTTOM - 18, Math.round(activeCoord.y) - 9)) + 12}
                fill="#ffffff"
                fontSize="10"
                fontWeight="700"
                fontFamily="JetBrains Mono, monospace"
                textAnchor="middle"
              >
                {formatNavCompact(activeVal)}
              </text>

              {/* Floating X-Axis Date Badge */}
              <rect
                x={Math.max(MARGIN_LEFT, Math.min(WIDTH - MARGIN_RIGHT - 74, Math.round(activeCoord.x) - 37))}
                y={BOTTOM + 4}
                width={74}
                height={18}
                rx={3}
                fill="#1e293b"
                stroke="#38bdf8"
                strokeWidth="1"
              />
              <text
                x={Math.max(MARGIN_LEFT, Math.min(WIDTH - MARGIN_RIGHT - 74, Math.round(activeCoord.x) - 37)) + 37}
                y={BOTTOM + 17}
                fill="#38bdf8"
                fontSize="10"
                fontWeight="700"
                fontFamily="JetBrains Mono, monospace"
                textAnchor="middle"
              >
                {activePoint?.tradingDate}
              </text>
            </g>
          )}
        </svg>
      </div>

      {/* Accessible Terminal Data Table */}
      <details className="mt-4 rounded-lg border border-slate-800 bg-slate-950/40 overflow-hidden">
        <summary className="cursor-pointer p-3 text-xs font-semibold text-slate-300 hover:text-white select-none transition-colors flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Table size={14} className="text-slate-400" />
            <span>Xem dữ liệu đường vốn dạng bảng</span>
          </div>
          <span className="text-[11px] font-mono text-slate-400 bg-slate-900 px-2 py-0.5 rounded border border-slate-800">
            {points.length} phiên
          </span>
        </summary>
        <div className="overflow-x-auto max-h-72 border-t border-slate-800">
          <table className="terminal-data-table">
            <thead>
              <tr>
                <th>Ngày</th>
                <th>Equity</th>
                <th>Tiền mặt</th>
                <th>Vị thế mở</th>
              </tr>
            </thead>
            <tbody>
              {points.map((p) => (
                <tr key={p.tradingDate}>
                  <td className="text-slate-300 font-mono text-xs">{p.tradingDate}</td>
                  <td className="text-cyan-300 font-mono text-xs font-semibold">{p.totalEquityVnd}</td>
                  <td className="text-slate-400 font-mono text-xs">{p.cashVnd}</td>
                  <td className="text-amber-300 font-mono text-xs">{p.openPositionValueVnd}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </section>
  );
}
