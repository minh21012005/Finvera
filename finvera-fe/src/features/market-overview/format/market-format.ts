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

export function formatDate(value: string | null | undefined): string {
  if (!value) return "Không có dữ liệu";
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    const [year, month, day] = value.split("-");
    return `${day}/${month}/${year}`;
  }
  try {
    const d = new Date(value);
    if (isNaN(d.getTime())) return value;
    return new Intl.DateTimeFormat("vi-VN", {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
      timeZone: "Asia/Ho_Chi_Minh",
    }).format(d);
  } catch {
    return value;
  }
}
