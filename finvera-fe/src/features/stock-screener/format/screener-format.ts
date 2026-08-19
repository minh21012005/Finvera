import type { CategoryDisclosure, DataStatus } from "../api/stock-screener";
import { dataStatusLabel } from "../../stock-detail/format/stock-format";

export { dataStatusLabel };

export function dataStatusClassName(status: DataStatus): string {
  return status.toLowerCase();
}

const CATEGORY_LABELS: Record<CategoryDisclosure["category"], string> = {
  MARKET: "Thị trường",
  PRICE: "Giá",
  TECHNICAL: "Kỹ thuật",
  FUNDAMENTAL: "Cơ bản",
};

export function categoryLabel(category: CategoryDisclosure["category"]): string {
  return CATEGORY_LABELS[category];
}

const MATCHED_VALUE_LABELS: Record<string, string> = {
  exchange: "Sàn",
  sector: "Ngành",
  marketCap: "Vốn hóa",
  price: "Giá",
  priceChangePercent: "Thay đổi giá",
  rsi: "RSI",
  macdSignal: "Tín hiệu MACD",
  maRelationship: "Tương quan MA",
  volume: "Khối lượng",
  relativeVolume: "KL tương đối",
  breakout: "Breakout",
  trend: "Xu hướng",
  revenueGrowthPercent: "Tăng trưởng doanh thu",
  earningsGrowthPercent: "Tăng trưởng lợi nhuận",
  roe: "ROE",
  roa: "ROA",
  pe: "P/E",
  pb: "P/B",
  debtToEquity: "Nợ/VCSH",
};

export function matchedValueLabel(key: string): string {
  return MATCHED_VALUE_LABELS[key] ?? key;
}

const SORT_FIELD_LABELS: Record<string, string> = {
  SYMBOL: "Mã CK",
  MARKET_CAP: "Vốn hóa",
  PRICE: "Giá",
  PRICE_CHANGE_PERCENT: "Thay đổi giá",
  RSI: "RSI",
  RELATIVE_VOLUME: "KL tương đối",
  REVENUE_GROWTH_PERCENT: "Tăng trưởng doanh thu",
  EARNINGS_GROWTH_PERCENT: "Tăng trưởng lợi nhuận",
  ROE: "ROE",
  ROA: "ROA",
  PE: "P/E",
  PB: "P/B",
  DEBT_TO_EQUITY: "Nợ/VCSH",
};

export function sortFieldLabel(field: string): string {
  return SORT_FIELD_LABELS[field] ?? field;
}
