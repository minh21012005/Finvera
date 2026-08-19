import { getCsrf } from "../../auth/api/owner-access";

export type DataStatus = "CURRENT" | "DELAYED" | "STALE" | "PARTIAL" | "UNAVAILABLE";

export type MacdSignal = "BULLISH" | "BEARISH" | "NEUTRAL";
export type MaRelationship =
  | "PRICE_ABOVE_MA20" | "PRICE_BELOW_MA20"
  | "PRICE_ABOVE_MA50" | "PRICE_BELOW_MA50"
  | "PRICE_ABOVE_MA200" | "PRICE_BELOW_MA200"
  | "MA20_ABOVE_MA50" | "MA20_BELOW_MA50"
  | "MA50_ABOVE_MA200" | "MA50_BELOW_MA200";
export type BreakoutCondition = "BREAKOUT_UP" | "BREAKOUT_DOWN";
export type TrendDirection = "UPTREND" | "DOWNTREND" | "SIDEWAYS";

export type SortField =
  | "SYMBOL" | "MARKET_CAP" | "PRICE" | "PRICE_CHANGE_PERCENT" | "RSI" | "RELATIVE_VOLUME"
  | "REVENUE_GROWTH_PERCENT" | "EARNINGS_GROWTH_PERCENT" | "ROE" | "ROA" | "PE" | "PB" | "DEBT_TO_EQUITY";
export type SortDirection = "ASC" | "DESC";

export interface MarketFilter {
  exchange?: string[];
  sectorId?: string[];
  marketCapMin?: string;
  marketCapMax?: string;
}

export interface PriceFilter {
  priceMin?: string;
  priceMax?: string;
  priceChangePercentMin?: string;
  priceChangePercentMax?: string;
}

export interface TechnicalFilter {
  rsiMin?: string;
  rsiMax?: string;
  macdSignal?: MacdSignal;
  maRelationship?: MaRelationship[];
  volumeMin?: number;
  volumeMax?: number;
  relativeVolumeMin?: string;
  relativeVolumeMax?: string;
  breakout?: BreakoutCondition;
  trend?: TrendDirection;
}

export interface FundamentalFilter {
  revenueGrowthPercentMin?: string;
  revenueGrowthPercentMax?: string;
  earningsGrowthPercentMin?: string;
  earningsGrowthPercentMax?: string;
  roeMin?: string;
  roeMax?: string;
  roaMin?: string;
  roaMax?: string;
  peMin?: string;
  peMax?: string;
  pbMin?: string;
  pbMax?: string;
  debtToEquityMin?: string;
  debtToEquityMax?: string;
}

export interface ScreenRequest {
  market?: MarketFilter;
  price?: PriceFilter;
  technical?: TechnicalFilter;
  fundamental?: FundamentalFilter;
  sortField?: SortField;
  sortDirection?: SortDirection;
  limit?: number;
  offset?: number;
}

export interface ScreenMatch {
  symbol: string;
  companyName: string;
  exchange: string;
  sectorName: string | null;
  matchedValues: Record<string, string>;
  dataStatus: DataStatus;
  asOfTradingDate: string | null;
}

export interface CategoryDisclosure {
  category: "MARKET" | "PRICE" | "TECHNICAL" | "FUNDAMENTAL";
  status: DataStatus;
  reasonCode: string | null;
  excludedCount: number;
}

export interface ScreenResponse {
  ruleVersion: "screener-v1";
  matches: ScreenMatch[];
  totalMatchCount: number;
  limit: number;
  offset: number;
  categoryDisclosures: CategoryDisclosure[];
  coherenceKey: string;
  calculatedAt: string;
}

export class ScreenerApiError extends Error {
  constructor(
    readonly status: number,
    readonly reasonCode?: string,
  ) {
    super(`Screener request failed with HTTP ${status}`);
    this.name = "ScreenerApiError";
  }
}

export async function executeScreen(request: ScreenRequest, signal?: AbortSignal): Promise<ScreenResponse> {
  // A POST is state-changing by HTTP method as far as Spring Security's CSRF
  // filter is concerned, regardless of this endpoint's own read-only business
  // semantics (research R-006) — a missing token is a 403 at the real backend
  // (see ScreenerControllerTests.executeRequiresACsrfTokenEvenWithAnAuthenticatedOwnerSession),
  // so this reuses the same CSRF fetch `owner-access.ts` already established.
  const csrf = await getCsrf();
  const response = await fetch("/api/v1/screener/executions", {
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
    throw new ScreenerApiError(response.status, reasonCode);
  }
  return parseScreenResponse(await response.json());
}

const STATUSES = new Set<DataStatus>(["CURRENT", "DELAYED", "STALE", "PARTIAL", "UNAVAILABLE"]);
const CATEGORIES = new Set(["MARKET", "PRICE", "TECHNICAL", "FUNDAMENTAL"]);

export function parseScreenResponse(value: unknown): ScreenResponse {
  const r = record(value, "screen response");
  if (r.ruleVersion !== "screener-v1") throw new Error("Unsupported screener ruleVersion");
  return {
    ruleVersion: "screener-v1",
    matches: array(r.matches, "screen matches").map(parseMatch),
    totalMatchCount: nonNegativeInteger(r.totalMatchCount, "totalMatchCount"),
    limit: nonNegativeInteger(r.limit, "limit"),
    offset: nonNegativeInteger(r.offset, "offset"),
    categoryDisclosures: array(r.categoryDisclosures, "categoryDisclosures").map(parseDisclosure),
    coherenceKey: text(r.coherenceKey, "coherenceKey"),
    calculatedAt: text(r.calculatedAt, "calculatedAt"),
  };
}

function parseMatch(value: unknown): ScreenMatch {
  const m = record(value, "screen match");
  const matchedValuesRaw = record(m.matchedValues, "match matchedValues");
  const matchedValues: Record<string, string> = {};
  for (const [key, raw] of Object.entries(matchedValuesRaw)) {
    matchedValues[key] = text(raw, `matchedValues.${key}`);
  }
  return {
    symbol: text(m.symbol, "match symbol"),
    companyName: text(m.companyName, "match companyName"),
    exchange: text(m.exchange, "match exchange"),
    sectorName: nullableText(m.sectorName, "match sectorName"),
    matchedValues,
    dataStatus: status(m.dataStatus, "match dataStatus"),
    asOfTradingDate: nullableText(m.asOfTradingDate, "match asOfTradingDate"),
  };
}

function parseDisclosure(value: unknown): CategoryDisclosure {
  const d = record(value, "category disclosure");
  const category = text(d.category, "disclosure category");
  if (!CATEGORIES.has(category)) throw new Error("disclosure category is invalid");
  return {
    category: category as CategoryDisclosure["category"],
    status: status(d.status, "disclosure status"),
    reasonCode: nullableText(d.reasonCode, "disclosure reasonCode"),
    excludedCount: nonNegativeInteger(d.excludedCount, "disclosure excludedCount"),
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

function text(value: unknown, name: string): string {
  if (typeof value !== "string" || value.length === 0) throw new Error(`${name} must be a non-empty string`);
  return value;
}

function nullableText(value: unknown, name: string): string | null {
  return value === null || value === undefined ? null : text(value, name);
}

function status(value: unknown, name: string): DataStatus {
  if (typeof value !== "string" || !STATUSES.has(value as DataStatus)) throw new Error(`${name} is invalid`);
  return value as DataStatus;
}

function nonNegativeInteger(value: unknown, name: string): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < 0) {
    throw new Error(`${name} must be a safe non-negative integer`);
  }
  return value;
}
