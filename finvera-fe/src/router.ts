/**
 * A dependency-free two-page router (market overview at `/`, stock detail at
 * `/stocks/:symbol`). Adding a routing library was not part of this
 * feature's planned dependencies (plan.md: "Primary dependencies: unchanged"),
 * and a private two-page app does not need one.
 */
export function navigate(path: string): void {
  window.history.pushState({}, "", path);
  window.dispatchEvent(new PopStateEvent("popstate"));
}

const STOCK_DETAIL_PATH = /^\/stocks\/([A-Za-z0-9]{1,10})\/?$/;

export function parseStockSymbolFromPath(pathname: string): string | null {
  const match = STOCK_DETAIL_PATH.exec(pathname);
  return match ? match[1].toUpperCase() : null;
}
