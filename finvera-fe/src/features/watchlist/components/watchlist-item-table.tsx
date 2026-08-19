import type { WatchlistItem } from "../api/watchlist";
import { navigate } from "../../../router";

interface WatchlistItemTableProps {
  items: WatchlistItem[];
  onRemove: (symbol: string) => Promise<void>;
}

export function WatchlistItemTable({ items, onRemove }: WatchlistItemTableProps) {
  if (items.length === 0) {
    return (
      <div style={{ padding: "32px", textAlign: "center", background: "var(--bg-card)", border: "1px solid var(--border-color)", borderRadius: "8px" }}>
        <p style={{ color: "var(--text-secondary)", marginBottom: "8px" }}>Danh sách này chưa có mã cổ phiếu nào.</p>
        <p style={{ fontSize: "0.9rem", color: "var(--text-muted)", margin: 0 }}>Hãy nhập mã cổ phiếu vào ô phía trên để bắt đầu theo dõi.</p>
      </div>
    );
  }

  function formatPrice(val: string | null) {
    if (!val) return "—";
    const num = Number(val);
    if (isNaN(num)) return val;
    return num.toLocaleString("vi-VN") + " ₫";
  }

  function formatChange(val: string | null) {
    if (!val) return { text: "—", sign: "" };
    const num = Number(val);
    if (isNaN(num)) return { text: val, sign: "" };
    if (num > 0) return { text: `(+) +${num.toFixed(2)}%`, sign: "up" };
    if (num < 0) return { text: `(-) ${num.toFixed(2)}%`, sign: "down" };
    return { text: `(0) 0.00%`, sign: "ref" };
  }

  function renderTrend(trend: string | null, reasonCode: string | null) {
    if (reasonCode === "INSUFFICIENT_HISTORY" || !trend) {
      return <span style={{ color: "var(--text-muted)", fontSize: "0.85rem" }}>Chưa đủ dữ liệu</span>;
    }
    if (trend === "BULLISH") {
      return <span style={{ color: "var(--color-up)", fontWeight: 600 }}>Tăng (Bullish)</span>;
    }
    if (trend === "BEARISH") {
      return <span style={{ color: "var(--color-down)", fontWeight: 600 }}>Giảm (Bearish)</span>;
    }
    return <span>{trend}</span>;
  }

  function renderVolume(vol: string | null) {
    if (!vol) return <span style={{ color: "var(--text-muted)" }}>—</span>;
    if (vol === "NORMAL") return <span>Bình thường</span>;
    if (vol === "HIGH_VOLUME") return <span style={{ fontWeight: 600 }}>Đột biến cao</span>;
    if (vol === "LOW_VOLUME") return <span style={{ color: "var(--text-muted)" }}>Thấp</span>;
    return <span>{vol}</span>;
  }

  function renderSignal(hasSignal: boolean, dir: string | null, risk: string | null) {
    if (!hasSignal || !dir) {
      return (
        <span
          data-testid="no-signal-badge"
          style={{
            fontSize: "0.8rem",
            padding: "3px 8px",
            background: "var(--bg-main)",
            border: "1px solid var(--border-color)",
            borderRadius: "4px",
            color: "var(--text-muted)",
          }}
        >
          Không có tín hiệu
        </span>
      );
    }

    const dirColor = dir === "BULLISH" || dir === "BUY" ? "var(--color-up)" : "var(--color-down)";
    const riskBadgeColor =
      risk === "LOW" ? "var(--color-up)" : risk === "HIGH" ? "var(--color-down)" : "var(--color-warn)";

    return (
      <div style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
        <span style={{ color: dirColor, fontWeight: 700, fontSize: "0.9rem" }}>{dir}</span>
        {risk && (
          <span
            data-testid="risk-badge"
            style={{
              fontSize: "0.75rem",
              color: riskBadgeColor,
              border: `1px solid ${riskBadgeColor}`,
              borderRadius: "4px",
              padding: "1px 6px",
              display: "inline-block",
              width: "fit-content",
            }}
          >
            Rủi ro: {risk}
          </span>
        )}
      </div>
    );
  }

  return (
    <div className="table-responsive" style={{ overflowX: "auto" }}>
      <table
        className="data-table"
        style={{
          width: "100%",
          borderCollapse: "collapse",
          fontSize: "0.95rem",
          background: "var(--bg-card)",
          border: "1px solid var(--border-color)",
          borderRadius: "8px",
        }}
      >
        <thead>
          <tr style={{ borderBottom: "2px solid var(--border-color)", textAlign: "left" }}>
            <th style={{ padding: "12px 16px" }}>Mã CK</th>
            <th style={{ padding: "12px 16px" }}>Tên công ty</th>
            <th style={{ padding: "12px 16px", textAlign: "right" }}>Giá hiện tại</th>
            <th style={{ padding: "12px 16px", textAlign: "right" }}>Thay đổi</th>
            <th style={{ padding: "12px 16px" }}>Xu hướng</th>
            <th style={{ padding: "12px 16px" }}>Khối lượng</th>
            <th style={{ padding: "12px 16px" }}>Tín hiệu & Rủi ro</th>
            <th style={{ padding: "12px 16px", textAlign: "center" }}>Hành động</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => {
            const chg = formatChange(item.dailyChangePercent);
            return (
              <tr
                key={item.symbol}
                data-testid={`watchlist-row-${item.symbol}`}
                style={{ borderBottom: "1px solid var(--border-color)" }}
              >
                <td style={{ padding: "12px 16px", fontWeight: 700 }}>
                  <button
                    type="button"
                    onClick={() => navigate(`/stocks/${item.symbol}`)}
                    style={{
                      background: "transparent",
                      border: "none",
                      color: "var(--color-accent)",
                      fontWeight: 700,
                      cursor: "pointer",
                      fontSize: "1rem",
                      padding: 0,
                    }}
                  >
                    {item.symbol}
                  </button>
                </td>
                <td style={{ padding: "12px 16px", color: "var(--text-secondary)" }}>{item.companyName}</td>
                <td style={{ padding: "12px 16px", textAlign: "right", fontWeight: 600 }}>
                  {formatPrice(item.currentPrice)}
                </td>
                <td
                  style={{
                    padding: "12px 16px",
                    textAlign: "right",
                    fontWeight: 600,
                    color: chg.sign === "up" ? "var(--color-up)" : chg.sign === "down" ? "var(--color-down)" : "inherit",
                  }}
                >
                  {chg.text}
                </td>
                <td style={{ padding: "12px 16px" }}>{renderTrend(item.technicalTrend, item.reasonCode)}</td>
                <td style={{ padding: "12px 16px" }}>{renderVolume(item.volumeCondition)}</td>
                <td style={{ padding: "12px 16px" }}>{renderSignal(item.hasCurrentSignal, item.signalDirection, item.riskLevel)}</td>
                <td style={{ padding: "12px 16px", textAlign: "center" }}>
                  <button
                    type="button"
                    onClick={() => onRemove(item.symbol)}
                    aria-label={`Xóa ${item.symbol} khỏi danh sách`}
                    style={{
                      padding: "4px 10px",
                      background: "transparent",
                      border: "1px solid var(--border-color)",
                      color: "var(--text-muted)",
                      borderRadius: "4px",
                      cursor: "pointer",
                      fontSize: "0.8rem",
                    }}
                  >
                    Xóa
                  </button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
