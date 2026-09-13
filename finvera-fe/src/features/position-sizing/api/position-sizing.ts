import { getCsrf } from "../../auth/api/owner-access";

export type SizingMode = "MANUAL" | "PORTFOLIO";
export type RiskKind = "FIXED_VND" | "PERCENT";
export interface OriginatingSignalContext {
  strategyCode: string; ruleVersion: string; calculatedAt: string; asOfTradingDate: string;
}
export interface SizingRequest {
  mode: SizingMode;
  symbol: string;
  portfolioId?: string;
  manualCapital?: {
    capitalBaseVnd: string; availableCashVnd: string; portfolioValueVnd?: string;
    existingSymbolMarketValueVnd?: string; currentDeployedMarketValueVnd?: string;
  };
  riskBudget: { kind: RiskKind; value: string };
  priceInput:
    | { source: "MANUAL"; entryPriceVnd: string; stopPriceVnd: string; originatingSignalContext?: OriginatingSignalContext }
    | { source: "SIGNAL"; strategyCode: string; ruleVersion: string; calculatedAt: string;
        entryBasis: "ENTRY_LOW" | "MIDPOINT" | "ENTRY_HIGH"; confirmed: true };
  costPolicy:
    | { excludeCosts: true }
    | { excludeCosts: false; entryFeeRate: string; exitFeeRate: string; sellTaxRate: string;
        entrySlippageRate: string; exitSlippageRate: string };
  exposureLimits?: { maxSymbolConcentrationRate?: string; maxDeploymentRate?: string };
}

export type ConstraintCode = "RISK_BUDGET" | "AFFORDABILITY" | "SYMBOL_CONCENTRATION" | "TOTAL_DEPLOYMENT";
export type EvidenceSource = "OWNER_ENTERED" | "PORTFOLIO" | "SIGNAL" | "SIGNAL_CONTEXT" | "MARKET_RULE";
export type EvidenceUnit = "VND" | "VND_PER_SHARE" | "DECIMAL_RATE" | "SHARES" | "TEXT";
export type WithholdingReason = "MARKET_LOT_RULE_UNAVAILABLE" | "PORTFOLIO_DATA_UNAVAILABLE" | "SIGNAL_NOT_CURRENT" | "BELOW_STANDARD_LOT";
export type SizingWarning = "COSTS_EXCLUDED" | "ENTRY_FEE_EXCLUDED" | "EXIT_FEE_EXCLUDED" | "SELL_TAX_EXCLUDED" | "ENTRY_SLIPPAGE_EXCLUDED" | "EXIT_SLIPPAGE_EXCLUDED";
export interface ConstraintResult { code: ConstraintCode; applicability: "APPLIED" | "NOT_APPLIED" | "WITHHELD"; rawQuantity: number | null; binding: boolean }
export interface InputEvidence { field: string; value: string; source: EvidenceSource; unit: EvidenceUnit; asOf: string | null; coherenceKey: string | null }
export interface SizingResult {
  status: "CALCULATED" | "WITHHELD"; symbol: string; mode: SizingMode; quantity: number | null;
  rawPermittedQuantity: number | null; lotSize: number; roundingRemainder: number | null;
  capitalBaseVnd: string | null; availableCashVnd: string | null; resolvedEntryPriceVnd: string | null;
  resolvedStopPriceVnd: string | null; effectiveEntryPriceVnd: string | null; effectiveStopPriceVnd: string | null;
  costsExcluded: boolean; riskBudgetVnd: string | null; acquisitionUnitCostVnd: string | null;
  stopNetProceedsPerShareVnd: string | null; lossPerShareVnd: string | null; requiredCapitalVnd: string | null;
  estimatedLossAtStopVnd: string | null; remainingCashVnd: string | null; projectedSymbolMarketValueVnd: string | null;
  projectedSymbolExposureRate: string | null; projectedDeploymentRate: string | null;
  constraints: ConstraintResult[]; inputEvidence: InputEvidence[]; reasonCodes: WithholdingReason[]; warnings: SizingWarning[];
  sizingRuleVersion: "position-sizing-v1"; marketRuleVersion: "market-lot-v1"; calculatedAt: string;
}

export class PositionSizingApiError extends Error {
  constructor(readonly status: number, readonly reasonCode: string) {
    super(`Position sizing failed: ${reasonCode}`); this.name = "PositionSizingApiError";
  }
}

export async function calculatePositionSize(request: SizingRequest, signal?: AbortSignal): Promise<SizingResult> {
  const csrf = await getCsrf();
  const response = await fetch("/api/v1/position-sizing/calculate", {
    method: "POST", credentials: "same-origin", signal,
    headers: { "Content-Type": "application/json", Accept: "application/json", [csrf.headerName]: csrf.token },
    body: JSON.stringify(request),
  });
  if (!response.ok) {
    const body = await response.json().catch(() => null) as Record<string, unknown> | null;
    throw new PositionSizingApiError(response.status,
      problemReason(body?.reasonCode));
  }
  return parseSizingResult(await response.json());
}

const RESULT_KEYS = ["status", "symbol", "mode", "quantity", "rawPermittedQuantity", "lotSize", "roundingRemainder",
  "capitalBaseVnd", "availableCashVnd", "resolvedEntryPriceVnd", "resolvedStopPriceVnd", "effectiveEntryPriceVnd",
  "effectiveStopPriceVnd", "costsExcluded", "riskBudgetVnd", "acquisitionUnitCostVnd", "stopNetProceedsPerShareVnd",
  "lossPerShareVnd", "requiredCapitalVnd", "estimatedLossAtStopVnd", "remainingCashVnd", "projectedSymbolMarketValueVnd",
  "projectedSymbolExposureRate", "projectedDeploymentRate", "constraints", "inputEvidence", "reasonCodes", "warnings",
  "sizingRuleVersion", "marketRuleVersion", "calculatedAt"] as const;

export function parseSizingResult(value: unknown): SizingResult {
  const o = object(value, "result"); assertKeys(o, RESULT_KEYS, "result");
  if (o.status !== "CALCULATED" && o.status !== "WITHHELD") throw new Error("Invalid status");
  if (o.mode !== "MANUAL" && o.mode !== "PORTFOLIO") throw new Error("Invalid mode");
  if (o.sizingRuleVersion !== "position-sizing-v1" || o.marketRuleVersion !== "market-lot-v1") throw new Error("Unsupported rule version");
  const constraints = array(o.constraints, "constraints").map((v) => {
    const c = object(v, "constraint"); assertKeys(c, ["code", "applicability", "rawQuantity", "binding"], "constraint");
    if (!["APPLIED", "NOT_APPLIED", "WITHHELD"].includes(String(c.applicability))) throw new Error("Invalid applicability");
    const code = enumText(c.code, ["RISK_BUDGET", "AFFORDABILITY", "SYMBOL_CONCENTRATION", "TOTAL_DEPLOYMENT"] as const, "constraint code");
    return { code, applicability: c.applicability as ConstraintResult["applicability"],
      rawQuantity: nullableInteger(c.rawQuantity), binding: bool(c.binding) };
  });
  const inputEvidence = array(o.inputEvidence, "inputEvidence").map((v) => {
    const e = object(v, "evidence"); assertKeys(e, ["field", "value", "source", "unit", "asOf", "coherenceKey"], "evidence");
    const source = enumText(e.source, ["OWNER_ENTERED", "PORTFOLIO", "SIGNAL", "SIGNAL_CONTEXT", "MARKET_RULE"] as const, "evidence source");
    const unit = enumText(e.unit, ["VND", "VND_PER_SHARE", "DECIMAL_RATE", "SHARES", "TEXT"] as const, "evidence unit");
    return { field: text(e.field), value: text(e.value), source, unit,
      asOf: nullableInstant(e.asOf), coherenceKey: nullableText(e.coherenceKey) };
  });
  const quantity = nullableInteger(o.quantity);
  const reasonCodes = enumArray(o.reasonCodes, ["MARKET_LOT_RULE_UNAVAILABLE", "PORTFOLIO_DATA_UNAVAILABLE", "SIGNAL_NOT_CURRENT", "BELOW_STANDARD_LOT"] as const, "reason code");
  const warnings = enumArray(o.warnings, ["COSTS_EXCLUDED", "ENTRY_FEE_EXCLUDED", "EXIT_FEE_EXCLUDED", "SELL_TAX_EXCLUDED", "ENTRY_SLIPPAGE_EXCLUDED", "EXIT_SLIPPAGE_EXCLUDED"] as const, "warning");
  if (o.status === "CALCULATED" && (quantity == null || reasonCodes.length > 0)) throw new Error("Inconsistent calculated result");
  if (o.status === "WITHHELD" && (quantity != null || reasonCodes.length === 0)) throw new Error("Inconsistent withheld result");
  return { status: o.status as SizingResult["status"], symbol: text(o.symbol), mode: o.mode as SizingMode,
    quantity, rawPermittedQuantity: nullableInteger(o.rawPermittedQuantity), lotSize: integer(o.lotSize),
    roundingRemainder: nullableInteger(o.roundingRemainder), capitalBaseVnd: nullableDecimal(o.capitalBaseVnd),
    availableCashVnd: nullableDecimal(o.availableCashVnd), resolvedEntryPriceVnd: nullableDecimal(o.resolvedEntryPriceVnd),
    resolvedStopPriceVnd: nullableDecimal(o.resolvedStopPriceVnd), effectiveEntryPriceVnd: nullableDecimal(o.effectiveEntryPriceVnd),
    effectiveStopPriceVnd: nullableDecimal(o.effectiveStopPriceVnd), costsExcluded: bool(o.costsExcluded),
    riskBudgetVnd: nullableDecimal(o.riskBudgetVnd), acquisitionUnitCostVnd: nullableDecimal(o.acquisitionUnitCostVnd),
    stopNetProceedsPerShareVnd: nullableDecimal(o.stopNetProceedsPerShareVnd), lossPerShareVnd: nullableDecimal(o.lossPerShareVnd),
    requiredCapitalVnd: nullableDecimal(o.requiredCapitalVnd), estimatedLossAtStopVnd: nullableDecimal(o.estimatedLossAtStopVnd),
    remainingCashVnd: nullableDecimal(o.remainingCashVnd), projectedSymbolMarketValueVnd: nullableDecimal(o.projectedSymbolMarketValueVnd),
    projectedSymbolExposureRate: nullableDecimal(o.projectedSymbolExposureRate), projectedDeploymentRate: nullableDecimal(o.projectedDeploymentRate),
    constraints, inputEvidence, reasonCodes, warnings,
    sizingRuleVersion: "position-sizing-v1", marketRuleVersion: "market-lot-v1", calculatedAt: instant(o.calculatedAt) };
}

function object(v: unknown, n: string): Record<string, unknown> { if (!v || typeof v !== "object" || Array.isArray(v)) throw new Error(`${n} must be object`); return v as Record<string, unknown>; }
function assertKeys(o: Record<string, unknown>, keys: readonly string[], n: string) { const allowed = new Set(keys); for (const k of Object.keys(o)) if (!allowed.has(k)) throw new Error(`${n} contains unknown field ${k}`); for (const k of keys) if (!(k in o)) throw new Error(`${n} missing ${k}`); }
function array(v: unknown, n: string): unknown[] { if (!Array.isArray(v)) throw new Error(`${n} must be array`); return v; }
function text(v: unknown): string { if (typeof v !== "string" || !v) throw new Error("Expected text"); return v; }
function nullableText(v: unknown): string | null { return v == null ? null : text(v); }
function instant(v: unknown): string { const value = text(v); if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$/.test(value) || Number.isNaN(Date.parse(value))) throw new Error("Expected UTC instant"); return value; }
function nullableInstant(v: unknown): string | null { return v == null ? null : instant(v); }
function bool(v: unknown): boolean { if (typeof v !== "boolean") throw new Error("Expected boolean"); return v; }
function integer(v: unknown): number { if (typeof v !== "number" || !Number.isSafeInteger(v) || v < 0) throw new Error("Expected integer"); return v; }
function nullableInteger(v: unknown): number | null { return v == null ? null : integer(v); }
function nullableDecimal(v: unknown): string | null { if (v == null) return null; if (typeof v !== "string" || !/^(0|[1-9][0-9]*)(\.[0-9]+)?$/.test(v)) throw new Error("Expected decimal string"); return v; }
function enumText<const T extends readonly string[]>(v: unknown, allowed: T, name: string): T[number] { const value = text(v); if (!(allowed as readonly string[]).includes(value)) throw new Error(`Invalid ${name}`); return value as T[number]; }
function enumArray<const T extends readonly string[]>(v: unknown, allowed: T, name: string): T[number][] { return array(v, name).map((item) => enumText(item, allowed, name)); }
function problemReason(v: unknown): string {
  const allowed = ["INVALID_REQUEST", "INVALID_INPUT", "INVALID_RISK_BUDGET", "INCOMPLETE_COST_POLICY",
    "INVALID_PRICE_RELATIONSHIP", "SIGNAL_NOT_CONFIRMED", "SERVER_ERROR"] as const;
  return typeof v === "string" && (allowed as readonly string[]).includes(v) ? v : "SERVER_ERROR";
}
