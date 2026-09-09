import type { Direction, MarketIndex, MarketOverview } from "../api/market-overview";
import { formatAsOf, formatDecimal, formatVolume, formatVnd } from "../format/market-format";

import { ReasonCodes } from "../../../shared/components/reason-codes";
import { dataStatusLabel as statusLabel } from "../../../shared/format/reason-codes";
const STABLE_ORDER: MarketIndex["code"][] = ["VN_INDEX", "VN30", "HNX_INDEX", "UPCOM_INDEX"];

export function IndexOverview({ overview }: { overview: MarketOverview }) {
  const indices = [...overview.indices].sort((left, right) => STABLE_ORDER.indexOf(left.code) - STABLE_ORDER.indexOf(right.code));
  return (
    <section aria-labelledby="market-indices-heading">
      <div className="index-grid">
        {indices.map((index) => (
          <IndexCard key={index.code} index={index} />
        ))}
      </div>
    </section>
  );
}

function IndexCard({ index }: { index: MarketIndex }) {
  const direction = directionLabel(index.direction);
  const unavailable = index.dataStatus === "UNAVAILABLE";

  return (
    <article
      className="index-card quant-index-card"
      aria-label={`${index.displayName}: ${direction.label}; ${statusLabel(index.dataStatus)}`}
    >
      <header className="index-card-header">
        <div className="index-title-group">
          <div className="flex items-center gap-2">
            <h3>{index.displayName}</h3>
            <span className="venue-tag">{index.venue}</span>
          </div>
          <p className="index-status-row">
            <span className={`status-pill ${index.dataStatus.toLowerCase()}`}>{statusLabel(index.dataStatus)}</span>
          </p>
        </div>
        <span className={`direction-badge ${direction.className}`} aria-label={direction.label}>
          {direction.icon} {direction.label}
        </span>
      </header>

      <div className="index-price-row">
        <div>
          <p className="index-value font-mono">{formatDecimal(index.value)}</p>
          {!unavailable && index.absoluteChange !== null && (
            <p className={`index-change-line font-mono ${direction.className}`}>
              {index.direction === "UP" ? "+" : ""}{formatDecimal(index.absoluteChange)} ({index.direction === "UP" ? "+" : ""}{formatDecimal(index.percentageChange)}%)
            </p>
          )}
        </div>
      </div>

      {unavailable ? (
        <div className="unavailable-msg">
          <p role="status">Không có dữ liệu: <ReasonCodes codes={index.reasonCodes.length > 0 ? index.reasonCodes : ["MISSING_INDEX"]} /></p>
        </div>
      ) : (
        <dl className="index-metrics-grid">
          <div className="index-metric-cell">
            <dt>Khối lượng khớp</dt>
            <dd className="font-mono">{formatVolume(index.matchedVolume)}</dd>
          </div>
          <div className="index-metric-cell">
            <dt>Giá trị khớp</dt>
            <dd className="font-mono">{formatVnd(index.matchedValueVnd)}</dd>
          </div>
          <div className="index-metric-cell">
            <dt>Cập nhật</dt>
            <dd className="font-mono">{formatAsOf(index.asOf)}</dd>
          </div>
          <div className="index-metric-cell full">
            <dt>Nguồn</dt>
            <dd className="font-mono text-slate-400">{index.source.provider}</dd>
          </div>
        </dl>
      )}
    </article>
  );
}

function directionLabel(direction: Direction): { icon: string; label: string; className: string; color?: string } {
  switch (direction) {
    case "UP":
      return { icon: "↑", label: "Tăng", className: "up", color: "var(--color-up)" };
    case "DOWN":
      return { icon: "↓", label: "Giảm", className: "down", color: "var(--color-down)" };
    case "UNCHANGED":
      return { icon: "→", label: "Không đổi", className: "unchanged", color: "var(--color-unchanged)" };
    default:
      return { icon: "—", label: "Chưa xác định", className: "neutral" };
  }
}

