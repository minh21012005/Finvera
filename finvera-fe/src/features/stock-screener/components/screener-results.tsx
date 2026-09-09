import type { ScreenResponse } from "../api/stock-screener";
import { navigate } from "../../../router";
import { formatAsOf, formatDecimal } from "../../market-overview/format/market-format";
import { categoryLabel, dataStatusClassName, dataStatusLabel, matchedValueLabel } from "../format/screener-format";
import { ReasonCode } from "../../../shared/components/reason-codes";

export function ScreenerResults({ result }: { result: ScreenResponse }) {
  return (
    <section aria-labelledby="screener-results-heading" className="screener-results-section">
      <div className="screener-results-header-row">
        <div>
          <h2 id="screener-results-heading" className="screener-main-title">
            KẾT QUẢ LỌC ĐỊNH LƯỢNG ({result.totalMatchCount} MÃ)
          </h2>
          <p className="screener-sub-hint">
            Dữ liệu kết quả lọc trực tiếp từ hệ thống định lượng theo các tiêu chí đã chọn
          </p>
        </div>
      </div>

      {result.categoryDisclosures.length > 0 && (
        <ul className="category-disclosures" aria-label="Trạng thái dữ liệu theo nhóm bộ lọc">
          {result.categoryDisclosures.map((d) => (
            <li key={d.category} className={`status-pill ${dataStatusClassName(d.status)}`}>
              {categoryLabel(d.category)}: {dataStatusLabel(d.status)}
              {d.excludedCount > 0 ? ` (${d.excludedCount} mã bị loại)` : ""}
              {d.reasonCode ? <> — <ReasonCode code={d.reasonCode} /></> : null}
            </li>
          ))}
        </ul>
      )}

      {result.matches.length === 0 ? (
        <p role="status" className="unavailable-msg">
          Không có mã cổ phiếu nào thỏa điều kiện lọc.
        </p>
      ) : (
        <div className="screener-table-container">
          <table className="terminal-quant-table">
            <thead>
              <tr>
                <th scope="col">MÃ CP</th>
                <th scope="col">TÊN DOANH NGHIỆP</th>
                <th scope="col" className="text-center">SÀN</th>
                <th scope="col">NGÀNH</th>
                <th scope="col">TIÊU CHÍ KHỚP BỘ LỌC</th>
                <th scope="col" className="text-center">TRẠNG THÁI</th>
                <th scope="col" className="text-center">CHI TIẾT</th>
              </tr>
            </thead>
            <tbody>
              {result.matches.map((match) => (
                <tr key={match.symbol} className="quant-row">
                  <th scope="row" className="symbol-th">
                    <button
                      type="button"
                      className="symbol-link quant-symbol-btn"
                      onClick={() => navigate(`/stocks/${match.symbol}`)}
                    >
                      {match.symbol}
                    </button>
                  </th>
                  <td className="company-cell font-semibold text-slate-200">{match.companyName}</td>
                  <td className="text-center"><span className="venue-tag">{match.exchange}</span></td>
                  <td className="text-slate-300 text-xs">{match.sectorName ?? "—"}</td>
                  <td>
                    <div className="matched-values-tags flex flex-wrap gap-1.5">
                      {Object.entries(match.matchedValues).map(([k, v]) => (
                        <span key={k} className="matched-val-chip font-mono text-xs px-2 py-0.5 rounded bg-slate-800/80 border border-slate-700/60 text-slate-200">
                          <span className="text-slate-400">{matchedValueLabel(k)}:</span> <strong className="text-cyan-400">{formatDecimal(v)}</strong>
                        </span>
                      ))}
                    </div>
                  </td>
                  <td className="text-center">
                    <span className={`status-pill ${dataStatusClassName(match.dataStatus)}`}>
                      {dataStatusLabel(match.dataStatus)}
                    </span>
                  </td>
                  <td className="text-center">
                    <button
                      type="button"
                      className="btn-terminal-action text-xs"
                      onClick={() => navigate(`/stocks/${match.symbol}`)}
                    >
                      Xem chi tiết →
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          <div className="screener-pagination-footer flex justify-between items-center flex-wrap gap-2">
            <span className="pagination-count-text">
              Hiển thị 1–{result.matches.length} trong tổng số {result.totalMatchCount} kết quả lọc
            </span>
            {result.calculatedAt && (
              <span className="text-xs text-slate-400 font-mono">
                Tính toán lúc: {formatAsOf(result.calculatedAt)}
              </span>
            )}
          </div>
        </div>
      )}

      <p className="screener-disclaimer" role="note">
        Kết quả lọc là dữ liệu định lượng hỗ trợ nghiên cứu, không phải khuyến nghị đầu tư.
      </p>
    </section>
  );
}
