import type { StockChart as StockChartData } from "../api/stock-detail";
import { formatAsOf } from "../format/stock-format";

const WIDTH = 640;
const HEIGHT = 240;
const PADDING = 24;

/**
 * A dependency-free inline SVG candlestick renderer. It only maps
 * already-computed accepted values to screen coordinates for display; it
 * never recomputes a financial value (plan.md's chart-rendering constraint).
 * Up candles are drawn hollow and down candles solid so direction reads
 * without relying on color alone (NFR-005).
 */
export function StockChart({ chart }: { chart: StockChartData }) {
  const { meta, bars, adjustmentStatus, window } = chart;

  if (bars.length === 0) {
    return (
      <section aria-labelledby="stock-chart-heading" className="stock-chart-card">
        <h2 id="stock-chart-heading">Biểu đồ giá ({window})</h2>
        <p role="status">Không có dữ liệu biểu đồ{meta.reasonCodes.length > 0 ? `: ${meta.reasonCodes.join(", ")}` : ""}</p>
      </section>
    );
  }

  const highs = bars.map((bar) => Number.parseFloat(bar.high));
  const lows = bars.map((bar) => Number.parseFloat(bar.low));
  const maxHigh = Math.max(...highs);
  const minLow = Math.min(...lows);
  const range = maxHigh - minLow || 1;
  const plotWidth = WIDTH - PADDING * 2;
  const plotHeight = HEIGHT - PADDING * 2;
  const step = plotWidth / bars.length;
  const candleWidth = Math.max(2, step * 0.6);

  const y = (value: number) => PADDING + plotHeight - ((value - minLow) / range) * plotHeight;
  const x = (index: number) => PADDING + step * index + step / 2;

  return (
    <section aria-labelledby="stock-chart-heading" className="stock-chart-card">
      <header>
        <h2 id="stock-chart-heading">Biểu đồ giá ({window})</h2>
        <p className="meta-item">
          Chuỗi giá {adjustmentStatus === "ADJUSTED" ? "đã điều chỉnh sự kiện doanh nghiệp" : "chưa điều chỉnh (RAW)"} · Cập nhật {formatAsOf(meta.asOf)}
        </p>
      </header>
      <svg
        role="img"
        aria-label={`Biểu đồ giá dạng nến, ${bars.length} phiên, chuỗi ${adjustmentStatus === "ADJUSTED" ? "đã điều chỉnh" : "chưa điều chỉnh"}`}
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        width="100%"
        height={HEIGHT}
      >
        {bars.map((bar, index) => {
          const open = Number.parseFloat(bar.open);
          const close = Number.parseFloat(bar.close);
          const high = Number.parseFloat(bar.high);
          const low = Number.parseFloat(bar.low);
          const up = close >= open;
          const bodyTop = y(Math.max(open, close));
          const bodyHeight = Math.max(1, Math.abs(y(open) - y(close)));
          const cx = x(index);
          return (
            <g key={bar.tradingDate} className={up ? "candle up" : "candle down"}>
              <line x1={cx} x2={cx} y1={y(high)} y2={y(low)} stroke="currentColor" strokeWidth={1} />
              <rect
                x={cx - candleWidth / 2}
                y={bodyTop}
                width={candleWidth}
                height={bodyHeight}
                fill={up ? "none" : "currentColor"}
                stroke="currentColor"
                strokeWidth={1.5}
              />
            </g>
          );
        })}
      </svg>
      <p className="chart-range">
        {bars[0].tradingDate} — {bars.at(-1)?.tradingDate}
      </p>
    </section>
  );
}
