import { useState } from "react";
import { createBacktest, STRATEGIES, type RunSummary } from "../api/backtest";
import { Play, Percent } from "lucide-react";

export function BacktestCreateForm({ onCreated }: { onCreated: (run: RunSummary) => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [exclude, setExclude] = useState(false);

  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const f = new FormData(e.currentTarget);
    const rate = (name: string) => String(f.get(name));
    try {
      onCreated(
        await createBacktest({
          strategyCode: String(f.get("strategy")) as never,
          symbol: String(f.get("symbol")).trim().toUpperCase(),
          startDate: String(f.get("start")),
          endDate: String(f.get("end")),
          initialCapitalVnd: rate("capital"),
          riskPerTrancheRate: rate("risk"),
          maxAggregateOpenRiskRate: rate("heat"),
          costs: exclude
            ? { excluded: true }
            : {
                excluded: false,
                entryFeeRate: rate("entryFee"),
                exitFeeRate: rate("exitFee"),
                sellTaxRate: rate("tax"),
                entrySlippageRate: rate("entrySlip"),
                exitSlippageRate: rate("exitSlip"),
              },
        })
      );
    } catch (x) {
      const msg = x instanceof Error ? x.message : "Không thể tạo backtest";
      if (msg.includes("NON_TRADING_DATE")) {
        setError("Ngày bắt đầu hoặc ngày kết thúc không phải là ngày có phiên giao dịch (rơi vào Thứ 7, Chủ Nhật hoặc ngày nghỉ Lễ/Tết). Vui lòng chọn một ngày mở cửa sàn thực tế (Thứ 2 – Thứ 6, ví dụ: 2024-01-02 đến 2026-09-17).");
      } else if (msg.includes("INCOMPLETE_END_SESSION")) {
        setError("Phiên giao dịch ngày kết thúc chưa đóng cửa hoàn tất. Vui lòng chọn ngày kết thúc là ngày giao dịch trước đó (sau 15:00).");
      } else if (msg.includes("INVALID_DATE_RANGE")) {
        setError("Khoảng ngày không hợp lệ. Ngày kết thúc phải sau ngày bắt đầu, không quá 10 năm và không vượt quá hôm nay.");
      } else if (msg.includes("SYMBOL_NOT_FOUND")) {
        setError("Không tìm thấy mã cổ phiếu trong hệ thống. Vui lòng kiểm tra lại mã (ví dụ: FPT, HPG, VNM, SSI).");
      } else {
        setError(msg);
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <form
      onSubmit={submit}
      className="panel bg-slate-900/60 border border-slate-800 rounded-xl p-5 shadow-lg mb-6"
      aria-label="Tạo backtest"
    >
      <div className="flex items-center justify-between mb-4 pb-3 border-b border-slate-800">
        <h2 className="text-sm font-bold text-white flex items-center gap-2 uppercase tracking-wide">
          <Play size={15} className="text-cyan-400 fill-cyan-400/20" />
          <span>Chạy backtest mới</span>
        </h2>
        <span className="text-xs text-slate-400 font-mono">Next-Open Fill</span>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-4">
        <label className="block">
          <span className="text-xs font-semibold text-slate-300 mb-1.5 block">Chiến lược</span>
          <select name="strategy" className="tx-field-input w-full font-mono text-xs">
            {STRATEGIES.map((x) => (
              <option key={x} value={x}>
                {x}
              </option>
            ))}
          </select>
        </label>

        <label className="block">
          <span className="text-xs font-semibold text-slate-300 mb-1.5 block">Mã cổ phiếu</span>
          <input
            name="symbol"
            pattern="[A-Za-z0-9]{1,10}"
            required
            defaultValue="FPT"
            placeholder="FPT"
            className="tx-field-input w-full font-mono uppercase font-bold text-cyan-400"
          />
        </label>

        <label className="block">
          <span className="text-xs font-semibold text-slate-300 mb-1.5 block">Từ ngày</span>
          <input
            name="start"
            type="date"
            defaultValue="2024-01-02"
            required
            className="tx-field-input w-full font-mono text-xs"
          />
          <span className="text-[10px] text-slate-500 mt-1 block">Thứ 2–Thứ 6, trừ Lễ/Tết</span>
        </label>

        <label className="block">
          <span className="text-xs font-semibold text-slate-300 mb-1.5 block">Đến ngày</span>
          <input
            name="end"
            type="date"
            defaultValue="2026-09-17"
            required
            className="tx-field-input w-full font-mono text-xs"
          />
          <span className="text-[10px] text-slate-500 mt-1 block">Phiên gần nhất đã đóng cửa</span>
        </label>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-4">
        <label className="block">
          <span className="text-xs font-semibold text-slate-300 mb-1.5 block">Vốn ban đầu (VND)</span>
          <input
            name="capital"
            inputMode="decimal"
            defaultValue="100000000"
            required
            className="tx-field-input w-full font-mono"
          />
        </label>

        <label className="block">
          <span className="text-xs font-semibold text-slate-300 mb-1.5 block">Risk mỗi tranche</span>
          <input
            name="risk"
            defaultValue="0.01"
            required
            className="tx-field-input w-full font-mono"
          />
        </label>

        <label className="block">
          <span className="text-xs font-semibold text-slate-300 mb-1.5 block">Tổng open risk tối đa</span>
          <input
            name="heat"
            defaultValue="0.04"
            required
            className="tx-field-input w-full font-mono"
          />
        </label>
      </div>

      <div className="pt-3 border-t border-slate-800/80 mb-4">
        <div className="flex flex-wrap items-center justify-between gap-3 p-3 rounded-xl bg-slate-950/60 border border-slate-800">
          <div className="flex items-center gap-2">
            <Percent size={14} className="text-cyan-400" />
            <span className="text-xs font-bold text-white uppercase tracking-wide">Chi phí giao dịch & Trượt giá</span>
            <span className="text-[11px] text-slate-400 font-mono hidden md:inline">
              (Chuẩn thị trường: Phí 0.15%, Thuế bán 0.1%, Trượt giá 0.1%)
            </span>
          </div>
          <label className="flex items-center gap-2 text-xs text-slate-300 cursor-pointer select-none bg-slate-900 px-3 py-1.5 rounded-lg border border-slate-800 hover:border-slate-700 transition-colors">
            <input
              type="checkbox"
              className="rounded border-slate-700 bg-slate-950 text-cyan-500 h-4 w-4"
              checked={exclude}
              onChange={(e) => setExclude(e.target.checked)}
            />
            <span className="text-xs font-medium">
              {exclude ? "Đang loại trừ chi phí (cảnh báo)" : "Tính chi phí & thuế"}
            </span>
          </label>
        </div>

        {!exclude && (
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3 mt-3 p-3.5 rounded-xl bg-slate-950/40 border border-slate-800/60">
            <label className="block">
              <span className="text-[11px] font-medium text-slate-400 mb-1.5 block">Phí mua</span>
              <input name="entryFee" defaultValue="0.0015" className="tx-field-input w-full font-mono text-xs" />
            </label>
            <label className="block">
              <span className="text-[11px] font-medium text-slate-400 mb-1.5 block">Phí bán</span>
              <input name="exitFee" defaultValue="0.0015" className="tx-field-input w-full font-mono text-xs" />
            </label>
            <label className="block">
              <span className="text-[11px] font-medium text-slate-400 mb-1.5 block">Thuế bán</span>
              <input name="tax" defaultValue="0.001" className="tx-field-input w-full font-mono text-xs" />
            </label>
            <label className="block">
              <span className="text-[11px] font-medium text-slate-400 mb-1.5 block">Trượt giá mua</span>
              <input name="entrySlip" defaultValue="0.001" className="tx-field-input w-full font-mono text-xs" />
            </label>
            <label className="block">
              <span className="text-[11px] font-medium text-slate-400 mb-1.5 block">Trượt giá bán</span>
              <input name="exitSlip" defaultValue="0.001" className="tx-field-input w-full font-mono text-xs" />
            </label>
          </div>
        )}
      </div>

      {error && (
        <p role="alert" className="p-3 mb-4 rounded-lg bg-rose-950/40 border border-rose-800/60 text-xs text-rose-300">
          {error}
        </p>
      )}

      <div className="flex justify-end">
        <button
          disabled={busy}
          className="px-5 py-2.5 rounded-lg bg-gradient-to-r from-cyan-500 to-blue-600 hover:from-cyan-400 hover:to-blue-500 text-slate-950 font-bold text-xs shadow-lg shadow-cyan-500/20 transition-all cursor-pointer flex items-center gap-1.5 disabled:opacity-50"
        >
          <Play size={14} className="fill-current" />
          <span>{busy ? "Đang tạo…" : "Chạy backtest"}</span>
        </button>
      </div>
    </form>
  );
}
