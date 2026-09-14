import { useState } from "react";
import { scanStrategy, StrategyScanApiError, type ScanResponse, type StrategyCode } from "./api/stock-strategy";
import { StrategyPicker } from "./components/strategy-picker";
import { StrategyScanResults } from "./components/strategy-scan-results";

export function StockStrategyPage() {
  const [result, setResult] = useState<ScanResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [activeStrategy, setActiveStrategy] = useState<StrategyCode | null>(null);

  async function fetchScan(strategyCode: StrategyCode, offset = 0) {
    setSubmitting(true);
    setError(null);
    try {
      const response = await scanStrategy(strategyCode, { limit: 50, offset });
      setResult(response);
      setActiveStrategy(strategyCode);
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

  async function handleSubmit(strategyCode: StrategyCode) {
    await fetchScan(strategyCode, 0);
  }

  async function handlePageChange(newOffset: number) {
    if (!activeStrategy) return;
    await fetchScan(activeStrategy, newOffset);
  }

  return (
    <main className="app-shell quant-terminal-layout" aria-labelledby="strategy-scan-page-heading">
      <header className="page-header strategy-header">
        <div className="strat-header-left">
          <p className="eyebrow">FINVERA · QUANTITATIVE STRATEGY ENGINE</p>
          <h1 id="strategy-scan-page-heading">Chiến Lược & Tín Hiệu Định Lượng</h1>
          <p className="strategy-header-sub">
            Mô hình tính toán tín hiệu kỹ thuật tất định theo các bộ quy tắc chiến lược định lượng, kịch bản giao dịch và quản trị rủi ro.
          </p>
        </div>
      </header>

      <StrategyPicker onSubmit={handleSubmit} submitting={submitting} />

      {error && (
        <p role="alert" className="unavailable-msg">
          {error}
        </p>
      )}

      {result && <StrategyScanResults result={result} onPageChange={handlePageChange} loading={submitting} />}
    </main>
  );
}
