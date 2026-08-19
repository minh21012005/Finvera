import type { ScanResponse } from "../api/stock-strategy";
import { navigate } from "../../../router";
import { formatDecimal } from "../../market-overview/format/market-format";
import { riskLevelDisplay, strategyLabel } from "../../stock-detail/format/signal-format";

export function StrategyScanResults({ result }: { result: ScanResponse }) {
  return (
    <section aria-labelledby="strategy-scan-results-heading" className="strategy-scan-results">
      <h2 id="strategy-scan-results-heading">
        Kết quả quét — {strategyLabel(result.strategyCode)} ({result.totalMatchCount} mã)
      </h2>

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
        <table>
          <thead>
            <tr>
              <th scope="col">Mã CK</th>
              <th scope="col">Công ty</th>
              <th scope="col">Sàn</th>
              <th scope="col">Vùng vào lệnh</th>
              <th scope="col">Dừng lỗ</th>
              <th scope="col">Rủi ro</th>
            </tr>
          </thead>
          <tbody>
            {result.matches.map((match) => {
              const risk = riskLevelDisplay(match.signal.riskLevel);
              return (
                <tr key={match.symbol}>
                  <th scope="row">
                    <button type="button" className="symbol-link" onClick={() => navigate(`/stocks/${match.symbol}`)}>
                      {match.symbol}
                    </button>
                  </th>
                  <td>{match.companyName}</td>
                  <td>{match.exchange}</td>
                  <td>
                    {formatDecimal(match.signal.entryLow)} – {formatDecimal(match.signal.entryHigh)}
                  </td>
                  <td>{formatDecimal(match.signal.stopLoss)}</td>
                  <td>
                    <span className={`risk-level-badge ${risk.className}`}>
                      <span aria-hidden="true">{risk.icon}</span>
                      <span>{risk.label}</span>
                    </span>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}

      <p className="disclaimer" role="note">
        Kết quả quét là kịch bản hỗ trợ ra quyết định định lượng, không phải khuyến nghị đầu tư hay đảm bảo kết quả.
      </p>
    </section>
  );
}
