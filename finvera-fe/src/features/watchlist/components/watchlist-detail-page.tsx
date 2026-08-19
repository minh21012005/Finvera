import { useEffect, useState } from "react";
import {
  addWatchlistItem,
  deleteWatchlist,
  getWatchlist,
  removeWatchlistItem,
  renameWatchlist,
  WatchlistApiError,
  type WatchlistDetail,
} from "../api/watchlist";
import { WatchlistItemTable } from "./watchlist-item-table";
import { navigate } from "../../../router";

interface WatchlistDetailPageProps {
  watchlistId: string;
}

export function WatchlistDetailPage({ watchlistId }: WatchlistDetailPageProps) {
  const [watchlist, setWatchlist] = useState<WatchlistDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [newSymbol, setNewSymbol] = useState("");
  const [adding, setAdding] = useState(false);
  const [isEditingName, setIsEditingName] = useState(false);
  const [editedName, setEditedName] = useState("");
  const [reloadCount, setReloadCount] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    getWatchlist(watchlistId, controller.signal)
      .then((data) => {
        setWatchlist(data);
        setEditedName(data.name);
        setError(null);
      })
      .catch(() => {
        if (controller.signal.aborted) return;
        setError("Không thể tải danh sách theo dõi.");
      })
      .finally(() => {
        if (controller.signal.aborted) return;
        setLoading(false);
      });
    return () => controller.abort();
  }, [watchlistId, reloadCount]);

  async function handleAddSymbol(e: React.FormEvent) {
    e.preventDefault();
    if (!newSymbol.trim()) return;

    setAdding(true);
    setError(null);
    try {
      const updated = await addWatchlistItem(watchlistId, {
        symbol: newSymbol.trim().toUpperCase(),
      });
      setWatchlist(updated);
      setNewSymbol("");
    } catch (err) {
      if (err instanceof WatchlistApiError && err.reasonCode === "UNSUPPORTED_INSTRUMENT") {
        setError(`Mã cổ phiếu "${newSymbol.toUpperCase()}" không nằm trong vũ trụ cổ phiếu được hỗ trợ.`);
      } else {
        setError("Không thể thêm mã cổ phiếu lúc này.");
      }
    } finally {
      setAdding(false);
    }
  }

  async function handleRemoveSymbol(symbol: string) {
    try {
      await removeWatchlistItem(watchlistId, symbol);
      setReloadCount((c) => c + 1);
    } catch {
      alert("Không thể xóa mã khỏi danh sách.");
    }
  }

  async function handleRename(e: React.FormEvent) {
    e.preventDefault();
    if (!editedName.trim() || editedName.trim() === watchlist?.name) {
      setIsEditingName(false);
      return;
    }

    try {
      const updated = await renameWatchlist(watchlistId, { name: editedName.trim() });
      if (watchlist) {
        setWatchlist({ ...watchlist, name: updated.name });
      }
      setIsEditingName(false);
    } catch (err) {
      if (err instanceof WatchlistApiError && err.reasonCode === "DUPLICATE_WATCHLIST_NAME") {
        setError("Tên danh sách theo dõi đã tồn tại.");
      } else {
        setError("Không thể đổi tên danh sách theo dõi.");
      }
    }
  }

  async function handleDelete() {
    if (!watchlist) return;
    if (!window.confirm(`Bạn có chắc chắn muốn xóa danh sách "${watchlist.name}"?`)) {
      return;
    }
    try {
      await deleteWatchlist(watchlistId);
      navigate("/watchlists");
    } catch {
      alert("Không thể xóa danh sách theo dõi.");
    }
  }

  if (loading) {
    return (
      <div style={{ padding: "40px", textAlign: "center", color: "var(--text-secondary)" }}>
        Đang tải dữ liệu danh sách theo dõi…
      </div>
    );
  }

  if (error && !watchlist) {
    return (
      <div style={{ padding: "40px", textAlign: "center" }}>
        <p style={{ color: "var(--color-down)", marginBottom: "16px" }}>{error}</p>
        <button
          type="button"
          onClick={() => navigate("/watchlists")}
          style={{ padding: "8px 16px", background: "var(--bg-card)", border: "1px solid var(--border-color)", borderRadius: "6px", cursor: "pointer" }}
        >
          ← Quay lại danh sách
        </button>
      </div>
    );
  }

  return (
    <div className="watchlist-detail-container">
      {/* Header */}
      <header className="page-header" style={{ marginBottom: "24px", display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
        <div>
          <button
            type="button"
            className="back-link"
            onClick={() => navigate("/watchlists")}
            style={{ background: "transparent", border: "none", color: "var(--text-secondary)", cursor: "pointer", padding: 0, marginBottom: "8px" }}
          >
            ← Danh sách theo dõi
          </button>

          {isEditingName ? (
            <form onSubmit={handleRename} style={{ display: "flex", gap: "8px", alignItems: "center" }}>
              <input
                type="text"
                value={editedName}
                onChange={(e) => setEditedName(e.target.value)}
                maxLength={120}
                required
                aria-label="Đổi tên danh sách"
                style={{
                  fontSize: "1.5rem",
                  fontWeight: 700,
                  padding: "4px 8px",
                  background: "var(--bg-main)",
                  border: "1px solid var(--border-color)",
                  borderRadius: "6px",
                  color: "var(--text-primary)",
                }}
              />
              <button
                type="submit"
                style={{ padding: "6px 12px", background: "var(--color-accent)", color: "#0a0e17", fontWeight: 600, border: "none", borderRadius: "6px", cursor: "pointer" }}
              >
                Lưu
              </button>
              <button
                type="button"
                onClick={() => {
                  setIsEditingName(false);
                  setEditedName(watchlist?.name ?? "");
                }}
                style={{ padding: "6px 12px", background: "var(--bg-card)", border: "1px solid var(--border-color)", color: "var(--text-secondary)", borderRadius: "6px", cursor: "pointer" }}
              >
                Hủy
              </button>
            </form>
          ) : (
            <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
              <h1 id="watchlist-detail-heading" style={{ fontSize: "1.75rem", fontWeight: 700, margin: 0 }}>
                {watchlist?.name}
              </h1>
              <button
                type="button"
                onClick={() => setIsEditingName(true)}
                aria-label="Đổi tên"
                style={{ background: "transparent", border: "none", color: "var(--text-muted)", cursor: "pointer", fontSize: "0.85rem" }}
              >
                ✎ Đổi tên
              </button>
            </div>
          )}
        </div>

        <div style={{ display: "flex", gap: "8px" }}>
          <button
            type="button"
            onClick={() => setReloadCount((c) => c + 1)}
            title="Làm mới dữ liệu"
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
          <button
            type="button"
            onClick={handleDelete}
            style={{
              padding: "8px 14px",
              background: "transparent",
              border: "1px solid var(--color-down-border)",
              color: "var(--color-down)",
              borderRadius: "6px",
              cursor: "pointer",
            }}
          >
            Xóa danh sách
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

      {/* Add symbol form */}
      <div className="card" style={{ padding: "16px 20px", background: "var(--bg-card)", border: "1px solid var(--border-color)", borderRadius: "8px", marginBottom: "24px" }}>
        <form onSubmit={handleAddSymbol} style={{ display: "flex", gap: "12px", alignItems: "center" }}>
          <input
            type="text"
            placeholder="Nhập mã cổ phiếu (VD: FPT, VNM, HPG...)"
            value={newSymbol}
            onChange={(e) => setNewSymbol(e.target.value)}
            pattern="^[a-zA-Z0-9]{3,10}$"
            required
            aria-label="Mã cổ phiếu cần thêm"
            style={{
              width: "300px",
              padding: "8px 12px",
              background: "var(--bg-main)",
              border: "1px solid var(--border-color)",
              borderRadius: "6px",
              color: "var(--text-primary)",
              textTransform: "uppercase",
            }}
          />
          <button
            type="submit"
            disabled={adding || !newSymbol.trim()}
            style={{
              padding: "8px 16px",
              background: "var(--color-accent)",
              color: "#0a0e17",
              fontWeight: 600,
              border: "none",
              borderRadius: "6px",
              cursor: "pointer",
            }}
          >
            {adding ? "Đang thêm..." : "+ Thêm vào danh sách"}
          </button>
        </form>
      </div>

      {/* Items table */}
      <WatchlistItemTable
        items={watchlist?.items ?? []}
        onRemove={handleRemoveSymbol}
      />
    </div>
  );
}
