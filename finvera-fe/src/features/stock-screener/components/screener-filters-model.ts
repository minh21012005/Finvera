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
};

function opt(value: string): string | undefined {
  const trimmed = value.trim();
  return trimmed.length === 0 ? undefined : trimmed;
}

function optInt(value: string): number | undefined {
  const trimmed = value.trim();
  if (trimmed.length === 0) return undefined;
  const n = Number(trimmed);
  return Number.isFinite(n) ? Math.trunc(n) : undefined;
}

export function buildScreenRequest(form: FormState): ScreenRequest {
  const market: MarketFilter = {
    exchange: opt(form.exchange) ? [form.exchange.trim().toUpperCase()] : undefined,
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
    debtToEquityMin: opt(form.debtToEquityMin),
    debtToEquityMax: opt(form.debtToEquityMax),
  };

  const hasAny = (o: object) => Object.values(o).some((v) => v !== undefined);

  return {
    market: hasAny(market) ? market : undefined,
    price: hasAny(price) ? price : undefined,
    technical: hasAny(technical) ? technical : undefined,
    fundamental: hasAny(fundamental) ? fundamental : undefined,
  };
}
