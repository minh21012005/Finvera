import { useEffect, useState } from "react";
import { MarketOverviewPage } from "./features/market-overview/market-overview-page";
import { StockDetailPage } from "./features/stock-detail/stock-detail-page";
import { StockScreenerPage } from "./features/stock-screener/stock-screener-page";
import { OwnerAccessGate } from "./features/auth/owner-access-gate";
import { isScreenerPath, parseStockSymbolFromPath } from "./router";

export function App() {
  const [pathname, setPathname] = useState(() => window.location.pathname);

  useEffect(() => {
    const onNavigate = () => setPathname(window.location.pathname);
    window.addEventListener("popstate", onNavigate);
    return () => window.removeEventListener("popstate", onNavigate);
  }, []);

  const symbol = parseStockSymbolFromPath(pathname);

  return (
    <OwnerAccessGate>
      {symbol ? (
        <StockDetailPage key={symbol} symbol={symbol} />
      ) : isScreenerPath(pathname) ? (
        <StockScreenerPage />
      ) : (
        <MarketOverviewPage />
      )}
    </OwnerAccessGate>
  );
}
