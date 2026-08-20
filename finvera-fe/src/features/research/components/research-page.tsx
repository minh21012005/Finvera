import { useEffect, useState } from "react";
import { Document, DocumentType, listDocuments } from "../api/documents";
import { AskPanel } from "./ask-panel";
import { DocumentList } from "./document-list";
import { DocumentUpload } from "./document-upload";
import { RetrievalResults } from "./retrieval-results";

export function ResearchPage() {
  const [activeTab, setActiveTab] = useState<"documents" | "retrieval" | "ask">("documents");
  const [documents, setDocuments] = useState<Document[]>([]);
  const [loading, setLoading] = useState(true);
  const [symbolFilter, setSymbolFilter] = useState("");
  const [typeFilter, setTypeFilter] = useState("");
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);

    listDocuments(
      {
        symbol: symbolFilter || undefined,
        documentType: (typeFilter ? (typeFilter as DocumentType) : undefined),
      },
      controller.signal,
    )
      .then((data) => {
        setDocuments(data.items || []);
      })
      .catch(() => {
        if (controller.signal.aborted) return;
        setDocuments([]);
      })
      .finally(() => {
        if (controller.signal.aborted) return;
        setLoading(false);
      });

    return () => controller.abort();
  }, [symbolFilter, typeFilter, reloadKey]);

  function handleDocumentSubmitted(newDoc: Document) {
    setDocuments((prev) => [newDoc, ...prev]);
  }

  function handleDocumentDeleted(id: string) {
    setDocuments((prev) => prev.filter((d) => d.id !== id));
  }

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 p-6 max-w-7xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-800 pb-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
            <span>Nghiên cứu & Dữ liệu RAG</span>
          </h1>
          <p className="text-xs text-slate-400 mt-1">
            Quản lý tài liệu báo cáo, tin tức thị trường và truy vấn trích dẫn ngữ nghĩa với công nghệ RAG v1.
          </p>
        </div>

        {/* Navigation Tabs */}
        <div className="flex items-center rounded-lg bg-slate-900 border border-slate-800 p-1">
          <button
            type="button"
            className={`px-4 py-1.5 rounded-md text-xs font-medium transition-colors ${
              activeTab === "documents"
                ? "bg-cyan-600 text-white shadow-sm"
                : "text-slate-400 hover:text-slate-200"
            }`}
            onClick={() => setActiveTab("documents")}
          >
            Quản lý Tài liệu
          </button>
          <button
            type="button"
            className={`px-4 py-1.5 rounded-md text-xs font-medium transition-colors ${
              activeTab === "retrieval"
                ? "bg-cyan-600 text-white shadow-sm"
                : "text-slate-400 hover:text-slate-200"
            }`}
            onClick={() => setActiveTab("retrieval")}
          >
            Truy vấn Đoạn trích
          </button>
          <button
            type="button"
            className={`px-4 py-1.5 rounded-md text-xs font-medium transition-colors ${
              activeTab === "ask"
                ? "bg-cyan-600 text-white shadow-sm"
                : "text-slate-400 hover:text-slate-200"
            }`}
            onClick={() => setActiveTab("ask")}
          >
            Hỏi Đáp AI Analyst
          </button>
        </div>
      </div>

      {/* Main Content */}
      {activeTab === "documents" && (
        <div className="space-y-6">
          <DocumentUpload onDocumentSubmitted={handleDocumentSubmitted} />
          <DocumentList
            documents={documents}
            loading={loading}
            onDocumentDeleted={handleDocumentDeleted}
            onRefresh={() => setReloadKey((k) => k + 1)}
            symbolFilter={symbolFilter}
            onSymbolFilterChange={setSymbolFilter}
            typeFilter={typeFilter}
            onTypeFilterChange={setTypeFilter}
          />
        </div>
      )}

      {activeTab === "retrieval" && (
        <div className="space-y-6">
          <RetrievalResults />
        </div>
      )}

      {activeTab === "ask" && (
        <div className="space-y-6">
          <AskPanel />
        </div>
      )}
    </div>
  );
}
