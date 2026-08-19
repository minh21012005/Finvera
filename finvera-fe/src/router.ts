/**
 * A dependency-free router (market overview at `/`, stock detail at
 * `/stocks/:symbol`, the screener at `/screener`, the strategy scan at
 * `/strategies`). Adding a routing library was not part of Feature 002's
 * planned dependencies (plan.md: "Primary dependencies: unchanged") and
 * Features 003/004 do not introduce one either — a private few-page app does
 * not need one.
 */
export function navigate(path: string): void {
  window.history.pushState({}, "", path);
  window.dispatchEvent(new PopStateEvent("popstate"));
}

const STOCK_DETAIL_PATH = /^\/stocks\/([A-Za-z0-9]{1,10})\/?$/;
const SCREENER_PATH = /^\/screener\/?$/;
const STRATEGIES_PATH = /^\/strategies\/?$/;

export function parseStockSymbolFromPath(pathname: string): string | null {
  const match = STOCK_DETAIL_PATH.exec(pathname);
  return match ? match[1].toUpperCase() : null;
}

export function isScreenerPath(pathname: string): boolean {
  return SCREENER_PATH.test(pathname);
}

export function isStrategyScanPath(pathname: string): boolean {
  return STRATEGIES_PATH.test(pathname);
}
