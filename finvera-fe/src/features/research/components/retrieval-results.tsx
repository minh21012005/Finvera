import { useState } from "react";
import { DocumentType } from "../api/documents";
import { Passage, retrievePassages, RetrieveRequest } from "../api/retrieve";

export function RetrievalResults() {
  const [query, setQuery] = useState("");
  const [symbol, setSymbol] = useState("");
  const [documentType, setDocumentType] = useState<string>("");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [passages, setPassages] = useState<Passage[] | null>(null);
  const [searched, setSearched] = useState(false);

  async function handleSearch(e: React.FormEvent) {
    e.preventDefault();
    if (!query.trim()) return;

    setLoading(true);
    setError(null);

    const req: RetrieveRequest = {
      query: query.trim(),
      filters: {
        symbol: symbol.trim() ? symbol.trim().toUpperCase() : null,
        documentType: (documentType ? (documentType as DocumentType) : null),
        dateFrom: dateFrom || null,
        dateTo: dateTo || null,
      },
    };

    try {
      const response = await retrievePassages(req);
      setPassages(response.passages || []);
      setSearched(true);
    } catch (err: unknown) {
      const errorObj = err as { response?: { detail?: string }; message?: string };
      setError(errorObj?.response?.detail || errorObj?.message || "Không thể thực hiện truy vấn trích dẫn lúc này.");
      setPassages([]);
      setSearched(true);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 shadow-sm">
      <h3 className="text-lg font-semibold text-slate-100 mb-2">Truy vấn Trích dẫn từ Kho Dữ liệu (RAG)</h3>
      <p className="text-xs text-slate-400 mb-4">
        Tìm kiếm các đoạn văn và trích dẫn chuẩn xác từ các tài liệu và bài báo đã được tiếp nhận trong kho cá nhân của bạn.
      </p>

      <form onSubmit={handleSearch} className="space-y-4 mb-6">
        <div>
          <div className="flex gap-2">
            <input
              type="text"
              className="flex-1 bg-slate-950 border border-slate-800 rounded-lg px-4 py-2.5 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-cyan-500"
              placeholder="Nhập câu hỏi hoặc từ khóa nghiên cứu (vd: Doanh thu mảng xuất khẩu phần mềm năm 2025?)..."
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              disabled={loading}
            />
            <button
              type="submit"
              disabled={loading || !query.trim()}
              className="px-6 py-2.5 bg-cyan-600 hover:bg-cyan-500 text-white font-medium text-sm rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? "Đang tìm..." : "Tìm Trích dẫn"}
            </button>
          </div>
        </div>

        {/* Filters */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          <div>
            <label className="block text-[11px] font-medium text-slate-400 mb-1">Mã CP</label>
            <input
              type="text"
              className="w-full bg-slate-950 border border-slate-800 rounded-lg px-2.5 py-1.5 text-xs text-slate-100 uppercase focus:outline-none focus:border-cyan-500"
              placeholder="Tất cả"
              value={symbol}
              onChange={(e) => setSymbol(e.target.value)}
              disabled={loading}
            />
          </div>
          <div>
            <label className="block text-[11px] font-medium text-slate-400 mb-1">Loại Tài liệu</label>
            <select
              className="w-full bg-slate-950 border border-slate-800 rounded-lg px-2.5 py-1.5 text-xs text-slate-100 focus:outline-none focus:border-cyan-500"
              value={documentType}
              onChange={(e) => setDocumentType(e.target.value)}
              disabled={loading}
            >
              <option value="">Tất cả loại</option>
              <option value="ANNUAL_REPORT">Báo cáo Thường niên</option>
              <option value="QUARTERLY_REPORT">Báo cáo Quý</option>
              <option value="FINANCIAL_REPORT">BCTC</option>
              <option value="ECONOMIC_REPORT">Kinh tế/Vĩ mô</option>
              <option value="INVESTOR_PRESENTATION">Thuyết trình NĐT</option>
              <option value="CORPORATE_DISCLOSURE">Công bố TT</option>
            </select>
          </div>
          <div>
            <label className="block text-[11px] font-medium text-slate-400 mb-1">Từ ngày</label>
            <input
              type="date"
              className="w-full bg-slate-950 border border-slate-800 rounded-lg px-2.5 py-1.5 text-xs text-slate-100 focus:outline-none focus:border-cyan-500"
              value={dateFrom}
              onChange={(e) => setDateFrom(e.target.value)}
              disabled={loading}
            />
          </div>
          <div>
            <label className="block text-[11px] font-medium text-slate-400 mb-1">Đến ngày</label>
            <input
              type="date"
              className="w-full bg-slate-950 border border-slate-800 rounded-lg px-2.5 py-1.5 text-xs text-slate-100 focus:outline-none focus:border-cyan-500"
              value={dateTo}
              onChange={(e) => setDateTo(e.target.value)}
              disabled={loading}
            />
          </div>
        </div>
      </form>

      {error && (
        <div className="mb-6 p-3 bg-rose-500/10 border border-rose-500/30 rounded-lg text-sm text-rose-400">
          {error}
        </div>
      )}

      {/* Results Section */}
      {searched && (
        <div className="border-t border-slate-800 pt-6">
          <div className="flex items-center justify-between mb-4">
            <h4 className="text-sm font-semibold text-slate-200">
              Kết quả Đoạn Trích dẫn ({passages?.length || 0})
            </h4>
          </div>

          {passages && passages.length > 0 ? (
            <div className="space-y-4">
              {passages.map((passage, idx) => (
                <div
                  key={`${passage.sourceId}-${idx}`}
                  className="bg-slate-950 border border-slate-800/80 rounded-lg p-4 hover:border-slate-700 transition-colors"
                >
                  <div className="flex items-start justify-between gap-3 mb-2">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="px-2 py-0.5 rounded text-[11px] font-medium bg-cyan-500/10 text-cyan-400 border border-cyan-500/30">
                        {passage.sourceType === "DOCUMENT" ? "Tài liệu" : "Tin tức"}
                      </span>
                      <span className="text-xs font-semibold text-slate-200">{passage.sourceTitle}</span>
                      <span className="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-300 font-mono">
                        {passage.location}
                      </span>
                    </div>
                    <div className="text-[11px] text-slate-500 whitespace-nowrap">
                      Độ khớp: {(passage.score * 100).toFixed(1)}%
                    </div>
                  </div>

                  <blockquote className="text-xs text-slate-300 bg-slate-900/60 p-3 rounded border-l-2 border-cyan-500 leading-relaxed font-sans mb-2">
                    "{passage.excerpt}"
                  </blockquote>

                  <div className="flex items-center justify-between text-[11px] text-slate-500">
                    <div>Nguồn: {passage.source}</div>
                    {passage.publicationDate && <div>Ngày công bố: {passage.publicationDate}</div>}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="py-8 text-center text-slate-400 text-xs bg-slate-950 rounded-lg border border-dashed border-slate-800">
              Không tìm thấy đoạn trích dẫn nào phù hợp trong kho tài liệu với câu hỏi của bạn.
            </div>
          )}
        </div>
      )}
    </div>
  );
}
