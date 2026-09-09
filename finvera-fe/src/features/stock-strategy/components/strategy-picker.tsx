import { useState } from "react";
import type { StrategyCode } from "../api/stock-strategy";
import { strategyLabel } from "../../stock-detail/format/signal-format";
import {
  Zap,
  Play,
  TrendingUp,
  LineChart,
  Activity,
  RotateCcw,
  Target,
  BarChart2,
  Compass,
} from "lucide-react";

const STRATEGY_CONFIGS: { code: StrategyCode; icon: React.ReactNode; shortDesc: string }[] = [
  { code: "TREND_FOLLOWING", icon: <Zap size={14} className="text-amber-400" />, shortDesc: "Theo xu hướng" },
  { code: "MOMENTUM", icon: <TrendingUp size={14} className="text-emerald-400" />, shortDesc: "Động lượng giá" },
  { code: "BREAKOUT", icon: <Compass size={14} className="text-cyan-400" />, shortDesc: "Phá vỡ cản" },
  { code: "PULLBACK", icon: <Target size={14} className="text-purple-400" />, shortDesc: "Hồi quy hỗ trợ" },
  { code: "MEAN_REVERSION", icon: <RotateCcw size={14} className="text-blue-400" />, shortDesc: "Về giá trị TB" },
  { code: "MA_CROSSOVER", icon: <LineChart size={14} className="text-orange-400" />, shortDesc: "Giao cắt MA" },
  { code: "MACD_BASED", icon: <BarChart2 size={14} className="text-teal-400" />, shortDesc: "Tín hiệu MACD" },
  { code: "RSI_BASED", icon: <Activity size={14} className="text-rose-400" />, shortDesc: "Quá mua / Quá bán" },
];

export function StrategyPicker({
  onSubmit,
  submitting,
}: {
  onSubmit: (strategyCode: StrategyCode) => void;
  submitting: boolean;
}) {
  const [strategyCode, setStrategyCode] = useState<StrategyCode>("TREND_FOLLOWING");

  const handleSelect = (code: StrategyCode) => {
    setStrategyCode(code);
  };

  return (
    <form
      aria-label="Chọn chiến lược để quét"
      className="quant-strategy-form"
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit(strategyCode);
      }}
    >
      {/* Visual Strategy Interactive Chips */}
      <div className="strategy-chips-grid" aria-label="Danh sách chiến lược định lượng">
        {STRATEGY_CONFIGS.map(({ code, icon, shortDesc }) => {
          const isSelected = strategyCode === code;
          return (
            <button
              key={code}
              type="button"
              className={`strategy-chip-btn ${isSelected ? "selected" : ""}`}
              onClick={() => handleSelect(code)}
              aria-pressed={isSelected}
            >
              <span className="chip-icon">{icon}</span>
              <div className="chip-content">
                <span className="chip-name">{strategyLabel(code)}</span>
                <span className="chip-desc">{shortDesc}</span>
              </div>
            </button>
          );
        })}
      </div>

      {/* Hidden select preserved strictly for screen readers and accessibility contracts */}
      <label className="sr-only" htmlFor="quant-strategy-hidden-select">
        Chiến lược
      </label>
      <select
        id="quant-strategy-hidden-select"
        aria-label="Chiến lược"
        value={strategyCode}
        onChange={(e) => setStrategyCode(e.target.value as StrategyCode)}
        className="sr-only"
        tabIndex={-1}
      >
        {STRATEGY_CONFIGS.map(({ code }) => (
          <option key={code} value={code}>
            {code}
          </option>
        ))}
      </select>

      {/* Action Footer Strip: Clean & Dedicated Execute Button */}
      <div className="strategy-actions-strip">
        <div className="strategy-active-summary">
          <span className="summary-label">Đang chọn:</span>
          <strong className="summary-name font-mono">{strategyLabel(strategyCode)}</strong>
        </div>

        <button
          type="submit"
          disabled={submitting}
          className="btn-scan-market"
        >
          <Play size={14} fill="currentColor" />
          {submitting ? "Đang quét thị trường…" : "Quét thị trường"}
        </button>
      </div>
    </form>
  );
}
