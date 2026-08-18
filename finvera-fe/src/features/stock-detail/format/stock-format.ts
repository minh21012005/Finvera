import type { Applicability, DataStatus, Direction, SessionState } from "../api/stock-detail";

export { formatDecimal, formatVnd, formatVolume, formatAsOf } from "../../market-overview/format/market-format";

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
