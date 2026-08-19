import type {
  Direction,
  EvaluationStatus,
  RiskFactorCode,
  RiskLevel,
  SignalStrength,
  StrategyCode,
} from "../api/stock-signals";

const STRATEGY_LABELS: Record<StrategyCode, string> = {
  TREND_FOLLOWING: "Theo xu hướng",
  MOMENTUM: "Động lượng",
  BREAKOUT: "Bứt phá",
  PULLBACK: "Điều chỉnh trong xu hướng",
  MEAN_REVERSION: "Hồi quy giá trị trung bình",
  MA_CROSSOVER: "Giao cắt đường trung bình",
  MACD_BASED: "Theo MACD",
  RSI_BASED: "Theo RSI",
};

export function strategyLabel(code: StrategyCode): string {
  return STRATEGY_LABELS[code];
}

export function evaluationStatusLabel(status: EvaluationStatus): string {
  return {
    SIGNAL: "Có tín hiệu",
    NO_SIGNAL: "Không có tín hiệu",
    INSUFFICIENT_HISTORY: "Chưa đủ dữ liệu lịch sử",
    WITHHELD: "Tạm giữ do xung đột dữ liệu",
  }[status];
}

/** NFR-003: direction is always shown with text, never colour alone. */
export function directionLabel(direction: Direction): string {
  return direction === "LONG" ? "Mua (LONG)" : direction;
}

/** NFR-003: risk level carries an icon + text indicator independent of colour. */
export function riskLevelDisplay(level: RiskLevel | null): { icon: string; label: string; className: string } {
  if (level === null) return { icon: "—", label: "Chưa xác định", className: "unknown" };
  return {
    LOW: { icon: "●", label: "Rủi ro thấp", className: "low" },
    MEDIUM: { icon: "●●", label: "Rủi ro trung bình", className: "medium" },
    HIGH: { icon: "●●●", label: "Rủi ro cao", className: "high" },
  }[level];
}

export function signalStrengthLabel(strength: SignalStrength | null): string {
  if (strength === null) return "Chưa xác định";
  return { WEAK: "Yếu", MODERATE: "Trung bình", STRONG: "Mạnh" }[strength];
}

const RISK_FACTOR_LABELS: Record<RiskFactorCode, string> = {
  VOLATILITY: "Biến động giá (ATR/giá)",
  ATR: "Biến động so với trung bình 250 phiên",
  DRAWDOWN: "Mức sụt giảm từ đỉnh 250 phiên",
  LIQUIDITY: "Thanh khoản (KL tương đối)",
  STOP_DISTANCE: "Khoảng cách tới điểm dừng lỗ",
  MARKET_REGIME: "Trạng thái thị trường chung",
};

export function riskFactorLabel(code: RiskFactorCode): string {
  return RISK_FACTOR_LABELS[code];
}
