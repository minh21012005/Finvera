import type {
  BreakoutCondition,
  FundamentalFilter,
  MacdSignal,
  MarketFilter,
  MaRelationship,
  PriceFilter,
  ScreenRequest,
  TechnicalFilter,
  TrendDirection,
} from "../api/stock-screener";

export const MA_RELATIONSHIPS: MaRelationship[] = [
  "PRICE_ABOVE_MA20", "PRICE_BELOW_MA20",
  "PRICE_ABOVE_MA50", "PRICE_BELOW_MA50",
  "PRICE_ABOVE_MA200", "PRICE_BELOW_MA200",
  "MA20_ABOVE_MA50", "MA20_BELOW_MA50",
  "MA50_ABOVE_MA200", "MA50_BELOW_MA200",
];

export interface FormState {
  exchange: string;
  marketCapMin: string;
  marketCapMax: string;
  priceMin: string;
  priceMax: string;
  priceChangePercentMin: string;
  priceChangePercentMax: string;
  rsiMin: string;
  rsiMax: string;
  macdSignal: MacdSignal | "";
  maRelationship: MaRelationship | "";
  volumeMin: string;
  volumeMax: string;
  relativeVolumeMin: string;
  relativeVolumeMax: string;
  breakout: BreakoutCondition | "";
  trend: TrendDirection | "";
  revenueGrowthPercentMin: string;
  revenueGrowthPercentMax: string;
  earningsGrowthPercentMin: string;
  earningsGrowthPercentMax: string;
  roeMin: string;
  roeMax: string;
  roaMin: string;
  roaMax: string;
  peMin: string;
  peMax: string;
  pbMin: string;
  pbMax: string;
  debtToEquityMin: string;
  debtToEquityMax: string;
  psMin: string;
  psMax: string;
  betaMin: string;
  betaMax: string;
  grossMarginMin: string;
  grossMarginMax: string;
  netMarginMin: string;
  netMarginMax: string;
  currentRatioMin: string;
  currentRatioMax: string;
  interestCoverageMin: string;
  interestCoverageMax: string;
  debtToAssetsMin: string;
  debtToAssetsMax: string;
  dividendYieldMin: string;
  dividendYieldMax: string;
}

export const EMPTY_FORM: FormState = {
  exchange: "",
  marketCapMin: "",
  marketCapMax: "",
  priceMin: "",
  priceMax: "",
  priceChangePercentMin: "",
  priceChangePercentMax: "",
  rsiMin: "",
  rsiMax: "",
  macdSignal: "",
  maRelationship: "",
  volumeMin: "",
  volumeMax: "",
  relativeVolumeMin: "",
  relativeVolumeMax: "",
  breakout: "",
  trend: "",
  revenueGrowthPercentMin: "",
  revenueGrowthPercentMax: "",
  earningsGrowthPercentMin: "",
  earningsGrowthPercentMax: "",
  roeMin: "",
  roeMax: "",
  roaMin: "",
  roaMax: "",
  peMin: "",
  peMax: "",
  pbMin: "",
  pbMax: "",
  debtToEquityMin: "",
  debtToEquityMax: "",
  psMin: "",
  psMax: "",
  betaMin: "",
  betaMax: "",
  grossMarginMin: "",
  grossMarginMax: "",
  netMarginMin: "",
  netMarginMax: "",
  currentRatioMin: "",
  currentRatioMax: "",
  interestCoverageMin: "",
  interestCoverageMax: "",
  debtToAssetsMin: "",
  debtToAssetsMax: "",
  dividendYieldMin: "",
  dividendYieldMax: "",
};

function opt(value: string): string | undefined {
  const trimmed = value.trim();
  return trimmed.length === 0 ? undefined : trimmed;
}

function optDebtToEquity(value: string): string | undefined {
  const trimmed = value.trim();
  if (trimmed.length === 0) return undefined;
  const n = Number(trimmed);
  if (!Number.isFinite(n)) return undefined;
  // If user entered as a decimal ratio (e.g. 0.8 or 1.5 lần), convert to percentage points (80 or 150)
  // to match backend percentage storage (e.g. 80.0%)
  return n <= 5 ? String(n * 100) : String(n);
}

function optInt(value: string): number | undefined {
  const trimmed = value.trim();
  if (trimmed.length === 0) return undefined;
  const n = Number(trimmed);
  return Number.isFinite(n) ? Math.trunc(n) : undefined;
}

function parseExchanges(value: string): string[] | undefined {
  const trimmed = value.trim();
  if (trimmed.length === 0) return undefined;
  const list = trimmed
    .split(/[,;\s]+/)
    .map((s) => s.trim().toUpperCase())
    .filter(Boolean);
  return list.length > 0 ? list : undefined;
}

export function buildScreenRequest(form: FormState): ScreenRequest {
  const market: MarketFilter = {
    exchange: parseExchanges(form.exchange),
    marketCapMin: opt(form.marketCapMin),
    marketCapMax: opt(form.marketCapMax),
  };
  const price: PriceFilter = {
    priceMin: opt(form.priceMin),
    priceMax: opt(form.priceMax),
    priceChangePercentMin: opt(form.priceChangePercentMin),
    priceChangePercentMax: opt(form.priceChangePercentMax),
  };
  const technical: TechnicalFilter = {
    rsiMin: opt(form.rsiMin),
    rsiMax: opt(form.rsiMax),
    macdSignal: form.macdSignal || undefined,
    maRelationship: form.maRelationship ? [form.maRelationship] : undefined,
    volumeMin: optInt(form.volumeMin),
    volumeMax: optInt(form.volumeMax),
    relativeVolumeMin: opt(form.relativeVolumeMin),
    relativeVolumeMax: opt(form.relativeVolumeMax),
    breakout: form.breakout || undefined,
    trend: form.trend || undefined,
  };
  const fundamental: FundamentalFilter = {
    revenueGrowthPercentMin: opt(form.revenueGrowthPercentMin),
    revenueGrowthPercentMax: opt(form.revenueGrowthPercentMax),
    earningsGrowthPercentMin: opt(form.earningsGrowthPercentMin),
    earningsGrowthPercentMax: opt(form.earningsGrowthPercentMax),
    roeMin: opt(form.roeMin),
    roeMax: opt(form.roeMax),
    roaMin: opt(form.roaMin),
    roaMax: opt(form.roaMax),
    peMin: opt(form.peMin),
    peMax: opt(form.peMax),
    pbMin: opt(form.pbMin),
    pbMax: opt(form.pbMax),
    debtToEquityMin: optDebtToEquity(form.debtToEquityMin),
    debtToEquityMax: optDebtToEquity(form.debtToEquityMax),
    psMin: opt(form.psMin),
    psMax: opt(form.psMax),
    betaMin: opt(form.betaMin),
    betaMax: opt(form.betaMax),
    grossMarginMin: opt(form.grossMarginMin),
    grossMarginMax: opt(form.grossMarginMax),
    netMarginMin: opt(form.netMarginMin),
    netMarginMax: opt(form.netMarginMax),
    currentRatioMin: opt(form.currentRatioMin),
    currentRatioMax: opt(form.currentRatioMax),
    interestCoverageMin: opt(form.interestCoverageMin),
    interestCoverageMax: opt(form.interestCoverageMax),
    debtToAssetsMin: opt(form.debtToAssetsMin),
    debtToAssetsMax: opt(form.debtToAssetsMax),
    dividendYieldMin: opt(form.dividendYieldMin),
    dividendYieldMax: opt(form.dividendYieldMax),
  };

  const hasAny = (o: object) => Object.values(o).some((v) => v !== undefined);

  return {
    market: hasAny(market) ? market : undefined,
    price: hasAny(price) ? price : undefined,
    technical: hasAny(technical) ? technical : undefined,
    fundamental: hasAny(fundamental) ? fundamental : undefined,
    sortField: "MARKET_CAP",
    sortDirection: "DESC",
  };
}
