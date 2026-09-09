import { useEffect, useState } from "react";
import {
  getPortfolio,
  getPositions,
  listTransactions,
  type PortfolioSummary,
  type Position,
  type PositionsResponse,
  type Transaction,
} from "../api/portfolio";
import { HoldingsTable } from "./holdings-table";
import { TransactionForm } from "./transaction-form";
import { TransactionLedger } from "./transaction-ledger";
import { PortfolioAnalyticsView } from "./portfolio-analytics-view";
import { navigate } from "../../../router";

interface PortfolioDetailPageProps {
  portfolioId: string;
}

export function PortfolioDetailPage({ portfolioId }: PortfolioDetailPageProps) {
  const [portfolio, setPortfolio] = useState<PortfolioSummary | null>(null);
  const [positions, setPositions] = useState<Position[]>([]);
  const [cashBalance, setCashBalance] = useState("0");
  const [totalValue, setTotalValue] = useState("0");
  const [positionsStatus, setPositionsStatus] = useState<PositionsResponse["dataStatus"]>("CURRENT");
  const [positionsReasons, setPositionsReasons] = useState<string[]>([]);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showTxForm, setShowTxForm] = useState(false);
  const [activeTab, setActiveTab] = useState<"holdings" | "analytics">("holdings");
  const [reloadCount, setReloadCount] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([
      getPortfolio(portfolioId, controller.signal),
      getPositions(portfolioId, controller.signal),
      listTransactions(portfolioId, 200, 0, controller.signal),
    ])
      .then(([pfSummary, posData, txPage]) => {
        setPortfolio(pfSummary);
        setPositions(posData.positions);
        setCashBalance(posData.cashBalance);
        setTotalValue(posData.totalValue);
        setPositionsStatus(posData.dataStatus);
        setPositionsReasons(posData.reasonCodes ?? []);
        setTransactions(txPage.items);
        setError(null);
      })
      .catch(() => {
        if (controller.signal.aborted) return;
        setError("Không thể tải dữ liệu danh mục đầu tư.");
      })
      .finally(() => {
        if (controller.signal.aborted) return;
        setLoading(false);
      });
    return () => controller.abort();
  }, [portfolioId, reloadCount]);

  function handleTransactionSuccess() {
    setReloadCount((c) => c + 1);
  }

  function handleVoidSuccess() {
    setReloadCount((c) => c + 1);
  }

  if (loading) {
    return (
      <div className="portfolio-loading-state">
        <span className="text-slate-400 font-mono text-sm">Đang tải dữ liệu danh mục…</span>
      </div>
    );
  }

  if (error && !portfolio) {
    return (
      <div className="portfolio-error-state">
        <p className="error-text">{error}</p>
        <button
          type="button"
          onClick={() => navigate("/portfolios")}
          className="btn-back-portfolios"
        >
          ← Quay lại danh sách danh mục
        </button>
      </div>
    );
  }

  return (
    <div className="app-shell quant-terminal-layout portfolio-page-layout">
      {/* Header */}
      <header className="page-header portfolio-detail-header">
        <div>
          <button
            type="button"
            className="back-link"
            onClick={() => navigate("/portfolios")}
          >
            ← Danh sách danh mục
          </button>
          <div className="portfolio-title-group">
            <h1 id="portfolio-detail-heading">
              {portfolio?.name}
            </h1>
          </div>
          <p className="portfolio-meta-sub">
            Khởi tạo: {portfolio ? new Date(portfolio.createdAt).toLocaleDateString("vi-VN") : "—"} • Định giá danh mục đa tài sản thời gian thực
          </p>
        </div>

        <div className="portfolio-header-actions">
          <button
            type="button"
            onClick={() => setShowTxForm((v) => !v)}
            className={`btn-tx-toggle ${showTxForm ? "active" : ""}`}
          >
            {showTxForm ? "Đóng biểu mẫu" : "+ Ghi nhận giao dịch"}
          </button>
          <button
            type="button"
            onClick={() => setReloadCount((c) => c + 1)}
            title="Làm mới dữ liệu"
            className="btn-port-refresh"
          >
            ↻
          </button>
        </div>
      </header>

      {/* Transaction Form Toggle */}
      {showTxForm && (
        <TransactionForm
          portfolioId={portfolioId}
          onSuccess={() => {
            setShowTxForm(false);
            handleTransactionSuccess();
          }}
        />
      )}

      {/* Tab bar */}
      <div className="portfolio-nav-tabs">
        <button
          type="button"
          onClick={() => setActiveTab("holdings")}
          className={`port-tab-btn ${activeTab === "holdings" ? "active" : ""}`}
        >
          Danh mục nắm giữ & Sổ cái
        </button>
        <button
          type="button"
          onClick={() => setActiveTab("analytics")}
          className={`port-tab-btn ${activeTab === "analytics" ? "active" : ""}`}
        >
          Phân tích & Hiệu quả đầu tư
        </button>
      </div>

      {activeTab === "holdings" ? (
        <>
          {/* Derived Holdings */}
          <HoldingsTable
            positions={positions}
            cashBalance={cashBalance}
            totalValue={totalValue}
            dataStatus={positionsStatus}
            reasonCodes={positionsReasons}
          />

          {/* Transaction Ledger */}
          <TransactionLedger
            portfolioId={portfolioId}
            transactions={transactions}
            onVoidSuccess={handleVoidSuccess}
          />
        </>
      ) : (
        <PortfolioAnalyticsView portfolioId={portfolioId} />
      )}
    </div>
  );
}
