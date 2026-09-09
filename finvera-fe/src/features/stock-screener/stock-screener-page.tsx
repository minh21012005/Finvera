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
    <main className="app-shell quant-terminal-layout" aria-labelledby="screener-page-heading">
      <header className="page-header screener-header">
        <button type="button" className="back-link" onClick={() => navigate("/")}>
          ← Trang chủ
        </button>
        <p className="eyebrow">FINVERA · QUANT RADAR & SCREENER</p>
        <h1 id="screener-page-heading">Bộ Lọc & Phân Tích Chuyên Sâu</h1>
        <p className="screener-header-sub">
          Mô hình định lượng phát hiện các cổ phiếu dẫn dắt (Leaders), đột phá khối lượng và đạt chuẩn CANSLIM toàn thị trường.
        </p>
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
