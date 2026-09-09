import { useState, useMemo } from "react";
import type { StockFundamentals as StockFundamentalsData, FundamentalMetricValue } from "../api/stock-detail";
import { dataStatusLabel, formatFundamentalValue, fundamentalMetricLabel } from "../format/stock-format";
import { ApplicabilityNote, ReasonCodes } from "../../../shared/components/reason-codes";
import { Search, Sparkles } from "lucide-react";

/** Feature 009 FR-002: catalog codes grouped by category; unknown codes fall into "Khác". */
const METRIC_GROUPS: ReadonlyArray<{ id: string; title: string; shortTitle: string; codes: ReadonlySet<string> }> = [
  { id: "business", title: "Kết quả kinh doanh", shortTitle: "Kinh doanh", codes: new Set(["REVENUE", "REVENUE_TTM", "REVENUE_GROWTH_PERCENT", "GROSS_PROFIT", "OPERATING_PROFIT", "NET_PROFIT", "NET_PROFIT_TTM", "EBITDA", "EBITDA_TTM", "EPS", "EPS_TTM", "TRAILING_EPS", "EPS_GROWTH_PERCENT"]) },
  { id: "profitability", title: "Khả năng sinh lời", shortTitle: "Sinh lời", codes: new Set(["ROE", "ROA", "ROE_TTM", "ROA_TTM", "ROCE", "GROSS_MARGIN", "OPERATING_MARGIN", "NET_MARGIN", "NIM", "COST_INCOME_RATIO"]) },
  { id: "liquidity", title: "Thanh khoản & khả năng trả nợ", shortTitle: "Thanh khoản", codes: new Set(["CURRENT_RATIO", "QUICK_RATIO", "CASH_RATIO", "INTEREST_COVERAGE", "LOAN_TO_DEPOSIT"]) },
  { id: "efficiency", title: "Hiệu quả hoạt động", shortTitle: "Hiệu quả", codes: new Set(["TOTAL_ASSET_TURNOVER", "INVENTORY_TURNOVER", "RECEIVABLES_TURNOVER"]) },
  { id: "capital", title: "Cơ cấu vốn", shortTitle: "Cơ cấu vốn", codes: new Set(["DEBT_TO_EQUITY", "DEBT_TO_ASSETS", "LIABILITIES_TO_EQUITY", "EQUITY_TO_ASSETS", "TOTAL_DEBT", "CASH_AND_EQUIVALENTS", "EQUITY_ATTRIBUTABLE_TO_PARENT", "BVPS", "TOTAL_ASSETS_GROWTH_PERCENT", "EQUITY_GROWTH_PERCENT"]) },
  { id: "cashflow", title: "Dòng tiền & cổ tức", shortTitle: "Cổ tức & Dòng tiền", codes: new Set(["FREE_CASH_FLOW", "DIVIDEND_PER_SHARE", "DIVIDEND_PER_SHARE_TTM", "DIVIDEND_YIELD"]) },
  { id: "market", title: "Thị trường", shortTitle: "Thị trường", codes: new Set(["BETA", "PS", "EV_EBITDA"]) },
];

const KEY_HIGHLIGHT_SPECS = [
  { code: "REVENUE_TTM", fallback: "REVENUE", label: "D.Thu thuần" },
  { code: "NET_PROFIT_TTM", fallback: "NET_PROFIT", label: "LNST" },
  { code: "EPS_TTM", fallback: "EPS", label: "EPS" },
  { code: "ROE_TTM", fallback: "ROE", label: "ROE" },
  { code: "NET_MARGIN", fallback: "GROSS_MARGIN", label: "Biên ròng" },
  { code: "DEBT_TO_EQUITY", fallback: "DEBT_TO_ASSETS", label: "Nợ / VCSH" },
];

export function StockFundamentals({ fundamentals }: { fundamentals: StockFundamentalsData }) {
  const period = fundamentals.period;
  const [activeCategory, setActiveCategory] = useState<string>("ALL");
  const [searchQuery, setSearchQuery] = useState<string>("");

  const allGroups = useMemo(() => {
    const groups = METRIC_GROUPS.map((g) => ({
      id: g.id,
      title: g.title,
      shortTitle: g.shortTitle,
      metrics: fundamentals.metrics.filter((m) => g.codes.has(m.metricCode)),
    }));
    const known = new Set(METRIC_GROUPS.flatMap((g) => [...g.codes]));
    const other = fundamentals.metrics.filter((m) => !known.has(m.metricCode));
    if (other.length > 0) {
      groups.push({ id: "other", title: "Khác", shortTitle: "Khác", metrics: other });
    }
    return groups.filter((g) => g.metrics.length > 0);
  }, [fundamentals.metrics]);

  const keyHighlights = useMemo(() => {
    const metricMap = new Map<string, FundamentalMetricValue>(fundamentals.metrics.map((m) => [m.metricCode, m]));
    return KEY_HIGHLIGHT_SPECS.map((spec) => {
      const metric = metricMap.get(spec.code) ?? metricMap.get(spec.fallback);
      if (!metric) return null;
      return {
        label: spec.label,
        metric,
      };
    }).filter((item): item is { label: string; metric: FundamentalMetricValue } => item !== null);
  }, [fundamentals.metrics]);

  const visibleGroups = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    if (query) {
      return allGroups
        .map((g) => ({
          ...g,
          metrics: g.metrics.filter(
            (m) =>
              m.metricCode.toLowerCase().includes(query) ||
              fundamentalMetricLabel(m.metricCode).toLowerCase().includes(query)
          ),
        }))
        .filter((g) => g.metrics.length > 0);
    }

    if (activeCategory === "ALL") {
      return allGroups;
    }

    return allGroups.filter((g) => g.id === activeCategory);
  }, [allGroups, activeCategory, searchQuery]);

  return (
    <section aria-labelledby="stock-fundamentals-heading" className="stock-fundamentals-card">
      <header className="fundamentals-card-header">
        <div>
          <h2 id="stock-fundamentals-heading">Chỉ số cơ bản</h2>
          {period ? (
            <div className="period-info">
              <span className="period-badge">{period.label}</span>
              <span className="meta-item">
                {period.reportKind === "CONSOLIDATED" ? "Hợp nhất" : "Riêng lẻ"} ·{" "}
                {period.auditStatus === "AUDITED"
                  ? "Kiểm toán"
                  : period.auditStatus === "REVIEWED"
                  ? "Soát xét"
                  : "Chưa kiểm toán"}{" "}
                · Đơn vị: {period.currency}
              </span>
              {period.restated && <span className="restated-badge">Điều chỉnh (Restated)</span>}
            </div>
          ) : (
            <p className="unavailable-msg">Chưa có báo cáo tài chính được ghi nhận.</p>
          )}
        </div>

        <span className={`status-pill ${fundamentals.meta.dataStatus.toLowerCase()}`}>
          {dataStatusLabel(fundamentals.meta.dataStatus)}
        </span>
      </header>

      {fundamentals.meta.reasonCodes.length > 0 && (
        <p className="reason-codes" role="note">
          <ReasonCodes prefix="Ghi chú: " codes={fundamentals.meta.reasonCodes} />
        </p>
      )}

      {/* Khối Chỉ Số Cốt Lõi (Key Financial Highlights) */}
      {keyHighlights.length > 0 && !searchQuery && (
        <div className="key-highlights-container">
          <div className="highlights-caption">
            <Sparkles size={13} className="text-cyan-400" />
            <span>CHỈ SỐ TÀI CHÍNH CỐT LÕI</span>
          </div>
          <div className="highlights-grid">
            {keyHighlights.map(({ label, metric }) => (
              <div key={metric.metricCode} className="highlight-cell">
                <span className="hl-label">{label}</span>
                <span className="hl-value font-mono">
                  {metric.applicability === "DEFINED" ? (
                    formatFundamentalValue(metric.value, metric.unit)
                  ) : (
                    <ApplicabilityNote applicability={metric.applicability} reasonCode={metric.reasonCode} />
                  )}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Thanh Điều Hướng Sub-tabs & Tìm Kiếm */}
      <div className="fundamentals-filter-bar">
        <div className="fundamentals-subtabs" role="tablist" aria-label="Nhóm chỉ số tài chính">
          <button
            type="button"
            role="tab"
            aria-selected={activeCategory === "ALL"}
            className={`subtab-pill ${activeCategory === "ALL" && !searchQuery ? "active" : ""}`}
            onClick={() => {
              setActiveCategory("ALL");
              setSearchQuery("");
            }}
          >
            <span>Tất cả</span>
            <span className="pill-count font-mono">{fundamentals.metrics.length}</span>
          </button>
          {allGroups.map((group) => {
            const isActive = activeCategory === group.id && !searchQuery;
            return (
              <button
                key={group.id}
                type="button"
                role="tab"
                aria-selected={isActive}
                className={`subtab-pill ${isActive ? "active" : ""}`}
                onClick={() => {
                  setActiveCategory(group.id);
                  setSearchQuery("");
                }}
              >
                <span>{group.shortTitle}</span>
                <span className="pill-count font-mono">{group.metrics.length}</span>
              </button>
            );
          })}
        </div>

        <div className="fundamentals-search-box">
          <Search size={13} className="search-icon text-slate-400" />
          <input
            type="text"
            placeholder="Lọc chỉ số (ROE, EPS, Nợ...)..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="search-input font-mono"
            aria-label="Lọc nhanh chỉ số"
          />
          {searchQuery && (
            <button
              type="button"
              className="clear-search-btn"
              onClick={() => setSearchQuery("")}
              aria-label="Xóa bộ lọc tìm kiếm"
            >
              ×
            </button>
          )}
        </div>
      </div>

      {/* Vùng Danh Sách Chỉ Số Cuộn Nội Bộ Cân Bằng Chiều Cao */}
      <div className="fundamentals-scroll-area">
        {visibleGroups.length === 0 ? (
          <div className="empty-metrics-msg">
            Không tìm thấy chỉ số nào khớp với từ khóa "{searchQuery}".
          </div>
        ) : (
          visibleGroups.map((group) => (
            <div key={group.title} className="fundamental-group">
              <h3 className="fundamental-group-title">
                <span>{group.title}</span>
                <span className="group-count-badge font-mono">{group.metrics.length} chỉ số</span>
              </h3>
              <ul className="fundamental-grid">
                {group.metrics.map((metric) => (
                  <li key={metric.metricCode} className={`fundamental-card ${metric.applicability.toLowerCase()}`}>
                    <p className="metric-name">{fundamentalMetricLabel(metric.metricCode)}</p>
                    <p className="metric-value font-mono">
                      {metric.applicability === "DEFINED" ? (
                        formatFundamentalValue(metric.value, metric.unit)
                      ) : (
                        <ApplicabilityNote applicability={metric.applicability} reasonCode={metric.reasonCode} />
                      )}
                    </p>
                  </li>
                ))}
              </ul>
            </div>
          ))
        )}
      </div>
    </section>
  );
}
