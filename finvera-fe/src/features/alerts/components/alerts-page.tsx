import { useCallback, useEffect, useState, type FormEvent } from "react";
import {
  Bell,
  Plus,
  Trash2,
  Clock,
  SlidersHorizontal,
  CheckCheck,
  Activity,
  AlertCircle,
  Inbox,
  Sparkles,
} from "lucide-react";
import * as api from "../api/alerts";

const TYPES = [
  "PRICE_ABOVE",
  "PRICE_BELOW",
  "RSI_ABOVE",
  "RSI_BELOW",
  "MACD_BULLISH_CROSS",
  "MACD_BEARISH_CROSS",
  "MA_BULLISH_CROSS",
  "MA_BEARISH_CROSS",
  "VOLUME_SPIKE",
  "BREAKOUT",
  "BREAKDOWN",
  "MARKET_REGIME_CHANGE",
  "STRATEGY_SIGNAL",
  "PORTFOLIO_CONCENTRATION_ABOVE",
  "NEW_DOCUMENT",
];

const TYPE_DESCRIPTIONS: Record<string, string> = {
  PRICE_ABOVE: "Giá đóng cửa ≥ ngưỡng",
  PRICE_BELOW: "Giá đóng cửa ≤ ngưỡng",
  RSI_ABOVE: "RSI ≥ ngưỡng quá mua",
  RSI_BELOW: "RSI ≤ ngưỡng quá bán",
  MACD_BULLISH_CROSS: "MACD cắt lên Signal (Bullish)",
  MACD_BEARISH_CROSS: "MACD cắt xuống Signal (Bearish)",
  MA_BULLISH_CROSS: "MA20 cắt lên MA50 (Golden Cross)",
  MA_BEARISH_CROSS: "MA20 cắt xuống MA50 (Death Cross)",
  VOLUME_SPIKE: "Khối lượng đột biến > 20 phiên",
  BREAKOUT: "Vượt đỉnh 20 phiên",
  BREAKDOWN: "Thủng đáy 20 phiên",
  MARKET_REGIME_CHANGE: "Chuyển trạng thái thị trường",
  STRATEGY_SIGNAL: "Tín hiệu chiến lược định lượng",
  PORTFOLIO_CONCENTRATION_ABOVE: "Tỷ trọng danh mục vượt ngưỡng",
  NEW_DOCUMENT: "Có tài liệu / BCTC mới",
};

const errorReason = (value: unknown) =>
  value instanceof api.AlertApiError ? value.reasonCode : "Không thể xử lý cảnh báo";

export function AlertsPage() {
  const [alerts, setAlerts] = useState<api.Alert[]>([]);
  const [notices, setNotices] = useState<api.Notification[]>([]);
  const [evaluations, setEvaluations] = useState<api.Evaluation[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [unread, setUnread] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [selectedType, setSelectedType] = useState(TYPES[0]);

  const load = useCallback(async () => {
    try {
      const [a, n, u] = await Promise.all([
        api.listAlerts(),
        api.listNotifications(),
        api.unreadCount(),
      ]);
      setAlerts(a.items);
      setNotices(n.items);
      setUnread(u.count);
      setError(null);
    } catch (e) {
      setError(errorReason(e));
    }
  }, []);

  useEffect(() => {
    const initial = window.setTimeout(() => void load(), 0);
    const id = window.setInterval(() => void load(), 10_000);
    return () => {
      clearTimeout(initial);
      clearInterval(id);
    };
  }, [load]);

  async function run(action: () => Promise<unknown>) {
    setBusy(true);
    try {
      await action();
      await load();
    } catch (e) {
      setError(errorReason(e));
    } finally {
      setBusy(false);
    }
  }

  async function history(id: string) {
    try {
      const page = await api.listEvaluations(id);
      setSelected(id);
      setEvaluations(page.items);
    } catch (e) {
      setError(errorReason(e));
    }
  }

  async function create(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const f = new FormData(form);
    const type = String(f.get("type"));
    const name = String(f.get("name"));
    const target = String(f.get("symbol") || "");
    const symbol = target.toUpperCase();
    const value = String(f.get("value") || "");

    let condition: api.Condition = { type };
    if (type.includes("PRICE")) {
      condition = { ...condition, symbol, threshold: value, adjustmentBasis: "RAW" };
    } else if (type.includes("RSI") || type === "VOLUME_SPIKE") {
      condition = { ...condition, symbol, threshold: value };
    } else if (type.startsWith("MACD")) {
      condition = { ...condition, symbol };
    } else if (type === "BREAKOUT" || type === "BREAKDOWN") {
      condition = { ...condition, symbol, adjustmentBasis: "RAW" };
    } else if (type.startsWith("MA_")) {
      condition = { ...condition, symbol, shortWindow: 20, longWindow: 50 };
    } else if (type === "MARKET_REGIME_CHANGE") {
      condition = { ...condition, targetLabel: value || "BULL" };
    } else if (type === "STRATEGY_SIGNAL") {
      condition = { ...condition, symbol, strategyCode: value || "TREND_FOLLOWING" };
    } else if (type === "PORTFOLIO_CONCENTRATION_ABOVE") {
      condition = { ...condition, portfolioId: target, thresholdPercent: value };
    } else {
      condition = { ...condition, symbol: symbol || undefined, documentType: value || undefined };
    }

    await run(async () => {
      await api.createAlert(name, condition);
      form.reset();
      setSelectedType(TYPES[0]);
    });
  }

  const enabledCount = alerts.filter((a) => a.enabled).length;

  return (
    <main className="app-shell quant-terminal-layout">
      {/* Header trang */}
      <header className="page-header">
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
          <div>
            <p className="eyebrow">AUTOMATION · DETERMINISTIC</p>
            <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-white flex items-center gap-3">
              <span>Cảnh báo</span>
              <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-slate-800 text-slate-400 border border-slate-700">
                {alerts.length} quy tắc
              </span>
            </h1>
            <p className="text-sm text-slate-400 mt-1 max-w-3xl">
              Theo dõi dữ liệu phiên hoàn tất và lưu bằng chứng cho từng lần kích hoạt.
            </p>
          </div>

          <div className="alerts-header-pill self-start md:self-auto">
            <Bell size={15} className={unread > 0 ? "text-amber-400 animate-pulse" : "text-cyan-400"} />
            <span>{unread} chưa đọc</span>
          </div>
        </div>
      </header>

      {/* Thông báo lỗi */}
      {error && (
        <div role="alert" className="error-card mb-6">
          <div className="flex items-center gap-2 text-rose-400 font-semibold text-sm">
            <AlertCircle size={16} />
            <span>{error}</span>
          </div>
        </div>
      )}

      {/* Form Thiết lập Cảnh báo mới */}
      <section className="terminal-panel alert-quant-card mb-8">
        <div className="flex items-center gap-2.5 mb-4 text-xs font-bold text-cyan-400 uppercase tracking-wider">
          <SlidersHorizontal size={16} />
          <h2>Tạo cảnh báo</h2>
        </div>

        <form onSubmit={create} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-12 gap-4 items-end">
          <div className="lg:col-span-3">
            <label className="alert-field-group">
              <span className="alert-field-label">Tên</span>
              <input
                name="name"
                required
                maxLength={120}
                className="tx-field-input"
                placeholder="Ví dụ: Giá VCB chạm ngưỡng"
              />
            </label>
          </div>

          <div className="lg:col-span-3">
            <label className="alert-field-group">
              <span className="alert-field-label">Điều kiện</span>
              <select
                name="type"
                value={selectedType}
                onChange={(e) => setSelectedType(e.target.value)}
                className="tx-field-input font-medium"
              >
                {TYPES.map((x) => (
                  <option key={x} value={x}>
                    {x} ({TYPE_DESCRIPTIONS[x] ?? x})
                  </option>
                ))}
              </select>
            </label>
          </div>

          <div className="lg:col-span-2">
            <label className="alert-field-group">
              <span className="alert-field-label">Mã / Portfolio ID</span>
              <input
                name="symbol"
                placeholder={selectedType === "PORTFOLIO_CONCENTRATION_ABOVE" ? "UUID Portfolio" : "VCB"}
                className="tx-field-input font-mono font-bold uppercase text-cyan-400"
              />
            </label>
          </div>

          <div className="lg:col-span-2">
            <label className="alert-field-group">
              <span className="alert-field-label">Ngưỡng / nhãn</span>
              <input
                name="value"
                placeholder={
                  selectedType.includes("PRICE")
                    ? "70000"
                    : selectedType.includes("RSI")
                    ? "70"
                    : selectedType === "VOLUME_SPIKE"
                    ? "1.5"
                    : selectedType === "MARKET_REGIME_CHANGE"
                    ? "BULL"
                    : "Giá trị"
                }
                className="tx-field-input font-mono"
              />
            </label>
          </div>

          <div className="lg:col-span-2">
            <button
              type="submit"
              className="btn-primary w-full flex items-center justify-center gap-2 h-[38px] font-semibold"
              disabled={busy}
            >
              <Plus size={15} />
              <span>Tạo</span>
            </button>
          </div>
        </form>
      </section>

      {/* 2 Cột: Định nghĩa & Hộp thư */}
      <div className="grid grid-cols-1 xl:grid-cols-2 gap-6 items-start">
        {/* Cột 1: Danh sách Quy tắc Cảnh báo */}
        <section className="terminal-panel alert-quant-card">
          <div className="flex items-center justify-between pb-3 border-b border-slate-800/80 mb-4">
            <div className="flex items-center gap-2">
              <Activity size={16} className="text-cyan-400" />
              <h2 className="text-base font-bold text-white tracking-wide">
                Định nghĩa ({alerts.length})
              </h2>
            </div>
            <div className="text-xs text-slate-400 font-medium flex items-center gap-2">
              <span className="inline-block w-2 h-2 rounded-full bg-emerald-400"></span>
              <span>{enabledCount} đang bật</span>
            </div>
          </div>

          {alerts.length === 0 ? (
            <div className="text-center py-10 px-4">
              <Sparkles size={32} className="mx-auto text-slate-600 mb-2" />
              <p className="text-slate-400 text-sm font-medium">Chưa có cảnh báo.</p>
              <p className="text-slate-500 text-xs mt-1">
                Sử dụng biểu mẫu phía trên để thiết lập quy tắc giám sát đầu tiên.
              </p>
            </div>
          ) : (
            <div className="space-y-3">
              {alerts.map((a) => (
                <article key={a.id} className="alert-item-card">
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2 flex-wrap">
                        <strong className="text-sm font-bold text-white truncate">{a.name}</strong>
                        <span
                          className={`text-[10px] font-bold px-2 py-0.5 rounded border uppercase tracking-wider ${
                            a.enabled
                              ? "bg-emerald-500/10 text-emerald-400 border-emerald-500/30"
                              : "bg-slate-800 text-slate-400 border-slate-700"
                          }`}
                        >
                          {a.enabled ? "Đang bật" : "Đã tắt"}
                        </span>
                      </div>
                      <p className="text-xs font-mono text-cyan-300 mt-1">{a.conditionSummary}</p>
                      <small className="text-[11px] text-slate-400 block mt-2 font-mono">
                        {a.enabled ? "Đang bật" : "Đã tắt"} · episode {a.episodeState} · {a.latestEvaluation?.outcome ?? "Chưa đánh giá"}{a.latestEvaluation?.reasonCode ? ` (${a.latestEvaluation.reasonCode})` : ""}
                      </small>
                    </div>

                    <div className="flex items-center gap-2 self-end sm:self-center flex-shrink-0">
                      <button
                        type="button"
                        className={`alert-action-btn ${a.enabled ? "active-state" : ""}`}
                        disabled={busy}
                        onClick={() => void run(() => api.setAlertState(a.id, !a.enabled))}
                      >
                        {a.enabled ? "Tắt" : "Bật"}
                      </button>

                      <button
                        type="button"
                        className={`alert-action-btn ${selected === a.id ? "border-cyan-500 text-cyan-400" : ""}`}
                        onClick={() => void history(a.id)}
                      >
                        <Clock size={12} />
                        <span>Lịch sử</span>
                      </button>

                      <button
                        type="button"
                        className="alert-action-btn delete-btn"
                        disabled={busy}
                        aria-label={`Xóa ${a.name}`}
                        onClick={() => void run(() => api.deleteAlert(a.id))}
                        title="Xóa cảnh báo"
                      >
                        <Trash2 size={13} />
                      </button>
                    </div>
                  </div>

                  {/* Ngăn Lịch sử Đánh giá */}
                  {selected === a.id && (
                    <div className="mt-4 pt-3 border-t border-slate-800/80" aria-label={`Lịch sử ${a.name}`}>
                      <div className="text-xs font-bold text-slate-300 mb-2 flex items-center gap-1.5">
                        <Clock size={13} className="text-cyan-400" />
                        <span>Lịch sử đánh giá gần nhất</span>
                      </div>

                      {evaluations.length === 0 ? (
                        <p className="text-xs text-slate-500 py-2">Chưa có lần đánh giá.</p>
                      ) : (
                        <div className="space-y-2">
                          {evaluations.map((x) => (
                            <details key={x.id} className="group text-xs bg-slate-950/60 border border-slate-800 rounded-lg p-2.5">
                              <summary className="cursor-pointer font-mono font-medium text-slate-300 hover:text-cyan-300 transition-colors flex items-center justify-between">
                                <span>
                                  {new Date(x.evaluatedAt).toLocaleString("vi-VN")} · {x.outcome}
                                  {x.reasonCode ? ` (${x.reasonCode})` : ""}
                                </span>
                                <span className="text-[10px] text-slate-500 group-open:rotate-180 transition-transform">▼</span>
                              </summary>
                              <pre className="overflow-auto text-[11px] font-mono text-slate-400 bg-slate-900/90 border border-slate-800 rounded p-2.5 mt-2.5 max-h-48 leading-relaxed">
                                {JSON.stringify(x.evidence, null, 2)}
                              </pre>
                            </details>
                          ))}
                        </div>
                      )}
                    </div>
                  )}
                </article>
              ))}
            </div>
          )}
        </section>

        {/* Cột 2: Hộp thư Thông báo */}
        <section className="terminal-panel alert-quant-card">
          <div className="flex items-center justify-between pb-3 border-b border-slate-800/80 mb-4">
            <div className="flex items-center gap-2">
              <Inbox size={16} className="text-cyan-400" />
              <h2 className="text-base font-bold text-white tracking-wide">Hộp thư</h2>
            </div>

            <button
              type="button"
              className="alert-action-btn"
              disabled={!unread || busy}
              onClick={() => void run(api.markAllRead)}
            >
              <CheckCheck size={13} className="text-cyan-400" />
              <span>Đánh dấu tất cả đã đọc</span>
            </button>
          </div>

          {notices.length === 0 ? (
            <div className="text-center py-10 px-4">
              <Inbox size={32} className="mx-auto text-slate-600 mb-2" />
              <p className="text-slate-400 text-sm font-medium">Chưa có thông báo.</p>
              <p className="text-slate-500 text-xs mt-1">
                Các sự kiện kích hoạt từ quy tắc cảnh báo sẽ xuất hiện tại đây.
              </p>
            </div>
          ) : (
            <div className="space-y-3 max-h-[700px] overflow-y-auto pr-1">
              {notices.map((n) => (
                <article
                  key={n.id}
                  className={`alert-notice-card ${!n.readAt ? "unread" : ""}`}
                >
                  <button
                    type="button"
                    className="block w-full text-left cursor-pointer focus:outline-none"
                    disabled={busy}
                    onClick={() => void run(() => api.markRead(n.id))}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <strong className={`text-sm font-bold ${!n.readAt ? "text-cyan-300" : "text-slate-300"}`}>
                        {n.readAt ? "" : "● "}
                        {n.title}
                      </strong>
                      {!n.readAt && (
                        <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-cyan-500/20 text-cyan-400 border border-cyan-500/40 flex-shrink-0">
                          Mới
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-slate-300 mt-1 leading-relaxed">{n.message}</p>
                    <small className="text-[11px] text-slate-500 mt-2 block font-mono">
                      {new Date(n.deliveredAt).toLocaleString("vi-VN")}
                    </small>
                  </button>

                  <details className="mt-3 pt-2 border-t border-slate-800/60 text-xs">
                    <summary className="cursor-pointer text-slate-400 hover:text-cyan-300 font-semibold transition-colors flex items-center justify-between">
                      <span>Bằng chứng</span>
                      <span className="text-[10px] text-slate-500">Chi tiết ▼</span>
                    </summary>
                    <pre className="overflow-auto text-[11px] font-mono text-slate-400 bg-slate-950/80 border border-slate-800 rounded p-2.5 mt-2 max-h-48 leading-relaxed">
                      {JSON.stringify(n.evidence, null, 2)}
                    </pre>
                  </details>
                </article>
              ))}
            </div>
          )}
        </section>
      </div>
    </main>
  );
}
