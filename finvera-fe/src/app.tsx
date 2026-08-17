import { MarketOverviewPage } from "./features/market-overview/market-overview-page";
import { OwnerAccessGate } from "./features/auth/owner-access-gate";

export function App() {
  return <OwnerAccessGate><MarketOverviewPage /></OwnerAccessGate>;
}
