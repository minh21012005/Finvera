import { getCsrf } from "../../auth/api/owner-access";
import type { Signal, StrategyCode } from "../../stock-detail/api/stock-signals";

export type { StrategyCode } from "../../stock-detail/api/stock-signals";

const STRATEGY_CODES = new Set<StrategyCode>([
  "TREND_FOLLOWING", "MOMENTUM", "BREAKOUT", "PULLBACK", "MEAN_REVERSION",
  "MA_CROSSOVER", "MACD_BASED", "RSI_BASED",
]);

export interface ScanRequest {
  limit?: number;
  offset?: number;
}

export interface ScanMatch {
  symbol: string;
  companyName: string;
  exchange: string;
  signal: Signal;
}

export interface ScanResponse {
  strategyCode: StrategyCode;
  matches: ScanMatch[];
  totalMatchCount: number;
  limit: number;
  offset: number;
  excludedForInsufficientHistoryCount: number;
  calculatedAt: string;
}

export class StrategyScanApiError extends Error {
  constructor(
    readonly status: number,
    readonly reasonCode?: string,
  ) {
    super(`Strategy scan request failed with HTTP ${status}`);
    this.name = "StrategyScanApiError";
  }
}

export async function scanStrategy(
  strategyCode: StrategyCode,
  request: ScanRequest = {},
  signal?: AbortSignal,
): Promise<ScanResponse> {
  // A POST is state-changing by HTTP method regardless of this endpoint's own
  // read-only business semantics (research R-007) — CSRF is required from the
  // first implementation (quickstart.md Authorization checks, Feature 003's
  // T030 follow-up finding), so this reuses the same CSRF fetch pattern
  // `stock-screener.ts` already established.
  const csrf = await getCsrf();
  const response = await fetch(`/api/v1/strategies/${encodeURIComponent(strategyCode)}/scan`, {
    method: "POST",
    credentials: "same-origin",
    headers: { Accept: "application/json", "Content-Type": "application/json", [csrf.headerName]: csrf.token },
    body: JSON.stringify(request),
    signal,
  });
  if (!response.ok) {
    let reasonCode: string | undefined;
    if (response.headers.get("content-type")?.includes("application/problem+json")) {
      const body = (await response.json().catch(() => null)) as { reasonCode?: string } | null;
      reasonCode = body?.reasonCode;
    }
    throw new StrategyScanApiError(response.status, reasonCode);
  }
  return parseScanResponse(await response.json());
}

export function parseScanResponse(value: unknown): ScanResponse {
  const r = record(value, "scan response");
  return {
    strategyCode: strategyCode(r.strategyCode, "scan strategyCode"),
    matches: array(r.matches, "scan matches").map(parseMatch),
    totalMatchCount: nonNegativeInteger(r.totalMatchCount, "totalMatchCount"),
    limit: nonNegativeInteger(r.limit, "limit"),
    offset: nonNegativeInteger(r.offset, "offset"),
    excludedForInsufficientHistoryCount: nonNegativeInteger(
      r.excludedForInsufficientHistoryCount,
      "excludedForInsufficientHistoryCount",
    ),
    calculatedAt: text(r.calculatedAt, "calculatedAt"),
  };
}

function parseMatch(value: unknown): ScanMatch {
  const m = record(value, "scan match");
  return {
    symbol: text(m.symbol, "match symbol"),
    companyName: text(m.companyName, "match companyName"),
    exchange: text(m.exchange, "match exchange"),
    signal: parseSignal(m.signal),
  };
}

// Reuses stock-signals.ts's exact field shape/validation for one Signal object.
function parseSignal(value: unknown): Signal {
  const s = record(value, "match signal");
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
    riskLevel: s.riskLevel == null ? null : (text(s.riskLevel, "signal riskLevel") as Signal["riskLevel"]),
    signalStrength:
      s.signalStrength == null ? null : (text(s.signalStrength, "signal signalStrength") as Signal["signalStrength"]),
    riskFactors: array(s.riskFactors, "signal riskFactors").map((f) => {
      const factor = record(f, "risk factor");
      return {
        factorCode: text(factor.factorCode, "factor factorCode") as Signal["riskFactors"][number]["factorCode"],
        inputValue: factor.inputValue == null ? null : decimal(factor.inputValue, "factor inputValue"),
        factorScore: nullableInteger(factor.factorScore, "factor factorScore"),
        applicability: text(factor.applicability, "factor applicability") as Signal["riskFactors"][number]["applicability"],
        reasonCode: factor.reasonCode == null ? null : text(factor.reasonCode, "factor reasonCode"),
      };
    }),
    supportingEvidence: stringRecord(s.supportingEvidence, "signal supportingEvidence"),
    reasonCodes: array(s.reasonCodes, "signal reasonCodes").map((r) => text(r, "signal reasonCode")),
    asOfTradingDate: text(s.asOfTradingDate, "signal asOfTradingDate"),
    calculatedAt: text(s.calculatedAt, "signal calculatedAt"),
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

const DECIMAL = /^-?[0-9]+(?:\.[0-9]+)?$/;

function decimal(value: unknown, name: string): string {
  if (typeof value !== "string" || !DECIMAL.test(value)) throw new Error(`${name} must be a decimal string`);
  return value;
}

function nullableInteger(value: unknown, name: string): number | null {
  if (value === null || value === undefined) return null;
  if (typeof value !== "number" || !Number.isSafeInteger(value)) throw new Error(`${name} must be a safe integer or null`);
  return value;
}

function nonNegativeInteger(value: unknown, name: string): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < 0) {
    throw new Error(`${name} must be a safe non-negative integer`);
  }
  return value;
}

function strategyCode(value: unknown, name: string): StrategyCode {
  if (typeof value !== "string" || !STRATEGY_CODES.has(value as StrategyCode)) throw new Error(`${name} is invalid`);
  return value as StrategyCode;
}
