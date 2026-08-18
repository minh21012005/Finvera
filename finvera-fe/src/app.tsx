import { useEffect, useState } from "react";
import { MarketOverviewPage } from "./features/market-overview/market-overview-page";
import { StockDetailPage } from "./features/stock-detail/stock-detail-page";
import { OwnerAccessGate } from "./features/auth/owner-access-gate";
import { parseStockSymbolFromPath } from "./router";

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
      {symbol ? <StockDetailPage key={symbol} symbol={symbol} /> : <MarketOverviewPage />}
    </OwnerAccessGate>
  );
}
