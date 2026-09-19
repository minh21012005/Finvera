import { type FormEvent, useEffect, useState } from "react";
import { listPortfolios, type PortfolioSummary } from "../../portfolio/api/portfolio";
import { getStockSignals, type Signal } from "../../stock-detail/api/stock-signals";
import {
  calculatePositionSize,
  PositionSizingApiError,
  type RiskKind,
  type SizingMode,
  type SizingRequest,
  type SizingResult,
} from "../api/position-sizing";
import {
  ShieldAlert,
  Wallet,
  Calculator,
  AlertTriangle,
  FileCheck2,
  CheckCircle2,
  Lock,
  Layers,
  Sparkles,
  Milestone,
  Target,
  ArrowUpRight,
  ShieldCheck,
} from "lucide-react";

const emptyCosts = {
  entryFeeRate: "",
  exitFeeRate: "",
  sellTaxRate: "",
  entrySlippageRate: "",
  exitSlippageRate: "",
};

const SIZING_WARNING_DEFINITIONS: Record<string, { title: string; desc: string }> = {
  COSTS_EXCLUDED: {
    title: "Đã loại trừ toàn bộ chi phí giao dịch & trượt giá",
    desc: "Tính toán phản ánh lợi nhuận gộp (Gross). Thực tế trên sàn sẽ phát sinh phí môi giới, thuế bán và trượt giá.",
  },
  ENTRY_FEE_EXCLUDED: {
    title: "Chưa tính phí môi giới mua",
    desc: "Chi phí mua thực tế có thể cao hơn một chút do phí CTCK.",
  },
  EXIT_FEE_EXCLUDED: {
    title: "Chưa tính phí môi giới bán",
    desc: "Tiền thu về khi đóng lệnh thực tế sẽ bị trừ phí CTCK.",
  },
  SELL_TAX_EXCLUDED: {
    title: "Chưa tính thuế bán chứng khoán (0.1%)",
    desc: "Thuế chuyển nhượng 0.1% sẽ được khấu trừ tự động khi bán.",
  },
  ENTRY_SLIPPAGE_EXCLUDED: {
    title: "Chưa tính trượt giá khi mua",
    desc: "Lệnh có thể khớp ở mức giá thực tế cao hơn giá kỳ vọng.",
  },
  EXIT_SLIPPAGE_EXCLUDED: {
    title: "Chưa tính trượt giá khi bán",
    desc: "Lệnh thoát có thể khớp ở mức giá thực tế thấp hơn giá kỳ vọng.",
  },
};

const SIZING_REASON_DEFINITIONS: Record<string, string> = {
  BELOW_STANDARD_LOT: "Khối lượng cho phép nhỏ hơn 1 lô tối thiểu (100 cp) theo quy định sàn",
  MARKET_LOT_RULE_UNAVAILABLE: "Mã chứng khoán không nằm trong danh mục lô chẵn hoặc chưa có quy định",
  PORTFOLIO_DATA_UNAVAILABLE: "Không truy xuất được dữ liệu danh mục của tài khoản",
  SIGNAL_NOT_CURRENT: "Tín hiệu đã quá hạn hoặc không còn hiệu lực",
};

const CONSTRAINT_NAMES: Record<string, { label: string; desc: string }> = {
  RISK_BUDGET: {
    label: "Ngân sách rủi ro",
    desc: "Khống chế số tiền lỗ tối đa nếu chạm Stop Loss",
  },
  AFFORDABILITY: {
    label: "Sức mua tiền mặt",
    desc: "Giới hạn theo số dư tiền mặt khả dụng hiện có",
  },
  SYMBOL_CONCENTRATION: {
    label: "Hạn mức tỷ trọng mã",
    desc: "Khống chế tỷ trọng cổ phiếu này trong tổng danh mục",
  },
  TOTAL_DEPLOYMENT: {
    label: "Hạn mức giải ngân toàn danh mục",
    desc: "Khống chế tổng giá trị cổ phiếu đang nắm giữ",
  },
};

const APPLICABILITY_LABELS: Record<string, string> = {
  APPLIED: "Đã áp dụng",
  NOT_APPLIED: "Không áp dụng",
  WITHHELD: "Chưa công bố",
};

export function PositionSizingPage() {
  const [mode, setMode] = useState<SizingMode>("MANUAL");
  const [symbol, setSymbol] = useState("FPT");
  const [riskKind, setRiskKind] = useState<RiskKind>("FIXED_VND");
  const [excludeCosts, setExcludeCosts] = useState(false);
  const [portfolios, setPortfolios] = useState<PortfolioSummary[]>([]);
  const [portfolioId, setPortfolioId] = useState("");
  const [costs, setCosts] = useState(emptyCosts);
  const [signal, setSignal] = useState<Signal | null>(null);
  const [originatingSignal, setOriginatingSignal] = useState<Signal | null>(null);
  const [signalConfirmed, setSignalConfirmed] = useState(false);
  const [entryBasis, setEntryBasis] = useState<"ENTRY_LOW" | "MIDPOINT" | "ENTRY_HIGH">("MIDPOINT");
  const [symbolCap, setSymbolCap] = useState("");
  const [deploymentCap, setDeploymentCap] = useState("");
  const [result, setResult] = useState<SizingResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    listPortfolios().then(setPortfolios).catch(() => setPortfolios([]));
  }, []);

  async function importSignal(sym: string) {
    setError(null);
    setSignalConfirmed(false);
    try {
      const response = await getStockSignals(sym.toUpperCase());
      const current = response.evaluations.find((e) => e.status === "SIGNAL" && e.signal)?.signal ?? null;
      setSignal(current);
      setOriginatingSignal(current);
      if (!current) setError("Không có tín hiệu LONG hiện hành cho mã này. Bạn vẫn có thể nhập giá thủ công.");
    } catch {
      setSignal(null);
      setOriginatingSignal(null);
      setError("Không thể tải tín hiệu hiện hành.");
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setResult(null);
    const data = new FormData(event.currentTarget);
    const normalizedSymbol = symbol.toUpperCase();
    const capSymbol = symbolCap;
    const capDeployment = deploymentCap;
    const request: SizingRequest = {
      mode,
      symbol: normalizedSymbol,
      ...(mode === "PORTFOLIO"
        ? { portfolioId }
        : {
            manualCapital: {
              capitalBaseVnd: String(data.get("capitalBase") ?? ""),
              availableCashVnd: String(data.get("cash") ?? ""),
              ...(capSymbol || capDeployment ? { portfolioValueVnd: String(data.get("portfolioValue") ?? "") } : {}),
              ...(capSymbol ? { existingSymbolMarketValueVnd: String(data.get("symbolValue") ?? "") } : {}),
              ...(capDeployment ? { currentDeployedMarketValueVnd: String(data.get("deployedValue") ?? "") } : {}),
            },
          }),
      riskBudget: { kind: riskKind, value: String(data.get("risk") ?? "") },
      priceInput: signal
        ? {
            source: "SIGNAL",
            strategyCode: signal.strategyCode,
            ruleVersion: signal.ruleVersion,
            calculatedAt: signal.calculatedAt,
            entryBasis,
            confirmed: signalConfirmed as true,
          }
        : {
            source: "MANUAL",
            entryPriceVnd: String(data.get("entry") ?? ""),
            stopPriceVnd: String(data.get("stop") ?? ""),
            ...(originatingSignal
              ? {
                  originatingSignalContext: {
                    strategyCode: originatingSignal.strategyCode,
                    ruleVersion: originatingSignal.ruleVersion,
                    calculatedAt: originatingSignal.calculatedAt,
                    asOfTradingDate: originatingSignal.asOfTradingDate,
                  },
                }
              : {}),
          },
      costPolicy: excludeCosts ? { excludeCosts: true } : { excludeCosts: false, ...costs },
      ...((capSymbol || capDeployment)
        ? {
            exposureLimits: {
              ...(capSymbol ? { maxSymbolConcentrationRate: capSymbol } : {}),
              ...(capDeployment ? { maxDeploymentRate: capDeployment } : {}),
            },
          }
        : {}),
    };
    try {
      setResult(await calculatePositionSize(request));
    } catch (reason) {
      setError(
        reason instanceof PositionSizingApiError
          ? `Không thể tính: ${reason.reasonCode}`
          : "Phản hồi máy chủ không đúng hợp đồng."
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="app-shell" aria-labelledby="position-sizing-title">
      <header className="page-header mb-6">
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-3">
          <div>
            <p className="eyebrow">FINVERA · QUANT RISK ENGINE</p>
            <h1 id="position-sizing-title" className="text-2xl font-bold text-white tracking-tight">
              Tính quy mô vị thế
            </h1>
            <p className="text-sm text-slate-400 mt-1 max-w-2xl">
              Kịch bản định lượng xác định theo dữ liệu và giả định bạn cung cấp. Kết quả mang tính chất hỗ trợ quyết định, không phải lệnh mua hoặc cam kết lợi nhuận.
            </p>
          </div>
          <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-lg bg-slate-900 border border-slate-800 text-xs font-semibold text-cyan-400 self-start">
            <Lock size={13} />
            <span>Quy định Lô 100 · HOSE / HNX / UPCOM</span>
          </div>
        </div>
      </header>

      <div className="sizing-layout grid grid-cols-1 xl:grid-cols-12 gap-6 items-start">
        {/* Cột Trái: Form Nhập Liệu Tham Số Đa Cột Cân Xứng (7 cols) */}
        <div className="xl:col-span-7">
          <form onSubmit={submit} className="sizing-form-card space-y-4">
            {/* Nhóm 1: Nguồn vốn & Phương thức */}
            <div className="quant-fieldset p-4 rounded-xl bg-slate-900/60 border border-slate-800 shadow-md">
              <div className="sizing-section-title flex items-center gap-2 mb-3 text-xs font-bold text-cyan-400 uppercase tracking-wider">
                <Wallet size={15} />
                <span>Nguồn vốn & Chế độ giao dịch</span>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
                <Field label="Chế độ">
                  <select
                    aria-label="Chế độ"
                    className="tx-field-input w-full"
                    value={mode}
                    onChange={(e) => {
                      setMode(e.target.value as SizingMode);
                      setSignal(null);
                      setResult(null);
                    }}
                  >
                    <option value="MANUAL">Nhập thủ công</option>
                    <option value="PORTFOLIO">Theo danh mục</option>
                  </select>
                </Field>

                <Field label="Mã cổ phiếu">
                  <input
                    className="tx-field-input w-full font-mono font-bold uppercase tracking-wider text-cyan-400"
                    name="symbol"
                    required
                    pattern="[A-Za-z0-9]{3,10}"
                    value={symbol}
                    onChange={(event) => {
                      setSymbol(event.target.value);
                      setSignal(null);
                      setOriginatingSignal(null);
                      setSignalConfirmed(false);
                    }}
                  />
                </Field>

                {mode === "PORTFOLIO" ? (
                  <div className="sm:col-span-2">
                    <Field label="Danh mục">
                      <select
                        className="tx-field-input w-full"
                        aria-label="Danh mục"
                        required
                        value={portfolioId}
                        onChange={(e) => setPortfolioId(e.target.value)}
                      >
                        <option value="">Chọn danh mục</option>
                        {portfolios.map((p) => (
                          <option value={p.id} key={p.id}>
                            {p.name} · {p.dataStatus}
                          </option>
                        ))}
                      </select>
                    </Field>
                  </div>
                ) : (
                  <>
                    <Field label="Vốn cơ sở (VND)">
                      <input className="tx-field-input w-full font-mono" inputMode="decimal" name="capitalBase" required />
                    </Field>
                    <Field label="Tiền có thể dùng (VND)">
                      <input className="tx-field-input w-full font-mono" inputMode="decimal" name="cash" required />
                    </Field>
                  </>
                )}
              </div>
            </div>

            {/* Nhóm 2: Ngân sách rủi ro và Giá */}
            <div className="quant-fieldset p-4 rounded-xl bg-slate-900/60 border border-slate-800 shadow-md">
              <div className="flex items-center justify-between mb-3">
                <div className="sizing-section-title flex items-center gap-2 text-xs font-bold text-cyan-400 uppercase tracking-wider">
                  <Calculator size={15} />
                  <span>Ngân sách rủi ro & Mức giá</span>
                </div>
                <button
                  type="button"
                  className="px-2.5 py-1 rounded bg-indigo-950/60 border border-indigo-700/50 text-xs font-semibold text-indigo-300 hover:bg-indigo-900/60 transition-all flex items-center gap-1.5"
                  onClick={() => void importSignal(symbol)}
                >
                  <Sparkles size={12} className="text-indigo-400" />
                  <span>Nạp tín hiệu</span>
                </button>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
                <Field label="Kiểu rủi ro">
                  <select
                    className="tx-field-input w-full"
                    value={riskKind}
                    onChange={(e) => setRiskKind(e.target.value as RiskKind)}
                  >
                    <option value="FIXED_VND">Số tiền VND</option>
                    <option value="PERCENT">Tỷ lệ vốn (0–1)</option>
                  </select>
                </Field>

                <Field label={riskKind === "FIXED_VND" ? "Rủi ro tối đa (VND)" : "Tỷ lệ rủi ro"}>
                  <input className="tx-field-input w-full font-mono" inputMode="decimal" name="risk" required />
                </Field>

                {!signal && (
                  <>
                    <Field label="Giá vào (VND/cp)">
                      <input className="tx-field-input w-full font-mono" inputMode="decimal" name="entry" required />
                    </Field>
                    <Field label="Giá dừng lỗ (VND/cp)">
                      <input className="tx-field-input w-full font-mono" inputMode="decimal" name="stop" required />
                    </Field>
                  </>
                )}
              </div>

              {signal && (
                <div className="mt-3 rounded-lg border border-cyan-800/60 bg-cyan-950/20 p-3.5 text-xs text-slate-200">
                  <div className="font-semibold text-cyan-300 flex items-center gap-1.5 mb-1.5">
                    <CheckCircle2 size={14} />
                    <span>
                      {signal.strategyCode}: vùng {signal.entryLow}–{signal.entryHigh}, stop {signal.stopLoss}, lúc {signal.calculatedAt}
                    </span>
                  </div>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mt-2">
                    <label className="block">
                      <span className="text-slate-400 text-xs font-semibold block mb-1">Điểm vào</span>
                      <select
                        className="tx-field-input w-full"
                        value={entryBasis}
                        onChange={(e) => setEntryBasis(e.target.value as typeof entryBasis)}
                      >
                        <option value="ENTRY_LOW">Cận dưới ({signal.entryLow})</option>
                        <option value="MIDPOINT">Trung điểm</option>
                        <option value="ENTRY_HIGH">Cận trên ({signal.entryHigh})</option>
                      </select>
                    </label>
                    <div className="flex flex-col justify-end">
                      <label className="flex items-center gap-2 cursor-pointer select-none text-slate-300 mb-1.5">
                        <input
                          type="checkbox"
                          className="rounded border-slate-700 bg-slate-900 text-cyan-500"
                          checked={signalConfirmed}
                          onChange={(e) => setSignalConfirmed(e.target.checked)}
                          required
                        />
                        <span className="text-xs">Tôi xác nhận dùng mức giá của tín hiệu này</span>
                      </label>
                      <button
                        type="button"
                        className="text-cyan-400 hover:text-cyan-300 text-xs underline font-medium text-left"
                        onClick={() => setSignal(null)}
                      >
                        Chuyển sang nhập tay
                      </button>
                    </div>
                  </div>
                </div>
              )}

              {!signal && originatingSignal && (
                <div className="mt-3 rounded-lg border border-slate-800 bg-slate-900/40 p-3 text-xs text-slate-300 flex items-center justify-between gap-2">
                  <p>
                    Giá nhập tay; tín hiệu {originatingSignal.strategyCode} lúc {originatingSignal.calculatedAt} chỉ được giữ làm ngữ cảnh, không tham gia tính toán.
                  </p>
                  <button
                    type="button"
                    className="text-cyan-400 hover:text-cyan-300 text-xs underline font-medium shrink-0"
                    onClick={() => setOriginatingSignal(null)}
                  >
                    Xóa ngữ cảnh
                  </button>
                </div>
              )}
            </div>

            {/* Nhóm 3: Giả định Chi Phí & Trượt Giá */}
            <div className="quant-fieldset p-4 rounded-xl bg-slate-900/60 border border-slate-800 shadow-md">
              <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
                <div className="sizing-section-title flex items-center gap-2 text-xs font-bold text-cyan-400 uppercase tracking-wider">
                  <Layers size={15} />
                  <span>Chi phí giao dịch & Trượt giá</span>
                </div>
                <label className="flex items-center gap-2 text-xs text-slate-300 cursor-pointer select-none">
                  <input
                    type="checkbox"
                    className="rounded border-slate-700 bg-slate-900 text-cyan-500"
                    checked={excludeCosts}
                    onChange={(e) => setExcludeCosts(e.target.checked)}
                  />
                  <span>Loại trừ toàn bộ chi phí và hiển thị cảnh báo</span>
                </label>
              </div>

              {!excludeCosts && (
                <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
                  {Object.keys(costs).map((key) => (
                    <Field key={key} label={costLabel(key)}>
                      <input
                        className="tx-field-input w-full font-mono"
                        inputMode="decimal"
                        required
                        value={costs[key as keyof typeof costs]}
                        onChange={(e) => setCosts({ ...costs, [key]: e.target.value })}
                      />
                    </Field>
                  ))}
                </div>
              )}
            </div>

            {/* Nhóm 4: Giới hạn tùy chọn */}
            <div className="quant-fieldset p-4 rounded-xl bg-slate-900/60 border border-slate-800 shadow-md">
              <div className="sizing-section-title flex items-center gap-2 mb-3 text-xs font-bold text-cyan-400 uppercase tracking-wider">
                <ShieldAlert size={15} />
                <span>Giới hạn tỷ trọng & Giải ngân (tùy chọn, tỷ lệ 0–1)</span>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                <Field label="Tỷ trọng tối đa một mã">
                  <input
                    className="tx-field-input w-full font-mono"
                    inputMode="decimal"
                    name="symbolCap"
                    value={symbolCap}
                    onChange={(event) => setSymbolCap(event.target.value)}
                  />
                </Field>

                <Field label="Tỷ lệ giải ngân tối đa">
                  <input
                    className="tx-field-input w-full font-mono"
                    inputMode="decimal"
                    name="deploymentCap"
                    value={deploymentCap}
                    onChange={(event) => setDeploymentCap(event.target.value)}
                  />
                </Field>

                {mode === "MANUAL" && (
                  <>
                    <Field label="Giá trị danh mục">
                      <input
                        className="tx-field-input w-full font-mono"
                        inputMode="decimal"
                        name="portfolioValue"
                        required={Boolean(symbolCap || deploymentCap)}
                      />
                    </Field>
                    <Field label="Giá trị mã hiện có">
                      <input
                        className="tx-field-input w-full font-mono"
                        inputMode="decimal"
                        name="symbolValue"
                        required={Boolean(symbolCap)}
                      />
                    </Field>
                    <Field label="Giá trị đang giải ngân">
                      <input
                        className="tx-field-input w-full font-mono"
                        inputMode="decimal"
                        name="deployedValue"
                        required={Boolean(deploymentCap)}
                      />
                    </Field>
                  </>
                )}
              </div>
            </div>

            {error && (
              <div role="alert" className="p-3.5 rounded-lg bg-rose-950/40 border border-rose-800/60 text-xs text-rose-300 flex items-start gap-2">
                <AlertTriangle size={15} className="shrink-0 mt-0.5" />
                <span>{error}</span>
              </div>
            )}

            <button
              className="w-full py-3 px-4 rounded-lg bg-gradient-to-r from-cyan-500 to-blue-600 hover:from-cyan-400 hover:to-blue-500 text-slate-950 font-bold text-sm shadow-lg shadow-cyan-500/20 transition-all cursor-pointer flex items-center justify-center gap-2 disabled:opacity-50"
              type="submit"
              disabled={busy}
            >
              <Calculator size={16} />
              <span>{busy ? "Đang tính toán…" : "Tính quy mô"}</span>
            </button>
          </form>
        </div>

        {/* Cột Phải: Kết Quả & Phân Tích Rủi Ro (Sticky Eye Level) */}
        <div className="xl:col-span-5 sticky top-6">
          {result ? (
            <ResultView result={result} signal={signal ?? originatingSignal} />
          ) : (
            <div className="sizing-placeholder-card panel bg-slate-900/40 border border-slate-800/80 rounded-xl p-8 text-center flex flex-col items-center justify-center min-h-[380px]">
              <div className="w-14 h-14 rounded-2xl bg-slate-900/90 border border-slate-800 flex items-center justify-center text-cyan-400 mb-4 shadow-inner">
                <ShieldAlert size={28} />
              </div>
              <h3 className="text-base font-bold text-white mb-2">Sẵn sàng tính toán quy mô vị thế</h3>
              <p className="text-xs text-slate-400 max-w-sm leading-relaxed mb-4">
                Điền đầy đủ các thông số giao dịch bên trái và nhấn <strong>"Tính quy mô"</strong>. Hệ thống sẽ áp dụng quy định lô chẵn 100 và các ràng buộc về vốn/rủi ro.
              </p>
              <div className="inline-flex items-center gap-2 text-[11px] text-slate-500 font-mono bg-slate-900/50 px-3 py-1.5 rounded-md border border-slate-800/60">
                <span>Deterministic Sizing Engine v1.0</span>
              </div>
            </div>
          )}
        </div>
      </div>
    </main>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="tx-field-group block">
      <span className="tx-field-lbl text-xs font-semibold text-slate-300 mb-1.5 block">{label}</span>
      {children}
    </label>
  );
}

function costLabel(key: string) {
  return (
    ({
      entryFeeRate: "Phí mua",
      exitFeeRate: "Phí bán",
      sellTaxRate: "Thuế bán",
      entrySlippageRate: "Trượt giá mua",
      exitSlippageRate: "Trượt giá bán",
    } as Record<string, string>)[key] ?? key
  );
}

function ResultView({ result, signal }: { result: SizingResult; signal: Signal | null }) {
  return (
    <section className="transaction-ledger-section space-y-5" aria-live="polite" aria-labelledby="sizing-result-title">
      {/* Hero Card Điểm Nhấn Kết Quả */}
      <div className="sizing-hero-card">
        <div className="flex items-center justify-between">
          <span className="text-xs font-mono font-bold tracking-wider uppercase text-cyan-300">
            KẾT QUẢ TÍNH TOÁN · {result.symbol}
          </span>
          <span className={`px-2.5 py-0.5 rounded text-[11px] font-mono font-extrabold uppercase ${
            result.status === "CALCULATED"
              ? "bg-emerald-500/20 text-emerald-300 border border-emerald-500/40"
              : "bg-amber-500/20 text-amber-300 border border-amber-500/40"
          }`}>
            {result.status}
          </span>
        </div>

        <h2 id="sizing-result-title" className={`sizing-hero-val ${result.status !== "CALCULATED" ? "withheld" : ""}`}>
          {result.status === "CALCULATED" ? `${result.quantity} cổ phiếu` : "Không công bố số lượng"}
        </h2>

        <p className="text-xs text-slate-300 flex items-center gap-1.5">
          <CheckCircle2 size={13} className="text-cyan-400" />
          <span>Đã áp dụng toàn bộ ràng buộc rủi ro và làm tròn xuống lô chẵn ({result.lotSize} cp).</span>
        </p>
      </div>

      {/* Kịch bản Chiến lược Toàn diện (Trade Playbook) */}
      {result.status === "CALCULATED" && (
        <StrategyPlaybook result={result} signal={signal} />
      )}

      {/* Cảnh báo hoặc Lý do từ chối (nếu có) */}
      {result.reasonCodes.length > 0 && (
        <div className="p-3.5 rounded-lg bg-amber-950/30 border border-amber-800/60 text-xs text-amber-300">
          <p className="font-bold flex items-center gap-1.5 mb-1.5">
            <AlertTriangle size={14} />
            <span>Lý do giới hạn / từ chối:</span>
          </p>
          <ul className="space-y-1">
            {result.reasonCodes.map((rc) => (
              <li key={rc} className="flex items-center gap-1.5 flex-wrap">
                <span className="font-semibold text-amber-200">
                  {SIZING_REASON_DEFINITIONS[rc] ?? rc}
                </span>
                <span className="text-[11px] text-amber-400/80 font-mono">
                  (<span role="status">{rc}</span>)
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Cảnh báo giả định về chi phí & trượt giá */}
      {result.warnings.length > 0 && (
        <div className="p-3.5 rounded-xl bg-slate-900/70 border border-amber-800/40 text-xs space-y-2">
          <div className="flex items-center gap-2 font-bold text-amber-400">
            <span>⚠️</span>
            <span>Lưu ý về chi phí & trượt giá ({result.warnings.length})</span>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 pt-1">
            {result.warnings.map((w) => {
              const def = SIZING_WARNING_DEFINITIONS[w] ?? {
                title: w,
                desc: "Lưu ý phương pháp luận từ hệ thống.",
              };
              return (
                <div key={w} className="p-2.5 rounded-lg bg-slate-950/70 border border-slate-800 space-y-0.5">
                  <div className="flex items-center justify-between gap-1 flex-wrap">
                    <span className="font-semibold text-slate-200 text-[11px]">{def.title}</span>
                    <span className="font-mono text-[10px] text-amber-400/80">({w})</span>
                  </div>
                  <p className="text-[10px] text-slate-400 leading-relaxed">{def.desc}</p>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Lưới Thẻ Chỉ Số Trọng Tâm */}
      <div className="sizing-kpi-grid">
        <div className="sizing-kpi-card">
          <span className="sizing-kpi-label">Vốn cần (VND)</span>
          <span className="sizing-kpi-value text-cyan-400">{result.requiredCapitalVnd ?? "—"}</span>
        </div>
        <div className="sizing-kpi-card">
          <span className="sizing-kpi-label">Lỗ ước tính tại stop (VND)</span>
          <span className="sizing-kpi-value text-rose-400">{result.estimatedLossAtStopVnd ?? "—"}</span>
        </div>
        <div className="sizing-kpi-card">
          <span className="sizing-kpi-label">Phần dư sau làm tròn lô (cp)</span>
          <span className="sizing-kpi-value text-slate-300">{result.roundingRemainder ?? "—"}</span>
        </div>
      </div>

      {/* Bảng Các Yếu Tố Ràng Buộc (Constraints Checklist) */}
      <div className="sizing-constraints-box">
        <h3 className="text-xs font-bold uppercase tracking-wider text-slate-300 mb-3 flex items-center gap-1.5">
          <FileCheck2 size={14} className="text-cyan-400" />
          <span>Các giới hạn & Ràng buộc rủi ro</span>
        </h3>
        <ul className="sizing-constraint-list space-y-2">
          {result.constraints.map((c) => {
            const meta = CONSTRAINT_NAMES[c.code] ?? { label: c.code, desc: "" };
            const appLabel = APPLICABILITY_LABELS[c.applicability] ?? c.applicability;
            return (
              <li
                key={c.code}
                className={`sizing-constraint-row flex items-center justify-between p-3 rounded-lg border text-xs ${
                  c.binding ? "binding border-amber-500/60 bg-amber-950/20 text-amber-200" : "border-slate-800 bg-slate-900/40 text-slate-300"
                }`}
              >
                <div className="space-y-0.5">
                  <div className="flex items-center gap-2 flex-wrap">
                    <strong className="font-bold text-slate-100">{meta.label}</strong>
                    <span className="font-mono text-[10px] text-slate-500">({c.code})</span>
                    <span className="text-[11px] text-slate-400">: {appLabel}</span>
                    {c.rawQuantity != null && (
                      <span className="text-xs text-cyan-300 font-mono font-semibold">
                        · {c.rawQuantity.toLocaleString("vi-VN")} cp
                      </span>
                    )}
                    {c.binding && <span className="font-bold text-amber-400"> · ĐANG GIỚI HẠN</span>}
                  </div>
                  {meta.desc && <p className="text-[11px] text-slate-400">{meta.desc}</p>}
                </div>
                <span className={`constraint-tag px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider shrink-0 ${
                  c.binding ? "binding-tag bg-amber-500/20 text-amber-400 border border-amber-500/40" : "bg-slate-800 text-slate-400"
                }`}>
                  {c.binding ? "ĐANG GIỚI HẠN" : appLabel.toUpperCase()}
                </span>
              </li>
            );
          })}
        </ul>
      </div>

      {/* Chi tiết Toàn bộ Phép Tính */}
      <div className="bg-slate-950/60 border border-slate-800 rounded-xl p-4">
        <h3 className="text-xs font-bold uppercase tracking-wider text-slate-300 mb-3">Chi tiết thông số định lượng</h3>
        <dl className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
          <Metric label="Giới hạn thô (cp)" value={result.rawPermittedQuantity} />
          <Metric label="Phần dư sau làm tròn lô (cp)" value={result.roundingRemainder} />
          <Metric label="Vốn cơ sở (VND)" value={result.capitalBaseVnd} />
          <Metric label="Tiền có thể dùng (VND)" value={result.availableCashVnd} />
          <Metric label="Giá vào đã chọn (VND/cp)" value={result.resolvedEntryPriceVnd} />
          <Metric label="Giá stop đã chọn (VND/cp)" value={result.resolvedStopPriceVnd} />
          <Metric label="Giá vào sau trượt giá (VND/cp)" value={result.effectiveEntryPriceVnd} />
          <Metric label="Giá stop sau trượt giá (VND/cp)" value={result.effectiveStopPriceVnd} />
          <Metric label="Chi phí mua mỗi cp (VND)" value={result.acquisitionUnitCostVnd} />
          <Metric label="Tiền thu ròng tại stop mỗi cp (VND)" value={result.stopNetProceedsPerShareVnd} />
          <Metric label="Ngân sách rủi ro (VND)" value={result.riskBudgetVnd} />
          <Metric label="Vốn cần (VND)" value={result.requiredCapitalVnd} />
          <Metric label="Lỗ ước tính tại stop (VND)" value={result.estimatedLossAtStopVnd} />
          <Metric label="Tiền còn lại (VND)" value={result.remainingCashVnd} />
          <Metric label="Lỗ mỗi cổ phiếu (VND)" value={result.lossPerShareVnd} />
          <Metric label="Giá trị mã dự kiến (VND)" value={result.projectedSymbolMarketValueVnd} />
          <Metric label="Tỷ trọng mã dự kiến" value={result.projectedSymbolExposureRate} />
          <Metric label="Tỷ lệ giải ngân dự kiến" value={result.projectedDeploymentRate} />
        </dl>
      </div>

      {/* Thông tin Provenance & Phiên Bản Quy Tắc */}
      <details className="rounded-lg border border-slate-800/80 bg-slate-900/40 p-3.5 text-xs text-slate-400">
        <summary className="cursor-pointer font-semibold text-slate-300 hover:text-cyan-400 transition-colors">
          Nguồn dữ liệu và phiên bản quy tắc
        </summary>
        <div className="mt-3 pt-3 border-t border-slate-800 space-y-2">
          <p className="font-mono text-slate-300">
            {result.sizingRuleVersion} · {result.marketRuleVersion} · {result.calculatedAt}
          </p>
          <ul className="space-y-1 mt-2 font-mono text-[11px] text-slate-400">
            {result.inputEvidence.map((e) => (
              <li key={`${e.field}-${e.source}`}>
                {e.field}: {e.value} {e.unit} · {e.source}
                {e.asOf ? ` · ${e.asOf}` : ""}
              </li>
            ))}
          </ul>
        </div>
      </details>
    </section>
  );
}

function Metric({ label, value }: { label: string; value: string | number | null }) {
  return (
    <div className="p-2 rounded bg-slate-900/40 border border-slate-800/40">
      <dt className="text-[11px] text-slate-400 mb-0.5">{label}</dt>
      <dd className="font-mono font-bold text-slate-200 text-sm">{value ?? "—"}</dd>
    </div>
  );
}

function StrategyPlaybook({ result, signal }: { result: SizingResult; signal: Signal | null }) {
  if (
    result.status !== "CALCULATED" ||
    !result.quantity ||
    !result.resolvedEntryPriceVnd ||
    !result.resolvedStopPriceVnd
  ) {
    return null;
  }

  const entry = Number(result.resolvedEntryPriceVnd);
  const stop = Number(result.resolvedStopPriceVnd);
  const qty = result.quantity;
  const lossPerShare = Number(result.lossPerShareVnd ?? Math.max(1, entry - stop));
  const estimatedLoss = Number(result.estimatedLossAtStopVnd ?? (lossPerShare * qty));

  // ATR calculation: matches BacktestExecutionService exactly: (entryHigh - entryLow) * 2
  const atrFactor = signal?.riskFactors?.find((f) => f.factorCode === "ATR");
  let atr = 0;
  if (signal?.entryHigh && signal?.entryLow) {
    atr = (Number(signal.entryHigh) - Number(signal.entryLow)) * 2;
  } else if (atrFactor?.inputValue) {
    const rawVal = Number(atrFactor.inputValue);
    atr = rawVal < 1 ? rawVal * entry : rawVal;
  } else if (lossPerShare > 0) {
    atr = lossPerShare / 1.5;
  } else {
    atr = entry * 0.03;
  }

  // Pyramiding steps (from financial-v1 rule: +0.5 ATR per step)
  const pyramid1Price = Math.round(entry + 0.5 * atr);
  const pyramid2Price = Math.round(entry + 1.0 * atr);
  const pyramid3Price = Math.round(entry + 1.5 * atr);

  // Take Profit targets
  const target1Price = signal?.target1
    ? Number(signal.target1)
    : Math.round(entry + 1.5 * lossPerShare);
  const target2Price = signal?.target2
    ? Number(signal.target2)
    : Math.round(entry + 2.5 * lossPerShare);

  const profitT1 = Math.round(Math.max(0, target1Price - entry) * qty);
  const profitT2 = Math.round(Math.max(0, target2Price - entry) * qty);

  return (
    <div className="rounded-xl border border-cyan-800/60 bg-gradient-to-br from-slate-900 via-slate-900 to-cyan-950/30 p-4 shadow-xl space-y-3.5">
      <div className="flex items-center justify-between border-b border-slate-800/80 pb-2.5">
        <div className="flex items-center gap-2">
          <Milestone size={16} className="text-cyan-400" />
          <h3 className="text-xs font-bold uppercase tracking-wider text-slate-200">
            Kịch bản Chiến lược Toàn diện (4 Tranches · Chuẩn Backtest)
          </h3>
        </div>
        <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-cyan-950/80 border border-cyan-800 text-cyan-300 font-semibold">
          Quản trị vốn 4 đợt
        </span>
      </div>

      <p className="text-[11px] text-slate-400 leading-relaxed">
        Chiến lược quản trị vốn đa đợt (Multi-tranche Pyramiding): Mỗi đợt chịu rủi ro 1 tranche (~{Math.round(estimatedLoss).toLocaleString("vi-VN")} ₫), tổng rủi ro mở trần tối đa 4% danh mục:
      </p>

      <div className="space-y-2.5 text-xs">
        {/* Đợt 1 */}
        <div className="p-3 rounded-lg bg-slate-900/90 border border-slate-800 flex items-start justify-between gap-3">
          <div className="space-y-1">
            <div className="flex items-center gap-2 font-semibold text-slate-200">
              <span className="w-5 h-5 rounded-full bg-cyan-500/20 text-cyan-400 border border-cyan-500/40 flex items-center justify-center text-[11px] font-bold">
                1
              </span>
              <span>Đợt 1 (Mở vị thế): Mua {qty.toLocaleString("vi-VN")} cp tại {entry.toLocaleString("vi-VN")} ₫</span>
            </div>
            <p className="text-[11px] text-slate-400 pl-7">
              Cắt lỗ ban đầu: <strong className="text-rose-400">{stop.toLocaleString("vi-VN")} ₫</strong> · Rủi ro Tranche 1: <strong className="text-rose-400">-{Math.round(estimatedLoss).toLocaleString("vi-VN")} ₫</strong>
            </p>
          </div>
          <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700 shrink-0">
            Tranche 1
          </span>
        </div>

        {/* Đợt 2 */}
        <div className="p-3 rounded-lg bg-slate-900/70 border border-cyan-900/40 flex items-start justify-between gap-3">
          <div className="space-y-1">
            <div className="flex items-center gap-2 font-semibold text-cyan-300">
              <span className="w-5 h-5 rounded-full bg-cyan-500/20 text-cyan-300 border border-cyan-500/40 flex items-center justify-center text-[11px] font-bold">
                2
              </span>
              <span>Đợt 2 (Mua nhồi 1): Khi giá ≥ {pyramid1Price.toLocaleString("vi-VN")} ₫ (+0.5×ATR)</span>
            </div>
            <p className="text-[11px] text-slate-400 pl-7">
              Khối lượng: ~{qty.toLocaleString("vi-VN")} cp · Hành động: <strong className="text-emerald-300">Dời Stop Loss Đợt 1 về hòa vốn ({entry.toLocaleString("vi-VN")} ₫)</strong> để triệt tiêu rủi ro Đợt 1!
            </p>
          </div>
          <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-cyan-950 text-cyan-400 border border-cyan-800/60 shrink-0 font-bold">
            +0.5×ATR
          </span>
        </div>

        {/* Đợt 3 */}
        <div className="p-3 rounded-lg bg-slate-900/50 border border-slate-800 flex items-start justify-between gap-3">
          <div className="space-y-1">
            <div className="flex items-center gap-2 font-semibold text-slate-300">
              <span className="w-5 h-5 rounded-full bg-slate-800 text-slate-400 border border-slate-700 flex items-center justify-center text-[11px] font-bold">
                3
              </span>
              <span>Đợt 3 (Mua nhồi 2): Khi giá ≥ {pyramid2Price.toLocaleString("vi-VN")} ₫ (+1.0×ATR)</span>
            </div>
            <p className="text-[11px] text-slate-400 pl-7">
              Khối lượng: ~{qty.toLocaleString("vi-VN")} cp · Hành động: Dời Stop Loss Đợt 2 về hòa vốn ({pyramid1Price.toLocaleString("vi-VN")} ₫).
            </p>
          </div>
          <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-slate-800 text-slate-400 border border-slate-700 shrink-0">
            +1.0×ATR
          </span>
        </div>

        {/* Đợt 4 */}
        <div className="p-3 rounded-lg bg-slate-900/50 border border-slate-800 flex items-start justify-between gap-3">
          <div className="space-y-1">
            <div className="flex items-center gap-2 font-semibold text-slate-300">
              <span className="w-5 h-5 rounded-full bg-slate-800 text-slate-400 border border-slate-700 flex items-center justify-center text-[11px] font-bold">
                4
              </span>
              <span>Đợt 4 (Mua nhồi tối đa): Khi giá ≥ {pyramid3Price.toLocaleString("vi-VN")} ₫ (+1.5×ATR)</span>
            </div>
            <p className="text-[11px] text-slate-400 pl-7">
              Hoàn tất 4 tranches tối đa theo quy tắc Backtest. Khóa giải ngân mới, tập trung bảo vệ vị thế và chờ chốt lời.
            </p>
          </div>
          <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-amber-950 text-amber-400 border border-amber-800 shrink-0">
            Tối đa 4 tranches
          </span>
        </div>

        {/* Chốt lời Target 1 & 2 */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
          <div className="p-2.5 rounded-lg bg-emerald-950/20 border border-emerald-800/40 space-y-1">
            <div className="flex items-center justify-between">
              <span className="font-semibold text-emerald-400 flex items-center gap-1.5">
                <Target size={13} />
                <span>Mục tiêu 1 (Target 1)</span>
              </span>
              <span className="font-mono font-bold text-emerald-300 text-xs">
                {target1Price.toLocaleString("vi-VN")} ₫
              </span>
            </div>
            <div className="flex items-center justify-between text-[11px] text-slate-400 pt-0.5">
              <span>Lãi dự kiến Đợt 1:</span>
              <span className="font-mono font-bold text-emerald-400">
                +{profitT1.toLocaleString("vi-VN")} ₫
              </span>
            </div>
            <p className="text-[10px] text-slate-400 italic">
              Khuyến nghị: Chốt 50% hoặc toàn bộ Đợt 1 để khóa lợi nhuận.
            </p>
          </div>

          <div className="p-2.5 rounded-lg bg-emerald-950/10 border border-emerald-900/30 space-y-1">
            <div className="flex items-center justify-between">
              <span className="font-semibold text-emerald-300 flex items-center gap-1.5">
                <ArrowUpRight size={13} />
                <span>Mục tiêu 2 (Target 2)</span>
              </span>
              <span className="font-mono font-bold text-emerald-200 text-xs">
                {target2Price.toLocaleString("vi-VN")} ₫
              </span>
            </div>
            <div className="flex items-center justify-between text-[11px] text-slate-400 pt-0.5">
              <span>Lãi dự kiến Đợt 1:</span>
              <span className="font-mono font-bold text-emerald-300">
                +{profitT2.toLocaleString("vi-VN")} ₫
              </span>
            </div>
            <p className="text-[10px] text-slate-400 italic">
              Khuyến nghị: Gồng lãi phần còn lại khi hoàn thành sóng tăng lớn.
            </p>
          </div>
        </div>

        {/* Nguyên tắc cắt lỗ */}
        <div className="p-2.5 rounded-lg bg-rose-950/20 border border-rose-800/40 flex items-start gap-2 text-[11px] text-rose-300">
          <ShieldAlert size={14} className="shrink-0 mt-0.5 text-rose-400" />
          <div>
            <strong className="text-rose-200">Nguyên tắc Cắt lỗ tuyệt đối: </strong>
            Nếu giá giảm chạm <span className="font-mono font-bold text-white">{stop.toLocaleString("vi-VN")} ₫</span> trước khi đạt Target, bán hết 100% vị thế. Khoản lỗ tối đa được khống chế ở mức{" "}
            <span className="font-mono font-bold text-white">-{Math.round(estimatedLoss).toLocaleString("vi-VN")} ₫</span>.
          </div>
        </div>
      </div>
    </div>
  );
}
