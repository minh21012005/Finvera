import React, { useEffect, useState } from 'react';
import {
  X,
  Search,
  Wrench,
  ArrowUpRight,
  Sparkles,
  BarChart3,
  Building2,
  TrendingUp,
  FileSpreadsheet,
  Scale,
  Zap,
  SlidersHorizontal,
  GitCompare,
  BookOpen,
  Newspaper,
  Briefcase,
} from 'lucide-react';
import { SUPPORTED_TOOLS, type SupportedTool } from './supported-tools-data';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onSelectQuery: (query: string, symbol?: string) => void;
}

const TOOL_ICONS: Record<
  string,
  {
    icon: React.ComponentType<{ size?: number; className?: string }>;
    colorClass: string;
    bgClass: string;
  }
> = {
  MARKET: { icon: BarChart3, colorClass: 'text-emerald-400', bgClass: 'bg-emerald-500/10 border-emerald-500/30' },
  STOCK: { icon: Building2, colorClass: 'text-blue-400', bgClass: 'bg-blue-500/10 border-blue-500/30' },
  TECHNICAL: { icon: TrendingUp, colorClass: 'text-cyan-400', bgClass: 'bg-cyan-500/10 border-cyan-500/30' },
  FUNDAMENTAL: { icon: FileSpreadsheet, colorClass: 'text-amber-400', bgClass: 'bg-amber-500/10 border-amber-500/30' },
  VALUATION: { icon: Scale, colorClass: 'text-violet-400', bgClass: 'bg-violet-500/10 border-violet-500/30' },
  STRATEGY_SCAN: { icon: Zap, colorClass: 'text-rose-400', bgClass: 'bg-rose-500/10 border-rose-500/30' },
  SCREENING: { icon: SlidersHorizontal, colorClass: 'text-indigo-400', bgClass: 'bg-indigo-500/10 border-indigo-500/30' },
  COMPARE: { icon: GitCompare, colorClass: 'text-sky-400', bgClass: 'bg-sky-500/10 border-sky-500/30' },
  RESEARCH_RAG: { icon: BookOpen, colorClass: 'text-purple-400', bgClass: 'bg-purple-500/10 border-purple-500/30' },
  NEWS: { icon: Newspaper, colorClass: 'text-orange-400', bgClass: 'bg-orange-500/10 border-orange-500/30' },
  PORTFOLIO: { icon: Briefcase, colorClass: 'text-teal-400', bgClass: 'bg-teal-500/10 border-teal-500/30' },
};

export const SupportedToolsModal: React.FC<Props> = ({ isOpen, onClose, onSelectQuery }) => {
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState<string>('');

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isOpen) {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const categories = [
    { id: 'ALL', label: 'Tất cả (11)' },
    { id: 'TECHNICAL', label: 'Kỹ thuật' },
    { id: 'FUNDAMENTAL', label: 'Cơ bản & Định giá' },
    { id: 'QUANT', label: 'Chiến lược & Sàng lọc' },
    { id: 'RESEARCH', label: 'Hybrid RAG' },
    { id: 'MARKET', label: 'Thị trường & Tin tức' },
    { id: 'PORTFOLIO', label: 'Danh mục' },
  ];

  const filteredTools = SUPPORTED_TOOLS.filter((tool) => {
    const matchCategory =
      selectedCategory === 'ALL' ||
      (selectedCategory === 'FUNDAMENTAL'
        ? tool.category === 'FUNDAMENTAL'
        : selectedCategory === 'MARKET'
        ? tool.category === 'MARKET'
        : selectedCategory === 'QUANT'
        ? tool.category === 'QUANT'
        : tool.category === selectedCategory);

    const q = searchQuery.toLowerCase().trim();
    const matchSearch =
      !q ||
      tool.name.toLowerCase().includes(q) ||
      tool.badge.toLowerCase().includes(q) ||
      tool.summary.toLowerCase().includes(q) ||
      tool.description.toLowerCase().includes(q) ||
      tool.sampleQueries.some((sq) => sq.query.toLowerCase().includes(q));

    return matchCategory && matchSearch;
  });

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="tools-modal-title"
      className="fixed inset-0 z-[200] flex items-center justify-center p-3 sm:p-6 bg-black/85 backdrop-blur-md animate-fade-in"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="relative w-full max-w-4xl max-h-[90vh] flex flex-col rounded-2xl border border-slate-700/80 bg-[#070e1a] text-slate-100 shadow-2xl overflow-hidden">
        {/* Modal Header */}
        <div className="flex items-center justify-between border-b border-slate-800/80 px-6 py-4 bg-slate-900/70">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-indigo-600/30 to-cyan-600/30 border border-indigo-500/40 flex items-center justify-center text-cyan-300 shadow-inner">
              <Wrench size={20} />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 id="tools-modal-title" className="text-base font-bold text-white">
                  Công Cụ Phân Tích Finvera AI Hỗ Trợ
                </h2>
                <span className="rounded-full bg-cyan-950/80 border border-cyan-800/80 px-2 py-0.5 text-[10px] font-mono font-bold text-cyan-300">
                  11 TOOLS SẴN SÀNG
                </span>
              </div>
              <p className="text-xs text-slate-400 mt-0.5">
                AI Copilot tự động kích hoạt các công cụ định lượng này để tính toán và thu thập chứng cứ chuẩn xác.
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Đóng cửa sổ công cụ"
            className="rounded-lg p-2 text-slate-400 hover:text-white hover:bg-slate-800 transition-all cursor-pointer"
          >
            <X size={18} />
          </button>
        </div>

        {/* Filter and Search Bar */}
        <div className="border-b border-slate-800 px-6 py-3 bg-[#08101e]/80 flex flex-col sm:flex-row gap-3 items-center justify-between">
          <div className="flex items-center gap-1.5 overflow-x-auto w-full sm:w-auto pb-1 sm:pb-0 scrollbar-none">
            {categories.map((cat) => (
              <button
                key={cat.id}
                type="button"
                onClick={() => setSelectedCategory(cat.id)}
                className={`rounded-lg px-3 py-1.5 text-xs font-semibold whitespace-nowrap transition-all cursor-pointer ${
                  selectedCategory === cat.id
                    ? 'bg-indigo-600 text-white shadow-md shadow-indigo-600/30'
                    : 'bg-slate-800/60 text-slate-400 hover:text-slate-200 hover:bg-slate-800'
                }`}
              >
                {cat.label}
              </button>
            ))}
          </div>

          <div className="relative w-full sm:w-64 shrink-0">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Tìm kiếm công cụ hoặc mẫu lệnh…"
              className="w-full rounded-xl border border-slate-800 bg-slate-900/90 pl-9 pr-3 py-1.5 text-xs text-slate-200 placeholder:text-slate-500 outline-none focus:border-indigo-500/80 focus:ring-1 focus:ring-indigo-500"
            />
          </div>
        </div>

        {/* Tools Grid */}
        <div className="flex-1 overflow-y-auto p-6 space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {filteredTools.map((tool: SupportedTool) => {
              const iconMeta = TOOL_ICONS[tool.id] || {
                icon: Wrench,
                colorClass: 'text-indigo-400',
                bgClass: 'bg-indigo-500/10 border-indigo-500/30',
              };
              const IconComp = iconMeta.icon;

              return (
                <div
                  key={tool.id}
                  className="flex flex-col justify-between rounded-xl border border-slate-800/80 bg-slate-900/40 hover:bg-slate-900/70 hover:border-slate-700/80 p-4 transition-all group"
                >
                  <div>
                    <div className="flex items-start gap-3 mb-2.5">
                      <div
                        className={`w-9 h-9 rounded-xl border flex items-center justify-center shrink-0 shadow-sm ${iconMeta.bgClass}`}
                      >
                        <IconComp size={18} className={iconMeta.colorClass} />
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center justify-between gap-2">
                          <span className="text-[10px] font-mono font-bold tracking-wider text-indigo-400 uppercase">
                            {tool.categoryLabel}
                          </span>
                          <span className="rounded bg-slate-800/80 border border-slate-700/60 px-1.5 py-0.2 text-[9px] font-mono font-bold text-slate-300 shrink-0">
                            {tool.badge}
                          </span>
                        </div>
                        <h3 className="text-sm font-bold text-white group-hover:text-cyan-300 transition-colors">
                          {tool.name}
                        </h3>
                      </div>
                    </div>

                    <p className="text-xs text-slate-300 leading-relaxed mb-3">
                      {tool.summary}
                    </p>
                  </div>

                  {/* Sample Queries */}
                  <div className="border-t border-slate-800/80 pt-2.5 mt-1 space-y-1.5">
                    <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wider block">
                      Câu hỏi mẫu (Bấm để hỏi):
                    </span>
                    <div className="flex flex-col gap-1.5">
                      {tool.sampleQueries.map((sq, idx) => (
                        <button
                          key={idx}
                          type="button"
                          onClick={() => {
                            onSelectQuery(sq.query, sq.symbol);
                            onClose();
                          }}
                          className="flex items-start justify-between gap-2 text-left rounded-lg bg-slate-950/70 hover:bg-indigo-950/60 border border-slate-800/70 hover:border-indigo-700/60 px-3 py-2 text-xs text-slate-300 hover:text-cyan-200 transition-all cursor-pointer group/btn"
                        >
                          <span className="flex-1 leading-relaxed break-words whitespace-normal font-normal">
                            "{sq.query}"
                          </span>
                          <ArrowUpRight size={13} className="text-slate-500 group-hover/btn:text-cyan-400 shrink-0 mt-0.5" />
                        </button>
                      ))}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>

          {filteredTools.length === 0 && (
            <div className="text-center py-12 text-slate-400 space-y-2">
              <Sparkles size={28} className="mx-auto text-slate-600" />
              <p className="text-sm">Không tìm thấy công cụ nào phù hợp với từ khóa.</p>
              <button
                type="button"
                onClick={() => {
                  setSelectedCategory('ALL');
                  setSearchQuery('');
                }}
                className="text-xs text-indigo-400 hover:underline cursor-pointer"
              >
                Đặt lại bộ lọc
              </button>
            </div>
          )}
        </div>

        {/* Modal Footer */}
        <div className="border-t border-slate-800 px-6 py-3 bg-slate-950 flex items-center justify-between text-xs text-slate-400">
          <span>Hệ thống tự động điều phối (Orchestration) công cụ thích hợp theo ngữ cảnh câu hỏi.</span>
          <button
            type="button"
            onClick={onClose}
            className="rounded-xl border border-slate-800 bg-slate-900 hover:bg-slate-800 px-4 py-1.5 text-xs font-semibold text-slate-200 hover:text-white transition-all cursor-pointer"
          >
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
};
