import { useEffect, useState } from "react";
import { getMarketOverview, MarketOverviewApiError, type MarketOverview } from "./api/market-overview";
import { IndexOverview } from "./components/index-overview";
import { BreadthOverview } from "./components/breadth-overview";
import { RegimeOverview } from "./components/regime-overview";
import { formatAsOf, formatDate } from "./format/market-format";
import { SymbolSearch } from "../stock-detail/components/symbol-search";
import { navigate } from "../../router";

import { dataStatusLabel as statusLabel } from "../../shared/format/reason-codes";
type LoadState =
  | { kind: "loading" }
  | { kind: "ready"; overview: MarketOverview }
  | { kind: "error"; status?: number };

export function MarketOverviewPage() {
  const [state, setState] = useState<LoadState>({ kind: "loading" });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let disposed = false;
    let controller: AbortController | null = null;
    const load = (initial: boolean) => {
      controller?.abort();
      controller = new AbortController();
      getMarketOverview(controller.signal)
        .then((overview) => { if (!disposed) setState({ kind: "ready", overview }); })
        .catch((error: unknown) => {
          if (disposed || controller?.signal.aborted || !initial) return;
          setState({ kind: "error", status: error instanceof MarketOverviewApiError ? error.status : undefined });
        });
    };
    load(true);
    const refreshTimer = window.setInterval(() => load(false), 30_000);
    return () => {
      disposed = true;
      window.clearInterval(refreshTimer);
      controller?.abort();
    };
  }, [attempt]);

  if (state.kind === "loading") {
    return (
      <main className="app-shell" aria-busy="true">
        <div className="loading-state">
          <div className="loading-spinner"></div>
          <p>Đang tải tổng quan thị trường…</p>
        </div>
      </main>
    );
  }
  if (state.kind === "error") {
    return (
      <main className="app-shell">
        <div className="error-card">
          <p role="alert">{errorMessage(state.status)}</p>
          <button
            type="button"
            className="btn-retry mt-4"
            onClick={() => {
              setState({ kind: "loading" });
              setAttempt((value) => value + 1);
            }}
          >
            Thử lại
          </button>
        </div>
      </main>
    );
  }

  const isSessionOpen = state.overview.session.state === "OPEN";

  return (
    <main className="app-shell quant-terminal-layout">
      <header className="page-header">
        <div className="market-header-top">
          <div>
            <p className="eyebrow">FINVERA QUANT TERMINAL · MARKET AI RADAR</p>
            <h1>Tổng quan thị trường & AI Radar</h1>
            <div className="meta-row">
              <span className="meta-item">
                <span className={`pulse-dot ${isSessionOpen ? "open" : "closed"}`}></span>
                Phiên giao dịch {formatDate(state.overview.tradingDate)}
              </span>
              <span className="meta-item">Cập nhật {formatAsOf(state.overview.generatedAt)}</span>
              <span className="meta-item">
                Trạng thái dữ liệu:{" "}
                <span className={`status-pill ${state.overview.dataStatus.toLowerCase()}`}>
                  {statusLabel(state.overview.dataStatus)}
                </span>
              </span>
            </div>
          </div>
          <SymbolSearch onSelect={(symbol) => navigate(`/stocks/${symbol}`)} />
        </div>
      </header>

      {/* Top 4 Hero Index Cards */}
      <IndexOverview overview={state.overview} />

      {/* Main Terminal Balanced Dashboard */}
      <div className="terminal-dashboard-grid">
        <div className="terminal-col-left">
          <RegimeOverview regime={state.overview.regime} />
        </div>
        <div className="terminal-col-right">
          <BreadthOverview breadth={state.overview.breadth} />
        </div>
      </div>

      <footer className="provenance-footer">
        <span>Trạng thái dữ liệu: {statusLabel(state.overview.dataStatus)}. Dữ liệu thiếu hoặc chậm được hiển thị rõ ràng, không được suy diễn.</span>
        <span>Finvera Quantitative Decision Support Engine</span>
      </footer>
    </main>
  );
}

function errorMessage(status?: number): string {
  if (status === 401 || status === 403) return "Phiên đăng nhập riêng tư không hợp lệ hoặc đã hết hạn.";
  return "Không thể tải tổng quan thị trường lúc này. Không có dữ liệu nào được thay thế bằng giá trị ước lượng.";
}

