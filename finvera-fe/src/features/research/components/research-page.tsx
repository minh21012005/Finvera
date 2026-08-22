import { useEffect, useState } from "react";
import { Document, DocumentType, listDocuments } from "../api/documents";
import { AskPanel } from "./ask-panel";
import { DocumentList } from "./document-list";
import { DocumentUpload } from "./document-upload";
import { NewsList } from "./news-list";
import { NewsSubmit } from "./news-submit";
import { RetrievalResults } from "./retrieval-results";

import { FileText, Newspaper, Search, Bot } from "lucide-react";
import { navigate } from "../../../router";

export function ResearchPage() {
  const [activeTab, setActiveTab] = useState<"documents" | "news" | "retrieval" | "ask">("documents");
  const [documents, setDocuments] = useState<Document[]>([]);
  const [loading, setLoading] = useState(true);
  const [symbolFilter, setSymbolFilter] = useState("");
  const [typeFilter, setTypeFilter] = useState("");
  const [reloadKey, setReloadKey] = useState(0);
  const [newsReloadKey, setNewsReloadKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();

    listDocuments(
      {
        symbol: symbolFilter || undefined,
        documentType: (typeFilter ? (typeFilter as DocumentType) : undefined),
      },
      controller.signal,
    )
      .then((data) => {
        if (!controller.signal.aborted) {
          setDocuments(data.items || []);
          setLoading(false);
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setDocuments([]);
          setLoading(false);
        }
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
    <main className="app-shell space-y-6">
      {/* Header */}
      <header className="page-header">
        <button type="button" className="back-link" onClick={() => navigate("/")}>
          ← Trang chủ
        </button>
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <p className="eyebrow">FINVERA · RESEARCH & RAG</p>
            <h1 className="text-2xl font-bold text-white">
              Nghiên cứu & Dữ liệu RAG
            </h1>
            <p className="text-xs text-slate-400 mt-1">
              Quản lý tài liệu báo cáo, tin tức thị trường và truy vấn trích dẫn ngữ nghĩa với công nghệ Hybrid RAG v1.
            </p>
          </div>

          {/* Navigation Tabs */}
          <div className="inline-flex rounded-xl bg-slate-900 border border-slate-800 p-1.5 self-start md:self-auto shadow-sm">
            <button
              type="button"
              className={`px-3.5 py-2 rounded-lg text-xs font-semibold flex items-center gap-2 transition-all cursor-pointer ${
                activeTab === "documents"
                  ? "bg-gradient-to-r from-cyan-600 to-blue-600 text-white shadow-md"
                  : "text-slate-400 hover:text-slate-200"
              }`}
              onClick={() => setActiveTab("documents")}
            >
              <FileText size={14} />
              <span>Quản lý Tài liệu</span>
            </button>
            <button
              type="button"
              className={`px-3.5 py-2 rounded-lg text-xs font-semibold flex items-center gap-2 transition-all cursor-pointer ${
                activeTab === "news"
                  ? "bg-gradient-to-r from-cyan-600 to-blue-600 text-white shadow-md"
                  : "text-slate-400 hover:text-slate-200"
              }`}
              onClick={() => setActiveTab("news")}
            >
              <Newspaper size={14} />
              <span>Tin Tức Thị Trường</span>
            </button>
            <button
              type="button"
              className={`px-3.5 py-2 rounded-lg text-xs font-semibold flex items-center gap-2 transition-all cursor-pointer ${
                activeTab === "retrieval"
                  ? "bg-gradient-to-r from-cyan-600 to-blue-600 text-white shadow-md"
                  : "text-slate-400 hover:text-slate-200"
              }`}
              onClick={() => setActiveTab("retrieval")}
            >
              <Search size={14} />
              <span>Truy vấn Đoạn trích</span>
            </button>
            <button
              type="button"
              className={`px-3.5 py-2 rounded-lg text-xs font-semibold flex items-center gap-2 transition-all cursor-pointer ${
                activeTab === "ask"
                  ? "bg-gradient-to-r from-indigo-600 to-violet-600 text-white shadow-md"
                  : "text-slate-400 hover:text-slate-200"
              }`}
              onClick={() => setActiveTab("ask")}
            >
              <Bot size={14} />
              <span>Hỏi Đáp AI Analyst</span>
            </button>
          </div>
        </div>
      </header>

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

      {activeTab === "news" && (
        <div className="space-y-6">
          <NewsSubmit onSubmitted={() => setNewsReloadKey((k) => k + 1)} />
          <NewsList refreshTrigger={newsReloadKey} />
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
    </main>
  );
}
