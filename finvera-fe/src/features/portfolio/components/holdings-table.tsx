import type { PortfolioDataStatus, Position } from "../api/portfolio";

interface HoldingsTableProps {
  positions: Position[];
  cashBalance: string;
  totalValue: string;
  dataStatus?: PortfolioDataStatus;
  reasonCodes?: string[];
}

/** Text cue for a non-CURRENT status; colour is never the only carrier (AGENTS.md). */
function describePortfolioStatus(status: PortfolioDataStatus | undefined, reasonCodes: string[] = []): string | null {
  switch (status) {
    case "PARTIAL":
      return reasonCodes.includes("POSITION_PRICE_UNAVAILABLE")
        ? "Thiếu giá: ít nhất một mã chưa có giá được chấp nhận — tổng dưới đây chưa bao gồm mã đó"
        : "Dữ liệu chưa đầy đủ";
    case "DELAYED":
      return "Giá trễ 1 phiên";
    case "STALE":
      return "Giá cũ nhiều phiên — tổng có thể không còn phản ánh thị trường";
    case "UNAVAILABLE":
      return "Không có giá";
    default:
      return null;
  }
}

function describePriceFreshness(status: PortfolioDataStatus): string | null {
  switch (status) {
    case "DELAYED":
      return "trễ 1 phiên";
    case "STALE":
      return "cũ";
    case "UNAVAILABLE":
      return "không có";
    default:
      return null;
  }
}

export function HoldingsTable({ positions, cashBalance, totalValue, dataStatus, reasonCodes = [] }: HoldingsTableProps) {
  const statusNote = describePortfolioStatus(dataStatus, reasonCodes);
  return (
    <div className="holdings-section" style={{ marginBottom: "32px" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "16px" }}>
        <h2 style={{ fontSize: "1.25rem", fontWeight: 700, margin: 0 }}>Danh mục nắm giữ hiện tại</h2>
        <span style={{ fontSize: "0.85rem", color: "var(--text-secondary)" }}>
          Tính toán FIFO tức thì theo sổ cái
        </span>
      </div>

      {/* Summary Cards */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: "12px", marginBottom: "16px" }}>
        <div style={{ padding: "16px", background: "var(--bg-card)", border: "1px solid var(--border-color)", borderRadius: "8px" }}>
          <span style={{ fontSize: "0.8rem", color: "var(--text-muted)", display: "block" }}>Tổng giá trị danh mục</span>
          <strong style={{ fontSize: "1.25rem" }}>{Number(totalValue).toLocaleString("vi-VN")} đ</strong>
          {statusNote && (
            <span role="status" data-testid="portfolio-data-status" style={{ display: "block", fontSize: "0.78rem", color: "var(--text-secondary)", marginTop: "4px" }}>
              ⚠ {dataStatus}: {statusNote}
            </span>
          )}
        </div>
        <div style={{ padding: "16px", background: "var(--bg-card)", border: "1px solid var(--border-color)", borderRadius: "8px" }}>
          <span style={{ fontSize: "0.8rem", color: "var(--text-muted)", display: "block" }}>Tiền mặt khả dụng</span>
          <strong style={{ fontSize: "1.25rem" }}>{Number(cashBalance).toLocaleString("vi-VN")} đ</strong>
        </div>
      </div>

      {positions.length === 0 ? (
        <div style={{ padding: "24px", textAlign: "center", background: "var(--bg-card)", border: "1px solid var(--border-color)", borderRadius: "8px", color: "var(--text-secondary)" }}>
          Hiện chưa có cổ phiếu nào trong danh mục. Hãy ghi nhận giao dịch MUA để xem danh mục nắm giữ.
        </div>
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table
            className="data-table"
            style={{
              width: "100%",
              borderCollapse: "collapse",
              background: "var(--bg-card)",
              border: "1px solid var(--border-color)",
              borderRadius: "8px",
              fontSize: "0.9rem",
            }}
          >
            <thead>
              <tr style={{ background: "var(--bg-header)", borderBottom: "1px solid var(--border-color)", textAlign: "left" }}>
                <th style={{ padding: "12px 16px" }}>Mã CK</th>
                <th style={{ padding: "12px 16px", textAlign: "right" }}>Khối lượng</th>
                <th style={{ padding: "12px 16px", textAlign: "right" }}>Giá vốn BQ</th>
                <th style={{ padding: "12px 16px", textAlign: "right" }}>Giá thị trường</th>
                <th style={{ padding: "12px 16px", textAlign: "right" }}>Lãi/Lỗ chưa thực hiện</th>
                <th style={{ padding: "12px 16px", textAlign: "right" }}>Lãi/Lỗ đã chốt</th>
                <th style={{ padding: "12px 16px", textAlign: "right" }}>Tỷ trọng</th>
              </tr>
            </thead>
            <tbody>
              {positions.map((pos) => {
                const unpl = pos.unrealizedPL ? parseFloat(pos.unrealizedPL) : null;
                const isPos = unpl !== null && unpl > 0;
                const isNeg = unpl !== null && unpl < 0;
                const allocPct = pos.allocation ? (parseFloat(pos.allocation) * 100).toFixed(2) + "%" : "-";

                return (
                  <tr key={pos.instrumentSymbol} style={{ borderBottom: "1px solid var(--border-color)" }}>
                    <td style={{ padding: "12px 16px", fontWeight: 700 }}>
                      <a
                        href={`/stocks/${pos.instrumentSymbol}`}
                        style={{ color: "var(--color-accent)", textDecoration: "none" }}
                      >
                        {pos.instrumentSymbol}
                      </a>
                    </td>
                    <td style={{ padding: "12px 16px", textAlign: "right" }}>
                      {Number(pos.quantity).toLocaleString("vi-VN")}
                    </td>
                    <td style={{ padding: "12px 16px", textAlign: "right" }}>
                      {Number(pos.averageCostBasis).toLocaleString("vi-VN")} đ
                    </td>
                    <td style={{ padding: "12px 16px", textAlign: "right" }}>
                      {pos.currentPrice ? `${Number(pos.currentPrice).toLocaleString("vi-VN")} đ` : "Chưa có"}
                      {pos.currentPrice && describePriceFreshness(pos.priceDataStatus) && (
                        <span style={{ display: "block", fontSize: "0.72rem", color: "var(--text-muted)" }}>
                          ({describePriceFreshness(pos.priceDataStatus)}{pos.priceTradingDate ? ` · ${pos.priceTradingDate}` : ""})
                        </span>
                      )}
                    </td>
                    <td style={{ padding: "12px 16px", textAlign: "right" }}>
                      {unpl !== null ? (
                        <span
                          style={{
                            fontWeight: 600,
                            color: isPos ? "var(--color-up)" : isNeg ? "var(--color-down)" : "inherit",
                          }}
                        >
                          {isPos ? "+" : ""}{Number(pos.unrealizedPL).toLocaleString("vi-VN")} đ
                          <span aria-label={isPos ? "lãi" : isNeg ? "lỗ" : "hòa vốn"} style={{ marginLeft: "4px", fontSize: "0.75rem" }}>
                            {isPos ? "(+)" : isNeg ? "(-)" : "(0)"}
                          </span>
                        </span>
                      ) : (
                        "-"
                      )}
                    </td>
                    <td style={{ padding: "12px 16px", textAlign: "right" }}>
                      {Number(pos.realizedPL).toLocaleString("vi-VN")} đ
                    </td>
                    <td style={{ padding: "12px 16px", textAlign: "right", fontWeight: 600 }}>
                      {allocPct}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
