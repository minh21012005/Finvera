import { useState, useMemo } from "react";
import type { ScreenRequest } from "../api/stock-screener";
import { buildScreenRequest, EMPTY_FORM, MA_RELATIONSHIPS, type FormState } from "./screener-filters-model";
import {
  SlidersHorizontal,
  RotateCcw,
  ChevronDown,
  ChevronUp,
  Search,
  Check,
  Building2,
  TrendingUp,
  LineChart,
  ShieldCheck,
  Layers,
} from "lucide-react";

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

type FilterCategory = "ALL" | "MARKET_PRICE" | "TECHNICAL" | "VALUATION" | "HEALTH";

interface RangeFieldProps {
  label: string;
  unit?: string;
  minLabel: string;
  maxLabel: string;
  minPlaceholder?: string;
  maxPlaceholder?: string;
  minVal: string;
  maxVal: string;
  onMinChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  onMaxChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  inputMode?: "decimal" | "numeric" | "text";
  highlight?: boolean;
}

function RangeField({
  label,
  unit,
  minLabel,
  maxLabel,
  minPlaceholder = "Từ",
  maxPlaceholder = "Đến",
  minVal,
  maxVal,
  onMinChange,
  onMaxChange,
  inputMode = "decimal",
  highlight = false,
}: RangeFieldProps) {
  const isFilled = Boolean(minVal || maxVal);

  return (
    <div className={`quant-range-row ${isFilled ? "active-row" : ""} ${highlight ? "highlighted-row" : ""}`}>
      <div className="range-label-row">
        <span className="range-title">{label}</span>
        {unit && <span className="range-unit">{unit}</span>}
      </div>
      <div className="range-inputs-pair">
        <label className="range-input-wrapper">
          <span className="sr-only">{minLabel}</span>
          <input
            type="text"
            inputMode={inputMode}
            placeholder={minPlaceholder}
            aria-label={minLabel}
            value={minVal}
            onChange={onMinChange}
            className="font-mono"
          />
        </label>
        <span className="range-sep" aria-hidden="true">
          —
        </span>
        <label className="range-input-wrapper">
          <span className="sr-only">{maxLabel}</span>
          <input
            type="text"
            inputMode={inputMode}
            placeholder={maxPlaceholder}
            aria-label={maxLabel}
            value={maxVal}
            onChange={onMaxChange}
            className="font-mono"
          />
        </label>
      </div>
    </div>
  );
}

export function ScreenerFilters({ onSubmit, submitting }: ScreenerFiltersProps) {
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [activePreset, setActivePreset] = useState<string | null>(null);
  const [showAdvanced, setShowAdvanced] = useState(true);
  const [activeCategory, setActiveCategory] = useState<FilterCategory>("ALL");
  const [searchQuery, setSearchQuery] = useState("");

  const handleChange = (key: keyof FormState) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const val = e.target.value;
    if (activePreset !== null) {
      setActivePreset(null);
    }
    setForm((prev) => (prev[key] === val ? prev : { ...prev, [key]: val }));
  };

  function field(key: keyof FormState) {
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
    setSearchQuery("");
  }

  // Count active filters filled
  const activeFiltersCount = useMemo(() => {
    return Object.values(form).filter((v) => v.trim() !== "").length;
  }, [form]);

  // Match search filter
  const isMatch = (terms: string[]) => {
    if (!searchQuery.trim()) return false;
    const q = searchQuery.toLowerCase().trim();
    return terms.some((t) => t.toLowerCase().includes(q));
  };

  return (
    <div className="screener-controls-wrapper">
      {/* Preset Filter Pills Strip */}
      <div className="screener-presets-bar">
        <div className="presets-list">
          <span className="text-xs text-slate-400 font-semibold self-center mr-2">Mẫu lọc chuẩn:</span>
          {PRESETS.map((p) => {
            const isActive = activePreset === p.id;
            return (
              <button
                key={p.id}
                type="button"
                className={`preset-pill ${isActive ? "active" : ""}`}
                onClick={() => applyPreset(p)}
              >
                {isActive && <Check size={12} className="preset-check" />}
                <span>{p.name}</span>
              </button>
            );
          })}
        </div>

        <div className="preset-actions">
          {activeFiltersCount > 0 && (
            <span className="active-filters-count-badge font-mono">
              Đang chọn: <strong>{activeFiltersCount}</strong> tiêu chí
            </span>
          )}
          <button type="button" className="btn-preset-action" onClick={resetForm} title="Đặt lại toàn bộ tiêu chí">
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
        {/* Form Control HUD Header */}
        <div className="filter-header-bar">
          <div className="filter-header-left">
            <button
              type="button"
              className="toggle-advanced-btn"
              onClick={() => setShowAdvanced(!showAdvanced)}
              aria-expanded={showAdvanced}
            >
              <SlidersHorizontal size={15} className="text-cyan-400" />
              <span className="font-bold">Tham số bộ lọc định lượng</span>
              {showAdvanced ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
            </button>
          </div>

          <div className="filter-header-right">
            <div className="screener-search-criteria">
              <Search size={13} className="search-icon" />
              <input
                type="text"
                placeholder="Tìm tiêu chí (ROE, PE, RSI...)"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="search-criteria-input"
              />
              {searchQuery && (
                <button
                  type="button"
                  className="clear-search-btn"
                  onClick={() => setSearchQuery("")}
                  title="Xóa tìm kiếm"
                >
                  ×
                </button>
              )}
            </div>

            <button type="submit" className="btn-quant-execute" disabled={submitting}>
              {submitting ? "Đang lọc…" : "Lọc cổ phiếu"}
            </button>
          </div>
        </div>

        {/* Category Navigation Pills */}
        {showAdvanced && (
          <nav className="screener-category-tabs" aria-label="Nhóm tham số bộ lọc">
            <button
              type="button"
              className={`cat-tab-btn ${activeCategory === "ALL" ? "active" : ""}`}
              onClick={() => setActiveCategory("ALL")}
            >
              <Layers size={13} />
              <span>Tất cả (4 cột)</span>
            </button>
            <button
              type="button"
              className={`cat-tab-btn ${activeCategory === "MARKET_PRICE" ? "active" : ""}`}
              onClick={() => setActiveCategory("MARKET_PRICE")}
            >
              <Building2 size={13} />
              <span>Thị trường & Giá</span>
            </button>
            <button
              type="button"
              className={`cat-tab-btn ${activeCategory === "TECHNICAL" ? "active" : ""}`}
              onClick={() => setActiveCategory("TECHNICAL")}
            >
              <LineChart size={13} />
              <span>Kỹ thuật & Xu hướng</span>
            </button>
            <button
              type="button"
              className={`cat-tab-btn ${activeCategory === "VALUATION" ? "active" : ""}`}
              onClick={() => setActiveCategory("VALUATION")}
            >
              <TrendingUp size={13} />
              <span>Định giá & Tăng trưởng</span>
            </button>
            <button
              type="button"
              className={`cat-tab-btn ${activeCategory === "HEALTH" ? "active" : ""}`}
              onClick={() => setActiveCategory("HEALTH")}
            >
              <ShieldCheck size={13} />
              <span>Hiệu quả & Tài chính</span>
            </button>
          </nav>
        )}

        {/* Balanced 4-Column Filter Fieldsets Grid */}
        {showAdvanced && (
          <div className="filter-fieldsets-grid balanced-screener-grid">
            {/* CỘT 1: THỊ TRƯỜNG & MỨC GIÁ */}
            {(activeCategory === "ALL" || activeCategory === "MARKET_PRICE") && (
              <fieldset className="quant-fieldset">
                <legend>
                  <Building2 size={13} className="inline mr-1" />
                  Thị trường & Giá
                </legend>
                <div className="field-group">
                  <div className={`single-field-row ${form.exchange ? "has-val" : ""}`}>
                    <label>
                      <span className="field-title">Sàn giao dịch</span>
                      <input
                        type="text"
                        placeholder="HOSE, HNX, UPCOM"
                        aria-label="Sàn giao dịch"
                        {...field("exchange")}
                        className="font-mono"
                      />
                    </label>
                  </div>

                  <RangeField
                    label="Vốn hóa"
                    unit="VND"
                    minLabel="Vốn hóa tối thiểu (VND)"
                    maxLabel="Vốn hóa tối đa (VND)"
                    minPlaceholder="> 5.000 Tỷ"
                    maxPlaceholder="Vốn hóa tối đa"
                    minVal={form.marketCapMin}
                    maxVal={form.marketCapMax}
                    onMinChange={handleChange("marketCapMin")}
                    onMaxChange={handleChange("marketCapMax")}
                    highlight={isMatch(["vốn hóa", "market cap"])}
                  />

                  <RangeField
                    label="Mức giá"
                    unit="VND"
                    minLabel="Giá tối thiểu"
                    maxLabel="Giá tối đa"
                    minPlaceholder="Từ VND"
                    maxPlaceholder="Đến VND"
                    minVal={form.priceMin}
                    maxVal={form.priceMax}
                    onMinChange={handleChange("priceMin")}
                    onMaxChange={handleChange("priceMax")}
                    highlight={isMatch(["giá", "price"])}
                  />

                  <RangeField
                    label="% Thay đổi giá"
                    unit="%"
                    minLabel="% thay đổi tối thiểu"
                    maxLabel="% thay đổi tối đa"
                    minPlaceholder="Từ %"
                    maxPlaceholder="Đến %"
                    minVal={form.priceChangePercentMin}
                    maxVal={form.priceChangePercentMax}
                    onMinChange={handleChange("priceChangePercentMin")}
                    onMaxChange={handleChange("priceChangePercentMax")}
                    highlight={isMatch(["thay đổi", "% thay đổi"])}
                  />
                </div>
              </fieldset>
            )}

            {/* CỘT 2: KỸ THUẬT & XU HƯỚNG */}
            {(activeCategory === "ALL" || activeCategory === "TECHNICAL") && (
              <fieldset className="quant-fieldset">
                <legend>
                  <LineChart size={13} className="inline mr-1" />
                  Kỹ thuật & Xu hướng
                </legend>
                <div className="field-group">
                  <RangeField
                    label="RSI 14"
                    unit="0-100"
                    minLabel="RSI tối thiểu"
                    maxLabel="RSI tối đa"
                    minPlaceholder="VD: 45"
                    maxPlaceholder="VD: 70"
                    minVal={form.rsiMin}
                    maxVal={form.rsiMax}
                    onMinChange={handleChange("rsiMin")}
                    onMaxChange={handleChange("rsiMax")}
                    highlight={isMatch(["rsi"])}
                  />

                  <RangeField
                    label="Khối lượng"
                    unit="CP"
                    minLabel="Khối lượng tối thiểu"
                    maxLabel="Khối lượng tối đa"
                    minPlaceholder="Tối thiểu CP"
                    maxPlaceholder="Tối đa CP"
                    minVal={form.volumeMin}
                    maxVal={form.volumeMax}
                    onMinChange={handleChange("volumeMin")}
                    onMaxChange={handleChange("volumeMax")}
                    inputMode="numeric"
                    highlight={isMatch(["khối lượng", "volume"])}
                  />

                  <RangeField
                    label="KL tương đối"
                    unit="x MA20"
                    minLabel="KL tương đối tối thiểu"
                    maxLabel="KL tương đối tối đa"
                    minPlaceholder="VD: 1.5"
                    maxPlaceholder="VD: 3.0"
                    minVal={form.relativeVolumeMin}
                    maxVal={form.relativeVolumeMax}
                    onMinChange={handleChange("relativeVolumeMin")}
                    onMaxChange={handleChange("relativeVolumeMax")}
                    highlight={isMatch(["kl tương đối", "relative volume"])}
                  />

                  <div className="single-field-row">
                    <label>
                      <span className="field-title">Tín hiệu MACD</span>
                      <select {...field("macdSignal")} className="font-mono">
                        <option value="">Bất kỳ</option>
                        <option value="BULLISH">Tích cực (Bullish)</option>
                        <option value="BEARISH">Tiêu cực (Bearish)</option>
                        <option value="NEUTRAL">Trung lập</option>
                      </select>
                    </label>
                  </div>

                  <div className="single-field-row">
                    <label>
                      <span className="field-title">Tương quan đường MA</span>
                      <select {...field("maRelationship")} className="font-mono">
                        <option value="">Bất kỳ</option>
                        {MA_RELATIONSHIPS.map((r) => (
                          <option key={r} value={r}>
                            {r}
                          </option>
                        ))}
                      </select>
                    </label>
                  </div>

                  <div className="single-field-row">
                    <label>
                      <span className="field-title">Breakout</span>
                      <select {...field("breakout")} className="font-mono">
                        <option value="">Bất kỳ</option>
                        <option value="BREAKOUT_UP">Breakout tăng</option>
                        <option value="BREAKOUT_DOWN">Breakout giảm</option>
                      </select>
                    </label>
                  </div>

                  <div className="single-field-row">
                    <label>
                      <span className="field-title">Xu hướng</span>
                      <select {...field("trend")} className="font-mono">
                        <option value="">Bất kỳ</option>
                        <option value="UPTREND">Tăng (Uptrend)</option>
                        <option value="DOWNTREND">Giảm (Downtrend)</option>
                        <option value="SIDEWAYS">Đi ngang</option>
                      </select>
                    </label>
                  </div>
                </div>
              </fieldset>
            )}

            {/* CỘT 3: ĐỊNH GIÁ & TĂNG TRƯỞNG */}
            {(activeCategory === "ALL" || activeCategory === "VALUATION") && (
              <fieldset className="quant-fieldset">
                <legend>
                  <TrendingUp size={13} className="inline mr-1" />
                  Định giá & Tăng trưởng
                </legend>
                <div className="field-group">
                  <RangeField
                    label="P/E"
                    unit="lần"
                    minLabel="P/E tối thiểu"
                    maxLabel="P/E tối đa"
                    minPlaceholder="VD: 5"
                    maxPlaceholder="VD: 20"
                    minVal={form.peMin}
                    maxVal={form.peMax}
                    onMinChange={handleChange("peMin")}
                    onMaxChange={handleChange("peMax")}
                    highlight={isMatch(["pe", "p/e"])}
                  />

                  <RangeField
                    label="P/B"
                    unit="lần"
                    minLabel="P/B tối thiểu"
                    maxLabel="P/B tối đa"
                    minPlaceholder="VD: 1.0"
                    maxPlaceholder="VD: 3.0"
                    minVal={form.pbMin}
                    maxVal={form.pbMax}
                    onMinChange={handleChange("pbMin")}
                    onMaxChange={handleChange("pbMax")}
                    highlight={isMatch(["pb", "p/b"])}
                  />

                  <RangeField
                    label="P/S"
                    unit="lần"
                    minLabel="P/S tối thiểu"
                    maxLabel="P/S tối đa"
                    minPlaceholder="Tối thiểu"
                    maxPlaceholder="Tối đa"
                    minVal={form.psMin}
                    maxVal={form.psMax}
                    onMinChange={handleChange("psMin")}
                    onMaxChange={handleChange("psMax")}
                    highlight={isMatch(["ps", "p/s"])}
                  />

                  <RangeField
                    label="Hệ số Beta"
                    unit="hệ số"
                    minLabel="Beta tối thiểu"
                    maxLabel="Beta tối đa"
                    minPlaceholder="Tối thiểu"
                    maxPlaceholder="Tối đa"
                    minVal={form.betaMin}
                    maxVal={form.betaMax}
                    onMinChange={handleChange("betaMin")}
                    onMaxChange={handleChange("betaMax")}
                    highlight={isMatch(["beta"])}
                  />

                  <RangeField
                    label="Tăng trưởng DT"
                    unit="%"
                    minLabel="Tăng trưởng DT % tối thiểu"
                    maxLabel="Tăng trưởng DT % tối đa"
                    minPlaceholder="Từ %"
                    maxPlaceholder="Đến %"
                    minVal={form.revenueGrowthPercentMin}
                    maxVal={form.revenueGrowthPercentMax}
                    onMinChange={handleChange("revenueGrowthPercentMin")}
                    onMaxChange={handleChange("revenueGrowthPercentMax")}
                    highlight={isMatch(["doanh thu", "dt"])}
                  />

                  <RangeField
                    label="Tăng trưởng LN"
                    unit="%"
                    minLabel="Tăng trưởng LN % tối thiểu"
                    maxLabel="Tăng trưởng LN % tối đa"
                    minPlaceholder="Từ %"
                    maxPlaceholder="Đến %"
                    minVal={form.earningsGrowthPercentMin}
                    maxVal={form.earningsGrowthPercentMax}
                    onMinChange={handleChange("earningsGrowthPercentMin")}
                    onMaxChange={handleChange("earningsGrowthPercentMax")}
                    highlight={isMatch(["lợi nhuận", "ln", "eps"])}
                  />
                </div>
              </fieldset>
            )}

            {/* CỘT 4: HIỆU QUẢ & SỨC KHỎE TÀI CHÍNH */}
            {(activeCategory === "ALL" || activeCategory === "HEALTH") && (
              <fieldset className="quant-fieldset">
                <legend>
                  <ShieldCheck size={13} className="inline mr-1" />
                  Hiệu quả & Tài chính
                </legend>
                <div className="field-group">
                  <RangeField
                    label="ROE 12 tháng"
                    unit="%"
                    minLabel="ROE 12 tháng tối thiểu"
                    maxLabel="ROE 12 tháng tối đa"
                    minPlaceholder="VD: 15%"
                    maxPlaceholder="Đến %"
                    minVal={form.roeMin}
                    maxVal={form.roeMax}
                    onMinChange={handleChange("roeMin")}
                    onMaxChange={handleChange("roeMax")}
                    highlight={isMatch(["roe"])}
                  />

                  <RangeField
                    label="ROA"
                    unit="%"
                    minLabel="ROA tối thiểu"
                    maxLabel="ROA tối đa"
                    minPlaceholder="Từ %"
                    maxPlaceholder="Đến %"
                    minVal={form.roaMin}
                    maxVal={form.roaMax}
                    onMinChange={handleChange("roaMin")}
                    onMaxChange={handleChange("roaMax")}
                    highlight={isMatch(["roa"])}
                  />

                  <RangeField
                    label="Biên LN gộp"
                    unit="%"
                    minLabel="Biên LN gộp % tối thiểu"
                    maxLabel="Biên LN gộp % tối đa"
                    minPlaceholder="Từ %"
                    maxPlaceholder="Đến %"
                    minVal={form.grossMarginMin}
                    maxVal={form.grossMarginMax}
                    onMinChange={handleChange("grossMarginMin")}
                    onMaxChange={handleChange("grossMarginMax")}
                    highlight={isMatch(["biên gộp", "ln gộp"])}
                  />

                  <RangeField
                    label="Biên LN ròng"
                    unit="%"
                    minLabel="Biên LN ròng % tối thiểu"
                    maxLabel="Biên LN ròng % tối đa"
                    minPlaceholder="Từ %"
                    maxPlaceholder="Đến %"
                    minVal={form.netMarginMin}
                    maxVal={form.netMarginMax}
                    onMinChange={handleChange("netMarginMin")}
                    onMaxChange={handleChange("netMarginMax")}
                    highlight={isMatch(["biên ròng", "ln ròng"])}
                  />

                  <RangeField
                    label="Nợ / VCSH"
                    unit="lần"
                    minLabel="Nợ/VCSH tối thiểu"
                    maxLabel="Nợ/VCSH tối đa"
                    minPlaceholder="VD: 0"
                    maxPlaceholder="VD: 1.5"
                    minVal={form.debtToEquityMin}
                    maxVal={form.debtToEquityMax}
                    onMinChange={handleChange("debtToEquityMin")}
                    onMaxChange={handleChange("debtToEquityMax")}
                    highlight={isMatch(["nợ/vcsh", "nợ"])}
                  />

                  <RangeField
                    label="Nợ vay / Tổng TS"
                    unit="%"
                    minLabel="Nợ vay/Tổng TS % tối thiểu"
                    maxLabel="Nợ vay/Tổng TS % tối đa"
                    minPlaceholder="Từ %"
                    maxPlaceholder="Đến %"
                    minVal={form.debtToAssetsMin}
                    maxVal={form.debtToAssetsMax}
                    onMinChange={handleChange("debtToAssetsMin")}
                    onMaxChange={handleChange("debtToAssetsMax")}
                    highlight={isMatch(["tổng ts", "nợ vay"])}
                  />

                  <RangeField
                    label="Thanh toán hiện hành"
                    unit="lần"
                    minLabel="Thanh toán hiện hành tối thiểu"
                    maxLabel="Thanh toán hiện hành tối đa"
                    minPlaceholder="Từ"
                    maxPlaceholder="Đến"
                    minVal={form.currentRatioMin}
                    maxVal={form.currentRatioMax}
                    onMinChange={handleChange("currentRatioMin")}
                    onMaxChange={handleChange("currentRatioMax")}
                    highlight={isMatch(["thanh toán", "thanh toán hiện hành"])}
                  />

                  <RangeField
                    label="Khả năng trả lãi"
                    unit="lần"
                    minLabel="Khả năng trả lãi tối thiểu"
                    maxLabel="Khả năng trả lãi tối đa"
                    minPlaceholder="Từ"
                    maxPlaceholder="Đến"
                    minVal={form.interestCoverageMin}
                    maxVal={form.interestCoverageMax}
                    onMinChange={handleChange("interestCoverageMin")}
                    onMaxChange={handleChange("interestCoverageMax")}
                    highlight={isMatch(["trả lãi", "lãi vay"])}
                  />
                </div>
              </fieldset>
            )}
          </div>
        )}
      </form>
    </div>
  );
}
