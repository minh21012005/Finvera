export type DataStatus = "CURRENT" | "DELAYED" | "STALE" | "PARTIAL" | "UNAVAILABLE";
export type Direction = "UP" | "DOWN" | "UNCHANGED" | null;
export type IndexCode = "VN_INDEX" | "VN30" | "HNX_INDEX" | "UPCOM_INDEX";
export type Venue = "HOSE" | "HNX" | "UPCOM";

export interface MarketIndex {
  code: IndexCode;
  displayName: string;
  venue: Venue;
  dataStatus: DataStatus;
  direction: Direction;
  value: string | null;
  absoluteChange: string | null;
  percentageChange: string | null;
  matchedVolume: number | null;
  matchedValueVnd: string | null;
  unit: "INDEX_POINT";
  currency: "VND";
  tradingDate: string | null;
  asOf: string | null;
  source: { provider: string; dataset: string };
  revision: number | null;
  reasonCodes: string[];
}

export interface MarketBreadth {
  dataStatus: DataStatus;
  advancing: number | null;
  declining: number | null;
  unchanged: number | null;
  eligible: number | null;
  unclassified: number | null;
  universeVersion: string;
  tradingDate: string | null;
  asOf: string | null;
  source: { provider: string; dataset: string };
  reasonCodes: string[];
}

export interface MarketOverview {
  contractVersion: "1.0";
  generatedAt: string;
  tradingDate: string;
  timezone: "Asia/Ho_Chi_Minh";
  dataStatus: DataStatus;
  session: {
    state: string;
    tradingDate: string;
    asOf: string;
    calendarVersion: string;
    venueStates: unknown[];
  };
  indices: MarketIndex[];
  breadth: MarketBreadth;
  regime: Record<string, unknown>;
  warnings: unknown[];
}

export class MarketOverviewApiError extends Error {
  constructor(readonly status: number) {
    super(`Market overview request failed with HTTP ${status}`);
    this.name = "MarketOverviewApiError";
  }
}

const STATUSES = new Set<DataStatus>(["CURRENT", "DELAYED", "STALE", "PARTIAL", "UNAVAILABLE"]);
const CODES = ["VN_INDEX", "VN30", "HNX_INDEX", "UPCOM_INDEX"] as const;
const VENUES = new Set<Venue>(["HOSE", "HNX", "UPCOM"]);
const DIRECTIONS = new Set<Exclude<Direction, null>>(["UP", "DOWN", "UNCHANGED"]);
const DECIMAL = /^-?[0-9]+(?:\.[0-9]+)?$/;

export async function getMarketOverview(signal?: AbortSignal): Promise<MarketOverview> {
  const response = await fetch("/api/v1/market/overview", {
    method: "GET",
    credentials: "same-origin",
    headers: { Accept: "application/json" },
    signal,
  });
  if (!response.ok) {
    throw new MarketOverviewApiError(response.status);
  }
  return parseMarketOverview(await response.json());
}

export function parseMarketOverview(value: unknown): MarketOverview {
  const overview = record(value, "market overview");
  if (overview.contractVersion !== "1.0") throw new Error("Unsupported market overview contractVersion");
  if (overview.timezone !== "Asia/Ho_Chi_Minh") throw new Error("Unexpected market overview timezone");

  const indices = array(overview.indices, "indices").map(parseIndex);
  if (indices.length !== CODES.length || !indices.every((index, position) => index.code === CODES[position])) {
    throw new Error("indices must contain the four supported codes in stable order");
  }
  return {
    contractVersion: "1.0",
    generatedAt: text(overview.generatedAt, "generatedAt"),
    tradingDate: text(overview.tradingDate, "tradingDate"),
    timezone: "Asia/Ho_Chi_Minh",
    dataStatus: status(overview.dataStatus, "dataStatus"),
    session: parseSession(overview.session),
    indices,
    breadth: parseBreadth(overview.breadth),
    regime: record(overview.regime, "regime"),
    warnings: array(overview.warnings, "warnings"),
  };
}

function parseBreadth(value: unknown): MarketBreadth {
  const breadth = record(value, "breadth");
  const source = record(breadth.source, "breadth source");
  return {
    dataStatus: status(breadth.dataStatus, "breadth dataStatus"),
    advancing: nullableInteger(breadth.advancing, "breadth advancing"),
    declining: nullableInteger(breadth.declining, "breadth declining"),
    unchanged: nullableInteger(breadth.unchanged, "breadth unchanged"),
    eligible: nullableInteger(breadth.eligible, "breadth eligible"),
    unclassified: nullableInteger(breadth.unclassified, "breadth unclassified"),
    universeVersion: text(breadth.universeVersion, "breadth universeVersion"),
    tradingDate: nullableText(breadth.tradingDate, "breadth tradingDate"),
    asOf: nullableText(breadth.asOf, "breadth asOf"),
    source: { provider: text(source.provider, "breadth source provider"), dataset: text(source.dataset, "breadth source dataset") },
    reasonCodes: array(breadth.reasonCodes, "breadth reasonCodes").map((reason) => text(reason, "breadth reasonCode")),
  };
}

function parseIndex(value: unknown): MarketIndex {
  const index = record(value, "index");
  const direction = index.direction === null ? null : directionValue(index.direction);
  const code = text(index.code, "index code");
  if (!CODES.includes(code as IndexCode)) throw new Error("Unsupported index code");
  const venue = text(index.venue, "venue");
  if (!VENUES.has(venue as Venue)) throw new Error("Unsupported venue");
  if (index.unit !== "INDEX_POINT" || index.currency !== "VND") throw new Error("Unexpected index unit or currency");
  const source = record(index.source, "source");
  return {
    code: code as IndexCode,
    displayName: text(index.displayName, "displayName"),
    venue: venue as Venue,
    dataStatus: status(index.dataStatus, "index dataStatus"),
    direction,
    value: decimal(index.value, "value"),
    absoluteChange: decimal(index.absoluteChange, "absoluteChange"),
    percentageChange: decimal(index.percentageChange, "percentageChange"),
    matchedVolume: nullableInteger(index.matchedVolume, "matchedVolume"),
    matchedValueVnd: decimal(index.matchedValueVnd, "matchedValueVnd"),
    unit: "INDEX_POINT",
    currency: "VND",
    tradingDate: nullableText(index.tradingDate, "tradingDate"),
    asOf: nullableText(index.asOf, "asOf"),
    source: { provider: text(source.provider, "source provider"), dataset: text(source.dataset, "source dataset") },
    revision: nullableInteger(index.revision, "revision"),
    reasonCodes: array(index.reasonCodes, "reasonCodes").map((reason) => text(reason, "reasonCode")),
  };
}

function parseSession(value: unknown): MarketOverview["session"] {
  const session = record(value, "session");
  return {
    state: text(session.state, "session state"),
    tradingDate: text(session.tradingDate, "session tradingDate"),
    asOf: text(session.asOf, "session asOf"),
    calendarVersion: text(session.calendarVersion, "calendarVersion"),
    venueStates: array(session.venueStates, "venueStates"),
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
  return value === null ? null : text(value, name);
}

function decimal(value: unknown, name: string): string | null {
  if (value === null) return null;
  if (typeof value !== "string" || !DECIMAL.test(value)) throw new Error(`${name} must be a decimal string or null`);
  return value;
}

function nullableInteger(value: unknown, name: string): number | null {
  if (value === null) return null;
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < 0) throw new Error(`${name} must be a safe non-negative integer or null`);
  return value;
}

function status(value: unknown, name: string): DataStatus {
  if (typeof value !== "string" || !STATUSES.has(value as DataStatus)) throw new Error(`${name} is invalid`);
  return value as DataStatus;
}

function directionValue(value: unknown): Exclude<Direction, null> {
  if (typeof value !== "string" || !DIRECTIONS.has(value as Exclude<Direction, null>)) throw new Error("direction is invalid");
  return value as Exclude<Direction, null>;
}
