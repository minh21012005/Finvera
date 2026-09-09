import { useState } from "react";
import type { ScreenRequest } from "../api/stock-screener";
import { buildScreenRequest, EMPTY_FORM, MA_RELATIONSHIPS, type FormState } from "./screener-filters-model";
import { SlidersHorizontal, RotateCcw, Bookmark, ChevronDown, ChevronUp, Sparkles } from "lucide-react";

interface ScreenerFiltersProps {
  onSubmit: (request: ScreenRequest) => void;
  submitting: boolean;
}

interface Preset {
  id: string;
  name: string;
  form: Partial<FormState>;
}

const PRESETS: Preset[] = [
  {
    id: "canslim",
    name: "CANSLIM Chuẩn",
    form: {
      exchange: "HOSE, HNX",
      marketCapMin: "5000000000000",
      roeMin: "18",
      earningsGrowthPercentMin: "20",
      revenueGrowthPercentMin: "15",
      peMax: "22",
      rsiMin: "45",
      rsiMax: "70",
      trend: "UPTREND",
    },
  },
  {
    id: "eps-growth",
    name: "Tăng Trưởng EPS > 25%",
    form: {
      exchange: "HOSE",
      earningsGrowthPercentMin: "25",
      roeMin: "15",
      peMax: "20",
    },
  },
  {
    id: "breakout-ma20",
    name: "Breakout MA20",
    form: {
      relativeVolumeMin: "1.5",
      breakout: "BREAKOUT_UP",
      trend: "UPTREND",
      rsiMin: "55",
    },
  },
  {
    id: "high-dividend",
    name: "Cổ Tức Tiền Mặt",
    form: {
      roeMin: "15",
      debtToEquityMax: "0.8",
      peMax: "15",
    },
  },
];

export function ScreenerFilters({ onSubmit, submitting }: ScreenerFiltersProps) {
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [activePreset, setActivePreset] = useState<string | null>(null);
  const [showAdvanced, setShowAdvanced] = useState(true);

  const handleChange = (key: keyof FormState) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const val = e.target.value;
    if (activePreset !== null) {
      setActivePreset(null);
    }
    setForm((prev) => (prev[key] === val ? prev : { ...prev, [key]: val }));
  };

  function field<K extends keyof FormState>(key: K) {
    return {
      value: form[key],
      onChange: handleChange(key),
    };
  }

  function applyPreset(preset: Preset) {
    setActivePreset(preset.id);
    setForm({
      ...EMPTY_FORM,
      ...preset.form,
    });
  }

  function resetForm() {
    setActivePreset(null);
    setForm(EMPTY_FORM);
  }

  return (
    <div className="screener-controls-wrapper">
      {/* Preset Filter Pills Strip */}
      <div className="screener-presets-bar">
        <div className="presets-list">
          <span className="text-xs text-slate-400 font-semibold self-center mr-2">Mẫu lọc:</span>
          {PRESETS.map((p) => {
            const isActive = activePreset === p.id;
            return (
              <button
                key={p.id}
                type="button"
                className={`preset-pill ${isActive ? "active" : ""}`}
                onClick={() => applyPreset(p)}
              >
                {isActive && <span className="preset-check">✓</span>}
                <span>{p.name}</span>
              </button>
            );
          })}
        </div>

        <div className="preset-actions">
          <button type="button" className="btn-preset-action" onClick={resetForm} title="Đặt lại bộ lọc">
            <RotateCcw size={13} />
            <span>Đặt Lại</span>
          </button>
        </div>
      </div>

      {/* Main Filter Form */}
      <form
        aria-label="Bộ lọc cổ phiếu"
        className="quant-filter-form"
        onSubmit={(e) => {
          e.preventDefault();
          onSubmit(buildScreenRequest(form));
        }}
      >
        <div className="filter-header-bar">
          <button
            type="button"
            className="toggle-advanced-btn"
            onClick={() => setShowAdvanced(!showAdvanced)}
            aria-expanded={showAdvanced}
          >
            <SlidersHorizontal size={14} />
            <span>Tham số bộ lọc định lượng</span>
            {showAdvanced ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
          </button>

          <button type="submit" className="btn-quant-execute" disabled={submitting}>
            {submitting ? "Đang lọc…" : "Lọc cổ phiếu"}
          </button>
        </div>

        {showAdvanced && (
          <div className="filter-fieldsets-grid">
            {/* THỊ TRƯỜNG */}
            <fieldset className="quant-fieldset">
              <legend>Thị trường</legend>
              <div className="field-group">
                <label>
                  Sàn giao dịch
                  <input type="text" placeholder="HOSE, HNX, UPCOM" {...field("exchange")} />
                </label>
                <label>
                  Vốn hóa tối thiểu (VND)
                  <input type="text" inputMode="decimal" placeholder="> 5,000 Tỷ" {...field("marketCapMin")} />
                </label>
                <label>
                  Vốn hóa tối đa (VND)
                  <input type="text" inputMode="decimal" {...field("marketCapMax")} />
                </label>
              </div>
            </fieldset>

            {/* GIÁ */}
            <fieldset className="quant-fieldset">
              <legend>Giá</legend>
              <div className="field-group">
                <label>
                  Giá tối thiểu
                  <input type="text" inputMode="decimal" placeholder="VND" {...field("priceMin")} />
                </label>
                <label>
                  Giá tối đa
                  <input type="text" inputMode="decimal" placeholder="VND" {...field("priceMax")} />
                </label>
                <label>
                  % thay đổi tối thiểu
                  <input type="text" inputMode="decimal" placeholder="%" {...field("priceChangePercentMin")} />
                </label>
                <label>
                  % thay đổi tối đa
                  <input type="text" inputMode="decimal" placeholder="%" {...field("priceChangePercentMax")} />
                </label>
              </div>
            </fieldset>

            {/* KỸ THUẬT */}
            <fieldset className="quant-fieldset">
              <legend>Kỹ thuật</legend>
              <div className="field-group">
                <label>
                  RSI tối thiểu
                  <input type="text" inputMode="decimal" placeholder="VD: 45" {...field("rsiMin")} />
                </label>
                <label>
                  RSI tối đa
                  <input type="text" inputMode="decimal" placeholder="VD: 70" {...field("rsiMax")} />
                </label>
                <label>
                  Tín hiệu MACD
                  <select {...field("macdSignal")}>
                    <option value="">Bất kỳ</option>
                    <option value="BULLISH">Tích cực (Bullish)</option>
                    <option value="BEARISH">Tiêu cực (Bearish)</option>
                    <option value="NEUTRAL">Trung lập</option>
                  </select>
                </label>
                <label>
                  Tương quan đường MA
                  <select {...field("maRelationship")}>
                    <option value="">Bất kỳ</option>
                    {MA_RELATIONSHIPS.map((r) => (
                      <option key={r} value={r}>
                        {r}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Khối lượng tối thiểu
                  <input type="text" inputMode="numeric" placeholder="CP" {...field("volumeMin")} />
                </label>
                <label>
                  Khối lượng tối đa
                  <input type="text" inputMode="numeric" placeholder="CP" {...field("volumeMax")} />
                </label>
                <label>
                  KL tương đối tối thiểu
                  <input type="text" inputMode="decimal" placeholder="x MA20" {...field("relativeVolumeMin")} />
                </label>
                <label>
                  KL tương đối tối đa
                  <input type="text" inputMode="decimal" placeholder="x MA20" {...field("relativeVolumeMax")} />
                </label>
                <label>
                  Breakout
                  <select {...field("breakout")}>
                    <option value="">Bất kỳ</option>
                    <option value="BREAKOUT_UP">Breakout tăng</option>
                    <option value="BREAKOUT_DOWN">Breakout giảm</option>
                  </select>
                </label>
                <label>
                  Xu hướng
                  <select {...field("trend")}>
                    <option value="">Bất kỳ</option>
                    <option value="UPTREND">Tăng (Uptrend)</option>
                    <option value="DOWNTREND">Giảm (Downtrend)</option>
                    <option value="SIDEWAYS">Đi ngang</option>
                  </select>
                </label>
              </div>
            </fieldset>

            {/* CƠ BẢN */}
            <fieldset className="quant-fieldset">
              <legend>Cơ bản</legend>
              <div className="field-group">
                <label>
                  Tăng trưởng DT % tối thiểu
                  <input type="text" inputMode="decimal" placeholder="%" {...field("revenueGrowthPercentMin")} />
                </label>
                <label>
                  Tăng trưởng DT % tối đa
                  <input type="text" inputMode="decimal" placeholder="%" {...field("revenueGrowthPercentMax")} />
                </label>
                <label>
                  Tăng trưởng LN % tối thiểu
                  <input type="text" inputMode="decimal" placeholder="%" {...field("earningsGrowthPercentMin")} />
                </label>
                <label>
                  Tăng trưởng LN % tối đa
                  <input type="text" inputMode="decimal" placeholder="%" {...field("earningsGrowthPercentMax")} />
                </label>
                <label>
                  ROE 12 tháng tối thiểu
                  <input type="text" inputMode="decimal" placeholder="%" {...field("roeMin")} />
                </label>
                <label>
                  ROE 12 tháng tối đa
                  <input type="text" inputMode="decimal" placeholder="%" {...field("roeMax")} />
                </label>
                <label>
                  P/E tối thiểu
                  <input type="text" inputMode="decimal" placeholder="x" {...field("peMin")} />
                </label>
                <label>
                  P/E tối đa
                  <input type="text" inputMode="decimal" placeholder="x" {...field("peMax")} />
                </label>
                <label>
                  P/B tối thiểu
                  <input type="text" inputMode="decimal" placeholder="x" {...field("pbMin")} />
                </label>
                <label>
                  P/B tối đa
                  <input type="text" inputMode="decimal" placeholder="x" {...field("pbMax")} />
                </label>
                <label>
                  P/S tối thiểu
                  <input type="text" inputMode="decimal" placeholder="x" {...field("psMin")} />
                </label>
                <label>
                  P/S tối đa
                  <input type="text" inputMode="decimal" placeholder="x" {...field("psMax")} />
                </label>
                <label>
                  Beta tối thiểu
                  <input type="text" inputMode="decimal" {...field("betaMin")} />
                </label>
                <label>
                  Beta tối đa
                  <input type="text" inputMode="decimal" {...field("betaMax")} />
                </label>
                <label>
                  Biên LN gộp % tối thiểu
                  <input type="text" inputMode="decimal" placeholder="%" {...field("grossMarginMin")} />
                </label>
                <label>
                  Biên LN gộp % tối đa
                  <input type="text" inputMode="decimal" placeholder="%" {...field("grossMarginMax")} />
                </label>
                <label>
                  Biên LN ròng % tối thiểu
                  <input type="text" inputMode="decimal" placeholder="%" {...field("netMarginMin")} />
                </label>
                <label>
                  Biên LN ròng % tối đa
                  <input type="text" inputMode="decimal" placeholder="%" {...field("netMarginMax")} />
                </label>
                <label>
                  Thanh toán hiện hành tối thiểu
                  <input type="text" inputMode="decimal" {...field("currentRatioMin")} />
                </label>
                <label>
                  Thanh toán hiện hành tối đa
                  <input type="text" inputMode="decimal" {...field("currentRatioMax")} />
                </label>
                <label>
                  Khả năng trả lãi tối thiểu
                  <input type="text" inputMode="decimal" {...field("interestCoverageMin")} />
                </label>
                <label>
                  Khả năng trả lãi tối đa
                  <input type="text" inputMode="decimal" {...field("interestCoverageMax")} />
                </label>
                <label>
                  Nợ vay/Tổng TS % tối thiểu
                  <input type="text" inputMode="decimal" {...field("debtToAssetsMin")} />
                </label>
                <label>
                  Nợ vay/Tổng TS % tối đa
                  <input type="text" inputMode="decimal" {...field("debtToAssetsMax")} />
                </label>
                <label>
                  ROA tối thiểu
                  <input type="text" inputMode="decimal" placeholder="%" {...field("roaMin")} />
                </label>
                <label>
                  ROA tối đa
                  <input type="text" inputMode="decimal" placeholder="%" {...field("roaMax")} />
                </label>
                <label>
                  Nợ/VCSH tối thiểu
                  <input type="text" inputMode="decimal" {...field("debtToEquityMin")} />
                </label>
                <label>
                  Nợ/VCSH tối đa
                  <input type="text" inputMode="decimal" {...field("debtToEquityMax")} />
                </label>
              </div>
            </fieldset>
          </div>
        )}
      </form>
    </div>
  );
}
