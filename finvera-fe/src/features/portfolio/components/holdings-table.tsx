import type { PortfolioDataStatus, Position } from "../api/portfolio";
import { dataStatusLabel, describeReasonCodes } from "../../../shared/format/reason-codes";
import { navigate } from "../../../router";

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
      return describeReasonCodes(reasonCodes, "Dữ liệu chưa đầy đủ");
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

  // Compute total unrealized & realized PL
  const totalUnrealized = positions.reduce((acc, pos) => acc + (pos.unrealizedPL ? parseFloat(pos.unrealizedPL) : 0), 0);
  const totalRealized = positions.reduce((acc, pos) => acc + (pos.realizedPL ? parseFloat(pos.realizedPL) : 0), 0);
  const numTotalVal = parseFloat(totalValue) || 0;
  const numCash = parseFloat(cashBalance) || 0;
  const stockVal = Math.max(0, numTotalVal - numCash);
  const stockPct = numTotalVal > 0 ? Math.round((stockVal / numTotalVal) * 100) : 0;
  const cashPct = numTotalVal > 0 ? 100 - stockPct : 100;

  return (
    <div className="portfolio-terminal-wrapper">
      {/* 4 Quant KPI Hero Cards */}
      <div className="portfolio-kpi-grid">
        {/* Card 1: Tổng NAV */}
        <div className="port-kpi-card">
          <div className="port-kpi-head">
            <span className="port-kpi-lbl">TỔNG TÀI SẢN (NAV)</span>
          </div>
          <div className="port-nav-val font-mono">
            <strong>{Number(totalValue).toLocaleString("vi-VN")}</strong> <span className="unit">VND</span>
          </div>
          <div className="port-sub-line text-slate-400 font-mono text-xs">
            Giá trị thực tế sổ cái
          </div>
          {statusNote && (
            <span role="status" data-testid="portfolio-data-status" className="data-status-warning">
              ⚠ {dataStatusLabel(dataStatus ?? "")}: {statusNote}
            </span>
          )}
        </div>

        {/* Card 2: Lợi Nhuận Tạm Tính */}
        <div className="port-kpi-card">
          <div className="port-kpi-head">
            <span className="port-kpi-lbl">LỢI NHUẬN TẠM TÍNH (P&L)</span>
          </div>
          <div className={`port-nav-val font-mono ${totalUnrealized >= 0 ? "text-emerald-400" : "text-rose-400"}`}>
            <strong>{totalUnrealized >= 0 ? "+" : ""}{totalUnrealized.toLocaleString("vi-VN")}</strong> <span className="unit">VND</span>
          </div>
          <div className="port-sub-line font-mono text-muted text-xs">
            Đã chốt (Realized): <strong className="text-slate-300">{totalRealized >= 0 ? "+" : ""}{totalRealized.toLocaleString("vi-VN")} VND</strong>
          </div>
        </div>

        {/* Card 3: Tiền Mặt & Cổ Phiếu */}
        <div className="port-kpi-card">
          <div className="port-kpi-head">
            <span className="port-kpi-lbl">TIỀN MẶT KHẢ DỤNG</span>
            <span className="port-ratio-tag font-mono">{cashPct}% NAV</span>
          </div>
          <div className="port-nav-val font-mono text-cyan-400">
            <strong>{numCash.toLocaleString("vi-VN")}</strong> <span className="unit">VND</span>
          </div>
          <div className="port-sub-line font-mono text-muted text-xs">
            Giá trị CP: <strong className="text-slate-300">{stockVal.toLocaleString("vi-VN")} VND ({stockPct}%)</strong>
          </div>
        </div>

        {/* Card 4: Cơ Cấu Danh Mục */}
        <div className="port-kpi-card">
          <div className="port-kpi-head">
            <span className="port-kpi-lbl">CƠ CẤU PHÂN BỔ</span>
            <span className="port-ratio-tag font-mono">{positions.length} mã CP</span>
          </div>
          <div className="alloc-values-row font-mono text-xs mb-2">
            <span className="stock-alloc-text">Cổ phiếu: {stockPct}%</span>
            <span className="cash-alloc-text">Tiền mặt: {cashPct}%</span>
          </div>
          <div className="alloc-progress-dual">
            <div className="bar-stock" style={{ width: `${stockPct}%` }} />
            <div className="bar-cash" style={{ width: `${cashPct}%` }} />
          </div>
        </div>
      </div>

      {/* Positions Table */}
      <div className="port-table-section mt-4">
        <div className="positions-header-row">
          <div className="pos-head-title">
            <h2 className="text-base font-bold text-slate-100">Vị Thế Đang Nắm Giữ ({positions.length} Cổ Phiếu)</h2>
            <span className="pos-sub-hint">Tính toán FIFO tức thì theo sổ cái giao dịch</span>
          </div>
        </div>

        {positions.length === 0 ? (
          <div className="empty-positions-box">
            Hiện chưa có cổ phiếu nào trong danh mục. Hãy ghi nhận giao dịch MUA để xem danh mục nắm giữ.
          </div>
        ) : (
          <div className="port-table-wrap">
            <table className="terminal-quant-table">
              <thead>
                <tr>
                  <th scope="col">Mã CK</th>
                  <th scope="col" className="text-right">Tỷ trọng</th>
                  <th scope="col" className="text-right">Khối lượng</th>
                  <th scope="col" className="text-right">Giá vốn BQ</th>
                  <th scope="col" className="text-right">Giá thị trường</th>
                  <th scope="col" className="text-right">Lãi/Lỗ chưa thực hiện</th>
                  <th scope="col" className="text-right">Lãi/Lỗ đã chốt</th>
                  <th scope="col" className="text-center">Chi tiết</th>
                </tr>
              </thead>
              <tbody>
                {positions.map((pos) => {
                  const unpl = pos.unrealizedPL ? parseFloat(pos.unrealizedPL) : null;
                  const isPos = unpl !== null && unpl > 0;
                  const isNeg = unpl !== null && unpl < 0;
                  const allocPct = pos.allocation ? (parseFloat(pos.allocation) * 100).toFixed(2) + "%" : "-";

                  return (
                    <tr key={pos.instrumentSymbol} className="quant-row">
                      <th scope="row" className="symbol-th">
                        <button
                          type="button"
                          className="symbol-link quant-symbol-btn"
                          onClick={() => navigate(`/stocks/${pos.instrumentSymbol}`)}
                        >
                          {pos.instrumentSymbol}
                        </button>
                      </th>
                      <td className="text-right font-mono font-semibold text-cyan-400">
                        {allocPct}
                      </td>
                      <td className="text-right font-mono text-slate-200">
                        {Number(pos.quantity).toLocaleString("vi-VN")}
                      </td>
                      <td className="text-right font-mono text-slate-300">
                        {Number(pos.averageCostBasis).toLocaleString("vi-VN")} đ
                      </td>
                      <td className="text-right font-mono">
                        {pos.currentPrice ? (
                          <span className="text-slate-100 font-semibold">
                            {Number(pos.currentPrice).toLocaleString("vi-VN")} đ
                          </span>
                        ) : (
                          <span className="text-muted">Chưa có</span>
                        )}
                        {pos.currentPrice && describePriceFreshness(pos.priceDataStatus) && (
                          <span className="price-freshness-sub">
                            ({describePriceFreshness(pos.priceDataStatus)}{pos.priceTradingDate ? ` · ${pos.priceTradingDate}` : ""})
                          </span>
                        )}
                      </td>
                      <td className="text-right font-mono font-semibold">
                        {unpl !== null ? (
                          <span className={isPos ? "text-emerald-400" : isNeg ? "text-rose-400" : "text-slate-300"}>
                            {isPos ? "+" : ""}{Number(pos.unrealizedPL).toLocaleString("vi-VN")} đ
                            <span aria-label={isPos ? "lãi" : isNeg ? "lỗ" : "hòa vốn"} className="text-xs ml-1">
                              {isPos ? "(+)" : isNeg ? "(-)" : "(0)"}
                            </span>
                          </span>
                        ) : (
                          "-"
                        )}
                      </td>
                      <td className="text-right font-mono text-slate-300">
                        {Number(pos.realizedPL).toLocaleString("vi-VN")} đ
                      </td>
                      <td className="text-center">
                        <button
                          type="button"
                          className="btn-terminal-action text-xs"
                          onClick={() => navigate(`/stocks/${pos.instrumentSymbol}`)}
                        >
                          Chi tiết CP →
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
