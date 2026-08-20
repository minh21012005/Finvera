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
      <div style={{ padding: "32px", textAlign: "center", color: "var(--text-secondary)" }}>
        Đang tính toán phân tích hiệu quả danh mục…
      </div>
    );
  }

  if (error && !analytics) {
    return (
      <div style={{ padding: "24px", textAlign: "center", color: "var(--color-down)" }}>
        {error}
      </div>
    );
  }

  if (!analytics) return null;

  const retInception = formatPercent(analytics.returnSinceInception);
  const retPeriod = formatPercent(analytics.returnOverPeriod);
  const benchRet = formatPercent(analytics.benchmark.benchmarkReturn);
  const maxDrawdownNum = analytics.maxDrawdown != null ? Number(analytics.maxDrawdown) : null;
  const maxDd =
    maxDrawdownNum != null && maxDrawdownNum !== 0
      ? `(-) -${(maxDrawdownNum * 100).toFixed(2)}%`
      : maxDrawdownNum === 0
        ? "0.00%"
        : "—";

  return (
    <div className="analytics-view-container" style={{ display: "flex", flexDirection: "column", gap: "24px" }}>
      {/* Time window selector and warnings */}
      <div
        className="card"
        style={{
          padding: "16px 20px",
          background: "var(--bg-card)",
          border: "1px solid var(--border-color)",
          borderRadius: "8px",
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          flexWrap: "wrap",
          gap: "16px",
        }}
      >
        <div style={{ display: "flex", gap: "12px", alignItems: "center" }}>
          <label style={{ fontSize: "0.9rem", color: "var(--text-secondary)" }}>
            Từ ngày:
            <input
              type="date"
              value={fromDate}
              onChange={(e) => setFromDate(e.target.value)}
              style={{
                marginLeft: "8px",
                padding: "6px 10px",
                background: "var(--bg-main)",
                border: "1px solid var(--border-color)",
                borderRadius: "4px",
                color: "var(--text-primary)",
              }}
            />
          </label>
          <label style={{ fontSize: "0.9rem", color: "var(--text-secondary)" }}>
            Đến ngày:
            <input
              type="date"
              value={toDate}
              onChange={(e) => setToDate(e.target.value)}
              style={{
                marginLeft: "8px",
                padding: "6px 10px",
                background: "var(--bg-main)",
                border: "1px solid var(--border-color)",
                borderRadius: "4px",
                color: "var(--text-primary)",
              }}
            />
          </label>
          {(fromDate || toDate) && (
            <button
              type="button"
              onClick={() => {
                setFromDate("");
                setToDate("");
              }}
              style={{
                padding: "6px 12px",
                background: "transparent",
                border: "1px solid var(--border-color)",
                borderRadius: "4px",
                color: "var(--text-muted)",
                cursor: "pointer",
                fontSize: "0.85rem",
              }}
            >
              Mặc định
            </button>
          )}
        </div>

        <button
          type="button"
          onClick={() => setReloadCount((c) => c + 1)}
          style={{
            padding: "6px 12px",
            background: "var(--bg-main)",
            border: "1px solid var(--border-color)",
            borderRadius: "4px",
            color: "var(--text-secondary)",
            cursor: "pointer",
          }}
        >
          ↻ Tính lại
        </button>
      </div>

      {analytics.periodClampedToInception && (
        <div
          role="note"
          data-testid="clamped-inception-banner"
          style={{
            padding: "12px 16px",
            background: "var(--bg-card)",
            border: "1px solid var(--border-color)",
            borderRadius: "6px",
            fontSize: "0.9rem",
            color: "var(--text-secondary)",
          }}
        >
          ℹ️ Thời gian phân tích được giới hạn bắt đầu từ ngày giao dịch đầu tiên ({analytics.periodFrom}), vì không có lịch sử giao dịch trước thời điểm này.
        </div>
      )}

      {/* Summary Cards */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: "16px" }}>
        <div className="card" style={{ padding: "16px", background: "var(--bg-card)", border: "1px solid var(--border-color)", borderRadius: "8px" }}>
          <div style={{ fontSize: "0.85rem", color: "var(--text-secondary)", marginBottom: "4px" }}>Tỷ suất sinh lời từ đầu</div>
          <div
            style={{
              fontSize: "1.4rem",
              fontWeight: 700,
              color: retInception.sign === "up" ? "var(--color-up)" : retInception.sign === "down" ? "var(--color-down)" : "inherit",
            }}
          >
            {retInception.text}
          </div>
          <div style={{ fontSize: "0.75rem", color: "var(--text-muted)", marginTop: "4px" }}>Phương pháp vốn góp ròng</div>
        </div>

        <div className="card" style={{ padding: "16px", background: "var(--bg-card)", border: "1px solid var(--border-color)", borderRadius: "8px" }}>
          <div style={{ fontSize: "0.85rem", color: "var(--text-secondary)", marginBottom: "4px" }}>Tỷ suất sinh lời trong kỳ</div>
          <div
            style={{
              fontSize: "1.4rem",
              fontWeight: 700,
              color: retPeriod.sign === "up" ? "var(--color-up)" : retPeriod.sign === "down" ? "var(--color-down)" : "inherit",
            }}
          >
            {retPeriod.text}
          </div>
          <div style={{ fontSize: "0.75rem", color: "var(--text-muted)", marginTop: "4px" }}>{analytics.periodFrom} → {analytics.periodTo}</div>
        </div>

        <div className="card" style={{ padding: "16px", background: "var(--bg-card)", border: "1px solid var(--border-color)", borderRadius: "8px" }}>
          <div style={{ fontSize: "0.85rem", color: "var(--text-secondary)", marginBottom: "4px" }}>So sánh VN-Index</div>
          <div style={{ fontSize: "1.4rem", fontWeight: 700 }}>
            {benchRet.text}
          </div>
          <div style={{ fontSize: "0.75rem", color: "var(--text-muted)", marginTop: "4px" }}>VNINDEX trong cùng kỳ</div>
        </div>

        <div className="card" style={{ padding: "16px", background: "var(--bg-card)", border: "1px solid var(--border-color)", borderRadius: "8px" }}>
          <div style={{ fontSize: "0.85rem", color: "var(--text-secondary)", marginBottom: "4px" }}>Sụt giảm tối đa (Max Drawdown)</div>
          <div style={{ fontSize: "1.4rem", fontWeight: 700, color: "var(--color-down)" }}>
            {maxDd}
          </div>
          <div style={{ fontSize: "0.75rem", color: "var(--text-muted)", marginTop: "4px" }}>Mức giảm từ đỉnh cao nhất</div>
        </div>
      </div>

      {/* Risk Exposure Section */}
      <div className="card" style={{ padding: "20px", background: "var(--bg-card)", border: "1px solid var(--border-color)", borderRadius: "8px" }}>
        <h2 style={{ fontSize: "1.1rem", fontWeight: 600, marginBottom: "16px" }}>Mức độ rủi ro danh mục (Risk Exposure)</h2>
        <div style={{ display: "flex", gap: "24px", alignItems: "center", flexWrap: "wrap" }}>
          <div>
            <div style={{ fontSize: "0.85rem", color: "var(--text-secondary)" }}>Điểm rủi ro tổng hợp</div>
            <div style={{ fontSize: "1.6rem", fontWeight: 700 }}>
              {analytics.riskExposure.riskExposureScore !== null ? `${analytics.riskExposure.riskExposureScore}/100` : "—"}
            </div>
          </div>
          <div>
            <div style={{ fontSize: "0.85rem", color: "var(--text-secondary)" }}>Phân loại rủi ro</div>
            <div style={{ marginTop: "4px" }}>
              {analytics.riskExposure.riskExposureLevel ? (
                <span
                  style={{
                    padding: "4px 10px",
                    borderRadius: "4px",
                    fontWeight: 700,
                    fontSize: "0.9rem",
                    border: "1px solid var(--border-color)",
                    color: analytics.riskExposure.riskExposureLevel === "LOW" ? "var(--color-up)" : analytics.riskExposure.riskExposureLevel === "HIGH" ? "var(--color-down)" : "var(--color-warn)",
                  }}
                >
                  {analytics.riskExposure.riskExposureLevel}
                </span>
              ) : (
                <span style={{ color: "var(--text-muted)" }}>Không có tín hiệu</span>
              )}
            </div>
          </div>
          <div>
            <div style={{ fontSize: "0.85rem", color: "var(--text-secondary)" }}>Tỷ lệ bao phủ danh mục</div>
            <div style={{ fontSize: "1.2rem", fontWeight: 600 }}>
              {formatWeight(analytics.riskExposure.coverageRatio)}
            </div>
            <div style={{ fontSize: "0.75rem", color: "var(--text-muted)" }}>Tỷ trọng vị thế có tín hiệu phân tích</div>
          </div>
        </div>
      </div>

      {/* Concentration Split */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "20px" }}>
        {/* Stock Concentration */}
        <div className="card" style={{ padding: "20px", background: "var(--bg-card)", border: "1px solid var(--border-color)", borderRadius: "8px" }}>
          <h2 style={{ fontSize: "1.1rem", fontWeight: 600, marginBottom: "16px" }}>Tập trung theo mã cổ phiếu</h2>
          {analytics.stockConcentration.length === 0 ? (
            <p style={{ color: "var(--text-muted)", fontSize: "0.9rem" }}>Chưa có vị thế nắm giữ.</p>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
              {analytics.stockConcentration.map((item) => (
                <div key={item.key} style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <span style={{ fontWeight: 600 }}>{item.key}</span>
                  <div style={{ display: "flex", alignItems: "center", gap: "12px", width: "60%" }}>
                    <div style={{ flex: 1, height: "8px", background: "var(--bg-main)", borderRadius: "4px", overflow: "hidden" }}>
                      <div style={{ width: formatWeight(item.percentage), height: "100%", background: "var(--color-accent)" }} />
                    </div>
                    <span style={{ width: "50px", textAlign: "right", fontSize: "0.9rem" }}>{formatWeight(item.percentage)}</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Sector Concentration */}
        <div className="card" style={{ padding: "20px", background: "var(--bg-card)", border: "1px solid var(--border-color)", borderRadius: "8px" }}>
          <h2 style={{ fontSize: "1.1rem", fontWeight: 600, marginBottom: "16px" }}>Tập trung theo nhóm ngành</h2>
          {analytics.sectorConcentration.length === 0 ? (
            <p style={{ color: "var(--text-muted)", fontSize: "0.9rem" }}>Chưa có vị thế nắm giữ.</p>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
              {analytics.sectorConcentration.map((item) => (
                <div key={item.key} style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <span style={{ fontWeight: 600, fontSize: "0.9rem" }}>{item.key}</span>
                  <div style={{ display: "flex", alignItems: "center", gap: "12px", width: "55%" }}>
                    <div style={{ flex: 1, height: "8px", background: "var(--bg-main)", borderRadius: "4px", overflow: "hidden" }}>
                      <div style={{ width: formatWeight(item.percentage), height: "100%", background: "var(--color-up)" }} />
                    </div>
                    <span style={{ width: "50px", textAlign: "right", fontSize: "0.9rem" }}>{formatWeight(item.percentage)}</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Performance History Table */}
      <div className="card" style={{ padding: "20px", background: "var(--bg-card)", border: "1px solid var(--border-color)", borderRadius: "8px" }}>
        <h2 style={{ fontSize: "1.1rem", fontWeight: 600, marginBottom: "16px" }}>Lịch sử biến động giá trị danh mục</h2>
        {analytics.performanceHistory.length === 0 ? (
          <p style={{ color: "var(--text-muted)", fontSize: "0.9rem" }}>Chưa có dữ liệu lịch sử trong kỳ phân tích.</p>
        ) : (
          <div style={{ maxHeight: "300px", overflowY: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.9rem" }}>
              <thead>
                <tr style={{ borderBottom: "1px solid var(--border-color)", textAlign: "left" }}>
                  <th style={{ padding: "8px 12px" }}>Ngày</th>
                  <th style={{ padding: "8px 12px", textAlign: "right" }}>Tổng giá trị tài sản</th>
                  <th style={{ padding: "8px 12px", textAlign: "center" }}>Trạng thái dữ liệu</th>
                </tr>
              </thead>
              <tbody>
                {analytics.performanceHistory.map((pt) => (
                  <tr key={pt.date} style={{ borderBottom: "1px solid var(--border-color)" }}>
                    <td style={{ padding: "8px 12px" }}>{pt.date}</td>
                    <td style={{ padding: "8px 12px", textAlign: "right", fontWeight: 600 }}>{formatMoney(pt.totalValue)}</td>
                    <td style={{ padding: "8px 12px", textAlign: "center" }}>
                      {pt.dataStatus === "PARTIAL" ? (
                        <span style={{ color: "var(--color-warn)", fontSize: "0.8rem" }}>Một phần (Điểm thiếu dữ liệu)</span>
                      ) : (
                        <span style={{ color: "var(--color-up)", fontSize: "0.8rem" }}>Đầy đủ</span>
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
