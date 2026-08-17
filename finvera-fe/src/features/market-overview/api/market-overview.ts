export type DataStatus = "CURRENT" | "DELAYED" | "STALE" | "PARTIAL" | "UNAVAILABLE";
export type Direction = "UP" | "DOWN" | "UNCHANGED" | null;
export type IndexCode = "VN_INDEX" | "VN30" | "HNX_INDEX" | "UPCOM_INDEX";
export type Venue = "HOSE" | "HNX" | "UPCOM";
export type SessionState = "PRE_OPEN" | "OPEN" | "BREAK" | "INTERRUPTED" | "CLOSED" | "NON_TRADING_DAY" | "UNKNOWN";
export type WarningSeverity = "INFO" | "WARNING" | "ERROR";
export type WarningSection = "OVERVIEW" | "SESSION" | "INDEX" | "BREADTH" | "REGIME";

export interface MarketSession {
  state: SessionState;
  tradingDate: string;
  asOf: string;
  calendarVersion: string;
  venueStates: Array<{ venue: Venue; state: SessionState }>;
}

export interface MarketWarning {
  code: string;
  severity: WarningSeverity;
  section: WarningSection;
  subject: string | null;
}

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

export type RegimeLabel = "BULL" | "EARLY_BULL" | "SIDEWAYS" | "EARLY_BEAR" | "BEAR";
export type FactorDirection = "POSITIVE" | "NEGATIVE" | "NEUTRAL";
export type RegimeFactorCode = "TREND" | "BREADTH" | "MOMENTUM" | "LIQUIDITY" | "VOLATILITY";

export interface MarketRegimeFactor {
  code: RegimeFactorCode;
  direction: FactorDirection;
  descriptionCode: string;
  normalizedScore: string | null;
  effectiveWeight: string | null;
  contribution: string | null;
  observations: Array<{ name: string; value: string | null; unit: string }>;
}

export interface MarketRegime {
  dataStatus: DataStatus;
  ruleVersion: "market-regime-v1";
  label: RegimeLabel | null;
  score: number | null;
  confidence: number | null;
  confidenceMeaning: "ASSESSMENT_QUALITY_NOT_FORECAST_PROBABILITY";
  tradingDate: string | null;
  asOf: string | null;
  factors: MarketRegimeFactor[];
  source: { provider: string; dataset: string };
  reasonCodes: string[];
  disclaimerCode: "QUANTITATIVE_DECISION_SUPPORT_NOT_INVESTMENT_ADVICE";
}

export interface MarketOverview {
  contractVersion: "1.0";
  generatedAt: string;
  tradingDate: string;
  timezone: "Asia/Ho_Chi_Minh";
  dataStatus: DataStatus;
  session: MarketSession;
  indices: MarketIndex[];
  breadth: MarketBreadth;
  regime: MarketRegime;
  warnings: MarketWarning[];
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
const REGIME_LABELS = new Set<RegimeLabel>(["BULL", "EARLY_BULL", "SIDEWAYS", "EARLY_BEAR", "BEAR"]);
const FACTOR_DIRECTIONS = new Set<FactorDirection>(["POSITIVE", "NEGATIVE", "NEUTRAL"]);
const FACTOR_CODES = new Set<RegimeFactorCode>(["TREND", "BREADTH", "MOMENTUM", "LIQUIDITY", "VOLATILITY"]);
const SESSION_STATES = new Set<SessionState>(["PRE_OPEN", "OPEN", "BREAK", "INTERRUPTED", "CLOSED", "NON_TRADING_DAY", "UNKNOWN"]);
const WARNING_SEVERITIES = new Set<WarningSeverity>(["INFO", "WARNING", "ERROR"]);
const WARNING_SECTIONS = new Set<WarningSection>(["OVERVIEW", "SESSION", "INDEX", "BREADTH", "REGIME"]);
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
    regime: parseRegime(overview.regime),
    warnings: array(overview.warnings, "warnings").map(parseWarning),
  };
}

function parseRegime(value: unknown): MarketRegime {
  const regime = record(value, "regime");
  if (regime.ruleVersion !== "market-regime-v1") throw new Error("Unexpected regime ruleVersion");
  if (regime.confidenceMeaning !== "ASSESSMENT_QUALITY_NOT_FORECAST_PROBABILITY") {
    throw new Error("Unexpected regime confidence meaning");
  }
  if (regime.disclaimerCode !== "QUANTITATIVE_DECISION_SUPPORT_NOT_INVESTMENT_ADVICE") {
    throw new Error("Unexpected regime disclaimer");
  }
  const label = nullableRegimeLabel(regime.label);
  const score = nullableScore(regime.score, "regime score");
  const confidence = nullableScore(regime.confidence, "regime confidence");
  if ((label === null) !== (score === null) || (label === null) !== (confidence === null)) {
    throw new Error("regime label, score, and confidence must be jointly present or absent");
  }
  const source = record(regime.source, "regime source");
  const factors = array(regime.factors, "regime factors").map(parseRegimeFactor);
  if (factors.length > 5) throw new Error("regime factors may contain at most five entries");
  return {
    dataStatus: status(regime.dataStatus, "regime dataStatus"),
    ruleVersion: "market-regime-v1",
    label,
    score,
    confidence,
    confidenceMeaning: "ASSESSMENT_QUALITY_NOT_FORECAST_PROBABILITY",
    tradingDate: nullableText(regime.tradingDate, "regime tradingDate"),
    asOf: nullableText(regime.asOf, "regime asOf"),
    factors,
    source: { provider: text(source.provider, "regime source provider"), dataset: text(source.dataset, "regime source dataset") },
    reasonCodes: array(regime.reasonCodes, "regime reasonCodes").map((reason) => text(reason, "regime reasonCode")),
    disclaimerCode: "QUANTITATIVE_DECISION_SUPPORT_NOT_INVESTMENT_ADVICE",
  };
}

function parseRegimeFactor(value: unknown): MarketRegimeFactor {
  const factor = record(value, "regime factor");
  const code = text(factor.code, "regime factor code");
  const direction = text(factor.direction, "regime factor direction");
  if (!FACTOR_CODES.has(code as RegimeFactorCode)) throw new Error("Unsupported regime factor code");
  if (!FACTOR_DIRECTIONS.has(direction as FactorDirection)) throw new Error("Unsupported regime factor direction");
  return {
    code: code as RegimeFactorCode,
    direction: direction as FactorDirection,
    descriptionCode: text(factor.descriptionCode, "regime factor descriptionCode"),
    normalizedScore: decimal(factor.normalizedScore, "regime factor normalizedScore"),
    effectiveWeight: decimal(factor.effectiveWeight, "regime factor effectiveWeight"),
    contribution: decimal(factor.contribution, "regime factor contribution"),
    observations: array(factor.observations, "regime factor observations").map(parseFactorObservation),
  };
}

function parseFactorObservation(value: unknown): MarketRegimeFactor["observations"][number] {
  const observation = record(value, "regime factor observation");
  return {
    name: text(observation.name, "regime observation name"),
    value: decimal(observation.value, "regime observation value"),
    unit: text(observation.unit, "regime observation unit"),
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

function parseSession(value: unknown): MarketSession {
  const session = record(value, "session");
  return {
    state: sessionState(session.state, "session state"),
    tradingDate: text(session.tradingDate, "session tradingDate"),
    asOf: text(session.asOf, "session asOf"),
    calendarVersion: text(session.calendarVersion, "calendarVersion"),
    venueStates: array(session.venueStates, "venueStates").map(parseVenueSession),
  };
}

function parseVenueSession(value: unknown): MarketSession["venueStates"][number] {
  const venueSession = record(value, "venue session");
  const venue = text(venueSession.venue, "venue session venue");
  if (!VENUES.has(venue as Venue)) throw new Error("Unsupported venue session venue");
  return { venue: venue as Venue, state: sessionState(venueSession.state, "venue session state") };
}

function parseWarning(value: unknown): MarketWarning {
  const warning = record(value, "warning");
  const severity = text(warning.severity, "warning severity");
  const section = text(warning.section, "warning section");
  if (!WARNING_SEVERITIES.has(severity as WarningSeverity)) throw new Error("warning severity is invalid");
  if (!WARNING_SECTIONS.has(section as WarningSection)) throw new Error("warning section is invalid");
  return {
    code: text(warning.code, "warning code"),
    severity: severity as WarningSeverity,
    section: section as WarningSection,
    subject: nullableText(warning.subject, "warning subject"),
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

function nullableScore(value: unknown, name: string): number | null {
  if (value === null) return null;
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < 0 || value > 100) {
    throw new Error(`${name} must be an integer from 0 to 100 or null`);
  }
  return value;
}

function nullableRegimeLabel(value: unknown): RegimeLabel | null {
  if (value === null) return null;
  if (typeof value !== "string" || !REGIME_LABELS.has(value as RegimeLabel)) throw new Error("regime label is invalid");
  return value as RegimeLabel;
}

function sessionState(value: unknown, name: string): SessionState {
  if (typeof value !== "string" || !SESSION_STATES.has(value as SessionState)) throw new Error(`${name} is invalid`);
  return value as SessionState;
}

function status(value: unknown, name: string): DataStatus {
  if (typeof value !== "string" || !STATUSES.has(value as DataStatus)) throw new Error(`${name} is invalid`);
  return value as DataStatus;
}

function directionValue(value: unknown): Exclude<Direction, null> {
  if (typeof value !== "string" || !DIRECTIONS.has(value as Exclude<Direction, null>)) throw new Error("direction is invalid");
  return value as Exclude<Direction, null>;
}
