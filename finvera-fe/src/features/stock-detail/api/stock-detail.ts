export type DataStatus = "CURRENT" | "DELAYED" | "STALE" | "PARTIAL" | "UNAVAILABLE";
export type Direction = "UP" | "DOWN" | "UNCHANGED";
export type SessionState = "PRE_OPEN" | "OPEN" | "BREAK" | "INTERRUPTED" | "CLOSED" | "NON_TRADING_DAY" | "UNKNOWN";
export type Applicability = "DEFINED" | "NOT_APPLICABLE" | "MISSING";
export type AdjustmentStatus = "ADJUSTED" | "RAW" | "NOT_APPLICABLE" | "UNKNOWN";
export type ListingStatus = "LISTED" | "SUSPENDED" | "HALTED" | "DELISTED" | "UNKNOWN";
export type ChartWindow = "1M" | "3M" | "6M" | "1Y" | "2Y";

export interface SectionMeta {
  contractVersion: "1.0";
  symbol: string;
  asOf: string;
  tradingDate: string | null;
  timezone: "Asia/Ho_Chi_Minh";
  dataStatus: DataStatus;
  coherenceKey: string;
  sources: string[];
  reasonCodes: string[];
}

export interface StockProfile {
  symbol: string;
  companyName: string | null;
  companyNameEn: string | null;
  exchange: string;
  sector: string | null;
  sectorScheme: string | null;
  listingStatus: ListingStatus;
  sharesOutstanding: number | null;
}

export interface StockPrice {
  currency: "VND";
  last: string | null;
  referencePrice: string | null;
  absoluteChange: string | null;
  percentageChange: string | null;
  direction: Direction;
  volume: number | null;
  valueVnd: string | null;
  marketCapVnd: string | null;
  applicability: Applicability;
  changeBasisReason: string | null;
}

export interface StockSession {
  state: SessionState;
  tradingDate: string | null;
  calendarVersion: string;
}

export interface StockOverview {
  meta: SectionMeta;
  profile: StockProfile;
  price: StockPrice;
  session: StockSession;
}

export interface ChartBar {
  tradingDate: string;
  open: string;
  high: string;
  low: string;
  close: string;
  volume: number | null;
}

export interface StockChart {
  meta: SectionMeta;
  window: ChartWindow;
  adjustmentStatus: AdjustmentStatus;
  bars: ChartBar[];
}

export interface StockSearchResult {
  symbol: string;
  companyName: string;
  exchange: string;
  sector: string | null;
  listingStatus: ListingStatus;
}

export class StockApiError extends Error {
  constructor(
    readonly status: number,
    readonly reasonCode?: string,
  ) {
    super(`Stock detail request failed with HTTP ${status}`);
    this.name = "StockApiError";
  }
}

const STATUSES = new Set<DataStatus>(["CURRENT", "DELAYED", "STALE", "PARTIAL", "UNAVAILABLE"]);
const DIRECTIONS = new Set<Direction>(["UP", "DOWN", "UNCHANGED"]);
const SESSION_STATES = new Set<SessionState>([
  "PRE_OPEN", "OPEN", "BREAK", "INTERRUPTED", "CLOSED", "NON_TRADING_DAY", "UNKNOWN",
]);
const APPLICABILITIES = new Set<Applicability>(["DEFINED", "NOT_APPLICABLE", "MISSING"]);
const ADJUSTMENT_STATUSES = new Set<AdjustmentStatus>(["ADJUSTED", "RAW", "NOT_APPLICABLE", "UNKNOWN"]);
const LISTING_STATUSES = new Set<ListingStatus>(["LISTED", "SUSPENDED", "HALTED", "DELISTED", "UNKNOWN"]);
const DECIMAL = /^-?[0-9]+(?:\.[0-9]+)?$/;

async function getJson(path: string, signal?: AbortSignal): Promise<unknown> {
  const response = await fetch(path, {
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
    throw new StockApiError(response.status, reasonCode);
  }
  return response.json();
}

export async function getStockOverview(symbol: string, signal?: AbortSignal): Promise<StockOverview> {
  return parseStockOverview(await getJson(`/api/v1/stocks/${encodeURIComponent(symbol)}`, signal));
}

export async function getStockChart(
  symbol: string,
  window: ChartWindow = "1Y",
  signal?: AbortSignal,
): Promise<StockChart> {
  return parseStockChart(
    await getJson(`/api/v1/stocks/${encodeURIComponent(symbol)}/chart?window=${window}`, signal),
  );
}

export async function searchStocks(query: string, signal?: AbortSignal): Promise<StockSearchResult[]> {
  if (query.trim().length === 0) return [];
  const value = await getJson(`/api/v1/stocks?query=${encodeURIComponent(query)}`, signal);
  const body = record(value, "search response");
  return array(body.results, "results").map(parseSearchResult);
}

export function parseStockOverview(value: unknown): StockOverview {
  const overview = record(value, "stock overview");
  const profile = record(overview.profile, "profile");
  return {
    meta: parseMeta(overview.meta),
    profile: {
      symbol: text(profile.symbol, "profile symbol"),
      companyName: nullableText(profile.companyName, "profile companyName"),
      companyNameEn: nullableText(profile.companyNameEn, "profile companyNameEn"),
      exchange: text(profile.exchange, "profile exchange"),
      sector: nullableText(profile.sector, "profile sector"),
      sectorScheme: nullableText(profile.sectorScheme, "profile sectorScheme"),
      listingStatus: listingStatus(profile.listingStatus, "profile listingStatus"),
      sharesOutstanding: nullableInteger(profile.sharesOutstanding, "profile sharesOutstanding"),
    },
    price: parsePrice(overview.price),
    session: parseSession(overview.session),
  };
}

export function parseStockChart(value: unknown): StockChart {
  const chart = record(value, "stock chart");
  return {
    meta: parseMeta(chart.meta),
    window: text(chart.window, "chart window") as ChartWindow,
    adjustmentStatus: adjustmentStatus(chart.adjustmentStatus, "chart adjustmentStatus"),
    bars: array(chart.bars, "chart bars").map(parseBar),
  };
}

function parsePrice(value: unknown): StockPrice {
  const price = record(value, "price");
  return {
    currency: "VND",
    last: decimal(price.last, "price last"),
    referencePrice: decimal(price.referencePrice, "price referencePrice"),
    absoluteChange: decimal(price.absoluteChange, "price absoluteChange"),
    percentageChange: decimal(price.percentageChange, "price percentageChange"),
    direction: direction(price.direction, "price direction"),
    volume: nullableInteger(price.volume, "price volume"),
    valueVnd: decimal(price.valueVnd, "price valueVnd"),
    marketCapVnd: decimal(price.marketCapVnd, "price marketCapVnd"),
    applicability: applicability(price.applicability, "price applicability"),
    changeBasisReason: nullableText(price.changeBasisReason, "price changeBasisReason"),
  };
}

function parseSession(value: unknown): StockSession {
  const session = record(value, "session");
  return {
    state: sessionState(session.state, "session state"),
    tradingDate: nullableText(session.tradingDate, "session tradingDate"),
    calendarVersion: text(session.calendarVersion, "session calendarVersion"),
  };
}

function parseMeta(value: unknown): SectionMeta {
  const meta = record(value, "meta");
  if (meta.contractVersion !== "1.0") throw new Error("Unsupported section contractVersion");
  if (meta.timezone !== "Asia/Ho_Chi_Minh") throw new Error("Unexpected section timezone");
  return {
    contractVersion: "1.0",
    symbol: text(meta.symbol, "meta symbol"),
    asOf: text(meta.asOf, "meta asOf"),
    tradingDate: nullableText(meta.tradingDate, "meta tradingDate"),
    timezone: "Asia/Ho_Chi_Minh",
    dataStatus: status(meta.dataStatus, "meta dataStatus"),
    coherenceKey: text(meta.coherenceKey, "meta coherenceKey"),
    sources: array(meta.sources, "meta sources").map((source) => text(source, "meta source")),
    reasonCodes: array(meta.reasonCodes, "meta reasonCodes").map((reason) => text(reason, "meta reasonCode")),
  };
}

function parseBar(value: unknown): ChartBar {
  const bar = record(value, "bar");
  return {
    tradingDate: text(bar.tradingDate, "bar tradingDate"),
    open: text(bar.open, "bar open"),
    high: text(bar.high, "bar high"),
    low: text(bar.low, "bar low"),
    close: text(bar.close, "bar close"),
    volume: nullableInteger(bar.volume, "bar volume"),
  };
}

function parseSearchResult(value: unknown): StockSearchResult {
  const result = record(value, "search result");
  return {
    symbol: text(result.symbol, "result symbol"),
    companyName: text(result.companyName, "result companyName"),
    exchange: text(result.exchange, "result exchange"),
    sector: nullableText(result.sector, "result sector"),
    listingStatus: listingStatus(result.listingStatus, "result listingStatus"),
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

function decimal(value: unknown, name: string): string | null {
  if (value === null || value === undefined) return null;
  if (typeof value !== "string" || !DECIMAL.test(value)) throw new Error(`${name} must be a decimal string or null`);
  return value;
}

function nullableInteger(value: unknown, name: string): number | null {
  if (value === null || value === undefined) return null;
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < 0) {
    throw new Error(`${name} must be a safe non-negative integer or null`);
  }
  return value;
}

function status(value: unknown, name: string): DataStatus {
  if (typeof value !== "string" || !STATUSES.has(value as DataStatus)) throw new Error(`${name} is invalid`);
  return value as DataStatus;
}

function direction(value: unknown, name: string): Direction {
  if (typeof value !== "string" || !DIRECTIONS.has(value as Direction)) throw new Error(`${name} is invalid`);
  return value as Direction;
}

function sessionState(value: unknown, name: string): SessionState {
  if (typeof value !== "string" || !SESSION_STATES.has(value as SessionState)) throw new Error(`${name} is invalid`);
  return value as SessionState;
}

function applicability(value: unknown, name: string): Applicability {
  if (typeof value !== "string" || !APPLICABILITIES.has(value as Applicability)) throw new Error(`${name} is invalid`);
  return value as Applicability;
}

function adjustmentStatus(value: unknown, name: string): AdjustmentStatus {
  if (typeof value !== "string" || !ADJUSTMENT_STATUSES.has(value as AdjustmentStatus)) throw new Error(`${name} is invalid`);
  return value as AdjustmentStatus;
}

function listingStatus(value: unknown, name: string): ListingStatus {
  if (typeof value !== "string" || !LISTING_STATUSES.has(value as ListingStatus)) throw new Error(`${name} is invalid`);
  return value as ListingStatus;
}
