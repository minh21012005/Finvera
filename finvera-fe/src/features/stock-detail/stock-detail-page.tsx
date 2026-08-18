import { useEffect, useState } from "react";
import {
  getStockChart,
  getStockOverview,
  getStockTechnical,
  StockApiError,
  type StockChart as StockChartData,
  type StockOverview as StockOverviewData,
  type StockTechnical as StockTechnicalData,
} from "./api/stock-detail";
import { StockOverview } from "./components/stock-overview";
import { StockChart } from "./components/stock-chart";
import { StockTechnical } from "./components/stock-technical";
import { SymbolSearch } from "./components/symbol-search";
import { navigate } from "../../router";

type OverviewState =
  | { kind: "loading" }
  | { kind: "ready"; overview: StockOverviewData }
  | { kind: "not-found" }
  | { kind: "error"; status?: number };

type ChartState =
  | { kind: "loading" }
  | { kind: "ready"; chart: StockChartData }
  | { kind: "unavailable" };

type TechnicalState =
  | { kind: "loading" }
  | { kind: "ready"; technical: StockTechnicalData }
  | { kind: "unavailable" };

export function StockDetailPage({ symbol }: { symbol: string }) {
  const [overviewState, setOverviewState] = useState<OverviewState>({ kind: "loading" });
  const [chartState, setChartState] = useState<ChartState>({ kind: "loading" });
  const [technicalState, setTechnicalState] = useState<TechnicalState>({ kind: "loading" });

  useEffect(() => {
    // `symbol` changes only via remount (App keys StockDetailPage by symbol),
    // so the initial "loading" state above already covers a symbol switch;
    // no reset is called here.
    const controller = new AbortController();

    getStockOverview(symbol, controller.signal)
      .then((overview) => setOverviewState({ kind: "ready", overview }))
      .catch((cause: unknown) => {
        if (controller.signal.aborted) return;
        if (cause instanceof StockApiError && cause.status === 404) {
          setOverviewState({ kind: "not-found" });
        } else {
          setOverviewState({ kind: "error", status: cause instanceof StockApiError ? cause.status : undefined });
        }
      });

    // The chart and technical sections fail independently of the overview
    // (FR-012): an outage in either must never block the rest of the page.
    getStockChart(symbol, "1Y", controller.signal)
      .then((chart) => setChartState({ kind: "ready", chart }))
      .catch(() => {
        if (controller.signal.aborted) return;
        setChartState({ kind: "unavailable" });
      });

    getStockTechnical(symbol, controller.signal)
      .then((technical) => setTechnicalState({ kind: "ready", technical }))
      .catch(() => {
        if (controller.signal.aborted) return;
        setTechnicalState({ kind: "unavailable" });
      });

    return () => controller.abort();
  }, [symbol]);

  return (
    <main className="app-shell">
      <header className="page-header">
        <p className="eyebrow">FINVERA · STOCK DETAIL</p>
        <h1>Chi tiết cổ phiếu {symbol}</h1>
        <button type="button" className="btn-link" onClick={() => navigate("/")}>
          ← Về tổng quan thị trường
        </button>
        <SymbolSearch onSelect={(nextSymbol) => navigate(`/stocks/${nextSymbol}`)} />
      </header>

      {overviewState.kind === "loading" && (
        <p aria-busy="true">Đang tải dữ liệu {symbol}…</p>
      )}
      {overviewState.kind === "not-found" && (
        <p role="alert">Không tìm thấy mã cổ phiếu "{symbol}" trong dữ liệu được hỗ trợ.</p>
      )}
      {overviewState.kind === "error" && (
        <p role="alert">{errorMessage(overviewState.status)}</p>
      )}
      {overviewState.kind === "ready" && (
        <>
          <StockOverview overview={overviewState.overview} />

          {chartState.kind === "loading" && <p aria-busy="true">Đang tải biểu đồ…</p>}
          {chartState.kind === "unavailable" && (
            <section aria-labelledby="stock-chart-heading" className="stock-chart-card">
              <h2 id="stock-chart-heading">Biểu đồ giá</h2>
              <p role="status">Biểu đồ tạm thời không có dữ liệu.</p>
            </section>
          )}
          {chartState.kind === "ready" && <StockChart chart={chartState.chart} />}

          {technicalState.kind === "loading" && <p aria-busy="true">Đang tải chỉ báo kỹ thuật…</p>}
          {technicalState.kind === "unavailable" && (
            <section aria-labelledby="stock-technical-heading" className="stock-technical-card">
              <h2 id="stock-technical-heading">Chỉ báo kỹ thuật</h2>
              <p role="status">Chỉ báo kỹ thuật tạm thời không có dữ liệu.</p>
            </section>
          )}
          {technicalState.kind === "ready" && <StockTechnical technical={technicalState.technical} />}

          <footer className="provenance-footer">
            <span>
              Dữ liệu kỹ thuật và định giá là hỗ trợ ra quyết định định lượng, không phải khuyến nghị đầu tư hay dự
              báo được đảm bảo.
            </span>
            <span>Finvera Quantitative Decision Support Engine</span>
          </footer>
        </>
      )}
    </main>
  );
}

function errorMessage(status?: number): string {
  if (status === 401 || status === 403) return "Phiên đăng nhập riêng tư không hợp lệ hoặc đã hết hạn.";
  return "Không thể tải dữ liệu cổ phiếu lúc này. Không có dữ liệu nào được thay thế bằng giá trị ước lượng.";
}
