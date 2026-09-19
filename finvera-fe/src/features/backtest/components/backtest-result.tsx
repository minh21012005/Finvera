import type { CostPolicy, RunDetail } from "../api/backtest";

const COST_FIELDS: Array<[keyof Omit<CostPolicy, "excluded">, string]> = [
  ["entryFeeRate", "Phí mua"],
  ["exitFeeRate", "Phí bán"],
  ["sellTaxRate", "Thuế bán"],
  ["entrySlippageRate", "Trượt giá mua"],
  ["exitSlippageRate", "Trượt giá bán"],
];

interface WarningDetail {
  title: string;
  desc: string;
  icon: string;
}

const WARNING_DEFINITIONS: Record<string, WarningDetail> = {
  COSTS_EXCLUDED: {
    title: "Chưa trừ chi phí giao dịch & thuế",
    desc: "Mô phỏng chạy với giả định 0% phí môi giới, 0% thuế và 0% trượt giá. Kết quả phản ánh lợi nhuận gộp (Gross Return); lợi nhuận thực tế trên sàn sẽ thấp hơn do phát sinh chi phí.",
    icon: "⚡",
  },
  CURRENT_MARKET_LOT_APPLIED_HISTORICALLY: {
    title: "Áp dụng quy chuẩn lô 100 cổ phiếu hiện hành",
    desc: "Khối lượng vào lệnh luôn được làm tròn theo bội số 100 cổ phiếu (chuẩn sàn HOSE/HNX hiện hành) cho toàn bộ chu kỳ mô phỏng.",
    icon: "📦",
  },
  SURVIVORSHIP_BIAS_NOT_ELIMINATED: {
    title: "Thiên lệch sống sót (Survivorship Bias)",
    desc: "Mô phỏng chạy trên cổ phiếu đang niêm yết hiện hành, chưa tính đến các mã từng bị hủy niêm yết hoặc phá sản trong quá khứ.",
    icon: "🛡️",
  },
  SUSPENSION_DELISTING_COVERAGE_LIMITED: {
    title: "Giới hạn dữ liệu ngừng giao dịch đột xuất",
    desc: "Dữ liệu lịch sử sử dụng các phiên giao dịch thực tế từ Sở; tự động bỏ qua các phiên không phát sinh khớp lệnh.",
    icon: "⏱️",
  },
  PROVIDER_ADJUSTED_EXECUTION_BASIS: {
    title: "Khớp lệnh trên nền giá điều chỉnh kỹ thuật (Adjusted OHLC)",
    desc: "Chuỗi giá đã được điều chỉnh sau chia cổ tức/thưởng cổ phiếu để các chỉ báo kỹ thuật (EMA, RSI) hoạt động chuẩn xác, không bị gãy đồ thị.",
    icon: "📈",
  },
};

const RUN_REASON_EXPLANATIONS: Record<string, string> = {
  INVALID_LEVELS: "Mức giá cắt lỗ hoặc chỉ báo ATR chưa đủ điều kiện tính toán",
  INSUFFICIENT_HISTORY: "Dữ liệu lịch sử nến chưa đủ 250 phiên để tính chỉ báo kỹ thuật",
  UNSUPPORTED_CORPORATE_ACTION: "Phát sinh sự kiện quyền (cổ tức/thưởng cổ phiếu) làm đổi hệ số điều chỉnh khi đang mở vị thế",
  CORPORATE_ACTION_UNSUPPORTED: "Phát sinh sự kiện quyền (cổ tức/thưởng cổ phiếu) làm đổi hệ số điều chỉnh khi đang mở vị thế",
  INCOHERENT_TRADING_CALENDAR: "Ngày giao dịch không nằm trong lịch giao dịch chính thức của sàn",
  PRICE_HISTORY_UNAVAILABLE: "Không tìm thấy dữ liệu giá của mã trong khoảng thời gian đã chọn",
  ADJUSTMENT_BASIS_UNAVAILABLE: "Không xác định được trạng thái điều chỉnh giá",
  BACKTEST_EXECUTION_FAILED: "Lỗi trong quá trình thực thi mô phỏng",
  WORKER_ATTEMPTS_EXHAUSTED: "Đã thử xử lý lại nhiều lần nhưng không thành công",
};

import { Trash2 } from "lucide-react";

export function BacktestResult({ run, onDelete }: { run: RunDetail; onDelete?: () => void }) {
  const isCompleted = run.status === "COMPLETED";
  const isRunning = run.status === "RUNNING";

  return (
    <section
      className="panel bg-slate-900/70 border border-slate-800 rounded-xl p-6 shadow-xl mb-6 space-y-6"
      aria-labelledby="backtest-result-title"
    >
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 pb-4 border-b border-slate-800">
        <div>
          <h2 id="backtest-result-title" className="text-xl font-bold text-white">
            Kết quả {run.symbol}
          </h2>
          <p className="text-xs text-slate-400 mt-1">
            Trạng thái:{" "}
            <strong
              className={`px-2 py-0.5 rounded text-xs font-mono font-bold uppercase ${
                isCompleted
                  ? "bg-emerald-500/20 text-emerald-300 border border-emerald-500/40"
                  : isRunning
                  ? "bg-amber-500/20 text-amber-300 border border-amber-500/40 animate-pulse"
                  : "bg-rose-500/20 text-rose-300 border border-rose-500/40"
              }`}
            >
              {run.status}
            </strong>{" "}
            · {run.processedSessions}/{run.totalSessions ?? "?"} phiên
          </p>
        </div>
        <div className="flex items-center gap-3">
          <div className="text-xs text-slate-400 font-mono">
            Strategy: <span className="text-cyan-400 font-bold">{run.strategyCode}</span>
          </div>
          {onDelete && (
            <button
              type="button"
              onClick={onDelete}
              className="flex items-center gap-1 px-2.5 py-1 rounded bg-slate-800/80 hover:bg-rose-950/40 text-slate-400 hover:text-rose-300 border border-slate-700/60 hover:border-rose-800/60 text-xs transition-colors"
              title="Xóa lượt chạy này"
            >
              <Trash2 size={12} />
              <span>Xóa</span>
            </button>
          )}
        </div>
      </div>

      {run.reasonCode && (
        <div role="alert" className="p-3.5 rounded-lg bg-rose-950/40 border border-rose-800/60 text-xs text-rose-300 font-medium flex items-start gap-2">
          <span className="text-sm select-none leading-none pt-0.5">⚠️</span>
          <div>
            <span className="font-bold text-rose-200">Lý do: </span>
            <span>{RUN_REASON_EXPLANATIONS[run.reasonCode] ?? run.reasonCode}</span>
            <span className="ml-1 text-[11px] font-mono text-rose-400/80">({run.reasonCode})</span>
          </div>
        </div>
      )}

      {run.warnings.length > 0 && (
        <div className="rounded-xl bg-slate-950/50 border border-slate-800 p-4 space-y-2.5">
          <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-amber-400/90 pb-1">
            <span>⚠️</span>
            <span>Giả định & Lưu ý phương pháp luận ({run.warnings.length})</span>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-2.5">
            {run.warnings.map((warning) => {
              const def = WARNING_DEFINITIONS[warning] ?? {
                title: warning,
                desc: "Lưu ý định lượng từ hệ thống backtest.",
                icon: "⚠️",
              };
              return (
                <div
                  key={warning}
                  role="status"
                  className="flex items-start gap-2.5 p-3 rounded-lg bg-slate-900/70 border border-slate-800/80 text-xs hover:border-slate-700 transition-colors"
                >
                  <span className="text-base leading-none pt-0.5 select-none">{def.icon}</span>
                  <div className="space-y-1 min-w-0 flex-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="font-semibold text-slate-200">{def.title}</span>
                      <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-amber-500/10 text-amber-400 border border-amber-500/20">
                        Cảnh báo: {warning}
                      </span>
                    </div>
                    <p className="text-slate-400 leading-relaxed text-[11px]">{def.desc}</p>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      <div>
        <h3 className="text-sm font-bold text-slate-200 uppercase tracking-wider mb-3">Chỉ số</h3>
        {run.metrics.length === 0 ? (
          <p className="p-4 rounded-lg bg-slate-950/40 border border-slate-800 text-xs text-slate-400 text-center">
            Không có chỉ số được công bố cho run này.
          </p>
        ) : (
          <div className="overflow-x-auto rounded-lg border border-slate-800">
            <table className="terminal-data-table">
              <thead>
                <tr>
                  <th>Chỉ số</th>
                  <th>Giá trị</th>
                  <th>Khả dụng</th>
                </tr>
              </thead>
              <tbody>
                {run.metrics.map((metric) => (
                  <tr key={metric.code}>
                    <td className="font-mono font-bold text-slate-200">{metric.code}</td>
                    <td className="font-mono text-cyan-300 font-bold">{metric.value ?? "—"}</td>
                    <td className="text-xs text-slate-400">
                      {metric.availability}
                      {metric.reasonCode ? ` (${metric.reasonCode})` : ""}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {run.metrics.some((m) => m.reasonCode === "NO_CLOSED_TRADES") && (
          <div className="mt-3 p-3 rounded-lg bg-slate-950/60 border border-slate-800 text-xs text-slate-300">
            <span className="text-cyan-400 font-semibold">ℹ️ Lưu ý về lượt chạy 0 lệnh:</span> Chiến lược không xuất hiện tín hiệu kích hoạt vị thế trong chu kỳ này. Theo chuẩn định lượng nghiêm ngặt, các chỉ số tỷ lệ (Win Rate, Profit Factor, Average Return) được đánh dấu <code>UNAVAILABLE (NO_CLOSED_TRADES)</code> và Sharpe Ratio là <code>UNAVAILABLE (ZERO_RETURN_VARIANCE)</code> để tránh chia cho 0 hoặc ngụy tạo số liệu.
          </div>
        )}
      </div>

      <div className="rounded-xl bg-slate-950/40 border border-slate-800 p-5">
        <h3 className="text-sm font-bold text-slate-200 uppercase tracking-wider mb-3">Giả định và chi phí</h3>
        <dl className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 text-xs">
          <div className="p-2.5 rounded bg-slate-900/60 border border-slate-800/60">
            <dt className="text-slate-400 mb-0.5">Vốn ban đầu</dt>
            <dd className="font-mono font-bold text-slate-200">{run.assumptions.initialCapitalVnd} VND</dd>
          </div>
          <div className="p-2.5 rounded bg-slate-900/60 border border-slate-800/60">
            <dt className="text-slate-400 mb-0.5">Rủi ro mỗi tranche</dt>
            <dd className="font-mono font-bold text-slate-200">{run.assumptions.riskPerTrancheRate}</dd>
          </div>
          <div className="p-2.5 rounded bg-slate-900/60 border border-slate-800/60">
            <dt className="text-slate-400 mb-0.5">Rủi ro mở tối đa</dt>
            <dd className="font-mono font-bold text-slate-200">{run.assumptions.maxAggregateOpenRiskRate}</dd>
          </div>
          <div className="p-2.5 rounded bg-slate-900/60 border border-slate-800/60">
            <dt className="text-slate-400 mb-0.5">Chính sách chi phí</dt>
            <dd className="font-semibold text-cyan-300">
              {run.assumptions.costs.excluded ? "Loại trừ theo lựa chọn của người dùng" : "Đã áp dụng"}
            </dd>
          </div>
          {!run.assumptions.costs.excluded &&
            COST_FIELDS.map(([field, label]) => (
              <CostRow key={field} label={label} value={run.assumptions.costs[field]} />
            ))}
          <div className="p-2.5 rounded bg-slate-900/60 border border-slate-800/60">
            <dt className="text-slate-400 mb-0.5">Khớp lệnh</dt>
            <dd className="text-slate-200 font-medium">Open của phiên đủ điều kiện kế tiếp</dd>
          </div>
          <div className="p-2.5 rounded bg-slate-900/60 border border-slate-800/60">
            <dt className="text-slate-400 mb-0.5">Cùng chạm stop/target</dt>
            <dd className="text-slate-200 font-medium">Ưu tiên stop để tránh đánh giá quá lạc quan</dd>
          </div>
          <div className="p-2.5 rounded bg-slate-900/60 border border-slate-800/60">
            <dt className="text-slate-400 mb-0.5">Số tranche tối đa</dt>
            <dd className="font-mono font-bold text-slate-200">{run.assumptions.maxOpenTranches}</dd>
          </div>
          <div className="p-2.5 rounded bg-slate-900/60 border border-slate-800/60">
            <dt className="text-slate-400 mb-0.5">Bước pyramiding</dt>
            <dd className="font-mono font-bold text-slate-200">{run.assumptions.pyramidStepAtr} ATR</dd>
          </div>
        </dl>
      </div>

      <div className="rounded-xl bg-slate-950/40 border border-slate-800 p-5">
        <h3 className="text-sm font-bold text-slate-200 uppercase tracking-wider mb-3">Phiên bản quy tắc</h3>
        <dl className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3 text-xs font-mono">
          <div className="p-2 rounded bg-slate-900/50 border border-slate-800/50">
            <dt className="text-slate-400 text-[11px] mb-0.5">Strategy</dt>
            <dd className="text-slate-200">{run.assumptions.strategyRuleVersion}</dd>
          </div>
          <div className="p-2 rounded bg-slate-900/50 border border-slate-800/50">
            <dt className="text-slate-400 text-[11px] mb-0.5">Position sizing</dt>
            <dd className="text-slate-200">{run.assumptions.sizingRuleVersion}</dd>
          </div>
          <div className="p-2 rounded bg-slate-900/50 border border-slate-800/50">
            <dt className="text-slate-400 text-[11px] mb-0.5">Engine</dt>
            <dd className="text-slate-200">{run.assumptions.engineRuleVersion}</dd>
          </div>
          <div className="p-2 rounded bg-slate-900/50 border border-slate-800/50">
            <dt className="text-slate-400 text-[11px] mb-0.5">Metrics</dt>
            <dd className="text-slate-200">{run.assumptions.metricsRuleVersion}</dd>
          </div>
          <div className="p-2 rounded bg-slate-900/50 border border-slate-800/50">
            <dt className="text-slate-400 text-[11px] mb-0.5">Pyramiding</dt>
            <dd className="text-slate-200">{run.assumptions.pyramidingRuleVersion}</dd>
          </div>
        </dl>
      </div>

      <div className="rounded-xl bg-slate-950/40 border border-slate-800 p-5">
        <h3 className="text-sm font-bold text-slate-200 uppercase tracking-wider mb-2">Nguồn và bằng chứng</h3>
        <p className="text-xs text-slate-400 mb-3 font-mono">Cutoff dữ liệu: {run.dataCutoffAcceptedAt}</p>
        {run.evidence.length === 0 ? (
          <p className="text-xs text-slate-500">Không có bằng chứng bổ sung.</p>
        ) : (
          <div className="overflow-x-auto rounded-lg border border-slate-800">
            <table className="terminal-data-table">
              <thead>
                <tr>
                  <th>Thuộc tính</th>
                  <th>Giá trị</th>
                  <th>Đơn vị</th>
                </tr>
              </thead>
              <tbody>
                {run.evidence.map((item) => (
                  <tr key={item.key}>
                    <td className="font-mono font-bold text-slate-300">{item.key}</td>
                    <td className="font-mono text-cyan-300">{item.value}</td>
                    <td className="text-xs text-slate-400">{item.unit}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="rounded-lg bg-amber-950/20 border border-amber-800/30 p-4">
        <h3 className="text-xs font-bold uppercase tracking-wider text-amber-300 mb-1.5">Giới hạn dữ liệu</h3>
        <p className="text-xs text-slate-300 leading-relaxed">
          Corporate action: run bị WITHHELD nếu adjustment factor đổi khi chờ vào hoặc đang giữ vị thế.
        </p>
      </div>
    </section>
  );
}

function CostRow({ label, value }: { label: string; value: string | undefined }) {
  return (
    <div className="p-2.5 rounded bg-slate-900/60 border border-slate-800/60">
      <dt className="text-slate-400 mb-0.5">{label}</dt>
      <dd className="font-mono font-bold text-slate-200">{value ?? "Không khả dụng"}</dd>
    </div>
  );
}
