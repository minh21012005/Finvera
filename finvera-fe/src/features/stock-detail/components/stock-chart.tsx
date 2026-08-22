import { useState, useRef, useMemo, useEffect, type MouseEvent } from "react";
import type { StockChart as StockChartData } from "../api/stock-detail";
import { formatAsOf, formatVolume } from "../format/stock-format";
import {
  ZoomIn,
  ZoomOut,
  RotateCcw,
  BarChart2,
  LineChart as LineChartIcon,
  TrendingUp,
  TrendingDown,
  MoveHorizontal,
} from "lucide-react";

// SVG Canvas Geometry - TradingView / SSI / TCBS Pro Standard
const WIDTH = 1000;
const HEIGHT = 350;
const MARGIN_LEFT = 16;
const MARGIN_RIGHT = 80;
const PRICE_PANE_TOP = 16;
const PRICE_PANE_HEIGHT = 205;
const PRICE_PANE_BOTTOM = PRICE_PANE_TOP + PRICE_PANE_HEIGHT; // 221
const VOLUME_PANE_TOP = 236;
const VOLUME_PANE_HEIGHT = 65;
const VOLUME_PANE_BOTTOM = VOLUME_PANE_TOP + VOLUME_PANE_HEIGHT; // 301
const TIME_AXIS_Y = 328;
const PLOT_WIDTH = WIDTH - MARGIN_LEFT - MARGIN_RIGHT; // 904

// Institutional Candle Colors - TCBS / SSI / TradingView Sea-Green & Coral Red
const COLOR_UP = "#52b89a"; // Sea-Green / Teal (Xanh ngọc bích chuẩn TradingView / TCBS)
const COLOR_DOWN = "#e55454"; // Coral Red (Đỏ san hô chuẩn TradingView / TCBS)
const COLOR_PRICE_LINE = "#6ee7b7"; // Fine dotted current price guideline
const COLOR_GRID = "#222a3a"; // Crisp subtle grid lines
const COLOR_BG_PANEL = "#161b24"; // Sleek graphite background
const COLOR_VOL_BG = "#10141d";

type TimeRange = "1W" | "1M" | "3M" | "6M" | "1Y" | "ALL";
type ChartType = "CANDLE" | "LINE";

/** Formats price numbers consistently with 2 decimal places for sub-1000 numbers (e.g. 20.85, 48.40, 50.00) */
function formatChartPrice(val: number | string): string {
  const num = typeof val === "number" ? val : Number.parseFloat(val);
  if (Number.isNaN(num)) return "0";
  if (num < 1000) {
    return num.toLocaleString("vi-VN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
  return num.toLocaleString("vi-VN", { maximumFractionDigits: 0 });
}

function formatVolCompact(vol: number): string {
  if (vol >= 1_000_000) {
    const m = vol / 1_000_000;
    return `${m.toLocaleString("vi-VN", { maximumFractionDigits: 1 })}M`;
  }
  if (vol >= 1_000) {
    const k = vol / 1_000;
    return `${k.toLocaleString("vi-VN", { maximumFractionDigits: 0 })}K`;
  }
  return vol.toLocaleString("vi-VN");
}

/** Calculates the exact calendar start date for a given time range based on the latest available trading session */
function getRangeStartDate(latestDateStr: string, range: TimeRange): string {
  const parts = latestDateStr.split("-").map(Number);
  if (parts.length < 3 || parts.some(Number.isNaN)) return "0000-00-00";
  const [year, month, day] = parts;
  const d = new Date(Date.UTC(year, month - 1, day));

  switch (range) {
    case "1W":
      d.setUTCDate(d.getUTCDate() - 7);
      break;
    case "1M":
      d.setUTCMonth(d.getUTCMonth() - 1);
      break;
    case "3M":
      d.setUTCMonth(d.getUTCMonth() - 3);
      break;
    case "6M":
      d.setUTCMonth(d.getUTCMonth() - 6);
      break;
    case "1Y":
      d.setUTCFullYear(d.getUTCFullYear() - 1);
      break;
    case "ALL":
    default:
      return "0000-00-00";
  }

  const yyyy = d.getUTCFullYear();
  const mm = String(d.getUTCMonth() + 1).padStart(2, "0");
  const dd = String(d.getUTCDate()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}`;
}

/** Calculates standard round "Nice Number" ticks for the Y-Axis (e.g. 19.00, 19.50, 20.00 or 44, 45, 46) */
function calculateNicePriceTicks(min: number, max: number, targetCount: number = 6): number[] {
  const rawRange = max - min;
  if (rawRange <= 0) return [min];

  const rawStep = rawRange / (targetCount - 1);
  const magnitude = Math.pow(10, Math.floor(Math.log10(rawStep)));
  const normalizedStep = rawStep / magnitude;

  let niceStep: number;
  if (normalizedStep < 1.5) {
    niceStep = 1 * magnitude;
  } else if (normalizedStep < 3) {
    niceStep = 2 * magnitude;
  } else if (normalizedStep < 7) {
    niceStep = 5 * magnitude;
  } else {
    niceStep = 10 * magnitude;
  }

  // If price is in thousands (e.g. 20.85 or 48.4), provide standard increments (0.25, 0.5, 1, 2, 5)
  if (max < 1000) {
    if (rawStep <= 0.35) niceStep = 0.25;
    else if (rawStep <= 0.75) niceStep = 0.5;
    else if (rawStep <= 1.5) niceStep = 1;
    else if (rawStep <= 3) niceStep = 2;
    else if (rawStep <= 7) niceStep = 5;
    else niceStep = 10;
  }

  if (niceStep <= 0) niceStep = 1;

  const firstTick = Math.ceil(min / niceStep) * niceStep;
  const lastTick = Math.floor(max / niceStep) * niceStep;

  const ticks: number[] = [];
  const numSteps = Math.round((lastTick - firstTick) / niceStep);
  for (let i = 0; i <= numSteps; i++) {
    const tick = Number((firstTick + i * niceStep).toFixed(4));
    if (tick >= min && tick <= max) {
      ticks.push(tick);
    }
  }

  if (ticks.length >= 2) return ticks;
  return [min, (min + max) / 2, max];
}

export function StockChart({ chart }: { chart: StockChartData }) {
  const { meta, bars, adjustmentStatus } = chart;

  const [timeRange, setTimeRange] = useState<TimeRange>("6M");
  const [chartType, setChartType] = useState<ChartType>("CANDLE");
  const [hoverSlot, setHoverSlot] = useState<number | null>(null);
  const [hoverY, setHoverY] = useState<number | null>(null);
  const [zoomLevel, setZoomLevel] = useState<number>(1);
  const [panOffset, setPanOffset] = useState<number>(0);
  const [isDragging, setIsDragging] = useState<boolean>(false);

  const containerRef = useRef<HTMLDivElement | null>(null);
  const svgRef = useRef<SVGSVGElement | null>(null);
  const isDraggingRef = useRef<boolean>(false);
  const dragStartXRef = useRef<number>(0);
  const dragStartOffsetRef = useRef<number>(0);

  // 1. Filter bars according to selected time range based on exact calendar intervals
  const rangeBars = useMemo(() => {
    if (!bars || bars.length === 0) return [];
    if (timeRange === "ALL") return bars;

    const latestBar = bars[bars.length - 1];
    const cutoffDate = getRangeStartDate(latestBar.tradingDate, timeRange);
    const filtered = bars.filter((b) => b.tradingDate >= cutoffDate);
    return filtered.length > 0 ? filtered : bars.slice(-10);
  }, [bars, timeRange]);

  // 2. Viewport capacity with flexible right margin (SSI / TradingView style)
  const totalBars = rangeBars.length;
  const baseSlots = Math.max(10, Math.floor(totalBars / zoomLevel));
  // Default right margin: 6 empty future slots so the latest candle is not stuck at the right edge
  const defaultRightMargin = Math.min(10, Math.max(4, Math.floor(baseSlots * 0.12)));
  const capacity = baseSlots + defaultRightMargin;

  // Strict Pan Bounds ensuring candles ALWAYS remain visible on screen in both directions:
  // - Drag left (panOffset > 0): Moves latest candle towards left of screen.
  //   We clamp so the latest candle stops at slot 3 (never dragged off the left edge).
  const maxDragLeft = Math.max(0, capacity - 4 - defaultRightMargin);
  // - Drag right (panOffset < 0): Moves oldest history towards right of screen.
  //   We clamp so the earliest candle stops at slot (capacity - 4) (never dragged off the right edge).
  const maxDragRight = Math.min(0, 4 - totalBars - defaultRightMargin);
  const clampedPan = Math.min(maxDragLeft, Math.max(maxDragRight, panOffset));

  // The starting bar index corresponding to slot 0 (left edge of chart)
  const startBarIndex = (totalBars - 1) - (capacity - 1 - defaultRightMargin - clampedPan);

  interface PositionedBar {
    bar: (typeof rangeBars)[0];
    barIndex: number;
    slotIndex: number;
  }

  const visibleBars: PositionedBar[] = useMemo(() => {
    if (totalBars === 0) return [];
    const result: PositionedBar[] = [];
    for (let slot = 0; slot < capacity; slot++) {
      const barIdx = startBarIndex + slot;
      if (barIdx >= 0 && barIdx < totalBars) {
        result.push({
          bar: rangeBars[barIdx],
          barIndex: barIdx,
          slotIndex: slot,
        });
      }
    }
    return result;
  }, [rangeBars, totalBars, capacity, startBarIndex]);

  // 3. Attach Mouse Wheel listener on container for smooth zooming
  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;

    const handleWheel = (e: globalThis.WheelEvent) => {
      e.preventDefault();
      const zoomFactor = e.deltaY < 0 ? 1.15 : 0.87;
      setZoomLevel((prevZoom) => {
        const nextZoom = Math.min(8, Math.max(1, Number((prevZoom * zoomFactor).toFixed(2))));
        if (nextZoom === 1) {
          setPanOffset(0);
        }
        return nextZoom;
      });
    };

    el.addEventListener("wheel", handleWheel, { passive: false });
    return () => {
      el.removeEventListener("wheel", handleWheel);
    };
  }, []);

  if (!bars || bars.length === 0) {
    return (
      <section aria-labelledby="stock-chart-heading" className="stock-chart-card">
        <h2 id="stock-chart-heading">Biểu đồ giá</h2>
        <p role="status">
          Không có dữ liệu biểu đồ{meta.reasonCodes.length > 0 ? `: ${meta.reasonCodes.join(", ")}` : ""}
        </p>
      </section>
    );
  }

  // 4. Coordinate Scaling Calculations
  const highs = visibleBars.length > 0
    ? visibleBars.map((p) => Number.parseFloat(p.bar.high))
    : [Number.parseFloat(bars[bars.length - 1].high)];
  const lows = visibleBars.length > 0
    ? visibleBars.map((p) => Number.parseFloat(p.bar.low))
    : [Number.parseFloat(bars[bars.length - 1].low)];
  const minLow = Math.min(...lows);
  const maxHigh = Math.max(...highs);

  // 6% price headroom & footroom for clean spacing
  const priceHeadroom = (maxHigh - minLow) * 0.06 || (maxHigh < 1000 ? 0.5 : 1000);
  const priceMin = Math.max(0, minLow - priceHeadroom);
  const priceMax = maxHigh + priceHeadroom;
  const priceRange = priceMax - priceMin || 1;

  const maxVol = Math.max(...visibleBars.map((p) => p.bar.volume || 0), 1000);

  // Scale Functions
  const yPrice = (val: number) =>
    PRICE_PANE_TOP + PRICE_PANE_HEIGHT - ((val - priceMin) / priceRange) * PRICE_PANE_HEIGHT;

  const yVolume = (vol: number) =>
    VOLUME_PANE_BOTTOM - (Math.max(0, vol) / maxVol) * VOLUME_PANE_HEIGHT;

  const step = PLOT_WIDTH / capacity;
  // Slender, sharp width with clear spacing between candles
  const candleWidth = Math.max(1, Math.min(14, Math.floor(step * 0.62)));

  const xSlot = (slot: number) => MARGIN_LEFT + step * slot + step / 2;

  // Active bar for OHLCV inspector
  const hoveredBarObj = hoverSlot !== null ? visibleBars.find((p) => p.slotIndex === hoverSlot) : null;
  const activeBar = hoveredBarObj?.bar || visibleBars.at(-1)?.bar || bars[bars.length - 1];

  const activeOpen = Number.parseFloat(activeBar.open);
  const activeClose = Number.parseFloat(activeBar.close);
  const activeHigh = Number.parseFloat(activeBar.high);
  const activeLow = Number.parseFloat(activeBar.low);
  const activeVol = activeBar.volume;
  const activeDiff = activeClose - activeOpen;
  const activeDiffPct = activeOpen > 0 ? (activeDiff / activeOpen) * 100 : 0;
  const isActiveUp = activeClose >= activeOpen;

  // Latest bar close for current price horizontal line
  const latestBar = bars[bars.length - 1];
  const latestClose = Number.parseFloat(latestBar.close);
  const latestY = yPrice(latestClose);

  // Calculate Round "Nice Numbers" Price Ticks for the Right-Side Y-Axis
  const priceTicks = calculateNicePriceTicks(priceMin, priceMax, 6).map((val) => ({
    val,
    y: yPrice(val),
  }));

  // Vertical Time Grid Ticks (up to 6 evenly spaced dates across visible bars)
  const timeTickCount = Math.min(6, visibleBars.length);
  const timeTickIndices = timeTickCount <= 1
    ? (visibleBars.length > 0 ? [0] : [])
    : Array.from({ length: timeTickCount }, (_, i) =>
        Math.round(i * ((visibleBars.length - 1) / (timeTickCount - 1)))
      );

  // 5. Handle Mouse Drag (Pan) & Move (Crosshair)
  function handleMouseDown(e: MouseEvent<SVGSVGElement>) {
    if (e.button !== 0) return; // Left click only
    isDraggingRef.current = true;
    dragStartXRef.current = e.clientX;
    dragStartOffsetRef.current = panOffset;
    setIsDragging(true);
  }

  function handleMouseMove(e: MouseEvent<SVGSVGElement>) {
    if (!svgRef.current) return;
    const rect = svgRef.current.getBoundingClientRect();
    const svgX = ((e.clientX - rect.left) / rect.width) * WIDTH;
    const svgY = ((e.clientY - rect.top) / rect.height) * HEIGHT;

    // Drag to pan smoothly in both directions with strict boundary clamping
    if (isDraggingRef.current) {
      const deltaPixel = e.clientX - dragStartXRef.current;
      const pixelPerSlot = rect.width / capacity;
      const deltaSlots = Math.round(deltaPixel / pixelPerSlot);
      const rawOffset = dragStartOffsetRef.current - deltaSlots;
      const nextOffset = Math.min(maxDragLeft, Math.max(maxDragRight, rawOffset));
      setPanOffset(nextOffset);
    }

    if (svgX >= MARGIN_LEFT && svgX <= WIDTH - MARGIN_RIGHT) {
      const slot = Math.min(capacity - 1, Math.max(0, Math.floor((svgX - MARGIN_LEFT) / step)));
      setHoverSlot(slot);
    } else {
      setHoverSlot(null);
    }

    if (svgY >= PRICE_PANE_TOP && svgY <= VOLUME_PANE_BOTTOM) {
      setHoverY(svgY);
    } else {
      setHoverY(null);
    }
  }

  function handleMouseUp() {
    isDraggingRef.current = false;
    setIsDragging(false);
  }

  function handleMouseLeave() {
    isDraggingRef.current = false;
    setIsDragging(false);
    setHoverSlot(null);
    setHoverY(null);
  }

  // Price corresponding to hover cursor Y
  const hoveredPriceVal =
    hoverY !== null && hoverY >= PRICE_PANE_TOP && hoverY <= PRICE_PANE_BOTTOM
      ? priceMin + (1 - (hoverY - PRICE_PANE_TOP) / PRICE_PANE_HEIGHT) * priceRange
      : null;

  // Zoom Button Helpers
  function handleZoomIn() {
    setZoomLevel((z) => Math.min(8, Number((z * 1.3).toFixed(2))));
  }

  function handleZoomOut() {
    setZoomLevel((z) => {
      const next = Math.max(1, Number((z / 1.3).toFixed(2)));
      if (next === 1) setPanOffset(0);
      return next;
    });
  }

  function handleReset() {
    setZoomLevel(1);
    setPanOffset(0);
    setTimeRange("6M");
  }

  // Generate Area Path for Line Mode
  const linePoints = visibleBars
    .map((p) => `${Math.round(xSlot(p.slotIndex))},${yPrice(Number.parseFloat(p.bar.close))}`)
    .join(" ");

  const areaPath = visibleBars.length > 0
    ? `M ${Math.round(xSlot(visibleBars[0].slotIndex))},${PRICE_PANE_BOTTOM} L ${visibleBars
        .map((p) => `${Math.round(xSlot(p.slotIndex))},${yPrice(Number.parseFloat(p.bar.close))}`)
        .join(" L ")} L ${Math.round(
        xSlot(visibleBars[visibleBars.length - 1].slotIndex)
      )},${PRICE_PANE_BOTTOM} Z`
    : "";

  return (
    <section
      aria-labelledby="stock-chart-heading"
      className="stock-chart-card institutional-chart-card"
    >
      {/* Top Header & Range/Zoom Toolbar */}
      <div className="chart-header-row">
        <div>
          <h2 id="stock-chart-heading" className="chart-title">
            Biểu đồ giá
          </h2>
          <p className="meta-item text-xs text-slate-400">
            Chuỗi giá{" "}
            <span className="font-semibold text-cyan-400">
              {adjustmentStatus === "ADJUSTED"
                ? "đã điều chỉnh sự kiện doanh nghiệp"
                : "chưa điều chỉnh (RAW)"}
            </span>{" "}
            · Cập nhật {formatAsOf(meta.asOf)}
          </p>
        </div>

        {/* Action Controls Toolbar */}
        <div className="chart-controls-toolbar">
          {/* Time Range Selector */}
          <div className="range-button-group">
            {(["1W", "1M", "3M", "6M", "1Y", "ALL"] as TimeRange[]).map((r) => (
              <button
                key={r}
                type="button"
                className={`range-pill-btn ${timeRange === r ? "active" : ""}`}
                onClick={() => {
                  setTimeRange(r);
                  setZoomLevel(1);
                  setPanOffset(0);
                }}
              >
                {r}
              </button>
            ))}
          </div>

          {/* Chart Type Toggle */}
          <div className="chart-type-toggle">
            <button
              type="button"
              className={`type-icon-btn ${chartType === "CANDLE" ? "active" : ""}`}
              title="Biểu đồ Nến Nhật"
              onClick={() => setChartType("CANDLE")}
            >
              <BarChart2 size={14} />
              <span>Nến</span>
            </button>
            <button
              type="button"
              className={`type-icon-btn ${chartType === "LINE" ? "active" : ""}`}
              title="Biểu đồ Đường giá"
              onClick={() => setChartType("LINE")}
            >
              <LineChartIcon size={14} />
              <span>Đường</span>
            </button>
          </div>

          {/* Zoom Controls */}
          <div className="zoom-button-group">
            <button
              type="button"
              className="zoom-btn"
              onClick={handleZoomIn}
              title="Phóng to (Zoom In hoặc Cuộn chuột lên)"
              disabled={zoomLevel >= 8}
            >
              <ZoomIn size={14} />
            </button>
            <button
              type="button"
              className="zoom-btn"
              onClick={handleZoomOut}
              title="Thu nhỏ (Zoom Out hoặc Cuộn chuột xuống)"
              disabled={zoomLevel <= 1}
            >
              <ZoomOut size={14} />
            </button>
            <button
              type="button"
              className="zoom-btn"
              onClick={handleReset}
              title="Đặt lại góc nhìn (Reset View)"
            >
              <RotateCcw size={14} />
            </button>
          </div>

          {(zoomLevel > 1 || panOffset !== 0) && (
            <span className="zoom-badge-pill">
              Zoom: {Math.round(zoomLevel * 100)}%{panOffset !== 0 ? " (Đã dịch chuyển)" : ""}
            </span>
          )}
        </div>
      </div>

      {/* Real-Time / Hover OHLCV Status Bar */}
      <div className="ohlcv-status-bar">
        <div className="ohlcv-item date">
          <span className="label">Phiên:</span>
          <strong className="value font-mono text-slate-200">{activeBar.tradingDate}</strong>
        </div>
        <div className="ohlcv-item">
          <span className="label">Mở:</span>
          <span className="value font-mono">{formatChartPrice(activeOpen)}</span>
        </div>
        <div className="ohlcv-item">
          <span className="label">Cao:</span>
          <span className="value font-mono" style={{ color: COLOR_UP }}>{formatChartPrice(activeHigh)}</span>
        </div>
        <div className="ohlcv-item">
          <span className="label">Thấp:</span>
          <span className="value font-mono" style={{ color: COLOR_DOWN }}>{formatChartPrice(activeLow)}</span>
        </div>
        <div className="ohlcv-item">
          <span className="label">Đóng:</span>
          <strong className="value font-mono" style={{ color: isActiveUp ? COLOR_UP : COLOR_DOWN }}>
            {formatChartPrice(activeClose)}
          </strong>
        </div>
        <div className="ohlcv-item diff">
          <span className="label">Biến động:</span>
          <span
            className="diff-chip font-mono flex items-center gap-1"
            style={{ color: isActiveUp ? COLOR_UP : COLOR_DOWN }}
          >
            {isActiveUp ? <TrendingUp size={12} /> : <TrendingDown size={12} />}
            {isActiveUp ? "+" : ""}
            {formatChartPrice(activeDiff)} ({isActiveUp ? "+" : ""}
            {activeDiffPct.toFixed(2)}%)
          </span>
        </div>
        <div className="ohlcv-item volume">
          <span className="label">Khối lượng:</span>
          <span className="value font-mono text-slate-300">
            {formatVolume(activeVol)} CP
          </span>
        </div>
      </div>

      {/* Interactive High-DPI SVG Chart with Mouse Wheel Zoom & Drag Pan */}
      <div
        ref={containerRef}
        className={`chart-svg-wrapper ${isDragging ? "is-panning" : ""}`}
        style={{ cursor: isDragging ? "grabbing" : "grab" }}
      >
        <svg
          ref={svgRef}
          role="img"
          aria-label={`Biểu đồ giá dạng nến, ${bars.length} phiên, chuỗi ${
            adjustmentStatus === "ADJUSTED" ? "đã điều chỉnh" : "chưa điều chỉnh"
          }`}
          viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
          width="100%"
          height="100%"
          preserveAspectRatio="none"
          className="chart-svg"
          onMouseDown={handleMouseDown}
          onMouseMove={handleMouseMove}
          onMouseUp={handleMouseUp}
          onMouseLeave={handleMouseLeave}
        >
          <defs>
            {/* Area gradient for Line Mode */}
            <linearGradient id="areaGradient" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#52b89a" stopOpacity="0.3" />
              <stop offset="100%" stopColor="#52b89a" stopOpacity="0.0" />
            </linearGradient>
          </defs>

          {/* Background Panels */}
          <rect
            x={MARGIN_LEFT}
            y={PRICE_PANE_TOP}
            width={PLOT_WIDTH}
            height={PRICE_PANE_HEIGHT}
            fill={COLOR_BG_PANEL}
            stroke="#242c3d"
            strokeWidth="1"
          />
          <rect
            x={MARGIN_LEFT}
            y={VOLUME_PANE_TOP}
            width={PLOT_WIDTH}
            height={VOLUME_PANE_HEIGHT}
            fill={COLOR_VOL_BG}
            stroke="#242c3d"
            strokeWidth="1"
          />

          {/* 1. Horizontal Price Grid Lines & Round "Nice" Labels */}
          {priceTicks.map(({ val, y }, idx) => {
            const isCollidingWithCurrentPrice =
              latestY >= PRICE_PANE_TOP &&
              latestY <= PRICE_PANE_BOTTOM &&
              Math.abs(y - latestY) < 11;

            return (
              <g key={idx} className="price-grid-line">
                <line
                  x1={MARGIN_LEFT}
                  x2={WIDTH - MARGIN_RIGHT}
                  y1={Math.round(y)}
                  y2={Math.round(y)}
                  stroke={COLOR_GRID}
                  strokeDasharray="1 3"
                  strokeWidth="1"
                  shapeRendering="crispEdges"
                />
                {!isCollidingWithCurrentPrice && (
                  <text
                    x={WIDTH - MARGIN_RIGHT + 8}
                    y={Math.round(y) + 4}
                    fill="#94a3b8"
                    fontSize="10"
                    fontFamily="JetBrains Mono, monospace"
                    textAnchor="start"
                  >
                    {formatChartPrice(val)}
                  </text>
                )}
              </g>
            );
          })}

          {/* 2. Vertical Time Grid Lines & Labels */}
          {timeTickIndices.map((idx) => {
            const pBar = visibleBars[idx];
            if (!pBar) return null;
            const x = Math.round(xSlot(pBar.slotIndex));
            const dateStr = pBar.bar.tradingDate.slice(5).replace("-", "/"); // MM/DD
            return (
              <g key={pBar.bar.tradingDate} className="time-grid-line">
                <line
                  x1={x}
                  x2={x}
                  y1={PRICE_PANE_TOP}
                  y2={VOLUME_PANE_BOTTOM}
                  stroke={COLOR_GRID}
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

          {/* 3. Volume Section Title & Max Label */}
          <text
            x={MARGIN_LEFT + 8}
            y={VOLUME_PANE_TOP + 14}
            fill="#475569"
            fontSize="9"
            fontWeight="bold"
            letterSpacing="0.05em"
          >
            VOL (KL)
          </text>

          {/* Volume Section Y-Axis Scale Numbers (Right Side) */}
          <line
            x1={MARGIN_LEFT}
            x2={WIDTH - MARGIN_RIGHT}
            y1={VOLUME_PANE_TOP}
            y2={VOLUME_PANE_TOP}
            stroke={COLOR_GRID}
            strokeDasharray="2 3"
            strokeWidth="1"
            shapeRendering="crispEdges"
          />
          <text
            x={WIDTH - MARGIN_RIGHT + 8}
            y={VOLUME_PANE_TOP + 9}
            fill="#64748b"
            fontSize="9"
            fontFamily="JetBrains Mono, monospace"
            textAnchor="start"
          >
            {formatVolCompact(maxVol)}
          </text>

          <line
            x1={MARGIN_LEFT}
            x2={WIDTH - MARGIN_RIGHT}
            y1={Math.round(VOLUME_PANE_TOP + VOLUME_PANE_HEIGHT / 2)}
            y2={Math.round(VOLUME_PANE_TOP + VOLUME_PANE_HEIGHT / 2)}
            stroke={COLOR_GRID}
            strokeDasharray="2 3"
            strokeWidth="1"
            shapeRendering="crispEdges"
          />
          <text
            x={WIDTH - MARGIN_RIGHT + 8}
            y={Math.round(VOLUME_PANE_TOP + VOLUME_PANE_HEIGHT / 2) + 3}
            fill="#475569"
            fontSize="8.5"
            fontFamily="JetBrains Mono, monospace"
            textAnchor="start"
          >
            {formatVolCompact(maxVol / 2)}
          </text>

          <text
            x={WIDTH - MARGIN_RIGHT + 8}
            y={VOLUME_PANE_BOTTOM}
            fill="#475569"
            fontSize="8.5"
            fontFamily="JetBrains Mono, monospace"
            textAnchor="start"
          >
            0
          </text>

          {/* 4. Render Volume Bars - Slender Crisp Bars */}
          {visibleBars.map((p) => {
            const { bar, slotIndex } = p;
            const open = Number.parseFloat(bar.open);
            const close = Number.parseFloat(bar.close);
            const up = close >= open;
            const cx = Math.round(xSlot(slotIndex));
            const volY = yVolume(bar.volume || 0);
            const barH = Math.max(1, VOLUME_PANE_BOTTOM - volY);
            const color = up ? "rgba(82, 184, 154, 0.75)" : "rgba(229, 84, 84, 0.75)";

            return (
              <rect
                key={`vol-${bar.tradingDate}`}
                x={Math.round(cx - candleWidth / 2)}
                y={Math.round(volY)}
                width={candleWidth}
                height={barH}
                fill={color}
                shapeRendering="crispEdges"
              />
            );
          })}

          {/* 5. Render Price Candles OR Area Line */}
          {chartType === "LINE" ? (
            <g className="chart-line-layer">
              <path d={areaPath} fill="url(#areaGradient)" />
              <polyline
                points={linePoints}
                fill="none"
                stroke="#52b89a"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </g>
          ) : (
            <g className="chart-candle-layer">
              {visibleBars.map((p) => {
                const { bar, slotIndex } = p;
                const open = Number.parseFloat(bar.open);
                const close = Number.parseFloat(bar.close);
                const high = Number.parseFloat(bar.high);
                const low = Number.parseFloat(bar.low);
                const up = close >= open;
                const color = up ? COLOR_UP : COLOR_DOWN;
                const bodyTop = yPrice(Math.max(open, close));
                const bodyHeight = Math.max(1, Math.abs(yPrice(open) - yPrice(close)));
                const cx = Math.round(xSlot(slotIndex));

                return (
                  <g
                    key={bar.tradingDate}
                    className={up ? "candle up" : "candle down"}
                  >
                    {/* Candle Upper & Lower Wick - 1px pixel-crisp line */}
                    <line
                      x1={cx}
                      x2={cx}
                      y1={Math.round(yPrice(high))}
                      y2={Math.round(yPrice(low))}
                      stroke={color}
                      strokeWidth={1}
                      shapeRendering="crispEdges"
                    />

                    {/* Candle Body - Solid Slender Rect without border radius */}
                    <rect
                      x={Math.round(cx - candleWidth / 2)}
                      y={Math.round(bodyTop)}
                      width={candleWidth}
                      height={bodyHeight}
                      fill={color}
                      stroke={color}
                      strokeWidth={1}
                      shapeRendering="crispEdges"
                    />
                  </g>
                );
              })}
            </g>
          )}

          {/* 6. Current Price Guideline (Đường giá hiện tại) */}
          {latestY >= PRICE_PANE_TOP && latestY <= PRICE_PANE_BOTTOM && (
            <g className="current-price-guideline">
              <line
                x1={MARGIN_LEFT}
                x2={WIDTH - MARGIN_RIGHT}
                y1={Math.round(latestY)}
                y2={Math.round(latestY)}
                stroke={COLOR_PRICE_LINE}
                strokeDasharray="1 1.5"
                strokeWidth="1"
                shapeRendering="crispEdges"
              />
              <rect
                x={WIDTH - MARGIN_RIGHT + 2}
                y={Math.round(latestY) - 9}
                width={MARGIN_RIGHT - 4}
                height="18"
                rx="2"
                fill={COLOR_UP}
              />
              <text
                x={WIDTH - MARGIN_RIGHT + 6}
                y={Math.round(latestY) + 4}
                fill="#ffffff"
                fontSize="10"
                fontWeight="bold"
                fontFamily="JetBrains Mono, monospace"
              >
                {formatChartPrice(latestClose)}
              </text>
            </g>
          )}

          {/* 7. Interactive Crosshair & Cursor Hover Badges */}
          {hoverSlot !== null && (
            <g className="crosshair-layer pointer-events-none">
              {/* Vertical Crosshair Line */}
              <line
                x1={Math.round(xSlot(hoverSlot))}
                x2={Math.round(xSlot(hoverSlot))}
                y1={PRICE_PANE_TOP}
                y2={VOLUME_PANE_BOTTOM}
                stroke="#64748b"
                strokeDasharray="2 2"
                strokeWidth="1"
                shapeRendering="crispEdges"
              />

              {/* Bottom Date Badge */}
              {hoveredBarObj && (
                <>
                  <rect
                    x={Math.round(xSlot(hoverSlot)) - 38}
                    y={TIME_AXIS_Y - 14}
                    width="76"
                    height="18"
                    rx="2"
                    fill="#1e293b"
                    stroke="#475569"
                    strokeWidth="1"
                  />
                  <text
                    x={Math.round(xSlot(hoverSlot))}
                    y={TIME_AXIS_Y - 1}
                    fill="#f8fafc"
                    fontSize="10"
                    fontWeight="600"
                    fontFamily="JetBrains Mono, monospace"
                    textAnchor="middle"
                  >
                    {hoveredBarObj.bar.tradingDate}
                  </text>
                </>
              )}

              {/* Horizontal Crosshair Line */}
              {hoverY !== null && (
                <>
                  <line
                    x1={MARGIN_LEFT}
                    x2={WIDTH - MARGIN_RIGHT}
                    y1={Math.round(hoverY)}
                    y2={Math.round(hoverY)}
                    stroke="#64748b"
                    strokeDasharray="2 2"
                    strokeWidth="1"
                    shapeRendering="crispEdges"
                  />
                  {hoveredPriceVal !== null && (
                    <g>
                      <rect
                        x={WIDTH - MARGIN_RIGHT + 2}
                        y={Math.round(hoverY) - 9}
                        width={MARGIN_RIGHT - 4}
                        height="18"
                        rx="2"
                        fill="#334155"
                        stroke="#64748b"
                        strokeWidth="1"
                      />
                      <text
                        x={WIDTH - MARGIN_RIGHT + 6}
                        y={Math.round(hoverY) + 4}
                        fill="#f8fafc"
                        fontSize="10"
                        fontWeight="600"
                        fontFamily="JetBrains Mono, monospace"
                      >
                        {formatChartPrice(hoveredPriceVal)}
                      </text>
                    </g>
                  )}
                </>
              )}
            </g>
          )}
        </svg>
      </div>

      {/* Date Range & Interaction Instructions Footer */}
      <div className="chart-footer-row">
        <span className="chart-range-text">
          Dữ liệu: {visibleBars[0]?.bar.tradingDate ?? bars[0]?.tradingDate} →{" "}
          {visibleBars.at(-1)?.bar.tradingDate ?? bars.at(-1)?.tradingDate} ({visibleBars.length} phiên hiển thị)
        </span>
        <span className="chart-hint-text flex items-center gap-1.5">
          <MoveHorizontal size={13} className="text-cyan-400" />
          <span>
            <strong>Kéo chuột</strong> sang trái/phải để dịch chuyển nến · <strong>Cuộn chuột</strong> để Zoom
          </span>
        </span>
      </div>
    </section>
  );
}
