import { useEffect, useState } from "react";
import {
  getPortfolioAnalytics,
  type PortfolioAnalytics,
} from "../api/portfolio-analytics";

interface PortfolioAnalyticsViewProps {
  portfolioId: string;
}

export function PortfolioAnalyticsView({ portfolioId }: PortfolioAnalyticsViewProps) {
  const [analytics, setAnalytics] = useState<PortfolioAnalytics | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [reloadCount, setReloadCount] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    getPortfolioAnalytics(portfolioId, fromDate || undefined, toDate || undefined, controller.signal)
      .then((data) => {
        setAnalytics(data);
        setError(null);
      })
      .catch(() => {
        if (controller.signal.aborted) return;
        setError("Không thể tải dữ liệu phân tích danh mục.");
      })
      .finally(() => {
        if (controller.signal.aborted) return;
        setLoading(false);
      });
    return () => controller.abort();
  }, [portfolioId, fromDate, toDate, reloadCount]);

  function formatPercent(val: string | null) {
    if (!val) return { text: "—", sign: "" };
    const num = Number(val) * 100;
    if (isNaN(num)) return { text: val, sign: "" };
    if (num > 0) return { text: `(+) +${num.toFixed(2)}%`, sign: "up" };
    if (num < 0) return { text: `(-) ${num.toFixed(2)}%`, sign: "down" };
    return { text: `(0) 0.00%`, sign: "ref" };
  }

  function formatWeight(val: string | null) {
    if (!val) return "0.00%";
    const num = Number(val) * 100;
    return isNaN(num) ? val : `${num.toFixed(2)}%`;
  }

  function formatMoney(val: string | null) {
    if (!val) return "—";
    const num = Number(val);
    return isNaN(num) ? val : num.toLocaleString("vi-VN") + " ₫";
  }

  if (loading && !analytics) {
    return (
      <div className="portfolio-loading-state">
        <span className="text-slate-400 font-mono text-sm">Đang tính toán phân tích hiệu quả danh mục…</span>
      </div>
    );
  }

  if (error && !analytics) {
    return (
      <div className="portfolio-error-banner" role="alert">
        {error}
      </div>
    );
  }

  if (!analytics) return null;

  const retInception = formatPercent(analytics.returnSinceInception);
  const retPeriod = formatPercent(analytics.returnOverPeriod);
  const benchRet =
    analytics.benchmark.benchmarkReturn == null
      ? { text: "Không có dữ liệu VN-Index cho kỳ này", sign: "" }
      : formatPercent(analytics.benchmark.benchmarkReturn);
  const maxDrawdownNum = analytics.maxDrawdown != null ? Number(analytics.maxDrawdown) : null;
  const maxDd =
    maxDrawdownNum != null && maxDrawdownNum !== 0
      ? `(-) -${(maxDrawdownNum * 100).toFixed(2)}%`
      : maxDrawdownNum === 0
        ? "0.00%"
        : "—";

  return (
    <div className="analytics-view-container">
      {/* Time window selector and warnings */}
      <div className="analytics-filter-toolbar quant-terminal-card">
        <div className="analytics-date-controls">
          <label className="analytics-date-field">
            <span className="analytics-date-lbl">Từ ngày:</span>
            <input
              type="date"
              value={fromDate}
              onChange={(e) => setFromDate(e.target.value)}
              className="analytics-date-input font-mono"
            />
          </label>
          <label className="analytics-date-field">
            <span className="analytics-date-lbl">Đến ngày:</span>
            <input
              type="date"
              value={toDate}
              onChange={(e) => setToDate(e.target.value)}
              className="analytics-date-input font-mono"
            />
          </label>
          {(fromDate || toDate) && (
            <button
              type="button"
              onClick={() => {
                setFromDate("");
                setToDate("");
              }}
              className="btn-analytics-reset"
            >
              Mặc định
            </button>
          )}
        </div>

        <button
          type="button"
          onClick={() => setReloadCount((c) => c + 1)}
          className="btn-analytics-recalc"
        >
          ↻ Tính lại
        </button>
      </div>

      {analytics.periodClampedToInception && (
        <div
          role="note"
          data-testid="clamped-inception-banner"
          className="clamped-inception-banner"
        >
          <span className="banner-icon">ℹ️</span>
          <span>
            Thời gian phân tích được giới hạn bắt đầu từ ngày giao dịch đầu tiên (<strong>{analytics.periodFrom}</strong>), vì không có lịch sử giao dịch trước thời điểm này.
          </span>
        </div>
      )}

      {/* Summary KPI Cards */}
      <div className="analytics-kpi-grid">
        <div className="analytics-kpi-card">
          <span className="kpi-lbl">Tỷ suất sinh lời từ đầu</span>
          <div className={`kpi-val font-mono ${retInception.sign === "up" ? "text-emerald-400" : retInception.sign === "down" ? "text-rose-400" : "text-slate-100"}`}>
            <strong>{retInception.text}</strong>
          </div>
          <span className="kpi-sub font-mono">Phương pháp vốn góp ròng</span>
        </div>

        <div className="analytics-kpi-card">
          <span className="kpi-lbl">Tỷ suất sinh lời trong kỳ</span>
          <div className={`kpi-val font-mono ${retPeriod.sign === "up" ? "text-emerald-400" : retPeriod.sign === "down" ? "text-rose-400" : "text-slate-100"}`}>
            <strong>{retPeriod.text}</strong>
          </div>
          <span className="kpi-sub font-mono">{analytics.periodFrom} → {analytics.periodTo}</span>
        </div>

        <div className="analytics-kpi-card">
          <span className="kpi-lbl">So sánh VN-Index</span>
          <div className="kpi-val font-mono text-cyan-400">
            <strong>{benchRet.text}</strong>
          </div>
          <span className="kpi-sub font-mono">VNINDEX trong cùng kỳ</span>
        </div>

        <div className="analytics-kpi-card">
          <span className="kpi-lbl">Sụt giảm tối đa (Max Drawdown)</span>
          <div className="kpi-val font-mono text-rose-400">
            <strong>{maxDd}</strong>
          </div>
          <span className="kpi-sub font-mono">Mức giảm từ đỉnh cao nhất</span>
        </div>
      </div>

      {/* Risk Exposure Section */}
      <div className="risk-exposure-box quant-terminal-card">
        <div className="risk-box-header">
          <h2 className="risk-box-title">Mức độ rủi ro danh mục (Risk Exposure)</h2>
          <span className="risk-box-sub">Đo lường mức độ biến động & mức độ tập trung vốn của danh mục</span>
        </div>
        <div className="risk-metrics-strip">
          <div className="risk-metric-cell">
            <span className="risk-metric-lbl">Điểm rủi ro tổng hợp</span>
            <div className="risk-score-badge font-mono">
              <strong>{analytics.riskExposure.riskExposureScore !== null ? `${analytics.riskExposure.riskExposureScore}/100` : "—"}</strong>
            </div>
          </div>
          <div className="risk-metric-cell">
            <span className="risk-metric-lbl">Phân loại rủi ro</span>
            <div>
              {analytics.riskExposure.riskExposureLevel ? (
                <span className={`risk-level-tag risk-${analytics.riskExposure.riskExposureLevel.toLowerCase()}`}>
                  {analytics.riskExposure.riskExposureLevel}
                </span>
              ) : (
                <span className="text-slate-500 text-xs">Không có tín hiệu</span>
              )}
            </div>
          </div>
          <div className="risk-metric-cell">
            <span className="risk-metric-lbl">Tỷ lệ bao phủ danh mục</span>
            <div className="risk-coverage-val font-mono">
              <strong>{formatWeight(analytics.riskExposure.coverageRatio)}</strong>
            </div>
            <span className="risk-metric-hint">Tỷ trọng vị thế có tín hiệu phân tích</span>
          </div>
        </div>
      </div>

      {/* Concentration Split (Stock vs Sector) */}
      <div className="concentration-grid">
        {/* Stock Concentration */}
        <div className="concentration-card quant-terminal-card">
          <div className="conc-header">
            <h2 className="conc-title">Tập trung theo mã cổ phiếu</h2>
            <span className="conc-count font-mono">{analytics.stockConcentration.length} mã</span>
          </div>
          {analytics.stockConcentration.length === 0 ? (
            <p className="conc-empty-hint">Chưa có vị thế nắm giữ.</p>
          ) : (
            <div className="conc-bars-list">
              {analytics.stockConcentration.map((item) => (
                <div key={item.key} className="conc-bar-row">
                  <span className="conc-bar-name font-mono font-bold">{item.key}</span>
                  <div className="conc-bar-track-wrap">
                    <div className="conc-track">
                      <div className="conc-fill fill-cyan" style={{ width: formatWeight(item.percentage) }} />
                    </div>
                    <span className="conc-pct font-mono">{formatWeight(item.percentage)}</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Sector Concentration */}
        <div className="concentration-card quant-terminal-card">
          <div className="conc-header">
            <h2 className="conc-title">Tập trung theo nhóm ngành</h2>
            <span className="conc-count font-mono">{analytics.sectorConcentration.length} ngành</span>
          </div>
          {analytics.sectorConcentration.length === 0 ? (
            <p className="conc-empty-hint">Chưa có vị thế nắm giữ.</p>
          ) : (
            <div className="conc-bars-list">
              {analytics.sectorConcentration.map((item) => (
                <div key={item.key} className="conc-bar-row">
                  <span className="conc-bar-name">{item.key}</span>
                  <div className="conc-bar-track-wrap">
                    <div className="conc-track">
                      <div className="conc-fill fill-emerald" style={{ width: formatWeight(item.percentage) }} />
                    </div>
                    <span className="conc-pct font-mono">{formatWeight(item.percentage)}</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Performance History Table */}
      <div className="history-table-card quant-terminal-card">
        <div className="history-header">
          <h2 className="history-title">Lịch sử biến động giá trị danh mục</h2>
          <span className="history-sub font-mono">{analytics.performanceHistory.length} mốc tính toán</span>
        </div>
        {analytics.performanceHistory.length === 0 ? (
          <p className="history-empty-hint">Chưa có dữ liệu lịch sử trong kỳ phân tích.</p>
        ) : (
          <div className="port-table-wrap history-table-wrap">
            <table className="terminal-quant-table">
              <thead>
                <tr>
                  <th scope="col">Ngày giao dịch</th>
                  <th scope="col" className="text-right">Tổng giá trị tài sản</th>
                  <th scope="col" className="text-center">Trạng thái dữ liệu</th>
                </tr>
              </thead>
              <tbody>
                {analytics.performanceHistory.map((pt) => (
                  <tr key={pt.date} className="quant-row">
                    <td className="font-mono text-slate-300 text-xs">{pt.date}</td>
                    <td className="text-right font-mono font-bold text-slate-100">{formatMoney(pt.totalValue)}</td>
                    <td className="text-center">
                      {pt.dataStatus === "PARTIAL" ? (
                        <span className="status-pill status-pill-partial">Một phần (Điểm thiếu dữ liệu)</span>
                      ) : (
                        <span className="status-pill status-pill-valid">Đầy đủ</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
