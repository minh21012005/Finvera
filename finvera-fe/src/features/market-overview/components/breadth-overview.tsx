import type { DataStatus, MarketBreadth } from "../api/market-overview";
import { formatAsOf } from "../format/market-format";

export function BreadthOverview({ breadth }: { breadth: MarketBreadth }) {
  const unavailable = breadth.dataStatus === "UNAVAILABLE";
  return (
    <section aria-labelledby="market-breadth-heading" aria-label={`Độ rộng thị trường: ${statusLabel(breadth.dataStatus)}`}>
      <h2 id="market-breadth-heading">Độ rộng thị trường</h2>
      <p>Trạng thái: {statusLabel(breadth.dataStatus)}</p>
      {unavailable ? (
        <p role="status">Không có dữ liệu độ rộng: {breadth.reasonCodes.join(", ") || "BREADTH_NOT_AVAILABLE"}</p>
      ) : (
        <>
          <dl className="breadth-grid">
            <div><dt>Tăng giá</dt><dd>{breadth.advancing}</dd></div>
            <div><dt>Giảm giá</dt><dd>{breadth.declining}</dd></div>
            <div><dt>Không đổi</dt><dd>{breadth.unchanged}</dd></div>
            <div><dt>Đủ điều kiện</dt><dd>{breadth.eligible}</dd></div>
          </dl>
          {breadth.unclassified !== null && breadth.unclassified > 0 && (
            <p role="status">{breadth.unclassified} mã chưa phân loại. Lý do: {breadth.reasonCodes.join(", ")}</p>
          )}
        </>
      )}
      <p>Universe: {breadth.universeVersion} · Cập nhật: {formatAsOf(breadth.asOf)} · Nguồn: {breadth.source.provider}</p>
    </section>
  );
}

function statusLabel(status: DataStatus): string {
  return ({ CURRENT: "Hiện tại", DELAYED: "Chậm", STALE: "Cũ", PARTIAL: "Một phần", UNAVAILABLE: "Không có dữ liệu" })[status];
}
