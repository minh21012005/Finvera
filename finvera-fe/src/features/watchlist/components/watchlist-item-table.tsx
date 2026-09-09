import type { WatchlistItem } from "../api/watchlist";
import { navigate } from "../../../router";

interface WatchlistItemTableProps {
  items: WatchlistItem[];
  onRemove: (symbol: string) => Promise<void>;
}

export function WatchlistItemTable({ items, onRemove }: WatchlistItemTableProps) {
  if (items.length === 0) {
    return (
      <div className="portfolio-empty-state">
        <p className="empty-title">Danh sách này chưa có mã cổ phiếu nào.</p>
        <p className="empty-sub">Hãy nhập mã cổ phiếu vào ô phía trên để bắt đầu theo dõi.</p>
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
      return <span className="text-slate-500 text-xs font-mono">Chưa đủ dữ liệu</span>;
    }
    if (trend === "BULLISH") {
      return <span className="trend-tag trend-bullish font-semibold">Tăng (Bullish)</span>;
    }
    if (trend === "BEARISH") {
      return <span className="trend-tag trend-bearish font-semibold">Giảm (Bearish)</span>;
    }
    return <span className="font-mono text-xs">{trend}</span>;
  }

  function renderVolume(vol: string | null) {
    if (!vol) return <span className="text-slate-600">—</span>;
    if (vol === "NORMAL") return <span className="text-slate-300">Bình thường</span>;
    if (vol === "HIGH_VOLUME") return <span className="vol-high font-bold text-amber-400">Đột biến cao</span>;
    if (vol === "LOW_VOLUME") return <span className="vol-low text-slate-500">Thấp</span>;
    return <span className="font-mono">{vol}</span>;
  }

  function renderSignal(hasSignal: boolean, dir: string | null, risk: string | null) {
    if (!hasSignal || !dir) {
      return (
        <span
          data-testid="no-signal-badge"
          className="no-signal-badge"
        >
          Không có tín hiệu
        </span>
      );
    }

    const dirClass = dir === "BULLISH" || dir === "BUY" ? "text-emerald-400" : "text-rose-400";
    const riskClass =
      risk === "LOW" ? "risk-low" : risk === "HIGH" ? "risk-high" : "risk-warn";

    return (
      <div className="flex flex-col gap-1">
        <span className={`font-mono font-extrabold text-sm ${dirClass}`}>{dir}</span>
        {risk && (
          <span
            data-testid="risk-badge"
            className={`risk-badge font-mono ${riskClass}`}
          >
            Rủi ro: {risk}
          </span>
        )}
      </div>
    );
  }

  return (
    <div className="port-table-wrap watchlist-table-wrap quant-terminal-card">
      <table className="terminal-quant-table watchlist-table">
        <thead>
          <tr>
            <th scope="col">Mã CK</th>
            <th scope="col">Tên công ty</th>
            <th scope="col" className="text-right">Giá hiện tại</th>
            <th scope="col" className="text-right">Thay đổi</th>
            <th scope="col">Xu hướng</th>
            <th scope="col">Khối lượng</th>
            <th scope="col">Tín hiệu & Rủi ro</th>
            <th scope="col" className="text-center">Hành động</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => {
            const chg = formatChange(item.dailyChangePercent);
            return (
              <tr
                key={item.symbol}
                data-testid={`watchlist-row-${item.symbol}`}
                className="quant-row"
              >
                <td>
                  <button
                    type="button"
                    onClick={() => navigate(`/stocks/${item.symbol}`)}
                    className="symbol-link font-mono font-extrabold text-cyan-400 hover:underline cursor-pointer"
                  >
                    {item.symbol}
                  </button>
                </td>
                <td className="company-name-cell font-semibold text-slate-200">{item.companyName}</td>
                <td className="text-right font-mono font-bold text-slate-100">
                  {formatPrice(item.currentPrice)}
                </td>
                <td
                  className={`text-right font-mono font-bold ${chg.sign === "up" ? "text-emerald-400" : chg.sign === "down" ? "text-rose-400" : "text-slate-300"}`}
                >
                  {chg.text}
                </td>
                <td>{renderTrend(item.technicalTrend, item.reasonCode)}</td>
                <td className="font-mono text-xs">{renderVolume(item.volumeCondition)}</td>
                <td>{renderSignal(item.hasCurrentSignal, item.signalDirection, item.riskLevel)}</td>
                <td className="text-center">
                  <button
                    type="button"
                    onClick={() => onRemove(item.symbol)}
                    aria-label={`Xóa ${item.symbol} khỏi danh sách`}
                    className="btn-wl-remove-symbol"
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
