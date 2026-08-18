import type {
  Applicability,
  DataStatus,
  Direction,
  IndicatorCode,
  IndicatorComponentCode,
  IndicatorUnit,
  SessionState,
} from "../api/stock-detail";

export { formatDecimal, formatVnd, formatVolume, formatAsOf } from "../../market-overview/format/market-format";
import { formatDecimal } from "../../market-overview/format/market-format";

export function formatPercent(value: string | null): string {
  if (value === null) return "Không có dữ liệu";
  const negative = value.startsWith("-");
  const magnitude = negative ? value.slice(1) : value;
  const [integerPart, fractionalPart = ""] = magnitude.split(".");
  const trimmedFraction = fractionalPart.replace(/0+$/, "").slice(0, 2);
  return `${negative ? "−" : "+"}${integerPart}${trimmedFraction ? `,${trimmedFraction}` : ""}%`;
}

export function directionLabel(direction: Direction): { icon: string; label: string; className: string; color?: string } {
  switch (direction) {
    case "UP":
      return { icon: "↑", label: "Tăng", className: "up", color: "var(--color-up)" };
    case "DOWN":
      return { icon: "↓", label: "Giảm", className: "down", color: "var(--color-down)" };
    default:
      return { icon: "→", label: "Không đổi", className: "unchanged", color: "var(--color-unchanged)" };
  }
}

export function dataStatusLabel(status: DataStatus): string {
  return { CURRENT: "Hiện tại", DELAYED: "Chậm", STALE: "Cũ", PARTIAL: "Một phần", UNAVAILABLE: "Không có dữ liệu" }[status];
}

export function sessionStateLabel(state: SessionState): string {
  return (
    {
      PRE_OPEN: "trước mở cửa",
      OPEN: "đang mở cửa",
      BREAK: "nghỉ giữa phiên",
      INTERRUPTED: "gián đoạn",
      CLOSED: "đã đóng cửa",
      NON_TRADING_DAY: "không giao dịch",
      UNKNOWN: "chưa xác định",
    }[state] ?? "chưa xác định"
  );
}

export function applicabilityReasonLabel(applicability: Applicability, reasonCode: string | null): string | null {
  if (applicability === "DEFINED") return null;
  return reasonCode ?? (applicability === "MISSING" ? "MISSING" : "NOT_APPLICABLE");
}

const INDICATOR_LABELS: Record<IndicatorCode, string> = {
  MA20: "MA20",
  MA50: "MA50",
  MA200: "MA200",
  RSI14: "RSI(14)",
  MACD: "MACD(12,26,9)",
  BBANDS: "Dải Bollinger(20,2)",
  ATR14: "ATR(14)",
  AVG_VOLUME20: "KL bình quân(20)",
  RELATIVE_VOLUME: "KL tương đối",
};

export function indicatorLabel(code: IndicatorCode): string {
  return INDICATOR_LABELS[code];
}

const COMPONENT_LABELS: Record<IndicatorComponentCode, string> = {
  VALUE: "Giá trị",
  UPPER: "Dải trên",
  MIDDLE: "Dải giữa",
  LOWER: "Dải dưới",
  BANDWIDTH: "Độ rộng dải",
  MACD_LINE: "Đường MACD",
  SIGNAL: "Đường tín hiệu",
  HISTOGRAM: "Biểu đồ cột",
  PERCENT_OF_CLOSE: "% so với giá đóng cửa",
};

export function componentLabel(code: IndicatorComponentCode): string {
  return COMPONENT_LABELS[code];
}

/**
 * `value` arrives already display-rounded by the backend (rule U-4: rounding
 * happens exactly once, at the presentation boundary) — this only adds the
 * unit suffix, it never re-rounds.
 */
export function formatIndicatorValue(value: string | null, unit: IndicatorUnit): string {
  if (value === null) return "Không có dữ liệu";
  switch (unit) {
    case "VND":
      return `${formatDecimal(value)} VND`;
    case "PERCENT":
      return `${formatDecimal(value)}%`;
    case "SHARES":
      return formatDecimal(value);
    case "POINTS":
    case "RATIO":
      return formatDecimal(value);
  }
}
