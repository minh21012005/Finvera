import { useEffect, useState } from "react";
import { getMarketOverview, MarketOverviewApiError, type MarketOverview } from "./api/market-overview";
import { IndexOverview } from "./components/index-overview";
import { BreadthOverview } from "./components/breadth-overview";
import { RegimeOverview } from "./components/regime-overview";
import { formatAsOf } from "./format/market-format";
import { SymbolSearch } from "../stock-detail/components/symbol-search";
import { navigate } from "../../router";

type LoadState =
  | { kind: "loading" }
  | { kind: "ready"; overview: MarketOverview }
  | { kind: "error"; status?: number };

export function MarketOverviewPage() {
  const [state, setState] = useState<LoadState>({ kind: "loading" });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    getMarketOverview(controller.signal)
      .then((overview) => setState({ kind: "ready", overview }))
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        setState({ kind: "error", status: error instanceof MarketOverviewApiError ? error.status : undefined });
      });
    return () => controller.abort();
  }, [attempt]);

  if (state.kind === "loading") {
    return (
      <main className="app-shell" aria-busy="true">
        <p>Đang tải tổng quan thị trường…</p>
      </main>
    );
  }
  if (state.kind === "error") {
    return (
      <main className="app-shell">
        <p role="alert">{errorMessage(state.status)}</p>
        <button
          type="button"
          className="btn-retry"
          onClick={() => {
            setState({ kind: "loading" });
            setAttempt((value) => value + 1);
          }}
        >
          Thử lại
        </button>
      </main>
    );
  }

  const isSessionOpen = state.overview.session.state === "OPEN";

  return (
    <main className="app-shell">
      <header className="page-header">
        <p className="eyebrow">FINVERA · MARKET OVERVIEW</p>
        <h1>Tổng quan thị trường</h1>
        <div className="meta-row">
          <span className="meta-item">
            <span className={`pulse-dot ${isSessionOpen ? "open" : "closed"}`}></span>
            Phiên giao dịch {state.overview.tradingDate}
          </span>
          <span className="meta-item">Cập nhật {formatAsOf(state.overview.generatedAt)}</span>
          <span className="meta-item">
            Trạng thái dữ liệu:{" "}
            <span className={`status-pill ${state.overview.dataStatus.toLowerCase()}`}>
              {statusLabel(state.overview.dataStatus)}
            </span>
          </span>
        </div>
        <SymbolSearch onSelect={(symbol) => navigate(`/stocks/${symbol}`)} />
        <button type="button" className="screener-nav-link" onClick={() => navigate("/screener")}>
          Sàng lọc cổ phiếu →
        </button>
        <button type="button" className="screener-nav-link" onClick={() => navigate("/strategies")}>
          Quét chiến lược →
        </button>
        <button type="button" className="screener-nav-link" onClick={() => navigate("/portfolios")}>
          Danh mục đầu tư →
        </button>
        <button type="button" className="screener-nav-link" onClick={() => navigate("/watchlists")}>
          Danh sách theo dõi →
        </button>
        <button type="button" className="screener-nav-link" onClick={() => navigate("/research")}>
          Nghiên cứu & RAG →
        </button>
      </header>

      <IndexOverview overview={state.overview} />

      <div className="dashboard-split">
        <BreadthOverview breadth={state.overview.breadth} />
        <RegimeOverview regime={state.overview.regime} />
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

function statusLabel(status: MarketOverview["dataStatus"]): string {
  return ({ CURRENT: "Hiện tại", DELAYED: "Chậm", STALE: "Cũ", PARTIAL: "Một phần", UNAVAILABLE: "Không có dữ liệu" })[status];
}
