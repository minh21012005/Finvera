import { useState } from "react";
import type { StrategyCode } from "../api/stock-strategy";
import { strategyLabel } from "../../stock-detail/format/signal-format";
import { Zap, Play } from "lucide-react";

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
      className="quant-strategy-form"
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit(strategyCode);
      }}
    >
      <div className="strategy-select-row">
        <label className="strategy-select-label">
          <span className="label-text-group">
            <Zap size={14} className="text-amber-400" />
            <span>Chiến lược</span>
          </span>
          <select
            aria-label="Chiến lược"
            value={strategyCode}
            onChange={(e) => setStrategyCode(e.target.value as StrategyCode)}
            className="quant-strategy-select font-mono"
          >
            {STRATEGY_CODES.map((code) => (
              <option key={code} value={code}>
                {strategyLabel(code)}
              </option>
            ))}
          </select>
        </label>

        <button
          type="submit"
          disabled={submitting}
          className="btn-scan-market"
        >
          <Play size={14} fill="currentColor" />
          {submitting ? "Đang quét…" : "Quét thị trường"}
        </button>
      </div>
    </form>
  );
}
