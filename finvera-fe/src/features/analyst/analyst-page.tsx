import React from 'react';
import { AskAnalyst } from './components/AskAnalyst';
import { navigate } from '../../router';

export const AnalystPage: React.FC = () => {
  return (
    <main className="app-shell space-y-6">
      <header className="page-header">
        <button type="button" className="back-link" onClick={() => navigate("/")}>
          ← Trang chủ
        </button>
        <p className="eyebrow">FINVERA · AI COPILOT</p>
        <h1 className="text-2xl font-bold text-white">AI Financial Analyst</h1>
        <p className="text-xs text-slate-400 mt-1">
          Trợ lý phân tích định lượng đa công cụ — tích hợp tra cứu giá, chỉ báo kỹ thuật, BCTC, độ rộng thị trường và Hybrid RAG.
        </p>
      </header>
      <AskAnalyst />
    </main>
  );
};
