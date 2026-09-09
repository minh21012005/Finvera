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
      <div className="portfolio-loading-state">
        <span className="text-slate-400 font-mono text-sm">Đang tải dữ liệu danh sách theo dõi…</span>
      </div>
    );
  }

  if (error && !watchlist) {
    return (
      <div className="portfolio-error-state">
        <p className="error-text">{error}</p>
        <button
          type="button"
          onClick={() => navigate("/watchlists")}
          className="btn-back-portfolios"
        >
          ← Quay lại danh sách
        </button>
      </div>
    );
  }

  return (
    <main className="app-shell quant-terminal-layout watchlist-detail-layout">
      {/* Header */}
      <header className="page-header watchlist-detail-header">
        <div>
          <button
            type="button"
            className="back-link"
            onClick={() => navigate("/watchlists")}
          >
            ← Danh sách theo dõi
          </button>

          {isEditingName ? (
            <form onSubmit={handleRename} className="watchlist-rename-form">
              <input
                type="text"
                value={editedName}
                onChange={(e) => setEditedName(e.target.value)}
                maxLength={120}
                required
                aria-label="Đổi tên danh sách"
                className="watchlist-rename-input font-mono"
              />
              <button
                type="submit"
                className="btn-rename-save"
              >
                Lưu
              </button>
              <button
                type="button"
                onClick={() => {
                  setIsEditingName(false);
                  setEditedName(watchlist?.name ?? "");
                }}
                className="btn-rename-cancel"
              >
                Hủy
              </button>
            </form>
          ) : (
            <div className="watchlist-title-row">
              <h1 id="watchlist-detail-heading" className="watchlist-detail-title">
                {watchlist?.name}
              </h1>
              <button
                type="button"
                onClick={() => setIsEditingName(true)}
                aria-label="Đổi tên"
                className="btn-trigger-rename"
              >
                ✎ Đổi tên
              </button>
            </div>
          )}
          <p className="watchlist-meta-sub font-mono text-xs">
            Theo dõi tín hiệu kỹ thuật thời gian thực & biến động giá cổ phiếu
          </p>
        </div>

        <div className="watchlist-header-actions">
          <button
            type="button"
            onClick={() => setReloadCount((c) => c + 1)}
            title="Làm mới dữ liệu"
            className="btn-wl-refresh"
          >
            ↻ Làm mới
          </button>
          <button
            type="button"
            onClick={handleDelete}
            className="btn-wl-delete"
          >
            Xóa danh sách
          </button>
        </div>
      </header>

      {error && (
        <div role="alert" className="ledger-error-banner">
          {error}
        </div>
      )}

      {/* Add symbol form */}
      <div className="watchlist-add-bar quant-terminal-card">
        <form onSubmit={handleAddSymbol} className="watchlist-add-form">
          <div className="watchlist-add-input-wrap">
            <span className="add-symbol-icon text-slate-500 font-mono text-xs">MÃ CP:</span>
            <input
              type="text"
              placeholder="Nhập mã cổ phiếu (VD: FPT, VNM, HPG...)"
              value={newSymbol}
              onChange={(e) => setNewSymbol(e.target.value)}
              pattern="^[a-zA-Z0-9]{3,10}$"
              required
              aria-label="Mã cổ phiếu cần thêm"
              className="watchlist-add-input font-mono uppercase"
            />
          </div>
          <button
            type="submit"
            disabled={adding || !newSymbol.trim()}
            className="btn-add-symbol-submit"
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
    </main>
  );
}
