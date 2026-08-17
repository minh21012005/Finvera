export function formatDecimal(value: string | null): string {
  if (value === null) return "Không có dữ liệu";
  const negative = value.startsWith("-");
  const [integerPart, fractionalPart = ""] = (negative ? value.slice(1) : value).split(".");
  const grouped = integerPart.replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  const trimmedFraction = fractionalPart.replace(/0+$/, "");
  return `${negative ? "−" : ""}${grouped}${trimmedFraction ? `,${trimmedFraction}` : ""}`;
}

export function formatVnd(value: string | null): string {
  return value === null ? "Không có dữ liệu" : `${formatDecimal(value)} VND`;
}

export function formatVolume(value: number | null): string {
  return value === null ? "Không có dữ liệu" : new Intl.NumberFormat("vi-VN").format(value);
}

export function formatAsOf(value: string | null): string {
  if (value === null) return "Không có dữ liệu";
  return new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "short",
    timeStyle: "short",
    timeZone: "Asia/Ho_Chi_Minh",
  }).format(new Date(value));
}
