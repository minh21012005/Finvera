import { useState } from "react";
import { Document, DocumentType, deleteDocument } from "../api/documents";
import { Files, RotateCw } from "lucide-react";

interface DocumentListProps {
  documents: Document[];
  loading: boolean;
  onDocumentDeleted: (id: string) => void;
  onRefresh: () => void;
  symbolFilter: string;
  onSymbolFilterChange: (val: string) => void;
  typeFilter: string;
  onTypeFilterChange: (val: string) => void;
}

export function DocumentList({
  documents,
  loading,
  onDocumentDeleted,
  onRefresh,
  symbolFilter,
  onSymbolFilterChange,
  typeFilter,
  onTypeFilterChange,
}: DocumentListProps) {
  const [deletingId, setDeletingId] = useState<string | null>(null);

  async function handleDelete(doc: Document) {
    if (!window.confirm(`Bạn có chắc chắn muốn xóa tài liệu "${doc.title}"?`)) {
      return;
    }
    setDeletingId(doc.id);
    try {
      await deleteDocument(doc.id);
      onDocumentDeleted(doc.id);
    } catch {
      alert("Không thể xóa tài liệu lúc này. Vui lòng thử lại.");
    } finally {
      setDeletingId(null);
    }
  }

  function renderStatusBadge(status: string, failureReason?: string | null) {
    switch (status) {
      case "READY":
        return (
          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/30">
            ● Sẵn sàng (READY)
          </span>
        );
      case "PROCESSING":
        return (
          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amber-500/10 text-amber-400 border border-amber-500/30">
            ⏳ Đang xử lý (PROCESSING)
          </span>
        );
      case "FAILED":
        return (
          <span
            className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-rose-500/10 text-rose-400 border border-rose-500/30"
            title={failureReason || "Lỗi xử lý"}
          >
            ✕ Thất bại (FAILED)
          </span>
        );
      case "PENDING":
      default:
        return (
          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-slate-500/10 text-slate-400 border border-slate-500/30">
            ⏱ Chờ xử lý (PENDING)
          </span>
        );
    }
  }

  function formatType(type: DocumentType) {
    switch (type) {
      case "ANNUAL_REPORT":
        return "Báo cáo Thường niên";
      case "QUARTERLY_REPORT":
        return "Báo cáo Quý";
      case "FINANCIAL_REPORT":
        return "Báo cáo Tài chính";
      case "ECONOMIC_REPORT":
        return "Báo cáo Kinh tế";
      case "INVESTOR_PRESENTATION":
        return "Thuyết trình NĐT";
      case "CORPORATE_DISCLOSURE":
        return "Công bố Thông tin";
      default:
        return "Khác";
    }
  }

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 shadow-sm">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-4 pb-4 border-b border-slate-800/80">
        <h3 className="text-base font-semibold text-slate-100 flex items-center gap-2.5">
          <Files size={18} className="text-cyan-400" />
          <span>Kho Tài liệu Đã Tải lên</span>
          <span className="text-xs px-2.5 py-0.5 rounded-full bg-slate-800 font-mono text-cyan-300 font-semibold">
            {documents.length}
          </span>
        </h3>

        <div className="flex items-center gap-2.5 flex-wrap">
          <div className="relative">
            <input
              type="text"
              className="bg-slate-950 border border-slate-800 rounded-lg pl-3 pr-3 py-1.5 text-xs text-slate-100 placeholder-slate-500 uppercase focus:outline-none focus:border-cyan-500 w-32"
              placeholder="Mã CP (FPT...)"
              value={symbolFilter}
              onChange={(e) => onSymbolFilterChange(e.target.value)}
            />
          </div>
          <select
            className="bg-slate-950 border border-slate-800 rounded-lg px-3 py-1.5 text-xs text-slate-100 focus:outline-none focus:border-cyan-500 cursor-pointer"
            value={typeFilter}
            onChange={(e) => onTypeFilterChange(e.target.value)}
          >
            <option value="">Tất cả loại tài liệu</option>
            <option value="ANNUAL_REPORT">BCTN</option>
            <option value="QUARTERLY_REPORT">Báo cáo Quý</option>
            <option value="FINANCIAL_REPORT">BCTC</option>
            <option value="ECONOMIC_REPORT">Vĩ mô</option>
            <option value="INVESTOR_PRESENTATION">Thuyết trình</option>
            <option value="CORPORATE_DISCLOSURE">CBTT</option>
          </select>
          <button
            type="button"
            className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs rounded-lg transition-colors flex items-center gap-1.5 cursor-pointer font-medium"
            onClick={onRefresh}
          >
            <RotateCw size={13} />
            <span>Làm mới</span>
          </button>
        </div>
      </div>

      {loading ? (
        <div className="py-12 text-center text-slate-500 text-sm">Đang tải danh sách tài liệu...</div>
      ) : documents.length === 0 ? (
        <div className="py-12 text-center text-slate-500 text-sm">
          Chưa có tài liệu nào trong kho lưu trữ của bạn.
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs border-collapse">
            <thead>
              <tr className="border-b border-slate-800 text-slate-400">
                <th className="py-3 px-3">Tiêu đề & Nguồn</th>
                <th className="py-3 px-3">Mã CP</th>
                <th className="py-3 px-3">Loại / Thời kỳ</th>
                <th className="py-3 px-3">Ngày công bố</th>
                <th className="py-3 px-3">Trạng thái</th>
                <th className="py-3 px-3 text-right">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {documents.map((doc) => (
                <tr key={doc.id} className="hover:bg-slate-800/30 transition-colors">
                  <td className="py-3 px-3">
                    <div className="font-medium text-slate-200">{doc.title}</div>
                    <div className="text-slate-500 text-[11px]">{doc.source}</div>
                    {doc.ingestionFailureReason && (
                      <div className="text-rose-400 text-[11px] mt-0.5">
                        Lỗi: {doc.ingestionFailureReason}
                      </div>
                    )}
                  </td>
                  <td className="py-3 px-3">
                    {doc.symbol ? (
                      <span className="font-semibold text-cyan-400">{doc.symbol}</span>
                    ) : (
                      <span className="text-slate-600">--</span>
                    )}
                  </td>
                  <td className="py-3 px-3">
                    <div className="text-slate-300">{formatType(doc.documentType)}</div>
                    <div className="text-slate-500 text-[11px]">
                      {doc.quarter ? `Q${doc.quarter}/` : ""}
                      {doc.year}
                    </div>
                  </td>
                  <td className="py-3 px-3 text-slate-400">{doc.publicationDate}</td>
                  <td className="py-3 px-3">
                    {renderStatusBadge(doc.ingestionStatus, doc.ingestionFailureReason)}
                  </td>
                  <td className="py-3 px-3 text-right">
                    <button
                      type="button"
                      className="px-2 py-1 text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 rounded transition-colors disabled:opacity-50"
                      disabled={deletingId === doc.id}
                      onClick={() => handleDelete(doc)}
                    >
                      {deletingId === doc.id ? "Đang xóa..." : "Xóa"}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
