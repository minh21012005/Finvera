import React, { useEffect, useState, useCallback } from "react";
import {
  NewsArticle,
  NewsCategory,
  Sentiment,
  listNewsArticles,
  deleteNewsArticle,
} from "../api/news";

interface NewsListProps {
  refreshTrigger?: number;
}

export const NewsList: React.FC<NewsListProps> = ({ refreshTrigger = 0 }) => {
  const [articles, setArticles] = useState<NewsArticle[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [symbolFilter, setSymbolFilter] = useState("");
  const [categoryFilter, setCategoryFilter] = useState<NewsCategory | "">("");
  const [sentimentFilter, setSentimentFilter] = useState<Sentiment | "">("");

  // Deletion state
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const fetchArticles = useCallback(async () => {
    setError(null);
    try {
      const res = await listNewsArticles({
        symbol: symbolFilter.trim() ? symbolFilter.trim().toUpperCase() : undefined,
        category: categoryFilter ? categoryFilter : undefined,
        sentiment: sentimentFilter ? sentimentFilter : undefined,
        limit: 50,
        offset: 0,
      });
      setArticles(res.items);
      setTotalCount(res.totalCount);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Không thể tải danh sách tin tức");
    } finally {
      setLoading(false);
    }
  }, [symbolFilter, categoryFilter, sentimentFilter]);

  useEffect(() => {
    let ignore = false;
    listNewsArticles({
      symbol: symbolFilter.trim() ? symbolFilter.trim().toUpperCase() : undefined,
      category: categoryFilter ? categoryFilter : undefined,
      sentiment: sentimentFilter ? sentimentFilter : undefined,
      limit: 50,
      offset: 0,
    })
      .then((res) => {
        if (!ignore) {
          setArticles(res.items);
          setTotalCount(res.totalCount);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (!ignore) {
          setError(err instanceof Error ? err.message : "Không thể tải danh sách tin tức");
          setLoading(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, [symbolFilter, categoryFilter, sentimentFilter, refreshTrigger]);

  const handleDelete = async (id: string, title: string) => {
    if (!window.confirm(`Bạn có chắc chắn muốn xóa bài báo "${title}" cùng toàn bộ vector trích dẫn?`)) {
      return;
    }

    setDeletingId(id);
    try {
      await deleteNewsArticle(id);
      setArticles((prev) => prev.filter((a) => a.id !== id));
      setTotalCount((prev) => Math.max(0, prev - 1));
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : "Lỗi khi xóa bài báo");
    } finally {
      setDeletingId(null);
    }
  };

  const renderCategoryBadge = (cat?: NewsCategory | null, app?: string) => {
    if (app === "MISSING" || !cat) {
      return (
        <span className="px-2 py-0.5 rounded text-[11px] font-medium bg-slate-800 text-slate-400 border border-slate-700">
          Chưa phân loại
        </span>
      );
    }
    const map: Record<NewsCategory, { label: string; bg: string }> = {
      COMPANY: { label: "Doanh nghiệp", bg: "bg-blue-500/10 text-blue-400 border-blue-500/20" },
      SECTOR: { label: "Ngành", bg: "bg-purple-500/10 text-purple-400 border-purple-500/20" },
      MARKET: { label: "Thị trường", bg: "bg-amber-500/10 text-amber-400 border-amber-500/20" },
      MACRO: { label: "Vĩ mô", bg: "bg-indigo-500/10 text-indigo-400 border-indigo-500/20" },
      REGULATION: { label: "Quy định", bg: "bg-rose-500/10 text-rose-400 border-rose-500/20" },
    };
    const c = map[cat] || { label: cat, bg: "bg-slate-800 text-slate-300 border-slate-700" };
    return (
      <span className={`px-2 py-0.5 rounded text-[11px] font-medium border ${c.bg}`}>
        {c.label}
      </span>
    );
  };

  const renderSentimentBadge = (sentiment?: Sentiment | null, app?: string) => {
    if (app === "MISSING" || !sentiment) {
      return (
        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-medium bg-slate-800 text-slate-400 border border-slate-700">
          <span>—</span>
          <span>Chưa xác định</span>
        </span>
      );
    }
    if (sentiment === "POSITIVE") {
      return (
        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
          <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6" />
          </svg>
          <span>Tích cực</span>
        </span>
      );
    }
    if (sentiment === "NEGATIVE") {
      return (
        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-semibold bg-rose-500/10 text-rose-400 border border-rose-500/20">
          <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M13 17h8m0 0v-8m0 8l-8-8-4 4-6-6" />
          </svg>
          <span>Tiêu cực</span>
        </span>
      );
    }
    return (
      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-medium bg-slate-800 text-slate-300 border border-slate-700">
        <span>—</span>
        <span>Trung tính</span>
      </span>
    );
  };

  const renderImpactBadge = (impact?: string | null, app?: string) => {
    if (app === "MISSING" || !impact) return null;
    const map: Record<string, { label: string; color: string }> = {
      HIGH: { label: "Tác động lớn", color: "bg-amber-500/10 text-amber-300 border-amber-500/20" },
      MEDIUM: { label: "Tác động vừa", color: "bg-blue-500/10 text-blue-300 border-blue-500/20" },
      LOW: { label: "Tác động nhẹ", color: "bg-slate-800 text-slate-400 border-slate-700" },
    };
    const imp = map[impact] || { label: impact, color: "bg-slate-800 text-slate-400 border-slate-700" };
    return (
      <span className={`px-2 py-0.5 rounded text-[11px] font-medium border ${imp.color}`}>
        {imp.label}
      </span>
    );
  };

  const renderIngestionStatus = (status: string, reason?: string | null) => {
    if (status === "READY") {
      return (
        <span className="inline-flex items-center gap-1 text-xs text-emerald-400 font-medium">
          <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <span>Sẵn sàng</span>
        </span>
      );
    }
    if (status === "FAILED") {
      return (
        <span
          className="inline-flex items-center gap-1 text-xs text-rose-400 font-medium cursor-help"
          title={reason || "Lỗi xử lý"}
        >
          <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
          <span>Thất bại</span>
        </span>
      );
    }
    return (
      <span className="inline-flex items-center gap-1.5 text-xs text-amber-400 font-medium">
        <svg className="w-3.5 h-3.5 animate-spin" viewBox="0 0 24 24">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
        </svg>
        <span>Đang phân tích...</span>
      </span>
    );
  };

  return (
    <div className="space-y-4">
      {/* Filter toolbar */}
      <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-4 backdrop-blur-md flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-3 flex-1">
          {/* Symbol Filter */}
          <div className="relative min-w-[140px]">
            <input
              type="text"
              placeholder="Lọc mã CP (FPT...)"
              value={symbolFilter}
              onChange={(e) => setSymbolFilter(e.target.value)}
              className="w-full bg-slate-950/80 border border-slate-800 rounded-lg pl-8 pr-3 py-1.5 text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-blue-500 uppercase font-mono"
            />
            <svg className="w-3.5 h-3.5 text-slate-500 absolute left-2.5 top-2.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
          </div>

          {/* Category Filter */}
          <select
            value={categoryFilter}
            onChange={(e) => setCategoryFilter(e.target.value as NewsCategory | "")}
            className="bg-slate-950/80 border border-slate-800 rounded-lg px-3 py-1.5 text-xs text-slate-200 focus:outline-none focus:border-blue-500"
          >
            <option value="">Tất cả danh mục</option>
            <option value="COMPANY">Doanh nghiệp</option>
            <option value="SECTOR">Ngành</option>
            <option value="MARKET">Thị trường</option>
            <option value="MACRO">Vĩ mô</option>
            <option value="REGULATION">Quy định pháp lý</option>
          </select>

          {/* Sentiment Filter */}
          <select
            value={sentimentFilter}
            onChange={(e) => setSentimentFilter(e.target.value as Sentiment | "")}
            className="bg-slate-950/80 border border-slate-800 rounded-lg px-3 py-1.5 text-xs text-slate-200 focus:outline-none focus:border-blue-500"
          >
            <option value="">Tất cả cảm xúc</option>
            <option value="POSITIVE">Tích cực</option>
            <option value="NEUTRAL">Trung tính</option>
            <option value="NEGATIVE">Tiêu cực</option>
          </select>
        </div>

        <div className="flex items-center gap-2">
          <span className="text-xs text-slate-400">
            Tổng cộng: <strong className="text-slate-200">{totalCount}</strong> bài báo
          </span>
          <button
            onClick={fetchArticles}
            disabled={loading}
            className="p-1.5 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg border border-slate-700 transition-colors cursor-pointer"
            title="Làm mới"
          >
            <svg className={`w-4 h-4 ${loading ? "animate-spin text-blue-400" : ""}`} fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
          </button>
        </div>
      </div>

      {error && (
        <div
          role="alert"
          className="p-4 bg-red-950/40 border border-red-800/50 rounded-xl text-sm text-red-300 flex items-center gap-2"
        >
          <svg className="w-4 h-4 text-red-400 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
          <span>{error}</span>
        </div>
      )}

      {/* Article list */}
      {loading && articles.length === 0 ? (
        <div className="p-12 text-center text-slate-400 flex flex-col items-center gap-3">
          <svg className="w-8 h-8 animate-spin text-blue-500" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
          </svg>
          <p className="text-sm">Đang tải danh sách tin tức...</p>
        </div>
      ) : articles.length === 0 ? (
        <div className="p-12 text-center text-slate-400 bg-slate-900/30 border border-slate-800/60 rounded-xl">
          <p className="text-sm">Chưa có bài báo hoặc tin tức nào phù hợp với bộ lọc.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-3.5">
          {articles.map((item) => (
            <div
              key={item.id}
              className="bg-slate-900/60 border border-slate-800 hover:border-slate-700/80 rounded-xl p-4.5 transition-all backdrop-blur-sm shadow-sm hover:shadow-md"
            >
              <div className="flex items-start justify-between gap-4">
                <div className="space-y-2 flex-1">
                  {/* Badges & Meta */}
                  <div className="flex flex-wrap items-center gap-2 text-xs">
                    {item.symbol && (
                      <span className="px-2 py-0.5 rounded font-mono font-bold bg-blue-500/20 text-blue-300 border border-blue-500/30">
                        {item.symbol}
                      </span>
                    )}
                    {renderCategoryBadge(item.category, item.categoryApplicability)}
                    {renderSentimentBadge(item.sentiment, item.sentimentApplicability)}
                    {renderImpactBadge(item.impactLevel, item.impactApplicability)}
                    {item.sector && (
                      <span className="px-2 py-0.5 rounded text-[11px] font-medium bg-slate-800/80 text-slate-300 border border-slate-700">
                        {item.sector}
                      </span>
                    )}
                  </div>

                  {/* Title & Link */}
                  <h3 className="text-base font-semibold text-slate-100 hover:text-blue-300 transition-colors leading-snug">
                    {item.title}
                  </h3>

                  {/* Publisher / Date Info */}
                  <div className="flex flex-wrap items-center gap-3 text-xs text-slate-400">
                    <span className="flex items-center gap-1 text-slate-300">
                      <svg className="w-3.5 h-3.5 text-slate-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
                      </svg>
                      {item.source}
                    </span>
                    <span>•</span>
                    <span className="flex items-center gap-1">
                      <svg className="w-3.5 h-3.5 text-slate-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                      </svg>
                      {new Date(item.publishedAt).toLocaleString("vi-VN", {
                        day: "2-digit",
                        month: "2-digit",
                        year: "numeric",
                        hour: "2-digit",
                        minute: "2-digit",
                      })}
                    </span>
                    {item.sourceUrl && (
                      <>
                        <span>•</span>
                        <a
                          href={item.sourceUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="inline-flex items-center gap-1 text-blue-400 hover:text-blue-300 transition-colors"
                        >
                          <span>Xem bài viết gốc</span>
                          <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
                          </svg>
                        </a>
                      </>
                    )}
                  </div>
                </div>

                {/* Status and Action */}
                <div className="flex flex-col items-end gap-3 shrink-0">
                  {renderIngestionStatus(item.ingestionStatus, item.ingestionFailureReason)}

                  <button
                    onClick={() => handleDelete(item.id, item.title)}
                    disabled={deletingId === item.id}
                    className="p-1.5 text-slate-500 hover:text-rose-400 hover:bg-rose-500/10 rounded-lg border border-transparent hover:border-rose-500/20 transition-all cursor-pointer"
                    title="Xóa bài báo"
                  >
                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                    </svg>
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
