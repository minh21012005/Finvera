export type StrategyCode =
  | "TREND_FOLLOWING" | "MOMENTUM" | "BREAKOUT" | "PULLBACK" | "MEAN_REVERSION"
  | "MA_CROSSOVER" | "MACD_BASED" | "RSI_BASED";
export type EvaluationStatus = "SIGNAL" | "NO_SIGNAL" | "INSUFFICIENT_HISTORY" | "WITHHELD";
export type Direction = "LONG";
export type RiskLevel = "LOW" | "MEDIUM" | "HIGH";
export type SignalStrength = "WEAK" | "MODERATE" | "STRONG";
export type RiskFactorCode = "VOLATILITY" | "ATR" | "DRAWDOWN" | "LIQUIDITY" | "STOP_DISTANCE" | "MARKET_REGIME";
export type Applicability = "DEFINED" | "NOT_APPLICABLE" | "MISSING";
export type DataStatus = "CURRENT" | "DELAYED" | "STALE" | "PARTIAL" | "UNAVAILABLE";

const STRATEGY_CODES = new Set<StrategyCode>([
  "TREND_FOLLOWING", "MOMENTUM", "BREAKOUT", "PULLBACK", "MEAN_REVERSION",
  "MA_CROSSOVER", "MACD_BASED", "RSI_BASED",
]);
const EVALUATION_STATUSES = new Set<EvaluationStatus>([
  "SIGNAL", "NO_SIGNAL", "INSUFFICIENT_HISTORY", "WITHHELD",
]);
const RISK_LEVELS = new Set<RiskLevel>(["LOW", "MEDIUM", "HIGH"]);
const SIGNAL_STRENGTHS = new Set<SignalStrength>(["WEAK", "MODERATE", "STRONG"]);
const RISK_FACTOR_CODES = new Set<RiskFactorCode>([
  "VOLATILITY", "ATR", "DRAWDOWN", "LIQUIDITY", "STOP_DISTANCE", "MARKET_REGIME",
]);
const APPLICABILITIES = new Set<Applicability>(["DEFINED", "NOT_APPLICABLE", "MISSING"]);
const DATA_STATUSES = new Set<DataStatus>(["CURRENT", "DELAYED", "STALE", "PARTIAL", "UNAVAILABLE"]);
const DECIMAL = /^-?[0-9]+(?:\.[0-9]+)?$/;

export interface RiskFactor {
  factorCode: RiskFactorCode;
  inputValue: string | null;
  factorScore: number | null;
  applicability: Applicability;
  reasonCode: string | null;
}

export interface Signal {
  strategyCode: StrategyCode;
  ruleVersion: "strategy-signal-v1";
  direction: Direction;
  entryLow: string;
  entryHigh: string;
  stopLoss: string;
  target1: string;
  target2: string;
  riskReward: string;
  riskScore: number | null;
  riskLevel: RiskLevel | null;
  signalStrength: SignalStrength | null;
  riskFactors: RiskFactor[];
  supportingEvidence: Record<string, string>;
  reasonCodes: string[];
  asOfTradingDate: string;
  calculatedAt: string;
}

export interface StrategyEvaluation {
  strategyCode: StrategyCode;
  status: EvaluationStatus;
  reasonCode: string | null;
  signal: Signal | null;
}

export interface StockSignals {
  symbol: string;
  dataStatus: DataStatus;
  evaluations: StrategyEvaluation[];
  disclaimerCode: string;
  coherenceKey: string;
  asOf: string;
}

export class SignalsApiError extends Error {
  constructor(
    readonly status: number,
    readonly reasonCode?: string,
  ) {
    super(`Stock signals request failed with HTTP ${status}`);
    this.name = "SignalsApiError";
  }
}

export async function getStockSignals(symbol: string, signal?: AbortSignal): Promise<StockSignals> {
  const response = await fetch(`/api/v1/stocks/${encodeURIComponent(symbol)}/signals`, {
    method: "GET",
    credentials: "same-origin",
    headers: { Accept: "application/json" },
    signal,
  });
  if (!response.ok) {
    let reasonCode: string | undefined;
    if (response.headers.get("content-type")?.includes("application/problem+json")) {
      const body = (await response.json().catch(() => null)) as { reasonCode?: string } | null;
      reasonCode = body?.reasonCode;
    }
    throw new SignalsApiError(response.status, reasonCode);
  }
  return parseStockSignals(await response.json());
}

export function parseStockSignals(value: unknown): StockSignals {
  const body = record(value, "stock signals");
  return {
    symbol: text(body.symbol, "symbol"),
    dataStatus: dataStatus(body.dataStatus, "dataStatus"),
    evaluations: array(body.evaluations, "evaluations").map(parseEvaluation),
    disclaimerCode: text(body.disclaimerCode, "disclaimerCode"),
    coherenceKey: text(body.coherenceKey, "coherenceKey"),
    asOf: text(body.asOf, "asOf"),
  };
}

function parseEvaluation(value: unknown): StrategyEvaluation {
  const e = record(value, "evaluation");
  return {
    strategyCode: strategyCode(e.strategyCode, "evaluation strategyCode"),
    status: evaluationStatus(e.status, "evaluation status"),
    reasonCode: nullableText(e.reasonCode, "evaluation reasonCode"),
    signal: e.signal == null ? null : parseSignal(e.signal),
  };
}

function parseSignal(value: unknown): Signal {
  const s = record(value, "signal");
  if (s.ruleVersion !== "strategy-signal-v1") throw new Error("Unsupported signal ruleVersion");
  if (s.direction !== "LONG") throw new Error("Unsupported signal direction");
  return {
    strategyCode: strategyCode(s.strategyCode, "signal strategyCode"),
    ruleVersion: "strategy-signal-v1",
    direction: "LONG",
    entryLow: decimal(s.entryLow, "signal entryLow"),
    entryHigh: decimal(s.entryHigh, "signal entryHigh"),
    stopLoss: decimal(s.stopLoss, "signal stopLoss"),
    target1: decimal(s.target1, "signal target1"),
    target2: decimal(s.target2, "signal target2"),
    riskReward: decimal(s.riskReward, "signal riskReward"),
    riskScore: nullableInteger(s.riskScore, "signal riskScore"),
    riskLevel: s.riskLevel == null ? null : riskLevel(s.riskLevel, "signal riskLevel"),
    signalStrength: s.signalStrength == null ? null : signalStrength(s.signalStrength, "signal signalStrength"),
    riskFactors: array(s.riskFactors, "signal riskFactors").map(parseRiskFactor),
    supportingEvidence: stringRecord(s.supportingEvidence, "signal supportingEvidence"),
    reasonCodes: array(s.reasonCodes, "signal reasonCodes").map((r) => text(r, "signal reasonCode")),
    asOfTradingDate: text(s.asOfTradingDate, "signal asOfTradingDate"),
    calculatedAt: text(s.calculatedAt, "signal calculatedAt"),
  };
}

function parseRiskFactor(value: unknown): RiskFactor {
  const f = record(value, "risk factor");
  return {
    factorCode: riskFactorCode(f.factorCode, "factor factorCode"),
    inputValue: nullableDecimal(f.inputValue, "factor inputValue"),
    factorScore: nullableInteger(f.factorScore, "factor factorScore"),
    applicability: applicability(f.applicability, "factor applicability"),
    reasonCode: nullableText(f.reasonCode, "factor reasonCode"),
  };
}

function record(value: unknown, name: string): Record<string, unknown> {
  if (typeof value !== "object" || value === null || Array.isArray(value)) throw new Error(`${name} must be an object`);
  return value as Record<string, unknown>;
}

function array(value: unknown, name: string): unknown[] {
  if (!Array.isArray(value)) throw new Error(`${name} must be an array`);
  return value;
}

function stringRecord(value: unknown, name: string): Record<string, string> {
  const obj = record(value, name);
  const result: Record<string, string> = {};
  for (const [key, v] of Object.entries(obj)) {
    if (v !== null && v !== undefined) result[key] = String(v);
  }
  return result;
}

function text(value: unknown, name: string): string {
  if (typeof value !== "string" || value.length === 0) throw new Error(`${name} must be a non-empty string`);
  return value;
}

function nullableText(value: unknown, name: string): string | null {
  return value === null || value === undefined ? null : text(value, name);
}

function decimal(value: unknown, name: string): string {
  if (typeof value !== "string" || !DECIMAL.test(value)) throw new Error(`${name} must be a decimal string`);
  return value;
}

function nullableDecimal(value: unknown, name: string): string | null {
  if (value === null || value === undefined) return null;
  return decimal(value, name);
}

function nullableInteger(value: unknown, name: string): number | null {
  if (value === null || value === undefined) return null;
  if (typeof value !== "number" || !Number.isSafeInteger(value)) throw new Error(`${name} must be a safe integer or null`);
  return value;
}

function strategyCode(value: unknown, name: string): StrategyCode {
  if (typeof value !== "string" || !STRATEGY_CODES.has(value as StrategyCode)) throw new Error(`${name} is invalid`);
  return value as StrategyCode;
}

function evaluationStatus(value: unknown, name: string): EvaluationStatus {
  if (typeof value !== "string" || !EVALUATION_STATUSES.has(value as EvaluationStatus)) throw new Error(`${name} is invalid`);
  return value as EvaluationStatus;
}

function riskLevel(value: unknown, name: string): RiskLevel {
  if (typeof value !== "string" || !RISK_LEVELS.has(value as RiskLevel)) throw new Error(`${name} is invalid`);
  return value as RiskLevel;
}

function signalStrength(value: unknown, name: string): SignalStrength {
  if (typeof value !== "string" || !SIGNAL_STRENGTHS.has(value as SignalStrength)) throw new Error(`${name} is invalid`);
  return value as SignalStrength;
}

function riskFactorCode(value: unknown, name: string): RiskFactorCode {
  if (typeof value !== "string" || !RISK_FACTOR_CODES.has(value as RiskFactorCode)) throw new Error(`${name} is invalid`);
  return value as RiskFactorCode;
}

function applicability(value: unknown, name: string): Applicability {
  if (typeof value !== "string" || !APPLICABILITIES.has(value as Applicability)) throw new Error(`${name} is invalid`);
  return value as Applicability;
}

function dataStatus(value: unknown, name: string): DataStatus {
  if (typeof value !== "string" || !DATA_STATUSES.has(value as DataStatus)) throw new Error(`${name} is invalid`);
  return value as DataStatus;
}
