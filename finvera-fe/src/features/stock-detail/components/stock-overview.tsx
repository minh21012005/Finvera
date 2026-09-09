import type { StockOverview as StockOverviewData } from "../api/stock-detail";
import {
  dataStatusLabel,
  directionLabel,
  formatAsOf,
  formatDate,
  formatDecimal,
  formatPercent,
  formatVnd,
  formatVolume,
  sessionStateLabel,
} from "../format/stock-format";

import { ReasonCode, ReasonCodes } from "../../../shared/components/reason-codes";

export function StockOverview({ overview }: { overview: StockOverviewData }) {
  const { profile, price, session, meta } = overview;
  const direction = directionLabel(price.direction);
  const changeUnavailable = price.applicability !== "DEFINED";

  return (
    <section aria-labelledby="stock-overview-heading" className="stock-overview-card institutional-overview">
      <header className="overview-header-row">
        <div className="stock-identity">
          <div className="stock-symbol-row">
            <span className="stock-symbol-headline font-mono">{profile.symbol}</span>
            <span className="venue-tag">{profile.exchange}</span>
            {profile.sector && <span className="sector-tag">{profile.sector}</span>}
            {profile.listingStatus && profile.listingStatus !== "LISTED" && (
              <span className="listing-status-tag" role="status">
                {profile.listingStatus === "SUSPENDED"
                  ? " · Đình chỉ GD"
                  : profile.listingStatus === "HALTED"
                  ? " · Tạm ngừng GD"
                  : profile.listingStatus === "DELISTED"
                  ? " · Đã hủy niêm yết"
                  : ` · ${profile.listingStatus}`}
              </span>
            )}
          </div>
          <h2 id="stock-overview-heading" className="stock-company-name">
            {profile.companyName ?? profile.symbol}
          </h2>
          <p className="eyebrow sr-only">
            {profile.symbol} · {profile.exchange}
            {profile.sector ? ` · ${profile.sector}` : ""}
          </p>
        </div>

        <div className="overview-header-status">
          <span className={`status-pill ${meta.dataStatus.toLowerCase()}`}>
            {dataStatusLabel(meta.dataStatus)}
          </span>
          <span className="session-state-pill font-mono">
            <span className={`pulse-dot ${session.state === "OPEN" ? "open" : "closed"}`}></span>
            {sessionStateLabel(session.state)}
          </span>
        </div>
      </header>

      {/* Hero Price & Change Strip */}
      <div className="stock-price-hero-strip">
        <div className="price-primary-group">
          <p className="stock-price font-mono font-extrabold" aria-label={`Giá hiện tại ${formatDecimal(price.last)} đồng`}>
            {formatDecimal(price.last)} <span className="currency">VND</span>
          </p>

          {changeUnavailable ? (
            <p role="status" className="unavailable-msg">
              Thay đổi giá: Không có dữ liệu{price.changeBasisReason ? <> — <ReasonCode code={price.changeBasisReason} /></> : null}
            </p>
          ) : (
            <p className={`direction-badge ${direction.className}`} aria-label={`${direction.label}: ${formatDecimal(price.absoluteChange)} đồng, ${formatPercent(price.percentageChange)}`}>
              <span aria-hidden="true">{direction.icon}</span> {direction.label} {formatDecimal(price.absoluteChange)} ({formatPercent(price.percentageChange)})
            </p>
          )}
        </div>
      </div>

      {/* Grid of Essential Trading & Market Statistics (Balanced 6-col / 3x2 Grid) */}
      <dl className="stock-market-stats-grid">
        <div className="stat-cell">
          <dt>Khối lượng</dt>
          <dd className="font-mono">{formatVolume(price.volume)}</dd>
        </div>
        <div className="stat-cell">
          <dt>Giá trị giao dịch</dt>
          <dd className="font-mono">{formatVnd(price.valueVnd)}</dd>
        </div>
        <div className="stat-cell">
          <dt>Vốn hóa</dt>
          <dd className="font-mono">{formatVnd(price.marketCapVnd)}</dd>
        </div>
        <div className="stat-cell">
          <dt>CP lưu hành</dt>
          <dd className="font-mono">{profile.sharesOutstanding != null ? formatVolume(profile.sharesOutstanding) : "Không có dữ liệu"}</dd>
        </div>
        <div className="stat-cell limits-cell">
          <dt>Trần / Sàn</dt>
          <dd className="font-mono">
            {price.ceilingPrice || price.floorPrice
              ? `${formatDecimal(price.ceilingPrice ?? null)} / ${formatDecimal(price.floorPrice ?? null)}`
              : "Không có dữ liệu"}
            {price.limitState && (
              <span role="status" style={{ marginLeft: 6 }}>
                ({price.limitState === "AT_CEILING" ? "▲ đang ở giá trần" : "▼ đang ở giá sàn"})
              </span>
            )}
          </dd>
        </div>
        <div className="stat-cell">
          <dt>Room nước ngoài</dt>
          <dd className="font-mono">{price.foreignRoom != null ? formatVolume(price.foreignRoom) : "Không có dữ liệu"}</dd>
        </div>
      </dl>

      {/* Balanced Session & Timestamp Info Strip */}
      <div className="overview-session-footer font-mono">
        <div className="session-footer-item">
          <span className="footer-label">Trạng thái phiên:</span>
          <span className="footer-value">{sessionStateLabel(session.state)}</span>
        </div>
        <span className="footer-divider" aria-hidden="true">•</span>
        <div className="session-footer-item">
          <span className="footer-label">Ngày giao dịch:</span>
          <span className="footer-value">{formatDate(session.tradingDate)}</span>
        </div>
        <span className="footer-divider" aria-hidden="true">•</span>
        <div className="session-footer-item">
          <span className="footer-label">Cập nhật lúc:</span>
          <span className="footer-value text-slate-400">{formatAsOf(meta.asOf)}</span>
        </div>
      </div>

      {meta.reasonCodes.length > 0 && (
        <p className="reason-codes" role="note">
          <ReasonCodes prefix="Ghi chú: " codes={meta.reasonCodes} />
        </p>
      )}
    </section>
  );
}
