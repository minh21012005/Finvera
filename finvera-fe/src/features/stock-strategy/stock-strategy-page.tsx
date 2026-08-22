import { useState } from "react";
import { scanStrategy, StrategyScanApiError, type ScanResponse, type StrategyCode } from "./api/stock-strategy";
import { StrategyPicker } from "./components/strategy-picker";
import { StrategyScanResults } from "./components/strategy-scan-results";
import { navigate } from "../../router";

export function StockStrategyPage() {
  const [result, setResult] = useState<ScanResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(strategyCode: StrategyCode) {
    setSubmitting(true);
    setError(null);
    try {
      const response = await scanStrategy(strategyCode);
      setResult(response);
    } catch (err) {
      setResult(null);
      if (err instanceof StrategyScanApiError && (err.status === 401 || err.status === 403)) {
        setError("Phiên đăng nhập riêng tư không hợp lệ hoặc đã hết hạn.");
      } else {
        setError("Không thể quét chiến lược lúc này. Vui lòng thử lại.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="app-shell" aria-labelledby="strategy-scan-page-heading">
      <header className="page-header">
        <button type="button" className="back-link" onClick={() => navigate("/")}>
          ← Trang chủ
        </button>
        <p className="eyebrow">FINVERA · STRATEGIES</p>
        <h1 id="strategy-scan-page-heading">Quét chiến lược giao dịch</h1>
        <p style={{ color: "var(--text-secondary)", margin: 0, fontSize: "0.875rem" }}>
          Đánh giá kịch bản tín hiệu kỹ thuật tất định theo các chiến lược Trend Following, Breakout, Pullback, Mean Reversion...
        </p>
      </header>

      <StrategyPicker onSubmit={handleSubmit} submitting={submitting} />

      {error && (
        <p role="alert" className="unavailable-msg">
          {error}
        </p>
      )}

      {result && <StrategyScanResults result={result} />}
    </main>
  );
}
