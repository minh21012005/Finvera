import { useState } from "react";
import type { ScreenRequest } from "../api/stock-screener";
import { buildScreenRequest, EMPTY_FORM, MA_RELATIONSHIPS, type FormState } from "./screener-filters-model";

export function ScreenerFilters({ onSubmit, submitting }: { onSubmit: (request: ScreenRequest) => void; submitting: boolean }) {
  const [form, setForm] = useState<FormState>(EMPTY_FORM);

  function field<K extends keyof FormState>(key: K) {
    return {
      value: form[key],
      onChange: (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
        setForm((prev) => ({ ...prev, [key]: e.target.value })),
    };
  }

  return (
    <form
      aria-label="Bộ lọc cổ phiếu"
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit(buildScreenRequest(form));
      }}
    >
      <fieldset>
        <legend>Thị trường</legend>
        <label>
          Sàn giao dịch
          <input type="text" placeholder="HOSE, HNX, UPCOM" {...field("exchange")} />
        </label>
        <label>
          Vốn hóa tối thiểu (VND)
          <input type="text" inputMode="decimal" {...field("marketCapMin")} />
        </label>
        <label>
          Vốn hóa tối đa (VND)
          <input type="text" inputMode="decimal" {...field("marketCapMax")} />
        </label>
      </fieldset>

      <fieldset>
        <legend>Giá</legend>
        <label>
          Giá tối thiểu
          <input type="text" inputMode="decimal" {...field("priceMin")} />
        </label>
        <label>
          Giá tối đa
          <input type="text" inputMode="decimal" {...field("priceMax")} />
        </label>
        <label>
          % thay đổi tối thiểu
          <input type="text" inputMode="decimal" {...field("priceChangePercentMin")} />
        </label>
        <label>
          % thay đổi tối đa
          <input type="text" inputMode="decimal" {...field("priceChangePercentMax")} />
        </label>
      </fieldset>

      <fieldset>
        <legend>Kỹ thuật</legend>
        <label>
          RSI tối thiểu
          <input type="text" inputMode="decimal" {...field("rsiMin")} />
        </label>
        <label>
          RSI tối đa
          <input type="text" inputMode="decimal" {...field("rsiMax")} />
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
          <input type="text" inputMode="numeric" {...field("volumeMin")} />
        </label>
        <label>
          Khối lượng tối đa
          <input type="text" inputMode="numeric" {...field("volumeMax")} />
        </label>
        <label>
          KL tương đối tối thiểu
          <input type="text" inputMode="decimal" {...field("relativeVolumeMin")} />
        </label>
        <label>
          KL tương đối tối đa
          <input type="text" inputMode="decimal" {...field("relativeVolumeMax")} />
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
            <option value="UPTREND">Tăng</option>
            <option value="DOWNTREND">Giảm</option>
            <option value="SIDEWAYS">Đi ngang</option>
          </select>
        </label>
      </fieldset>

      <fieldset>
        <legend>Cơ bản</legend>
        <label>
          Tăng trưởng DT % tối thiểu
          <input type="text" inputMode="decimal" {...field("revenueGrowthPercentMin")} />
        </label>
        <label>
          Tăng trưởng DT % tối đa
          <input type="text" inputMode="decimal" {...field("revenueGrowthPercentMax")} />
        </label>
        <label>
          Tăng trưởng LN % tối thiểu
          <input type="text" inputMode="decimal" {...field("earningsGrowthPercentMin")} />
        </label>
        <label>
          Tăng trưởng LN % tối đa
          <input type="text" inputMode="decimal" {...field("earningsGrowthPercentMax")} />
        </label>
        <label>
          ROE tối thiểu
          <input type="text" inputMode="decimal" {...field("roeMin")} />
        </label>
        <label>
          ROE tối đa
          <input type="text" inputMode="decimal" {...field("roeMax")} />
        </label>
        <label>
          ROA tối thiểu
          <input type="text" inputMode="decimal" {...field("roaMin")} />
        </label>
        <label>
          ROA tối đa
          <input type="text" inputMode="decimal" {...field("roaMax")} />
        </label>
        <label>
          P/E tối thiểu
          <input type="text" inputMode="decimal" {...field("peMin")} />
        </label>
        <label>
          P/E tối đa
          <input type="text" inputMode="decimal" {...field("peMax")} />
        </label>
        <label>
          P/B tối thiểu
          <input type="text" inputMode="decimal" {...field("pbMin")} />
        </label>
        <label>
          P/B tối đa
          <input type="text" inputMode="decimal" {...field("pbMax")} />
        </label>
        <label>
          Nợ/VCSH tối thiểu
          <input type="text" inputMode="decimal" {...field("debtToEquityMin")} />
        </label>
        <label>
          Nợ/VCSH tối đa
          <input type="text" inputMode="decimal" {...field("debtToEquityMax")} />
        </label>
      </fieldset>

      <button type="submit" disabled={submitting}>
        {submitting ? "Đang lọc…" : "Lọc cổ phiếu"}
      </button>
    </form>
  );
}
