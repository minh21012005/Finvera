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
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit(strategyCode);
      }}
      style={{
        display: "flex",
        flexWrap: "wrap",
        alignItems: "flex-end",
        gap: "16px",
        background: "var(--bg-card)",
        border: "1px solid var(--border-color)",
        borderRadius: "14px",
        padding: "20px 24px",
        marginBottom: "24px",
      }}
    >
      <label style={{ flex: 1, minWidth: "260px" }}>
        <span style={{ display: "flex", alignItems: "center", gap: "6px", marginBottom: "6px" }}>
          <Zap size={14} className="text-amber-400" />
          <span>Chiến lược</span>
        </span>
        <select
          aria-label="Chiến lược"
          value={strategyCode}
          onChange={(e) => setStrategyCode(e.target.value as StrategyCode)}
          style={{ width: "100%", padding: "10px 14px", background: "var(--bg-input)" }}
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
        style={{
          display: "inline-flex",
          alignItems: "center",
          gap: "8px",
          padding: "11px 22px",
          height: "44px",
        }}
      >
        <Play size={14} fill="currentColor" />
        {submitting ? "Đang quét…" : "Quét thị trường"}
      </button>
    </form>
  );
}
