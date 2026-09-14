import type { CostPolicy, RunDetail } from "../api/backtest";

const COST_FIELDS: Array<[keyof Omit<CostPolicy, "excluded">, string]> = [
  ["entryFeeRate", "Phí mua"],
  ["exitFeeRate", "Phí bán"],
  ["sellTaxRate", "Thuế bán"],
  ["entrySlippageRate", "Trượt giá mua"],
  ["exitSlippageRate", "Trượt giá bán"],
];

export function BacktestResult({ run }: { run: RunDetail }) {
  return <section className="panel" aria-labelledby="backtest-result-title">
    <h2 id="backtest-result-title">Kết quả {run.symbol}</h2>
    <p>Trạng thái: <strong>{run.status}</strong> · {run.processedSessions}/{run.totalSessions ?? "?"} phiên</p>
    {run.reasonCode && <p role="alert">Lý do: {run.reasonCode}</p>}
    {run.warnings.map((warning) => <p key={warning} role="status">Cảnh báo: {warning}</p>)}

    <h3>Chỉ số</h3>
    {run.metrics.length === 0 ? <p>Không có chỉ số được công bố cho run này.</p> : <table>
      <thead><tr><th>Chỉ số</th><th>Giá trị</th><th>Khả dụng</th></tr></thead>
      <tbody>{run.metrics.map((metric) => <tr key={metric.code}>
        <td>{metric.code}</td><td>{metric.value ?? "—"}</td>
        <td>{metric.availability}{metric.reasonCode ? ` (${metric.reasonCode})` : ""}</td>
      </tr>)}</tbody>
    </table>}

    <h3>Giả định và chi phí</h3>
    <dl>
      <dt>Vốn ban đầu</dt><dd>{run.assumptions.initialCapitalVnd} VND</dd>
      <dt>Rủi ro mỗi tranche</dt><dd>{run.assumptions.riskPerTrancheRate}</dd>
      <dt>Rủi ro mở tối đa</dt><dd>{run.assumptions.maxAggregateOpenRiskRate}</dd>
      <dt>Chính sách chi phí</dt><dd>{run.assumptions.costs.excluded ? "Loại trừ theo lựa chọn của người dùng" : "Đã áp dụng"}</dd>
      {!run.assumptions.costs.excluded && COST_FIELDS.map(([field, label]) =>
        <CostRow key={field} label={label} value={run.assumptions.costs[field]} />)}
      <dt>Khớp lệnh</dt><dd>Open của phiên đủ điều kiện kế tiếp</dd>
      <dt>Cùng chạm stop/target</dt><dd>Ưu tiên stop để tránh đánh giá quá lạc quan</dd>
      <dt>Số tranche tối đa</dt><dd>{run.assumptions.maxOpenTranches}</dd>
      <dt>Bước pyramiding</dt><dd>{run.assumptions.pyramidStepAtr} ATR</dd>
    </dl>

    <h3>Phiên bản quy tắc</h3>
    <dl>
      <dt>Strategy</dt><dd>{run.assumptions.strategyRuleVersion}</dd>
      <dt>Position sizing</dt><dd>{run.assumptions.sizingRuleVersion}</dd>
      <dt>Engine</dt><dd>{run.assumptions.engineRuleVersion}</dd>
      <dt>Metrics</dt><dd>{run.assumptions.metricsRuleVersion}</dd>
      <dt>Pyramiding</dt><dd>{run.assumptions.pyramidingRuleVersion}</dd>
    </dl>

    <h3>Nguồn và bằng chứng</h3>
    <p>Cutoff dữ liệu: {run.dataCutoffAcceptedAt}</p>
    {run.evidence.length === 0 ? <p>Không có bằng chứng bổ sung.</p> : <table>
      <thead><tr><th>Thuộc tính</th><th>Giá trị</th><th>Đơn vị</th></tr></thead>
      <tbody>{run.evidence.map((item) => <tr key={item.key}>
        <td>{item.key}</td><td>{item.value}</td><td>{item.unit}</td>
      </tr>)}</tbody>
    </table>}

    <h3>Giới hạn dữ liệu</h3>
    <p>Corporate action: run bị WITHHELD nếu adjustment factor đổi khi chờ vào hoặc đang giữ vị thế.</p>
  </section>;
}

function CostRow({ label, value }: { label: string; value: string | undefined }) {
  return <><dt>{label}</dt><dd>{value ?? "Không khả dụng"}</dd></>;
}
