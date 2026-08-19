import { useEffect, useState } from "react";
import { MarketOverviewPage } from "./features/market-overview/market-overview-page";
import { StockDetailPage } from "./features/stock-detail/stock-detail-page";
import { StockScreenerPage } from "./features/stock-screener/stock-screener-page";
import { StockStrategyPage } from "./features/stock-strategy/stock-strategy-page";
import { PortfolioList } from "./features/portfolio/components/portfolio-list";
import { PortfolioDetailPage } from "./features/portfolio/components/portfolio-detail-page";
import { WatchlistList } from "./features/watchlist/components/watchlist-list";
import { WatchlistDetailPage } from "./features/watchlist/components/watchlist-detail-page";
import { OwnerAccessGate } from "./features/auth/owner-access-gate";
import {
  isPortfoliosPath,
  isScreenerPath,
  isStrategyScanPath,
  isWatchlistsPath,
  parsePortfolioIdFromPath,
  parseStockSymbolFromPath,
  parseWatchlistIdFromPath,
} from "./router";

export function App() {
  const [pathname, setPathname] = useState(() => window.location.pathname);

  useEffect(() => {
    const onNavigate = () => setPathname(window.location.pathname);
    window.addEventListener("popstate", onNavigate);
    return () => window.removeEventListener("popstate", onNavigate);
  }, []);

  const symbol = parseStockSymbolFromPath(pathname);
  const portfolioId = parsePortfolioIdFromPath(pathname);
  const watchlistId = parseWatchlistIdFromPath(pathname);

  return (
    <OwnerAccessGate>
      {symbol ? (
        <StockDetailPage key={symbol} symbol={symbol} />
      ) : portfolioId ? (
        <PortfolioDetailPage key={portfolioId} portfolioId={portfolioId} />
      ) : isPortfoliosPath(pathname) ? (
        <PortfolioList />
      ) : watchlistId ? (
        <WatchlistDetailPage key={watchlistId} watchlistId={watchlistId} />
      ) : isWatchlistsPath(pathname) ? (
        <WatchlistList />
      ) : isScreenerPath(pathname) ? (
        <StockScreenerPage />
      ) : isStrategyScanPath(pathname) ? (
        <StockStrategyPage />
      ) : (
        <MarketOverviewPage />
      )}
    </OwnerAccessGate>
  );
}

