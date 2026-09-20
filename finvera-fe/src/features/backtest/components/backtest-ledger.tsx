import type { EntryEvent, Trade } from "../api/backtest";
import { ListFilter, XCircle, Info } from "lucide-react";

interface ReasonExplanation {
  label: string;
  detail: string;
}

const EVENT_OUTCOMES: Record<string, { text: string; bg: string; border: string; color: string }> = {
  REJECTED: {
    text: "Từ chối",
    bg: "bg-amber-950/40",
    border: "border-amber-800/60",
    color: "text-amber-300",
  },
  CANCELLED: {
    text: "Hủy lệnh",
    bg: "bg-rose-950/40",
    border: "border-rose-800/60",
    color: "text-rose-300",
  },
};

const EVENT_REASONS: Record<string, ReasonExplanation> = {
  PYRAMID_PRICE_STEP_NOT_MET: {
    label: "Chưa đủ bước giá gia tăng (+0.5×ATR)",
    detail: "Thị giá chưa tăng đủ biên độ an toàn so với đợt mua trước để được phép mua nhồi thêm vị thế.",
  },
  MAX_TRANCHES_REACHED: {
    label: "Đã đạt tối đa 4 đợt mua",
    detail: "Đã giải ngân đủ 4 tranches tối đa cho cổ phiếu này theo quy tắc quản lý vốn.",
  },
  NOT_NEW_SIGNAL_EPISODE: {
    label: "Tín hiệu liên tục kéo dài",
    detail: "Tín hiệu mua nối tiếp từ phiên hôm trước, chưa hình thành nhịp sóng bứt phá mới.",
  },
  BELOW_STANDARD_LOT: {
    label: "Không đủ mua 1 lô tối thiểu (100 cp)",
    detail: "Khối lượng tính theo hạn mức rủi ro nhỏ hơn 100 cổ phiếu quy định của sàn.",
  },
  INSUFFICIENT_CAPITAL_FOR_LOT: {
    label: "Tiền mặt không đủ 1 lô (100 cp)",
    detail: "Số dư tiền mặt còn lại không đủ để mua tối thiểu 1 lô 100 cổ phiếu.",
  },
  RISK_BUDGET_EXCEEDED: {
    label: "Chạm trần rủi ro danh mục (Open Risk)",
    detail: "Tổng rủi ro của các vị thế đang nắm giữ đã đạt mức tối đa cho phép.",
  },
  CASH_INSUFFICIENT: {
    label: "Thiếu tiền mặt khả dụng",
    detail: "Tiền mặt trong tài khoản không đủ để thực hiện lệnh mua này.",
  },
  EXECUTION_PRICE_UNAVAILABLE: {
    label: "Giá mở cửa không khả dụng",
    detail: "Giá mở cửa phiên kế tiếp không hợp lệ hoặc mức cắt lỗ vượt quá giá mở cửa.",
  },
  INVALID_LEVELS: {
    label: "Mức giá / ATR chưa đủ dữ liệu",
    detail: "Chưa đủ dữ liệu chuỗi nến để tính toán mức cắt lỗ và mục tiêu chốt lời.",
  },
};

const EXIT_REASONS: Record<string, { label: string; icon: string; className: string }> = {
  TARGET1: {
    label: "Chốt lời (Target 1)",
    icon: "🎯",
    className: "text-emerald-400 font-semibold",
  },
  STOP_LOSS: {
    label: "Cắt lỗ (Stop Loss)",
    icon: "🛑",
    className: "text-rose-400 font-semibold",
  },
  END_OF_PERIOD: {
    label: "Hết chu kỳ kiểm thử",
    icon: "⏱️",
    className: "text-slate-400",
  },
};

function formatVnd(val: string | number): string {
  const num = Number(val);
  if (isNaN(num)) return String(val);
  return (num > 0 ? "+" : "") + Math.round(num).toLocaleString("vi-VN") + " ₫";
}

export function BacktestLedger({ trades, events }: { trades: Trade[]; events: EntryEvent[] }) {
  return (
    <section className="panel bg-slate-900/70 border border-slate-800 rounded-xl p-6 shadow-xl mb-6 space-y-6">
      {/* Sổ giao dịch */}
      <div>
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-sm font-bold text-slate-200 uppercase tracking-wider flex items-center gap-2">
            <ListFilter size={16} className="text-cyan-400" />
            <span>Sổ giao dịch</span>
          </h2>
          <span className="text-xs font-mono text-slate-400">
            {trades.length} lệnh đã thực hiện
          </span>
        </div>

        {trades.length === 0 ? (
          <div className="p-4 rounded-lg bg-slate-950/40 border border-slate-800 text-xs text-slate-400 text-center">
            <p>Không có giao dịch nào được thực hiện trong chu kỳ backtest này.</p>
          </div>
        ) : (
          <div className="overflow-x-auto rounded-lg border border-slate-800">
            <table className="terminal-data-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Ngày tín hiệu</th>
                  <th>Ngày vào</th>
                  <th>Ngày ra</th>
                  <th>Khối lượng</th>
                  <th>Lãi / Lỗ ròng</th>
                  <th>Lý do thoát lệnh</th>
                </tr>
              </thead>
              <tbody>
                {trades.map((t) => {
                  const pnlNum = Number(t.netPnlVnd);
                  const isProfit = pnlNum > 0;
                  const isLoss = pnlNum < 0;
                  const exitMeta = EXIT_REASONS[t.exitReason] ?? {
                    label: t.exitReason,
                    icon: "ℹ️",
                    className: "text-slate-400",
                  };
                  return (
                    <tr key={t.sequence}>
                      <td className="font-mono text-xs text-slate-400">#{t.sequence}</td>
                      <td className="font-mono text-xs text-slate-300">{t.signalDate}</td>
                      <td className="font-mono text-xs text-slate-300">{t.entryDate}</td>
                      <td className="font-mono text-xs text-slate-300">{t.exitDate}</td>
                      <td className="font-mono text-xs text-slate-200 font-medium">
                        {Number(t.quantity).toLocaleString("vi-VN")} cp
                      </td>
                      <td className={`font-mono text-xs font-bold ${
                        isProfit ? "text-emerald-400" : isLoss ? "text-rose-400" : "text-slate-300"
                      }`}>
                        {formatVnd(t.netPnlVnd)}
                      </td>
                      <td className={`text-xs ${exitMeta.className} flex items-center gap-1.5 pt-3`}>
                        <span>{exitMeta.icon}</span>
                        <span>{exitMeta.label}</span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Tín hiệu không vào lệnh */}
      <div className="pt-4 border-t border-slate-800">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-1.5 mb-3">
          <h3 className="text-sm font-bold text-slate-200 uppercase tracking-wider flex items-center gap-2">
            <XCircle size={15} className="text-amber-400" />
            <span>Tín hiệu không vào lệnh ({events.length})</span>
          </h3>
          <span className="text-xs text-slate-400">
            {events.length > 0 ? "Bị chặn theo quy tắc bảo vệ vốn & bước giá gia tăng" : "Không có tín hiệu nào bị từ chối"}
          </span>
        </div>

        {events.length === 0 ? (
          <p className="text-xs text-slate-400 p-3 rounded-lg bg-slate-950/40 border border-slate-800/80">
            Tất cả tín hiệu đều được vào lệnh thành công, không có lệnh nào bị từ chối.
          </p>
        ) : (
          <ul className="space-y-2">
            {events.map((e) => {
              const outcomeMeta = EVENT_OUTCOMES[e.outcome] ?? {
                text: e.outcome,
                bg: "bg-slate-950/40",
                border: "border-slate-800",
                color: "text-slate-300",
              };
              const reasonMeta = EVENT_REASONS[e.reasonCode] ?? {
                label: e.reasonCode,
                detail: "Quy tắc định lượng từ chối thực thi lệnh.",
              };

              return (
                <li
                  key={e.sequence}
                  className="p-3 rounded-lg bg-slate-950/50 border border-slate-800/80 hover:border-slate-700/80 transition-all flex flex-col md:flex-row md:items-center justify-between gap-2.5"
                >
                  <div className="flex items-center gap-2.5 flex-wrap">
                    <span className={`px-2 py-0.5 rounded text-[11px] font-semibold border ${outcomeMeta.bg} ${outcomeMeta.border} ${outcomeMeta.color}`}>
                      {outcomeMeta.text}
                    </span>
                    <span className="font-mono text-xs text-cyan-400 font-semibold">
                      {e.signalDate}
                    </span>
                    <span className="text-xs text-white font-medium">
                      {reasonMeta.label}
                    </span>
                    <span className="text-[10px] font-mono text-slate-500">
                      ({e.reasonCode})
                    </span>
                  </div>
                  <div className="text-[11px] text-slate-400 md:text-right flex items-center gap-1.5 md:justify-end">
                    <Info size={12} className="text-slate-500 shrink-0 hidden md:inline" />
                    <span>{reasonMeta.detail}</span>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </section>
  );
}

