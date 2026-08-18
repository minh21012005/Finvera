import type { StockOverview as StockOverviewData } from "../api/stock-detail";
import {
  dataStatusLabel,
  directionLabel,
  formatAsOf,
  formatDecimal,
  formatPercent,
  formatVnd,
  formatVolume,
  sessionStateLabel,
} from "../format/stock-format";

export function StockOverview({ overview }: { overview: StockOverviewData }) {
  const { profile, price, session, meta } = overview;
  const direction = directionLabel(price.direction);
  const changeUnavailable = price.applicability !== "DEFINED";

  return (
    <section aria-labelledby="stock-overview-heading" className="stock-overview-card">
      <header>
        <div>
          <p className="eyebrow">
            {profile.symbol} · {profile.exchange}
            {profile.sector ? ` · ${profile.sector}` : ""}
          </p>
          <h2 id="stock-overview-heading">{profile.companyName ?? profile.symbol}</h2>
        </div>
        <span className={`status-pill ${meta.dataStatus.toLowerCase()}`}>{dataStatusLabel(meta.dataStatus)}</span>
      </header>

      <p className="stock-price" aria-label={`Giá hiện tại ${formatDecimal(price.last)} đồng`}>
        {formatDecimal(price.last)} <span className="currency">VND</span>
      </p>

      {changeUnavailable ? (
        <p role="status" className="unavailable-msg">
          Thay đổi giá: Không có dữ liệu{price.changeBasisReason ? ` (${price.changeBasisReason})` : ""}
        </p>
      ) : (
        <p className={`direction-badge ${direction.className}`} aria-label={`${direction.label}: ${formatDecimal(price.absoluteChange)} đồng, ${formatPercent(price.percentageChange)}`}>
          <span aria-hidden="true">{direction.icon}</span> {direction.label} {formatDecimal(price.absoluteChange)} ({formatPercent(price.percentageChange)})
        </p>
      )}

      <dl>
        <div>
          <dt>Khối lượng</dt>
          <dd>{formatVolume(price.volume)}</dd>
        </div>
        <div>
          <dt>Giá trị giao dịch</dt>
          <dd>{formatVnd(price.valueVnd)}</dd>
        </div>
        <div>
          <dt>Vốn hóa</dt>
          <dd>{formatVnd(price.marketCapVnd)}</dd>
        </div>
        <div>
          <dt>Trạng thái phiên</dt>
          <dd>{sessionStateLabel(session.state)}</dd>
        </div>
        <div>
          <dt>Ngày giao dịch</dt>
          <dd>{session.tradingDate ?? "Không có dữ liệu"}</dd>
        </div>
        <div>
          <dt>Cập nhật lúc</dt>
          <dd>{formatAsOf(meta.asOf)}</dd>
        </div>
      </dl>

      {meta.reasonCodes.length > 0 && (
        <p className="reason-codes" role="note">
          Ghi chú: {meta.reasonCodes.join(", ")}
        </p>
      )}
    </section>
  );
}
