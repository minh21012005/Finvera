import type { ScreenResponse } from "../api/stock-screener";
import { navigate } from "../../../router";
import { formatDecimal } from "../../market-overview/format/market-format";
import { categoryLabel, dataStatusClassName, dataStatusLabel, matchedValueLabel } from "../format/screener-format";

export function ScreenerResults({ result }: { result: ScreenResponse }) {
  return (
    <section aria-labelledby="screener-results-heading" className="screener-results">
      <h2 id="screener-results-heading">
        Kết quả ({result.totalMatchCount} mã)
      </h2>

      {result.categoryDisclosures.length > 0 && (
        <ul className="category-disclosures" aria-label="Trạng thái dữ liệu theo nhóm bộ lọc">
          {result.categoryDisclosures.map((d) => (
            <li key={d.category} className={`status-pill ${dataStatusClassName(d.status)}`}>
              {categoryLabel(d.category)}: {dataStatusLabel(d.status)}
              {d.excludedCount > 0 ? ` (${d.excludedCount} mã bị loại)` : ""}
              {d.reasonCode ? ` — ${d.reasonCode}` : ""}
            </li>
          ))}
        </ul>
      )}

      {result.matches.length === 0 ? (
        <p role="status" className="unavailable-msg">
          Không có mã cổ phiếu nào thỏa điều kiện lọc.
        </p>
      ) : (
        <table>
          <thead>
            <tr>
              <th scope="col">Mã CK</th>
              <th scope="col">Công ty</th>
              <th scope="col">Sàn</th>
              <th scope="col">Ngành</th>
              <th scope="col">Giá trị khớp</th>
              <th scope="col">Trạng thái</th>
            </tr>
          </thead>
          <tbody>
            {result.matches.map((match) => (
              <tr key={match.symbol}>
                <th scope="row">
                  <button type="button" className="symbol-link" onClick={() => navigate(`/stocks/${match.symbol}`)}>
                    {match.symbol}
                  </button>
                </th>
                <td>{match.companyName}</td>
                <td>{match.exchange}</td>
                <td>{match.sectorName ?? "—"}</td>
                <td>
                  <ul className="matched-values">
                    {Object.entries(match.matchedValues).map(([key, value]) => (
                      <li key={key}>
                        {matchedValueLabel(key)}: {formatDecimal(value)}
                      </li>
                    ))}
                  </ul>
                </td>
                <td>
                  <span className={`status-pill ${dataStatusClassName(match.dataStatus)}`}>
                    {dataStatusLabel(match.dataStatus)}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <p className="screener-disclaimer" role="note">
        Kết quả lọc là dữ liệu định lượng hỗ trợ nghiên cứu, không phải khuyến nghị đầu tư.
      </p>
    </section>
  );
}
