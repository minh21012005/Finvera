import { useState } from "react";
import { executeScreen, ScreenerApiError, type ScreenRequest, type ScreenResponse } from "./api/stock-screener";
import { ScreenerFilters } from "./components/screener-filters";
import { ScreenerResults } from "./components/screener-results";
import { navigate } from "../../router";

export function StockScreenerPage() {
  const [result, setResult] = useState<ScreenResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(request: ScreenRequest) {
    setSubmitting(true);
    setError(null);
    try {
      const response = await executeScreen(request);
      setResult(response);
    } catch (err) {
      setResult(null);
      if (err instanceof ScreenerApiError && err.reasonCode === "INVALID_FILTER_RANGE") {
        setError("Khoảng lọc không hợp lệ: giá trị tối thiểu lớn hơn giá trị tối đa.");
      } else {
        setError("Không thể thực hiện lọc cổ phiếu lúc này. Vui lòng thử lại.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main aria-labelledby="screener-page-heading">
      <header className="screener-page-header">
        <button type="button" className="back-link" onClick={() => navigate("/")}>
          ← Trang chủ
        </button>
        <h1 id="screener-page-heading">Sàng lọc cổ phiếu</h1>
      </header>

      <ScreenerFilters onSubmit={handleSubmit} submitting={submitting} />

      {error && (
        <p role="alert" className="unavailable-msg">
          {error}
        </p>
      )}

      {result && <ScreenerResults result={result} />}
    </main>
  );
}
