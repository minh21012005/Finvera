import { useEffect, useState } from "react";
import { getMarketOverview, MarketOverviewApiError, type MarketOverview } from "./api/market-overview";
import { IndexOverview } from "./components/index-overview";
import { BreadthOverview } from "./components/breadth-overview";
import { formatAsOf } from "./format/market-format";

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
    return <main className="app-shell" aria-busy="true"><p>Đang tải tổng quan thị trường…</p></main>;
  }
  if (state.kind === "error") {
    return (
      <main className="app-shell">
        <p role="alert">{errorMessage(state.status)}</p>
        <button type="button" onClick={() => {
          setState({ kind: "loading" });
          setAttempt((value) => value + 1);
        }}>Thử lại</button>
      </main>
    );
  }

  return (
    <main className="app-shell">
      <p className="eyebrow">FINVERA · MARKET OVERVIEW</p>
      <h1>Tổng quan thị trường</h1>
      <p>Ngày giao dịch {state.overview.tradingDate} · Cập nhật {formatAsOf(state.overview.generatedAt)}</p>
      <p>Trạng thái dữ liệu: {statusLabel(state.overview.dataStatus)}. Dữ liệu thiếu hoặc chậm được hiển thị rõ ràng, không được suy diễn.</p>
      <IndexOverview overview={state.overview} />
      <BreadthOverview breadth={state.overview.breadth} />
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
