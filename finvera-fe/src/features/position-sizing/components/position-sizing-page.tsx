import { type FormEvent, useEffect, useState } from "react";
import { listPortfolios, type PortfolioSummary } from "../../portfolio/api/portfolio";
import { getStockSignals, type Signal } from "../../stock-detail/api/stock-signals";
import { calculatePositionSize, PositionSizingApiError, type RiskKind, type SizingMode, type SizingRequest, type SizingResult } from "../api/position-sizing";

const emptyCosts = { entryFeeRate: "", exitFeeRate: "", sellTaxRate: "", entrySlippageRate: "", exitSlippageRate: "" };

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

  useEffect(() => { listPortfolios().then(setPortfolios).catch(() => setPortfolios([])); }, []);

  async function importSignal(symbol: string) {
    setError(null); setSignalConfirmed(false);
    try {
      const response = await getStockSignals(symbol.toUpperCase());
      const current = response.evaluations.find((e) => e.status === "SIGNAL" && e.signal)?.signal ?? null;
      setSignal(current);
      setOriginatingSignal(current);
      if (!current) setError("Không có tín hiệu LONG hiện hành cho mã này. Bạn vẫn có thể nhập giá thủ công.");
    } catch { setSignal(null); setOriginatingSignal(null); setError("Không thể tải tín hiệu hiện hành."); }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError(null); setResult(null);
    const data = new FormData(event.currentTarget);
    const normalizedSymbol = symbol.toUpperCase();
    const capSymbol = symbolCap;
    const capDeployment = deploymentCap;
    const request: SizingRequest = {
      mode, symbol: normalizedSymbol,
      ...(mode === "PORTFOLIO" ? { portfolioId } : { manualCapital: {
        capitalBaseVnd: String(data.get("capitalBase") ?? ""), availableCashVnd: String(data.get("cash") ?? ""),
        ...(capSymbol || capDeployment ? { portfolioValueVnd: String(data.get("portfolioValue") ?? "") } : {}),
        ...(capSymbol ? { existingSymbolMarketValueVnd: String(data.get("symbolValue") ?? "") } : {}),
        ...(capDeployment ? { currentDeployedMarketValueVnd: String(data.get("deployedValue") ?? "") } : {}),
      }}),
      riskBudget: { kind: riskKind, value: String(data.get("risk") ?? "") },
      priceInput: signal ? { source: "SIGNAL", strategyCode: signal.strategyCode, ruleVersion: signal.ruleVersion,
        calculatedAt: signal.calculatedAt, entryBasis, confirmed: signalConfirmed as true }
        : { source: "MANUAL", entryPriceVnd: String(data.get("entry") ?? ""), stopPriceVnd: String(data.get("stop") ?? ""),
          ...(originatingSignal ? { originatingSignalContext: { strategyCode: originatingSignal.strategyCode,
            ruleVersion: originatingSignal.ruleVersion, calculatedAt: originatingSignal.calculatedAt,
            asOfTradingDate: originatingSignal.asOfTradingDate } } : {}) },
      costPolicy: excludeCosts ? { excludeCosts: true } : { excludeCosts: false, ...costs },
      ...((capSymbol || capDeployment) ? { exposureLimits: {
        ...(capSymbol ? { maxSymbolConcentrationRate: capSymbol } : {}),
        ...(capDeployment ? { maxDeploymentRate: capDeployment } : {}),
      }} : {}),
    };
    try { setResult(await calculatePositionSize(request)); }
    catch (reason) { setError(reason instanceof PositionSizingApiError ? `Không thể tính: ${reason.reasonCode}` : "Phản hồi máy chủ không đúng hợp đồng."); }
    finally { setBusy(false); }
  }

  return <main className="app-shell" aria-labelledby="position-sizing-title">
    <header className="page-header">
      <p className="eyebrow">FINVERA · RISK ENGINE</p>
      <h1 id="position-sizing-title">Tính quy mô vị thế</h1>
      <p className="text-slate-400">Kịch bản định lượng theo dữ liệu và giả định bạn cung cấp. Kết quả không phải lệnh mua hoặc cam kết lợi nhuận.</p>
    </header>

    <form onSubmit={submit} className="transaction-form-card space-y-6">
      <fieldset className="tx-inputs-grid"><legend className="sr-only">Nguồn vốn</legend>
        <Field label="Chế độ"><select aria-label="Chế độ" className="tx-field-input" value={mode} onChange={(e) => { setMode(e.target.value as SizingMode); setSignal(null); setResult(null); }}><option value="MANUAL">Nhập thủ công</option><option value="PORTFOLIO">Theo danh mục</option></select></Field>
        <Field label="Mã cổ phiếu"><input className="tx-field-input" name="symbol" required pattern="[A-Za-z0-9]{3,10}" value={symbol} onChange={(event) => { setSymbol(event.target.value); setSignal(null); setOriginatingSignal(null); setSignalConfirmed(false); }} /></Field>
        {mode === "PORTFOLIO" ? <Field label="Danh mục"><select className="tx-field-input" aria-label="Danh mục" required value={portfolioId} onChange={(e) => setPortfolioId(e.target.value)}><option value="">Chọn danh mục</option>{portfolios.map((p) => <option value={p.id} key={p.id}>{p.name} · {p.dataStatus}</option>)}</select></Field> : <>
          <Field label="Vốn cơ sở (VND)"><input className="tx-field-input" inputMode="decimal" name="capitalBase" required /></Field>
          <Field label="Tiền có thể dùng (VND)"><input className="tx-field-input" inputMode="decimal" name="cash" required /></Field>
        </>}
      </fieldset>

      <fieldset className="tx-inputs-grid"><legend className="text-sm font-bold text-slate-200 mb-3">Ngân sách rủi ro và giá</legend>
        <Field label="Kiểu rủi ro"><select className="tx-field-input" value={riskKind} onChange={(e) => setRiskKind(e.target.value as RiskKind)}><option value="FIXED_VND">Số tiền VND</option><option value="PERCENT">Tỷ lệ vốn (0–1)</option></select></Field>
        <Field label={riskKind === "FIXED_VND" ? "Rủi ro tối đa (VND)" : "Tỷ lệ rủi ro"}><input className="tx-field-input" inputMode="decimal" name="risk" required /></Field>
        {!signal ? <><Field label="Giá vào (VND/cp)"><input className="tx-field-input" inputMode="decimal" name="entry" required /></Field><Field label="Giá dừng lỗ (VND/cp)"><input className="tx-field-input" inputMode="decimal" name="stop" required /></Field></> : null}
        <div className="tx-field-group"><span className="tx-field-lbl">Tín hiệu hiện hành</span><button type="button" className="tx-type-tab-btn" onClick={() => void importSignal(symbol)}>Nạp tín hiệu</button></div>
        {signal ? <div className="col-span-full rounded border border-cyan-900 p-3 text-sm"><p>{signal.strategyCode}: vùng {signal.entryLow}–{signal.entryHigh}, stop {signal.stopLoss}, lúc {signal.calculatedAt}</p><label>Điểm vào <select className="tx-field-input mt-2" value={entryBasis} onChange={(e) => setEntryBasis(e.target.value as typeof entryBasis)}><option value="ENTRY_LOW">Cận dưới</option><option value="MIDPOINT">Trung điểm</option><option value="ENTRY_HIGH">Cận trên</option></select></label><label className="mt-2 flex gap-2"><input type="checkbox" checked={signalConfirmed} onChange={(e) => setSignalConfirmed(e.target.checked)} required /> Tôi xác nhận dùng mức giá của tín hiệu này</label><button type="button" className="text-cyan-400 mt-2" onClick={() => setSignal(null)}>Chuyển sang nhập tay</button></div> : null}
        {!signal && originatingSignal ? <div className="col-span-full rounded border border-slate-700 p-3 text-sm"><p>Giá nhập tay; tín hiệu {originatingSignal.strategyCode} lúc {originatingSignal.calculatedAt} chỉ được giữ làm ngữ cảnh, không tham gia tính toán.</p><button type="button" className="text-cyan-400 mt-2" onClick={() => setOriginatingSignal(null)}>Xóa ngữ cảnh tín hiệu</button></div> : null}
      </fieldset>

      <fieldset><legend className="text-sm font-bold text-slate-200 mb-3">Chi phí</legend><label className="flex gap-2 mb-4"><input type="checkbox" checked={excludeCosts} onChange={(e) => setExcludeCosts(e.target.checked)} /> Loại trừ toàn bộ chi phí và hiển thị cảnh báo</label>{!excludeCosts ? <div className="tx-inputs-grid">{Object.keys(costs).map((key) => <Field key={key} label={costLabel(key)}><input className="tx-field-input" inputMode="decimal" required value={costs[key as keyof typeof costs]} onChange={(e) => setCosts({ ...costs, [key]: e.target.value })} /></Field>)}</div> : null}</fieldset>

      <fieldset><legend className="text-sm font-bold text-slate-200 mb-3">Giới hạn tùy chọn (tỷ lệ 0–1)</legend><div className="tx-inputs-grid"><Field label="Tỷ trọng tối đa một mã"><input className="tx-field-input" inputMode="decimal" name="symbolCap" value={symbolCap} onChange={(event) => setSymbolCap(event.target.value)} /></Field><Field label="Tỷ lệ giải ngân tối đa"><input className="tx-field-input" inputMode="decimal" name="deploymentCap" value={deploymentCap} onChange={(event) => setDeploymentCap(event.target.value)} /></Field>{mode === "MANUAL" ? <><Field label="Giá trị danh mục"><input className="tx-field-input" inputMode="decimal" name="portfolioValue" required={Boolean(symbolCap || deploymentCap)} /></Field><Field label="Giá trị mã hiện có"><input className="tx-field-input" inputMode="decimal" name="symbolValue" required={Boolean(symbolCap)} /></Field><Field label="Giá trị đang giải ngân"><input className="tx-field-input" inputMode="decimal" name="deployedValue" required={Boolean(deploymentCap)} /></Field></> : null}</div></fieldset>
      {error ? <p role="alert" className="tx-form-error-banner">{error}</p> : null}
      <button className="btn-primary" type="submit" disabled={busy}>{busy ? "Đang tính…" : "Tính quy mô"}</button>
    </form>
    {result ? <ResultView result={result} /> : null}
  </main>;
}

function Field({ label, children }: { label: string; children: React.ReactNode }) { return <label className="tx-field-group"><span className="tx-field-lbl">{label}</span>{children}</label>; }
function costLabel(key: string) { return ({ entryFeeRate: "Phí mua", exitFeeRate: "Phí bán", sellTaxRate: "Thuế bán", entrySlippageRate: "Trượt giá mua", exitSlippageRate: "Trượt giá bán" } as Record<string, string>)[key]; }
function ResultView({ result }: { result: SizingResult }) { return <section className="transaction-ledger-section" aria-live="polite" aria-labelledby="sizing-result-title"><h2 id="sizing-result-title" className="ledger-title">Kết quả: {result.status === "CALCULATED" ? `${result.quantity} cổ phiếu` : "Không công bố số lượng"}</h2>{result.reasonCodes.length ? <p role="status" className="text-amber-400">{result.reasonCodes.join(", ")}</p> : null}{result.warnings.map((w) => <p className="text-amber-300" key={w}>⚠ {w}</p>)}<dl className="tx-inputs-grid mt-5"><Metric label="Giới hạn thô (cp)" value={result.rawPermittedQuantity} /><Metric label="Phần dư sau làm tròn lô (cp)" value={result.roundingRemainder} /><Metric label="Vốn cơ sở (VND)" value={result.capitalBaseVnd} /><Metric label="Tiền có thể dùng (VND)" value={result.availableCashVnd} /><Metric label="Giá vào đã chọn (VND/cp)" value={result.resolvedEntryPriceVnd} /><Metric label="Giá stop đã chọn (VND/cp)" value={result.resolvedStopPriceVnd} /><Metric label="Giá vào sau trượt giá (VND/cp)" value={result.effectiveEntryPriceVnd} /><Metric label="Giá stop sau trượt giá (VND/cp)" value={result.effectiveStopPriceVnd} /><Metric label="Chi phí mua mỗi cp (VND)" value={result.acquisitionUnitCostVnd} /><Metric label="Tiền thu ròng tại stop mỗi cp (VND)" value={result.stopNetProceedsPerShareVnd} /><Metric label="Ngân sách rủi ro (VND)" value={result.riskBudgetVnd} /><Metric label="Vốn cần (VND)" value={result.requiredCapitalVnd} /><Metric label="Lỗ ước tính tại stop (VND)" value={result.estimatedLossAtStopVnd} /><Metric label="Tiền còn lại (VND)" value={result.remainingCashVnd} /><Metric label="Lỗ mỗi cổ phiếu (VND)" value={result.lossPerShareVnd} /><Metric label="Giá trị mã dự kiến (VND)" value={result.projectedSymbolMarketValueVnd} /><Metric label="Tỷ trọng mã dự kiến" value={result.projectedSymbolExposureRate} /><Metric label="Tỷ lệ giải ngân dự kiến" value={result.projectedDeploymentRate} /></dl><h3 className="font-bold mt-5">Các giới hạn</h3><ul>{result.constraints.map((c) => <li key={c.code}><strong>{c.code}</strong>: {c.applicability}{c.rawQuantity == null ? "" : ` · ${c.rawQuantity} cp`}{c.binding ? " · ĐANG GIỚI HẠN" : ""}</li>)}</ul><details className="mt-5"><summary className="cursor-pointer">Nguồn dữ liệu và phiên bản quy tắc</summary><p>{result.sizingRuleVersion} · {result.marketRuleVersion} · {result.calculatedAt}</p><ul>{result.inputEvidence.map((e) => <li key={`${e.field}-${e.source}`}>{e.field}: {e.value} {e.unit} · {e.source}{e.asOf ? ` · ${e.asOf}` : ""}</li>)}</ul></details></section>; }
function Metric({ label, value }: { label: string; value: string | number | null }) { return <div><dt className="tx-field-lbl">{label}</dt><dd className="font-mono text-lg">{value ?? "—"}</dd></div>; }
