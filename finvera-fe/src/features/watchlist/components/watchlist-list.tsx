import { useEffect, useState } from "react";
import {
  createWatchlist,
  deleteWatchlist,
  listWatchlists,
  WatchlistApiError,
  type WatchlistSummary,
} from "../api/watchlist";
import { navigate } from "../../../router";

export function WatchlistList() {
  const [watchlists, setWatchlists] = useState<WatchlistSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [newName, setNewName] = useState("");
  const [creating, setCreating] = useState(false);
  const [reloadCount, setReloadCount] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    listWatchlists(controller.signal)
      .then((data) => {
        setWatchlists(data);
        setError(null);
      })
      .catch(() => {
        if (controller.signal.aborted) return;
        setError("Không thể tải danh sách theo dõi. Vui lòng thử lại.");
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
      const created = await createWatchlist({ name: newName.trim() });
      setWatchlists((prev) => [...prev, created]);
      setNewName("");
    } catch (err) {
      if (err instanceof WatchlistApiError && err.reasonCode === "DUPLICATE_WATCHLIST_NAME") {
        setError("Tên danh sách theo dõi đã tồn tại. Vui lòng chọn tên khác.");
      } else {
        setError("Không thể tạo danh sách theo dõi lúc này.");
      }
    } finally {
      setCreating(false);
    }
  }

  async function handleDelete(id: string, name: string) {
    if (!window.confirm(`Bạn có chắc chắn muốn xóa danh sách theo dõi "${name}"?`)) {
      return;
    }
    try {
      await deleteWatchlist(id);
      setWatchlists((prev) => prev.filter((w) => w.id !== id));
    } catch {
      alert("Không thể xóa danh sách theo dõi.");
    }
  }

  return (
    <div className="watchlist-list-container">
      <header className="page-header" style={{ marginBottom: "24px" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
          <div>
            <button type="button" className="back-link" onClick={() => navigate("/")}>
              ← Trang chủ
            </button>
            <h1 id="watchlist-page-heading" style={{ fontSize: "1.75rem", fontWeight: 700, margin: "8px 0" }}>
              Danh sách theo dõi (Watchlist)
            </h1>
            <p style={{ color: "var(--text-secondary)", margin: 0 }}>
              Theo dõi và so sánh các ứng viên đầu tư với dữ liệu thị trường và tín hiệu trực tiếp
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
            }}
          >
            ↻ Làm mới
          </button>
        </div>
      </header>

      {error && (
        <div
          role="alert"
          style={{
            marginBottom: "16px",
            padding: "12px",
            background: "var(--color-down-bg)",
            border: "1px solid var(--color-down-border)",
            borderRadius: "6px",
            color: "var(--color-down)",
          }}
        >
          {error}
        </div>
      )}

      {/* Create form */}
      <div className="card" style={{ padding: "20px", background: "var(--bg-card)", border: "1px solid var(--border-color)", borderRadius: "8px", marginBottom: "24px" }}>
        <h2 style={{ fontSize: "1.1rem", fontWeight: 600, marginBottom: "12px" }}>Tạo danh sách theo dõi mới</h2>
        <form onSubmit={handleCreate} style={{ display: "flex", gap: "12px", alignItems: "center" }}>
          <input
            type="text"
            placeholder="Nhập tên danh sách (VD: Cổ phiếu Công nghệ, Ngân hàng tiềm năng...)"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            maxLength={120}
            required
            aria-label="Tên danh sách theo dõi mới"
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
            }}
          >
            {creating ? "Đang tạo..." : "Tạo danh sách"}
          </button>
        </form>
      </div>

      {/* Watchlist items */}
      {loading ? (
        <div style={{ padding: "32px", textAlign: "center", color: "var(--text-secondary)" }}>
          Đang tải danh sách theo dõi...
        </div>
      ) : watchlists.length === 0 ? (
        <div style={{ padding: "32px", textAlign: "center", background: "var(--bg-card)", border: "1px solid var(--border-color)", borderRadius: "8px" }}>
          <p style={{ color: "var(--text-secondary)", marginBottom: "8px" }}>Bạn chưa có danh sách theo dõi nào.</p>
          <p style={{ fontSize: "0.9rem", color: "var(--text-muted)", margin: 0 }}>Hãy tạo danh sách đầu tiên ở biểu mẫu phía trên để bắt đầu thêm cổ phiếu nghiên cứu.</p>
        </div>
      ) : (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(300px, 1fr))", gap: "16px" }}>
          {watchlists.map((wl) => (
            <div
              key={wl.id}
              data-testid={`watchlist-card-${wl.id}`}
              className="card"
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
                  <h3 style={{ fontSize: "1.2rem", fontWeight: 700, margin: 0 }}>{wl.name}</h3>
                  <button
                    type="button"
                    onClick={() => handleDelete(wl.id, wl.name)}
                    aria-label={`Xóa danh sách ${wl.name}`}
                    style={{ background: "transparent", border: "none", color: "var(--text-muted)", cursor: "pointer", fontSize: "0.85rem" }}
                  >
                    Xóa
                  </button>
                </div>
                <p style={{ fontSize: "0.9rem", color: "var(--text-secondary)", margin: "0 0 16px 0" }}>
                  Số mã cổ phiếu: <strong>{wl.itemCount}</strong>
                </p>
              </div>

              <button
                type="button"
                onClick={() => navigate(`/watchlists/${wl.id}`)}
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
                Mở danh sách theo dõi →
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
