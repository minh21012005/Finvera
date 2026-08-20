import React, { useState } from "react";
import { SubmitNewsArticleRequest, submitNewsArticle } from "../api/news";

interface NewsSubmitProps {
  onSubmitted?: () => void;
}

export const NewsSubmit: React.FC<NewsSubmitProps> = ({ onSubmitted }) => {
  const [title, setTitle] = useState("");
  const [source, setSource] = useState("");
  const [symbol, setSymbol] = useState("");
  const [publishedAt, setPublishedAt] = useState(new Date().toISOString().slice(0, 16));
  const [referenceUrl, setReferenceUrl] = useState("");
  const [body, setBody] = useState("");

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !source.trim() || !body.trim()) {
      setError("Vui lòng điền đầy đủ tiêu đề, nguồn và nội dung bài viết.");
      return;
    }

    setLoading(true);
    setError(null);
    setSuccess(false);

    try {
      const payload: SubmitNewsArticleRequest = {
        title: title.trim(),
        source: source.trim(),
        symbol: symbol.trim() ? symbol.trim().toUpperCase() : undefined,
        publishedAt: new Date(publishedAt).toISOString(),
        referenceUrl: referenceUrl.trim() || undefined,
        body: body.trim(),
      };

      await submitNewsArticle(payload);
      setSuccess(true);
      setTitle("");
      setSource("");
      setSymbol("");
      setReferenceUrl("");
      setBody("");
      if (onSubmitted) {
        onSubmitted();
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Không thể gửi bài báo");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-6 backdrop-blur-md shadow-xl">
      <div className="flex items-center gap-3 mb-5">
        <div className="p-2.5 bg-blue-500/10 text-blue-400 rounded-lg border border-blue-500/20">
          <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 20H5a2 2 0 01-2-2V6a2 2 0 012-2h10a2 2 0 012 2v1m2 13a2 2 0 01-2-2V7m2 13a2 2 0 002-2V9a2 2 0 00-2-2h-2m-4-3H9M7 16h6M7 8h6v4H7V8z" />
          </svg>
        </div>
        <div>
          <h2 className="text-lg font-semibold text-slate-100">Gửi Tin Tức & Bài Viết Mới</h2>
          <p className="text-xs text-slate-400">
            Hệ thống AI sẽ tự động phân loại danh mục, phân tích sắc thái cảm xúc và lập chỉ mục RAG.
          </p>
        </div>
      </div>

      {error && (
        <div
          role="alert"
          className="mb-4 p-3 bg-red-950/40 border border-red-800/50 rounded-lg flex items-center gap-2.5 text-sm text-red-300"
        >
          <svg className="w-4 h-4 text-red-400 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
          <span>{error}</span>
        </div>
      )}

      {success && (
        <div className="mb-4 p-3 bg-emerald-950/40 border border-emerald-800/50 rounded-lg flex items-center gap-2.5 text-sm text-emerald-300">
          <svg className="w-4 h-4 text-emerald-400 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
          </svg>
          <span>Đã gửi bài báo thành công! AI đang tiến hành phân loại và tạo vector embedding.</span>
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label htmlFor="news-title" className="block text-xs font-medium text-slate-300 mb-1">
            Tiêu đề bài báo <span className="text-red-400">*</span>
          </label>
          <input
            id="news-title"
            type="text"
            required
            maxLength={300}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="Ví dụ: FPT công bố lợi nhuận quý 4 tăng trưởng 25%..."
            className="w-full bg-slate-950/80 border border-slate-800 rounded-lg px-3.5 py-2 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-blue-500 transition-colors"
          />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div>
            <label htmlFor="news-source" className="block text-xs font-medium text-slate-300 mb-1">
              Nguồn / Nhà xuất bản <span className="text-red-400">*</span>
            </label>
            <input
              id="news-source"
              type="text"
              required
              maxLength={200}
              value={source}
              onChange={(e) => setSource(e.target.value)}
              placeholder="VnExpress, CafeF, Vietstock..."
              className="w-full bg-slate-950/80 border border-slate-800 rounded-lg px-3.5 py-2 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-blue-500 transition-colors"
            />
          </div>

          <div>
            <label htmlFor="news-symbol" className="block text-xs font-medium text-slate-300 mb-1">
              Mã cổ phiếu (tùy chọn)
            </label>
            <input
              id="news-symbol"
              type="text"
              maxLength={10}
              value={symbol}
              onChange={(e) => setSymbol(e.target.value.toUpperCase())}
              placeholder="FPT, VNM, HPG..."
              className="w-full bg-slate-950/80 border border-slate-800 rounded-lg px-3.5 py-2 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-blue-500 transition-colors uppercase font-mono"
            />
          </div>

          <div>
            <label htmlFor="news-date" className="block text-xs font-medium text-slate-300 mb-1">
              Thời gian xuất bản <span className="text-red-400">*</span>
            </label>
            <input
              id="news-date"
              type="datetime-local"
              required
              value={publishedAt}
              onChange={(e) => setPublishedAt(e.target.value)}
              className="w-full bg-slate-950/80 border border-slate-800 rounded-lg px-3.5 py-2 text-sm text-slate-100 focus:outline-none focus:border-blue-500 transition-colors"
            />
          </div>
        </div>

        <div>
          <label htmlFor="news-ref-url" className="block text-xs font-medium text-slate-300 mb-1">
            Liên kết nguồn (URL tùy chọn)
          </label>
          <input
            id="news-ref-url"
            type="url"
            value={referenceUrl}
            onChange={(e) => setReferenceUrl(e.target.value)}
            placeholder="https://..."
            className="w-full bg-slate-950/80 border border-slate-800 rounded-lg px-3.5 py-2 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-blue-500 transition-colors"
          />
        </div>

        <div>
          <label htmlFor="news-body" className="block text-xs font-medium text-slate-300 mb-1">
            Nội dung bài viết <span className="text-red-400">*</span>
          </label>
          <textarea
            id="news-body"
            required
            rows={5}
            value={body}
            onChange={(e) => setBody(e.target.value)}
            placeholder="Dán toàn bộ nội dung bài báo vào đây..."
            className="w-full bg-slate-950/80 border border-slate-800 rounded-lg px-3.5 py-2 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-blue-500 transition-colors resize-y leading-relaxed font-sans"
          />
        </div>

        <div className="flex justify-end pt-1">
          <button
            type="submit"
            disabled={loading || !title.trim() || !source.trim() || !body.trim()}
            className="inline-flex items-center gap-2 px-5 py-2.5 rounded-lg bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white font-medium text-sm shadow-lg shadow-blue-500/20 transition-all cursor-pointer"
          >
            {loading ? (
              <>
                <svg className="w-4 h-4 animate-spin" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                </svg>
                <span>Đang gửi bài viết...</span>
              </>
            ) : (
              <>
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
                </svg>
                <span>Gửi Bài Viết</span>
              </>
            )}
          </button>
        </div>
      </form>
    </div>
  );
};
