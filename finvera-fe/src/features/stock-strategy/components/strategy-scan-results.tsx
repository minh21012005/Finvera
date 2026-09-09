import type { ScanResponse } from "../api/stock-strategy";
import { navigate } from "../../../router";
import { formatDecimal } from "../../market-overview/format/market-format";
import { riskLevelDisplay, strategyLabel } from "../../stock-detail/format/signal-format";

interface StrategyScanResultsProps {
  result: ScanResponse;
  onPageChange?: (offset: number) => void;
  loading?: boolean;
}

export function StrategyScanResults({ result, onPageChange, loading = false }: StrategyScanResultsProps) {
  const limit = result.limit || 50;
  const offset = result.offset || 0;
  const total = result.totalMatchCount;
  const totalPages = Math.max(1, Math.ceil(total / limit));
  const currentPage = Math.floor(offset / limit) + 1;
  const startIdx = total > 0 ? offset + 1 : 0;
  const endIdx = Math.min(offset + limit, total);

  return (
    <section aria-labelledby="strategy-scan-results-heading" className="strategy-scan-results-section">
      <div className="strategy-results-header-bar">
        <div>
          <h2 id="strategy-scan-results-heading" className="strategy-results-h2">
            Kết quả quét — {strategyLabel(result.strategyCode)} ({result.totalMatchCount} mã)
          </h2>
          <p className="strategy-results-sub">
            Kịch bản tín hiệu kỹ thuật tất định tính toán dựa trên dữ liệu giao dịch thực tế sàn HSX, HNX
          </p>
        </div>
      </div>

      {result.excludedForInsufficientHistoryCount > 0 && (
        <p role="status" className="unavailable-msg">
          {result.excludedForInsufficientHistoryCount} mã bị loại do chưa đủ dữ liệu lịch sử (khác với việc không
          thỏa điều kiện chiến lược).
        </p>
      )}

      {result.matches.length === 0 ? (
        <p role="status" className="no-signal-msg">
          Không có mã cổ phiếu nào đang kích hoạt chiến lược này.
        </p>
      ) : (
        <div className="strategy-table-wrapper">
          <div className="table-responsive-wrapper">
            <table className="terminal-quant-table">
              <thead>
                <tr>
                  <th scope="col">Mã CK</th>
                  <th scope="col">Công ty</th>
                  <th scope="col" className="text-center">Sàn</th>
                  <th scope="col" className="text-right">Vùng vào lệnh</th>
                  <th scope="col" className="text-right">Dừng lỗ</th>
                  <th scope="col" className="text-right">Mục tiêu 1</th>
                  <th scope="col" className="text-right">R/R</th>
                  <th scope="col" className="text-center">Rủi ro</th>
                  <th scope="col" className="text-center">Chi tiết</th>
                </tr>
              </thead>
              <tbody>
                {result.matches.map((match) => {
                  const risk = riskLevelDisplay(match.signal.riskLevel);
                  return (
                    <tr
                      key={match.symbol}
                      className="quant-row"
                    >
                      <th scope="row">
                        <button
                          type="button"
                          className="symbol-link quant-symbol-btn"
                          onClick={(e) => {
                            e.stopPropagation();
                            navigate(`/stocks/${match.symbol}`);
                          }}
                        >
                          {match.symbol}
                        </button>
                      </th>
                      <td className="company-cell font-semibold text-slate-200">{match.companyName}</td>
                      <td className="text-center"><span className="venue-tag">{match.exchange}</span></td>
                      <td className="text-right font-mono text-cyan-400 font-semibold">
                        {formatDecimal(match.signal.entryLow)} – {formatDecimal(match.signal.entryHigh)}
                      </td>
                      <td className="text-right font-mono text-rose-400 font-semibold">
                        {formatDecimal(match.signal.stopLoss)}
                      </td>
                      <td className="text-right font-mono text-emerald-400">
                        {match.signal.target1 ? formatDecimal(match.signal.target1) : "—"}
                      </td>
                      <td className="text-right font-mono text-amber-400">
                        {match.signal.riskReward ? formatDecimal(match.signal.riskReward) : "—"}
                      </td>
                      <td className="text-center">
                        <span className={`risk-level-badge ${risk.className}`}>
                          <span aria-hidden="true">{risk.icon}</span>
                          <span>{risk.label}</span>
                        </span>
                      </td>
                      <td className="text-center">
                        <button
                          type="button"
                          className="btn-terminal-action text-xs"
                          onClick={(e) => {
                            e.stopPropagation();
                            navigate(`/stocks/${match.symbol}`);
                          }}
                        >
                          Xem biểu đồ →
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {total > limit && (
            <div className="pagination-bar" aria-label="Điều hướng phân trang">
              <span className="pagination-info">
                Hiển thị <strong>{startIdx}–{endIdx}</strong> trên tổng số <strong>{total}</strong> mã (Trang {currentPage}/{totalPages})
              </span>
              <div className="pagination-actions">
                <button
                  type="button"
                  className="btn-pagination"
                  disabled={currentPage <= 1 || loading}
                  onClick={() => onPageChange?.(offset - limit)}
                  aria-label="Trang trước"
                >
                  ← Trang trước
                </button>
                <span className="pagination-page-indicator">
                  {currentPage} / {totalPages}
                </span>
                <button
                  type="button"
                  className="btn-pagination"
                  disabled={currentPage >= totalPages || loading}
                  onClick={() => onPageChange?.(offset + limit)}
                  aria-label="Trang sau"
                >
                  Trang sau →
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      <footer className="strategy-disclaimer mt-4">
        <p className="text-xs text-muted" role="note">
          Tín hiệu chiến lược là kết quả tính toán định lượng tất định, không phải khuyến nghị đầu tư hay cam kết lợi nhuận.
        </p>
      </footer>
    </section>
  );
}
