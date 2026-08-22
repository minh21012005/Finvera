export type DataStatus = "CURRENT" | "DELAYED" | "STALE" | "PARTIAL" | "UNAVAILABLE";
export type Direction = "UP" | "DOWN" | "UNCHANGED";
export type SessionState = "PRE_OPEN" | "OPEN" | "BREAK" | "INTERRUPTED" | "CLOSED" | "NON_TRADING_DAY" | "UNKNOWN";
export type Applicability = "DEFINED" | "NOT_APPLICABLE" | "MISSING";
export type AdjustmentStatus = "ADJUSTED" | "RAW" | "NOT_APPLICABLE" | "UNKNOWN";
export type ListingStatus = "LISTED" | "SUSPENDED" | "HALTED" | "DELISTED" | "UNKNOWN";
export type ChartWindow = "1M" | "3M" | "6M" | "1Y" | "2Y" | "ALL";

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

export type IndicatorCode =
  | "MA20" | "MA50" | "MA200" | "RSI14" | "MACD" | "BBANDS" | "ATR14" | "AVG_VOLUME20" | "RELATIVE_VOLUME";
export type IndicatorComponentCode =
  | "VALUE" | "UPPER" | "MIDDLE" | "LOWER" | "BANDWIDTH" | "MACD_LINE" | "SIGNAL" | "HISTOGRAM" | "PERCENT_OF_CLOSE";
export type IndicatorUnit = "VND" | "PERCENT" | "RATIO" | "SHARES" | "POINTS";

export interface IndicatorComponentValue {
  componentCode: IndicatorComponentCode;
  value: string | null;
  unit: IndicatorUnit;
  displayPrecision: number;
  applicability: Applicability;
  reasonCode: string | null;
}

export interface IndicatorResult {
  indicatorCode: IndicatorCode;
  applicability: Applicability;
  requiredBars: number;
  availableBars: number;
  windowStartDate: string | null;
  windowEndDate: string | null;
  reasonCode: string | null;
  components: IndicatorComponentValue[];
}

export interface StockTechnical {
  meta: SectionMeta;
  ruleVersion: "technical-indicators-v1";
  adjustmentStatus: AdjustmentStatus;
  disclaimerCode: string;
  indicators: IndicatorResult[];
}

export type PeriodType = "ANNUAL" | "QUARTER";
export type ReportKind = "CONSOLIDATED" | "SEPARATE";
export type AuditStatus = "AUDITED" | "REVIEWED" | "UNAUDITED" | "UNKNOWN";

export interface FundamentalPeriod {
  label: string;
  periodType: PeriodType;
  fiscalYear: number;
  fiscalQuarter: number | null;
  periodStart: string;
  periodEnd: string;
  reportKind: ReportKind;
  auditStatus: AuditStatus;
  currency: string;
  restated: boolean;
}

export interface FundamentalMetricValue {
  metricCode: string;
  value: string | null;
  unit: string;
  displayPrecision: number;
  applicability: Applicability;
  reasonCode: string | null;
}

export interface StockFundamentals {
  meta: SectionMeta;
  period: FundamentalPeriod | null;
  metrics: FundamentalMetricValue[];
}

export type ValuationLabel = "UNDER_VALUED" | "FAIR_VALUED" | "OVER_VALUED";
export type ValuationMetricCode = "PE" | "PB" | "EV_EBITDA" | "PEG" | "DIVIDEND_YIELD";

export interface ValuationMetricValue {
  metricCode: ValuationMetricCode;
  value: string | null;
  applicability: Applicability;
  ownHistoryPercentile: string | null;
  sectorPercentile: string | null;
  effectiveWeight: string | null;
  reasonCode: string | null;
}

export interface ValuationBasis {
  usedOwnHistory: boolean;
  usedSector: boolean;
  sector: string | null;
  sectorScheme: string | null;
  sectorSchemeVersion: string | null;
  sectorConstituentCount: number | null;
  historyPointCount: number | null;
}

export interface StockValuation {
  meta: SectionMeta;
  ruleVersion: "valuation-v1";
  published: boolean;
  classification: ValuationLabel | null;
  score: string | null;
  displayedScore: number | null;
  confidence: number | null;
  disclaimerCode: string;
  basis: ValuationBasis;
  metrics: ValuationMetricValue[];
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
const INDICATOR_CODES = new Set<IndicatorCode>([
  "MA20", "MA50", "MA200", "RSI14", "MACD", "BBANDS", "ATR14", "AVG_VOLUME20", "RELATIVE_VOLUME",
]);
const INDICATOR_COMPONENT_CODES = new Set<IndicatorComponentCode>([
  "VALUE", "UPPER", "MIDDLE", "LOWER", "BANDWIDTH", "MACD_LINE", "SIGNAL", "HISTOGRAM", "PERCENT_OF_CLOSE",
]);
const INDICATOR_UNITS = new Set<IndicatorUnit>(["VND", "PERCENT", "RATIO", "SHARES", "POINTS"]);
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

export async function getStockTechnical(symbol: string, signal?: AbortSignal): Promise<StockTechnical> {
  return parseStockTechnical(await getJson(`/api/v1/stocks/${encodeURIComponent(symbol)}/technical`, signal));
}

export async function getStockFundamentals(symbol: string, signal?: AbortSignal): Promise<StockFundamentals> {
  return parseStockFundamentals(await getJson(`/api/v1/stocks/${encodeURIComponent(symbol)}/fundamentals`, signal));
}

export async function getStockValuation(symbol: string, signal?: AbortSignal): Promise<StockValuation> {
  return parseStockValuation(await getJson(`/api/v1/stocks/${encodeURIComponent(symbol)}/valuation`, signal));
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

export function parseStockTechnical(value: unknown): StockTechnical {
  const technical = record(value, "stock technical");
  if (technical.ruleVersion !== "technical-indicators-v1") throw new Error("Unsupported technical ruleVersion");
  return {
    meta: parseMeta(technical.meta),
    ruleVersion: "technical-indicators-v1",
    adjustmentStatus: adjustmentStatus(technical.adjustmentStatus, "technical adjustmentStatus"),
    disclaimerCode: text(technical.disclaimerCode, "technical disclaimerCode"),
    indicators: array(technical.indicators, "technical indicators").map(parseIndicatorResult),
  };
}

function parseIndicatorResult(value: unknown): IndicatorResult {
  const result = record(value, "indicator result");
  return {
    indicatorCode: indicatorCode(result.indicatorCode, "indicator indicatorCode"),
    applicability: applicability(result.applicability, "indicator applicability"),
    requiredBars: nonNegativeInteger(result.requiredBars, "indicator requiredBars"),
    availableBars: nonNegativeInteger(result.availableBars, "indicator availableBars"),
    windowStartDate: nullableText(result.windowStartDate, "indicator windowStartDate"),
    windowEndDate: nullableText(result.windowEndDate, "indicator windowEndDate"),
    reasonCode: nullableText(result.reasonCode, "indicator reasonCode"),
    components: array(result.components, "indicator components").map(parseIndicatorComponent),
  };
}

function parseIndicatorComponent(value: unknown): IndicatorComponentValue {
  const component = record(value, "indicator component");
  return {
    componentCode: indicatorComponentCode(component.componentCode, "component componentCode"),
    value: decimal(component.value, "component value"),
    unit: indicatorUnit(component.unit, "component unit"),
    displayPrecision: nonNegativeInteger(component.displayPrecision, "component displayPrecision"),
    applicability: applicability(component.applicability, "component applicability"),
    reasonCode: nullableText(component.reasonCode, "component reasonCode"),
  };
}

export function parseStockFundamentals(value: unknown): StockFundamentals {
  const f = record(value, "stock fundamentals");
  return {
    meta: parseMeta(f.meta),
    period: f.period ? parseFundamentalPeriod(f.period) : null,
    metrics: array(f.metrics, "fundamentals metrics").map(parseFundamentalMetric),
  };
}

function parseFundamentalPeriod(value: unknown): FundamentalPeriod {
  const p = record(value, "fundamental period");
  return {
    label: text(p.label, "period label"),
    periodType: text(p.periodType, "period periodType") as PeriodType,
    fiscalYear: nonNegativeInteger(p.fiscalYear, "period fiscalYear"),
    fiscalQuarter: nullableInteger(p.fiscalQuarter, "period fiscalQuarter"),
    periodStart: text(p.periodStart, "period periodStart"),
    periodEnd: text(p.periodEnd, "period periodEnd"),
    reportKind: text(p.reportKind, "period reportKind") as ReportKind,
    auditStatus: text(p.auditStatus, "period auditStatus") as AuditStatus,
    currency: text(p.currency, "period currency"),
    restated: typeof p.restated === "boolean" ? p.restated : false,
  };
}

function parseFundamentalMetric(value: unknown): FundamentalMetricValue {
  const m = record(value, "fundamental metric");
  return {
    metricCode: text(m.metricCode, "metric metricCode"),
    value: decimal(m.value, "metric value"),
    unit: text(m.unit, "metric unit"),
    displayPrecision: nonNegativeInteger(m.displayPrecision, "metric displayPrecision"),
    applicability: applicability(m.applicability, "metric applicability"),
    reasonCode: nullableText(m.reasonCode, "metric reasonCode"),
  };
}

export function parseStockValuation(value: unknown): StockValuation {
  const v = record(value, "stock valuation");
  if (v.ruleVersion !== "valuation-v1") throw new Error("Unsupported valuation ruleVersion");
  return {
    meta: parseMeta(v.meta),
    ruleVersion: "valuation-v1",
    published: typeof v.published === "boolean" ? v.published : false,
    classification: v.classification ? (text(v.classification, "valuation classification") as ValuationLabel) : null,
    score: decimal(v.score, "valuation score"),
    displayedScore: nullableInteger(v.displayedScore, "valuation displayedScore"),
    confidence: nullableInteger(v.confidence, "valuation confidence"),
    disclaimerCode: text(v.disclaimerCode, "valuation disclaimerCode"),
    basis: parseValuationBasis(v.basis),
    metrics: array(v.metrics, "valuation metrics").map(parseValuationMetric),
  };
}

function parseValuationBasis(value: unknown): ValuationBasis {
  const b = record(value, "valuation basis");
  return {
    usedOwnHistory: typeof b.usedOwnHistory === "boolean" ? b.usedOwnHistory : false,
    usedSector: typeof b.usedSector === "boolean" ? b.usedSector : false,
    sector: nullableText(b.sector, "basis sector"),
    sectorScheme: nullableText(b.sectorScheme, "basis sectorScheme"),
    sectorSchemeVersion: nullableText(b.sectorSchemeVersion, "basis sectorSchemeVersion"),
    sectorConstituentCount: nullableInteger(b.sectorConstituentCount, "basis sectorConstituentCount"),
    historyPointCount: nullableInteger(b.historyPointCount, "basis historyPointCount"),
  };
}

function parseValuationMetric(value: unknown): ValuationMetricValue {
  const m = record(value, "valuation metric");
  return {
    metricCode: text(m.metricCode, "valuation metricCode") as ValuationMetricCode,
    value: decimal(m.value, "valuation metric value"),
    applicability: applicability(m.applicability, "valuation metric applicability"),
    ownHistoryPercentile: decimal(m.ownHistoryPercentile, "valuation metric ownHistoryPercentile"),
    sectorPercentile: decimal(m.sectorPercentile, "valuation metric sectorPercentile"),
    effectiveWeight: decimal(m.effectiveWeight, "valuation metric effectiveWeight"),
    reasonCode: nullableText(m.reasonCode, "valuation metric reasonCode"),
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

function nonNegativeInteger(value: unknown, name: string): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < 0) {
    throw new Error(`${name} must be a safe non-negative integer`);
  }
  return value;
}

function indicatorCode(value: unknown, name: string): IndicatorCode {
  if (typeof value !== "string" || !INDICATOR_CODES.has(value as IndicatorCode)) throw new Error(`${name} is invalid`);
  return value as IndicatorCode;
}

function indicatorComponentCode(value: unknown, name: string): IndicatorComponentCode {
  if (typeof value !== "string" || !INDICATOR_COMPONENT_CODES.has(value as IndicatorComponentCode)) {
    throw new Error(`${name} is invalid`);
  }
  return value as IndicatorComponentCode;
}

function indicatorUnit(value: unknown, name: string): IndicatorUnit {
  if (typeof value !== "string" || !INDICATOR_UNITS.has(value as IndicatorUnit)) throw new Error(`${name} is invalid`);
  return value as IndicatorUnit;
}
