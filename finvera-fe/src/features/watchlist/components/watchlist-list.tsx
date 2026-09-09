import { useEffect, useState } from "react";
import {
  createWatchlist,
  deleteWatchlist,
  listWatchlists,
  WatchlistApiError,
  type WatchlistSummary,
} from "../api/watchlist";
import { navigate } from "../../../router";
import { RotateCw, Plus, Trash2, ArrowRight, BookmarkCheck, Eye } from "lucide-react";

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
    <main className="app-shell quant-terminal-layout portfolio-list-wrapper">
      <header className="page-header portfolio-list-header">
        <div className="portfolio-header-left">
          <button type="button" className="back-link" onClick={() => navigate("/")}>
            ← Trang chủ
          </button>
          <p className="eyebrow">FINVERA · WATCHLIST RESEARCH</p>
          <h1 id="watchlist-page-heading">
            Danh sách theo dõi (Watchlist)
          </h1>
          <p className="portfolio-header-sub">
            Theo dõi và so sánh các ứng viên đầu tư với dữ liệu thị trường và tín hiệu trực tiếp
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

      {/* Create Watchlist Card */}
      <section className="portfolio-create-card" aria-labelledby="create-watchlist-heading">
        <div className="create-card-header">
          <BookmarkCheck size={16} className="text-cyan-400" />
          <h2 id="create-watchlist-heading">Tạo danh sách theo dõi mới</h2>
        </div>
        <form onSubmit={handleCreate} className="portfolio-create-form">
          <input
            type="text"
            placeholder="Nhập tên danh sách theo dõi (VD: Cổ phiếu VN30, Ngành Thép, Bất động sản...)"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            maxLength={120}
            required
            aria-label="Tên danh sách theo dõi mới"
            className="portfolio-name-input font-mono"
          />
          <button
            type="submit"
            disabled={creating || !newName.trim()}
            className="btn-quant-execute"
          >
            <Plus size={15} />
            <span>{creating ? "Đang tạo…" : "Tạo danh sách"}</span>
          </button>
        </form>
      </section>

      {/* Watchlist Items Grid */}
      {loading ? (
        <div className="portfolio-loading-state font-mono">
          Đang tải danh sách theo dõi…
        </div>
      ) : watchlists.length === 0 ? (
        <div className="portfolio-empty-state">
          <p className="empty-title">Bạn chưa có danh sách theo dõi nào.</p>
          <p className="empty-sub">Hãy tạo danh sách đầu tiên ở biểu mẫu phía trên để bắt đầu thêm cổ phiếu nghiên cứu.</p>
        </div>
      ) : (
        <div className="portfolio-cards-grid">
          {watchlists.map((wl) => (
            <article
              key={wl.id}
              data-testid={`watchlist-card-${wl.id}`}
              className="quant-portfolio-tile"
            >
              <div>
                <div className="tile-top-row">
                  <h3 className="tile-portfolio-name">{wl.name}</h3>
                  <button
                    type="button"
                    onClick={() => handleDelete(wl.id, wl.name)}
                    aria-label={`Xóa danh sách ${wl.name}`}
                    className="btn-tile-delete"
                    title="Xóa danh sách theo dõi này"
                  >
                    <Trash2 size={13} />
                    <span>Xóa</span>
                  </button>
                </div>

                <div className="tile-metrics-grid single-col mb-5">
                  <div className="tile-metric-cell">
                    <span className="metric-lbl">Quy mô theo dõi</span>
                    <span className="metric-val font-mono flex items-center gap-1.5">
                      <Eye size={14} className="text-cyan-400" />
                      <strong>{wl.itemCount}</strong> <span className="text-slate-400 text-xs">mã cổ phiếu</span>
                    </span>
                  </div>
                </div>
              </div>

              <button
                type="button"
                onClick={() => navigate(`/watchlists/${wl.id}`)}
                className="btn-tile-navigate"
              >
                <span>Mở danh sách theo dõi</span>
                <ArrowRight size={14} />
              </button>
            </article>
          ))}
        </div>
      )}
    </main>
  );
}
