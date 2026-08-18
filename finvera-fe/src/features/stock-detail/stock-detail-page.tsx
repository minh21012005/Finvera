import { useEffect, useState } from "react";
import {
  getStockChart,
  getStockFundamentals,
  getStockOverview,
  getStockTechnical,
  getStockValuation,
  StockApiError,
  type StockChart as StockChartData,
  type StockFundamentals as StockFundamentalsData,
  type StockOverview as StockOverviewData,
  type StockTechnical as StockTechnicalData,
  type StockValuation as StockValuationData,
} from "./api/stock-detail";
import { StockOverview } from "./components/stock-overview";
import { StockChart } from "./components/stock-chart";
import { StockTechnical } from "./components/stock-technical";
import { StockFundamentals } from "./components/stock-fundamentals";
import { StockValuation } from "./components/stock-valuation";
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

type FundamentalsState =
  | { kind: "loading" }
  | { kind: "ready"; fundamentals: StockFundamentalsData }
  | { kind: "unavailable" };

type ValuationState =
  | { kind: "loading" }
  | { kind: "ready"; valuation: StockValuationData }
  | { kind: "unavailable" };

export function StockDetailPage({ symbol }: { symbol: string }) {
  const [overviewState, setOverviewState] = useState<OverviewState>({ kind: "loading" });
  const [chartState, setChartState] = useState<ChartState>({ kind: "loading" });
  const [technicalState, setTechnicalState] = useState<TechnicalState>({ kind: "loading" });
  const [fundamentalsState, setFundamentalsState] = useState<FundamentalsState>({ kind: "loading" });
  const [valuationState, setValuationState] = useState<ValuationState>({ kind: "loading" });

  useEffect(() => {
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

    // The five sections fail independently of each other (FR-012)
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

    getStockFundamentals(symbol, controller.signal)
      .then((fundamentals) => setFundamentalsState({ kind: "ready", fundamentals }))
      .catch(() => {
        if (controller.signal.aborted) return;
        setFundamentalsState({ kind: "unavailable" });
      });

    getStockValuation(symbol, controller.signal)
      .then((valuation) => setValuationState({ kind: "ready", valuation }))
      .catch(() => {
        if (controller.signal.aborted) return;
        setValuationState({ kind: "unavailable" });
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

          {fundamentalsState.kind === "loading" && <p aria-busy="true">Đang tải chỉ số cơ bản…</p>}
          {fundamentalsState.kind === "unavailable" && (
            <section aria-labelledby="stock-fundamentals-heading" className="stock-fundamentals-card">
              <h2 id="stock-fundamentals-heading">Chỉ số cơ bản</h2>
              <p role="status">Chỉ số cơ bản tạm thời không có dữ liệu.</p>
            </section>
          )}
          {fundamentalsState.kind === "ready" && <StockFundamentals fundamentals={fundamentalsState.fundamentals} />}

          {valuationState.kind === "loading" && <p aria-busy="true">Đang tải định giá…</p>}
          {valuationState.kind === "unavailable" && (
            <section aria-labelledby="stock-valuation-heading" className="stock-valuation-card">
              <h2 id="stock-valuation-heading">Định giá tương đối</h2>
              <p role="status">Định giá tạm thời không có dữ liệu.</p>
            </section>
          )}
          {valuationState.kind === "ready" && <StockValuation valuation={valuationState.valuation} />}

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
