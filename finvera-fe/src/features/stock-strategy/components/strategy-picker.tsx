import { useState } from "react";
import type { StrategyCode } from "../api/stock-strategy";
import { strategyLabel } from "../../stock-detail/format/signal-format";

const STRATEGY_CODES: StrategyCode[] = [
  "TREND_FOLLOWING", "MOMENTUM", "BREAKOUT", "PULLBACK", "MEAN_REVERSION",
  "MA_CROSSOVER", "MACD_BASED", "RSI_BASED",
];

export function StrategyPicker({
  onSubmit,
  submitting,
}: {
  onSubmit: (strategyCode: StrategyCode) => void;
  submitting: boolean;
}) {
  const [strategyCode, setStrategyCode] = useState<StrategyCode>("TREND_FOLLOWING");

  return (
    <form
      aria-label="Chọn chiến lược để quét"
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit(strategyCode);
      }}
    >
      <label>
        Chiến lược
        <select
          value={strategyCode}
          onChange={(e) => setStrategyCode(e.target.value as StrategyCode)}
        >
          {STRATEGY_CODES.map((code) => (
            <option key={code} value={code}>
              {strategyLabel(code)}
            </option>
          ))}
        </select>
      </label>

      <button type="submit" disabled={submitting}>
        {submitting ? "Đang quét…" : "Quét thị trường"}
      </button>
    </form>
  );
}
