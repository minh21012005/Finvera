import { useState } from "react";
import {
  DocumentType,
  generateIdempotencyKey,
  submitDocument,
  SubmitDocumentParams,
  Document,
} from "../api/documents";
import { UploadCloud, FileText, FileCode, AlertTriangle } from "lucide-react";

interface DocumentUploadProps {
  onDocumentSubmitted: (doc: Document) => void;
}

export function DocumentUpload({ onDocumentSubmitted }: DocumentUploadProps) {
  const [uploadMode, setUploadMode] = useState<"file" | "text">("file");
  const [title, setTitle] = useState("");
  const [symbol, setSymbol] = useState("");
  const [documentType, setDocumentType] = useState<DocumentType>("ANNUAL_REPORT");
  const [year, setYear] = useState<number>(new Date().getFullYear());
  const [quarter, setQuarter] = useState<string>("");
  const [source, setSource] = useState("");
  const [publicationDate, setPublicationDate] = useState<string>(
    new Date().toISOString().split("T")[0],
  );
  const [file, setFile] = useState<File | null>(null);
  const [pastedText, setPastedText] = useState("");

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [idempotencyKey, setIdempotencyKey] = useState<string>(() => generateIdempotencyKey());

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    if (!title.trim()) {
      setError("Vui lòng nhập tiêu đề tài liệu.");
      return;
    }
    if (!source.trim()) {
      setError("Vui lòng nhập nguồn phát hành tài liệu.");
      return;
    }
    if (!publicationDate) {
      setError("Vui lòng chọn ngày công bố.");
      return;
    }

    if (uploadMode === "file" && !file) {
      setError("Vui lòng chọn file PDF để tải lên.");
      return;
    }
    if (uploadMode === "text" && !pastedText.trim()) {
      setError("Vui lòng nhập nội dung văn bản.");
      return;
    }

    setLoading(true);

    const params: SubmitDocumentParams = {
      title: title.trim(),
      symbol: symbol.trim() ? symbol.trim().toUpperCase() : undefined,
      documentType,
      year: Number(year),
      quarter: quarter ? Number(quarter) : undefined,
      source: source.trim(),
      publicationDate,
      file: uploadMode === "file" ? file : null,
      pastedText: uploadMode === "text" ? pastedText.trim() : undefined,
    };

    try {
      const result = await submitDocument(params, idempotencyKey);
      onDocumentSubmitted(result);

      setTitle("");
      setSymbol("");
      setQuarter("");
      setFile(null);
      setPastedText("");
      setIdempotencyKey(generateIdempotencyKey());
    } catch (err: unknown) {
      const errorObj = err as { response?: { reasonCode?: string; detail?: string }; message?: string };
      if (errorObj?.response?.reasonCode === "DUPLICATE_SUBMISSION") {
        setError("Yêu cầu tải lên này đã được tiếp nhận trước đó (Duplicate Submission).");
      } else {
        setError(errorObj?.response?.detail || errorObj?.message || "Không thể tải lên tài liệu lúc này.");
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 shadow-sm">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-5 pb-4 border-b border-slate-800/80">
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-lg bg-cyan-500/10 text-cyan-400 border border-cyan-500/20">
            <UploadCloud size={20} />
          </div>
          <div>
            <h3 className="text-base font-semibold text-slate-100">Tải lên Tài liệu Nghiên cứu</h3>
            <p className="text-xs text-slate-400">
              Trích xuất văn bản tự động, lập chỉ mục vector và trích dẫn theo số trang.
            </p>
          </div>
        </div>

        <div className="inline-flex rounded-lg bg-slate-950 p-1 border border-slate-800 text-xs self-start sm:self-auto">
          <button
            type="button"
            className={`px-3 py-1.5 rounded-md flex items-center gap-1.5 transition-colors ${
              uploadMode === "file" ? "bg-cyan-600 text-white font-medium shadow-sm" : "text-slate-400 hover:text-slate-200"
            }`}
            onClick={() => setUploadMode("file")}
          >
            <FileText size={13} />
            <span>File PDF</span>
          </button>
          <button
            type="button"
            className={`px-3 py-1.5 rounded-md flex items-center gap-1.5 transition-colors ${
              uploadMode === "text" ? "bg-cyan-600 text-white font-medium shadow-sm" : "text-slate-400 hover:text-slate-200"
            }`}
            onClick={() => setUploadMode("text")}
          >
            <FileCode size={13} />
            <span>Dán Văn bản</span>
          </button>
        </div>
      </div>

      {error && (
        <div className="mb-4 p-3 bg-rose-500/10 border border-rose-500/30 rounded-lg text-sm text-rose-400 flex items-center gap-2">
          <AlertTriangle size={16} className="shrink-0" />
          <span>{error}</span>
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-xs font-medium text-slate-300 mb-1.5">
            Tiêu đề Tài liệu <span className="text-rose-400">*</span>
          </label>
          <input
            type="text"
            className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3.5 py-2 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-cyan-500 focus:ring-1 focus:ring-cyan-500"
            placeholder="vd: Báo cáo thường niên 2025 - CTCP FPT"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            disabled={loading}
          />
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <div>
            <label className="block text-xs font-medium text-slate-300 mb-1.5">Mã Cổ phiếu (Tùy chọn)</label>
            <input
              type="text"
              className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-100 uppercase placeholder-slate-500 focus:outline-none focus:border-cyan-500"
              placeholder="FPT"
              value={symbol}
              onChange={(e) => setSymbol(e.target.value)}
              disabled={loading}
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-300 mb-1.5">
              Loại Tài liệu <span className="text-rose-400">*</span>
            </label>
            <select
              className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-100 focus:outline-none focus:border-cyan-500 cursor-pointer"
              value={documentType}
              onChange={(e) => setDocumentType(e.target.value as DocumentType)}
              disabled={loading}
            >
              <option value="ANNUAL_REPORT">Báo cáo Thường niên</option>
              <option value="QUARTERLY_REPORT">Báo cáo Quý</option>
              <option value="FINANCIAL_REPORT">Báo cáo Tài chính</option>
              <option value="ECONOMIC_REPORT">Báo cáo Kinh tế/Vĩ mô</option>
              <option value="INVESTOR_PRESENTATION">Tài liệu Thuyết trình NĐT</option>
              <option value="CORPORATE_DISCLOSURE">Công bố Thông tin</option>
              <option value="OTHER">Khác</option>
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-300 mb-1.5">Năm</label>
            <input
              type="number"
              className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-100 focus:outline-none focus:border-cyan-500"
              value={year}
              onChange={(e) => setYear(Number(e.target.value))}
              disabled={loading}
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-300 mb-1.5">Quý</label>
            <select
              className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-100 focus:outline-none focus:border-cyan-500 cursor-pointer"
              value={quarter}
              onChange={(e) => setQuarter(e.target.value)}
              disabled={loading}
            >
              <option value="">Cả năm / Không quý</option>
              <option value="1">Quý 1 (Q1)</option>
              <option value="2">Quý 2 (Q2)</option>
              <option value="3">Quý 3 (Q3)</option>
              <option value="4">Quý 4 (Q4)</option>
            </select>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label className="block text-xs font-medium text-slate-300 mb-1.5">
              Nguồn phát hành <span className="text-rose-400">*</span>
            </label>
            <input
              type="text"
              className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-cyan-500"
              placeholder="vd: FPT IR / CTCK SSI"
              value={source}
              onChange={(e) => setSource(e.target.value)}
              disabled={loading}
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-300 mb-1.5">
              Ngày công bố <span className="text-rose-400">*</span>
            </label>
            <input
              type="date"
              className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-100 focus:outline-none focus:border-cyan-500"
              value={publicationDate}
              onChange={(e) => setPublicationDate(e.target.value)}
              disabled={loading}
            />
          </div>
        </div>

        {uploadMode === "file" ? (
          <div>
            <label className="block text-xs font-medium text-slate-300 mb-1.5">
              Chọn File PDF <span className="text-rose-400">*</span>
            </label>
            <input
              type="file"
              accept=".pdf,application/pdf"
              className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-sm text-slate-300 file:mr-4 file:py-1.5 file:px-3.5 file:rounded-md file:border-0 file:text-xs file:font-semibold file:bg-slate-800 file:text-slate-200 hover:file:bg-slate-700 cursor-pointer"
              onChange={(e) => setFile(e.target.files?.[0] || null)}
              disabled={loading}
            />
          </div>
        ) : (
          <div>
            <label className="block text-xs font-medium text-slate-300 mb-1.5">
              Nội dung văn bản <span className="text-rose-400">*</span>
            </label>
            <textarea
              rows={5}
              className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-cyan-500"
              placeholder="Dán nội dung văn bản báo cáo vào đây..."
              value={pastedText}
              onChange={(e) => setPastedText(e.target.value)}
              disabled={loading}
            />
          </div>
        )}

        <div className="flex justify-end pt-2">
          <button
            type="submit"
            disabled={loading}
            className="px-5 py-2.5 bg-gradient-to-r from-cyan-600 to-blue-600 hover:from-cyan-500 hover:to-blue-500 text-white font-semibold text-sm rounded-lg shadow-md transition-all flex items-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
          >
            {loading ? (
              <>
                <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></div>
                <span>Đang xử lý...</span>
              </>
            ) : (
              <>
                <UploadCloud size={16} />
                <span>Tải lên & Bắt đầu Xử lý</span>
              </>
            )}
          </button>
        </div>
      </form>
    </div>
  );
}
