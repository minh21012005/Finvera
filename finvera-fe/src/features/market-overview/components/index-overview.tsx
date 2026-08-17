import type { DataStatus, Direction, MarketIndex, MarketOverview } from "../api/market-overview";
import { formatAsOf, formatDecimal, formatVolume, formatVnd } from "../format/market-format";

const STABLE_ORDER: MarketIndex["code"][] = ["VN_INDEX", "VN30", "HNX_INDEX", "UPCOM_INDEX"];

export function IndexOverview({ overview }: { overview: MarketOverview }) {
  const indices = [...overview.indices].sort((left, right) => STABLE_ORDER.indexOf(left.code) - STABLE_ORDER.indexOf(right.code));
  return (
    <section aria-labelledby="market-indices-heading">
      <div className="section-title">
        <h2 id="market-indices-heading">Chỉ số thị trường</h2>
        <span className="meta-item">
          Phiên {sessionLabel(overview.session.state)} · Trạng thái tổng thể: {statusLabel(overview.dataStatus)}
        </span>
      </div>
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
      className="index-card"
      aria-label={`${index.displayName}: ${direction.label}; ${statusLabel(index.dataStatus)}`}
    >
      <header>
        <div>
          <h3>{index.displayName}</h3>
          <p>
            {index.venue} · <span className={`status-pill ${index.dataStatus.toLowerCase()}`}>{statusLabel(index.dataStatus)}</span>
          </p>
        </div>
        <span className={`direction-badge ${direction.className}`} aria-label={direction.label}>
          {direction.icon} {direction.label}
        </span>
      </header>

      <p className="index-value">{formatDecimal(index.value)}</p>

      {unavailable ? (
        <div className="unavailable-msg">
          <p role="status">Không có dữ liệu: {index.reasonCodes.join(", ") || "MISSING_INDEX"}</p>
        </div>
      ) : (
        <dl>
          <div>
            <dt>Thay đổi</dt>
            <dd style={{ color: direction.color }}>
              {formatDecimal(index.absoluteChange)} ({formatDecimal(index.percentageChange)}%)
            </dd>
          </div>
          <div>
            <dt>Khối lượng khớp</dt>
            <dd>{formatVolume(index.matchedVolume)}</dd>
          </div>
          <div>
            <dt>Giá trị khớp</dt>
            <dd>{formatVnd(index.matchedValueVnd)}</dd>
          </div>
          <div>
            <dt>Cập nhật</dt>
            <dd>{formatAsOf(index.asOf)}</dd>
          </div>
          <div style={{ gridColumn: "span 2" }}>
            <dt>Nguồn</dt>
            <dd>{index.source.provider}</dd>
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

function statusLabel(status: DataStatus): string {
  return ({ CURRENT: "Hiện tại", DELAYED: "Chậm", STALE: "Cũ", PARTIAL: "Một phần", UNAVAILABLE: "Không có dữ liệu" })[status];
}

function sessionLabel(state: string): string {
  return (
    {
      PRE_OPEN: "trước mở cửa",
      OPEN: "đang mở",
      BREAK: "nghỉ giữa phiên",
      INTERRUPTED: "gián đoạn",
      CLOSED: "đã đóng cửa",
      NON_TRADING_DAY: "không giao dịch",
      UNKNOWN: "chưa xác định",
    }[state] ?? "chưa xác định"
  );
}
