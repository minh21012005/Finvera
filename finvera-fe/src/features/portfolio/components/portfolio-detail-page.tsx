import { useEffect, useState } from "react";
import {
  getPortfolio,
  getPositions,
  listTransactions,
  type PortfolioSummary,
  type Position,
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
      <div style={{ padding: "40px", textAlign: "center", color: "var(--text-secondary)" }}>
        Đang tải dữ liệu danh mục…
      </div>
    );
  }

  if (error && !portfolio) {
    return (
      <div style={{ padding: "40px", textAlign: "center" }}>
        <p style={{ color: "var(--color-down)", marginBottom: "16px" }}>{error}</p>
        <button
          type="button"
          onClick={() => navigate("/portfolios")}
          style={{ padding: "8px 16px", background: "var(--bg-card)", border: "1px solid var(--border-color)", borderRadius: "6px", cursor: "pointer" }}
        >
          ← Quay lại danh sách danh mục
        </button>
      </div>
    );
  }

  return (
    <div className="portfolio-detail-container" style={{ maxWidth: "1200px", margin: "0 auto" }}>
      {/* Header */}
      <header className="page-header" style={{ marginBottom: "24px", display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
        <div>
          <button
            type="button"
            className="back-link"
            onClick={() => navigate("/portfolios")}
            style={{ background: "transparent", border: "none", color: "var(--text-secondary)", cursor: "pointer", padding: 0, marginBottom: "8px" }}
          >
            ← Danh sách danh mục
          </button>
          <h1 id="portfolio-detail-heading" style={{ fontSize: "1.75rem", fontWeight: 700, margin: 0 }}>
            {portfolio?.name}
          </h1>
          <p style={{ color: "var(--text-secondary)", margin: "4px 0 0 0", fontSize: "0.9rem" }}>
            Khởi tạo: {portfolio ? new Date(portfolio.createdAt).toLocaleDateString("vi-VN") : "—"}
          </p>
        </div>

        <div style={{ display: "flex", gap: "12px", alignItems: "center" }}>
          <button
            type="button"
            onClick={() => setShowTxForm((v) => !v)}
            style={{
              padding: "10px 18px",
              background: showTxForm ? "var(--bg-card)" : "var(--color-accent)",
              color: showTxForm ? "var(--text-primary)" : "#0a0e17",
              fontWeight: 700,
              border: "none",
              borderRadius: "6px",
              cursor: "pointer",
            }}
          >
            {showTxForm ? "Đóng biểu mẫu" : "+ Ghi nhận giao dịch"}
          </button>
          <button
            type="button"
            onClick={() => setReloadCount((c) => c + 1)}
            title="Làm mới dữ liệu"
            style={{
              padding: "10px 14px",
              background: "var(--bg-card)",
              border: "1px solid var(--border-color)",
              color: "var(--text-secondary)",
              borderRadius: "6px",
              cursor: "pointer",
            }}
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
      <div style={{ display: "flex", gap: "8px", borderBottom: "1px solid var(--border-color)", marginBottom: "24px" }}>
        <button
          type="button"
          onClick={() => setActiveTab("holdings")}
          style={{
            padding: "10px 20px",
            background: "transparent",
            border: "none",
            borderBottom: activeTab === "holdings" ? "2px solid var(--color-accent)" : "2px solid transparent",
            color: activeTab === "holdings" ? "var(--text-primary)" : "var(--text-secondary)",
            fontWeight: activeTab === "holdings" ? 700 : 500,
            cursor: "pointer",
          }}
        >
          Danh mục nắm giữ & Sổ cái
        </button>
        <button
          type="button"
          onClick={() => setActiveTab("analytics")}
          style={{
            padding: "10px 20px",
            background: "transparent",
            border: "none",
            borderBottom: activeTab === "analytics" ? "2px solid var(--color-accent)" : "2px solid transparent",
            color: activeTab === "analytics" ? "var(--text-primary)" : "var(--text-secondary)",
            fontWeight: activeTab === "analytics" ? 700 : 500,
            cursor: "pointer",
          }}
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
