import { useEffect, useState } from "react";
import {
  createPortfolio,
  deletePortfolio,
  listPortfolios,
  PortfolioApiError,
  type PortfolioSummary,
} from "../api/portfolio";
import { navigate } from "../../../router";
import { RotateCw, Plus } from "lucide-react";

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
    <div className="portfolio-list-container">
      <header className="page-header" style={{ marginBottom: "24px" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
          <div>
            <button type="button" className="back-link" onClick={() => navigate("/")}>
              ← Trang chủ
            </button>
            <h1 id="portfolio-page-heading" style={{ fontSize: "1.75rem", fontWeight: 700, margin: "8px 0" }}>
              Quản lý danh mục đầu tư
            </h1>
            <p style={{ color: "var(--text-secondary)", margin: 0 }}>
              Theo dõi số dư, giao dịch và hiệu quả danh mục theo phương pháp FIFO
            </p>
          </div>
          <button
            type="button"
            onClick={() => setReloadCount((c) => c + 1)}
            title="Làm mới danh sách"
            style={{
              padding: "8px 14px",
              background: "var(--bg-card)",
              border: "1px solid var(--border-color)",
              color: "var(--text-secondary)",
              borderRadius: "6px",
              cursor: "pointer",
              display: "inline-flex",
              alignItems: "center",
              gap: "6px",
            }}
          >
            <RotateCw size={13} />
            <span>Làm mới</span>
          </button>
        </div>
      </header>

      {error && (
        <div role="alert" className="error-banner" style={{ marginBottom: "16px", padding: "12px", background: "var(--color-down-bg)", border: "1px solid var(--color-down-border)", borderRadius: "6px", color: "var(--color-down)" }}>
          {error}
        </div>
      )}

      {/* Create form */}
      <div className="card" style={{ padding: "20px", background: "var(--bg-card)", border: "1px solid var(--border-color)", borderRadius: "8px", marginBottom: "24px" }}>
        <h2 style={{ fontSize: "1.1rem", fontWeight: 600, marginBottom: "12px" }}>Tạo danh mục mới</h2>
        <form onSubmit={handleCreate} style={{ display: "flex", gap: "12px", alignItems: "center" }}>
          <input
            type="text"
            placeholder="Nhập tên danh mục (VD: Đầu tư dài hạn, Lướt sóng...)"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            maxLength={120}
            required
            aria-label="Tên danh mục mới"
            style={{
              flex: 1,
              padding: "10px 14px",
              background: "var(--bg-main)",
              border: "1px solid var(--border-color)",
              borderRadius: "6px",
              color: "var(--text-primary)",
            }}
          />
          <button
            type="submit"
            disabled={creating || !newName.trim()}
            style={{
              padding: "10px 20px",
              background: "var(--color-accent)",
              color: "#0a0e17",
              fontWeight: 600,
              border: "none",
              borderRadius: "6px",
              cursor: "pointer",
              display: "inline-flex",
              alignItems: "center",
              gap: "6px",
            }}
          >
            <Plus size={15} />
            <span>{creating ? "Đang tạo..." : "Tạo danh mục"}</span>
          </button>
        </form>
      </div>

      {/* Portfolio items */}
      {loading ? (
        <div style={{ padding: "32px", textAlign: "center", color: "var(--text-secondary)" }}>
          Đang tải danh mục...
        </div>
      ) : portfolios.length === 0 ? (
        <div style={{ padding: "32px", textAlign: "center", background: "var(--bg-card)", border: "1px solid var(--border-color)", borderRadius: "8px" }}>
          <p style={{ color: "var(--text-secondary)", marginBottom: "8px" }}>Bạn chưa có danh mục đầu tư nào.</p>
          <p style={{ fontSize: "0.9rem", color: "var(--text-muted)", margin: 0 }}>Hãy tạo danh mục đầu tiên ở biểu mẫu phía trên để bắt đầu ghi nhận giao dịch.</p>
        </div>
      ) : (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(320px, 1fr))", gap: "16px" }}>
          {portfolios.map((portfolio) => {
            const unplNum = parseFloat(portfolio.totalUnrealizedPL || "0");
            const isPos = unplNum > 0;
            const isNeg = unplNum < 0;
            const sign = isPos ? "+" : "";

            return (
              <div
                key={portfolio.id}
                data-testid={`portfolio-card-${portfolio.id}`}
                className="portfolio-card card"
                style={{
                  padding: "20px",
                  background: "var(--bg-card)",
                  border: "1px solid var(--border-color)",
                  borderRadius: "8px",
                  display: "flex",
                  flexDirection: "column",
                  justifyContent: "space-between",
                }}
              >
                <div>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: "12px" }}>
                    <h3 style={{ fontSize: "1.2rem", fontWeight: 700, margin: 0 }}>{portfolio.name}</h3>
                    <button
                      type="button"
                      onClick={() => handleDelete(portfolio.id, portfolio.name)}
                      aria-label={`Xóa danh mục ${portfolio.name}`}
                      style={{ background: "transparent", border: "none", color: "var(--text-muted)", cursor: "pointer", fontSize: "0.85rem" }}
                    >
                      Xóa
                    </button>
                  </div>

                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px", marginBottom: "16px" }}>
                    <div>
                      <span style={{ fontSize: "0.8rem", color: "var(--text-muted)", display: "block" }}>Tổng tài sản</span>
                      <strong style={{ fontSize: "1.1rem" }}>{Number(portfolio.totalValue).toLocaleString("vi-VN")} đ</strong>
                    </div>
                    <div>
                      <span style={{ fontSize: "0.8rem", color: "var(--text-muted)", display: "block" }}>Tiền mặt</span>
                      <span style={{ fontSize: "1.1rem" }}>{Number(portfolio.cashBalance).toLocaleString("vi-VN")} đ</span>
                    </div>
                    <div>
                      <span style={{ fontSize: "0.8rem", color: "var(--text-muted)", display: "block" }}>Lãi/Lỗ chưa thực hiện</span>
                      <span
                        style={{
                          fontWeight: 600,
                          color: isPos ? "var(--color-up)" : isNeg ? "var(--color-down)" : "inherit",
                        }}
                      >
                        {sign}{Number(portfolio.totalUnrealizedPL).toLocaleString("vi-VN")} đ
                        <span aria-label={isPos ? "lãi" : isNeg ? "lỗ" : "hòa vốn"} style={{ marginLeft: "4px", fontSize: "0.75rem" }}>
                          {isPos ? "(+)" : isNeg ? "(-)" : ""}
                        </span>
                      </span>
                    </div>
                    <div>
                      <span style={{ fontSize: "0.8rem", color: "var(--text-muted)", display: "block" }}>Lãi/Lỗ đã thực hiện</span>
                      <span>{Number(portfolio.totalRealizedPL).toLocaleString("vi-VN")} đ</span>
                    </div>
                  </div>
                </div>

                <button
                  type="button"
                  onClick={() => navigate(`/portfolios/${portfolio.id}`)}
                  style={{
                    width: "100%",
                    padding: "10px",
                    background: "var(--border-color)",
                    color: "var(--text-primary)",
                    fontWeight: 600,
                    border: "none",
                    borderRadius: "6px",
                    cursor: "pointer",
                    textAlign: "center",
                  }}
                >
                  Xem chi tiết & Giao dịch →
                </button>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
