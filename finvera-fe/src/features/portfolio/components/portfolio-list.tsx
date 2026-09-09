import { useEffect, useState } from "react";
import {
  createPortfolio,
  deletePortfolio,
  listPortfolios,
  PortfolioApiError,
  type PortfolioSummary,
} from "../api/portfolio";
import { navigate } from "../../../router";
import { RotateCw, Plus, Trash2, ArrowRight, Wallet } from "lucide-react";

import { ReasonCodes } from "../../../shared/components/reason-codes";
import { dataStatusLabel } from "../../../shared/format/reason-codes";

export function PortfolioList() {
  const [portfolios, setPortfolios] = useState<PortfolioSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [newName, setNewName] = useState("");
  const [creating, setCreating] = useState(false);
  const [reloadCount, setReloadCount] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    listPortfolios(controller.signal)
      .then((data) => {
        setPortfolios(data);
        setError(null);
      })
      .catch(() => {
        if (controller.signal.aborted) return;
        setError("Không thể tải danh sách danh mục. Vui lòng thử lại.");
      })
      .finally(() => {
        if (controller.signal.aborted) return;
        setLoading(false);
      });
    return () => controller.abort();
  }, [reloadCount]);

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    if (!newName.trim()) return;

    setCreating(true);
    setError(null);
    try {
      const created = await createPortfolio({ name: newName.trim() });
      setPortfolios((prev) => [...prev, created]);
      setNewName("");
    } catch (err) {
      if (err instanceof PortfolioApiError && err.reasonCode === "DUPLICATE_PORTFOLIO_NAME") {
        setError("Tên danh mục đã tồn tại. Vui lòng chọn tên khác.");
      } else {
        setError("Không thể tạo danh mục lúc này.");
      }
    } finally {
      setCreating(false);
    }
  }

  async function handleDelete(id: string, name: string) {
    if (!window.confirm(`Bạn có chắc chắn muốn xóa danh mục "${name}"?`)) {
      return;
    }
    try {
      await deletePortfolio(id);
      setPortfolios((prev) => prev.filter((p) => p.id !== id));
    } catch {
      alert("Không thể xóa danh mục.");
    }
  }

  return (
    <main className="app-shell quant-terminal-layout portfolio-list-wrapper">
      <header className="page-header portfolio-list-header">
        <div className="portfolio-header-left">
          <button type="button" className="back-link" onClick={() => navigate("/")}>
            ← Trang chủ
          </button>
          <p className="eyebrow">FINVERA · PORTFOLIO ASSET MANAGEMENT</p>
          <h1 id="portfolio-page-heading">
            Quản lý danh mục đầu tư
          </h1>
          <p className="portfolio-header-sub">
            Theo dõi số dư, giao dịch và hiệu quả danh mục theo phương pháp FIFO
          </p>
        </div>

        <button
          type="button"
          onClick={() => setReloadCount((c) => c + 1)}
          title="Làm mới danh sách"
          className="btn-engine-rescan"
        >
          <RotateCw size={13} />
          <span>Làm mới</span>
        </button>
      </header>

      {error && (
        <div role="alert" className="error-banner">
          {error}
        </div>
      )}

      {/* Create Portfolio Card */}
      <section className="portfolio-create-card" aria-labelledby="create-portfolio-heading">
        <div className="create-card-header">
          <Wallet size={16} className="text-cyan-400" />
          <h2 id="create-portfolio-heading">Tạo danh mục mới</h2>
        </div>
        <form onSubmit={handleCreate} className="portfolio-create-form">
          <input
            type="text"
            placeholder="Nhập tên danh mục (VD: Đầu tư dài hạn, Lướt sóng...)"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            maxLength={120}
            required
            aria-label="Tên danh mục mới"
            className="portfolio-name-input font-mono"
          />
          <button
            type="submit"
            disabled={creating || !newName.trim()}
            className="btn-quant-execute"
          >
            <Plus size={15} />
            <span>{creating ? "Đang tạo…" : "Tạo danh mục"}</span>
          </button>
        </form>
      </section>

      {/* Portfolio Items Grid */}
      {loading ? (
        <div className="portfolio-loading-state font-mono">
          Đang tải danh mục…
        </div>
      ) : portfolios.length === 0 ? (
        <div className="portfolio-empty-state">
          <p className="empty-title">Bạn chưa có danh mục đầu tư nào.</p>
          <p className="empty-sub">Hãy tạo danh mục đầu tiên ở biểu mẫu phía trên để bắt đầu ghi nhận giao dịch.</p>
        </div>
      ) : (
        <div className="portfolio-cards-grid">
          {portfolios.map((portfolio) => {
            const unplNum = parseFloat(portfolio.totalUnrealizedPL || "0");
            const isPos = unplNum > 0;
            const isNeg = unplNum < 0;
            const sign = isPos ? "+" : "";

            return (
              <article
                key={portfolio.id}
                data-testid={`portfolio-card-${portfolio.id}`}
                className="quant-portfolio-tile"
              >
                <div className="tile-top-row">
                  <h3 className="tile-portfolio-name">{portfolio.name}</h3>
                  <button
                    type="button"
                    onClick={() => handleDelete(portfolio.id, portfolio.name)}
                    aria-label={`Xóa danh mục ${portfolio.name}`}
                    className="btn-tile-delete"
                    title="Xóa danh mục này"
                  >
                    <Trash2 size={13} />
                    <span>Xóa</span>
                  </button>
                </div>

                <div className="tile-metrics-grid">
                  <div className="tile-metric-cell">
                    <span className="metric-lbl">Tổng tài sản</span>
                    <strong className="metric-val font-mono">
                      {Number(portfolio.totalValue).toLocaleString("vi-VN")} đ
                    </strong>
                    {portfolio.dataStatus && portfolio.dataStatus !== "CURRENT" && (
                      <span role="status" className="data-status-cue font-mono">
                        ⚠ {dataStatusLabel(portfolio.dataStatus)}
                        {portfolio.reasonCodes && portfolio.reasonCodes.length > 0 ? (
                          <> — <ReasonCodes codes={portfolio.reasonCodes} /></>
                        ) : null}
                      </span>
                    )}
                  </div>

                  <div className="tile-metric-cell">
                    <span className="metric-lbl">Tiền mặt</span>
                    <span className="metric-val font-mono text-cyan-400">
                      {Number(portfolio.cashBalance).toLocaleString("vi-VN")} đ
                    </span>
                  </div>

                  <div className="tile-metric-cell">
                    <span className="metric-lbl">Lãi/Lỗ chưa thực hiện</span>
                    <span
                      className={`metric-val font-mono ${
                        isPos ? "text-emerald-400 font-bold" : isNeg ? "text-rose-400 font-bold" : "text-slate-200"
                      }`}
                    >
                      {sign}{Number(portfolio.totalUnrealizedPL).toLocaleString("vi-VN")} đ
                      <span aria-label={isPos ? "lãi" : isNeg ? "lỗ" : "hòa vốn"} className="pl-sign-tag">
                        {isPos ? "(+)" : isNeg ? "(-)" : ""}
                      </span>
                    </span>
                  </div>

                  <div className="tile-metric-cell">
                    <span className="metric-lbl">Lãi/Lỗ đã thực hiện</span>
                    <span className="metric-val font-mono text-slate-300">
                      {Number(portfolio.totalRealizedPL).toLocaleString("vi-VN")} đ
                    </span>
                  </div>
                </div>

                <button
                  type="button"
                  onClick={() => navigate(`/portfolios/${portfolio.id}`)}
                  className="btn-tile-navigate"
                >
                  <span>Xem chi tiết & Giao dịch</span>
                  <ArrowRight size={14} />
                </button>
              </article>
            );
          })}
        </div>
      )}
    </main>
  );
}
