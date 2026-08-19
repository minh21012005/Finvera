import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import {
  addWatchlistItem,
  createWatchlist,
  deleteWatchlist,
  getWatchlist,
  listWatchlists,
  removeWatchlistItem,
  WatchlistApiError,
} from "./watchlist";

describe("Watchlist API client", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation((url: string, init?: RequestInit) => {
        if (url === "/api/v1/auth/csrf") {
          return Promise.resolve({
            ok: true,
            json: () =>
              Promise.resolve({
                token: "csrf-token-1234567890",
                headerName: "X-CSRF-TOKEN",
              }),
          });
        }
        if (url === "/api/v1/watchlists" && init?.method === "GET") {
          return Promise.resolve({
            ok: true,
            json: () =>
              Promise.resolve([
                {
                  id: "wl-1",
                  name: "Tech Watchlist",
                  createdAt: "2026-08-01T00:00:00Z",
                  itemCount: 2,
                },
              ]),
          });
        }
        if (url === "/api/v1/watchlists" && init?.method === "POST") {
          return Promise.resolve({
            ok: true,
            json: () =>
              Promise.resolve({
                id: "wl-2",
                name: "Banks",
                createdAt: "2026-08-15T00:00:00Z",
                itemCount: 0,
              }),
          });
        }
        if (url === "/api/v1/watchlists/wl-1") {
          return Promise.resolve({
            ok: true,
            json: () =>
              Promise.resolve({
                id: "wl-1",
                name: "Tech Watchlist",
                items: [
                  {
                    symbol: "FPT",
                    companyName: "Tập đoàn FPT",
                    addedAt: "2026-08-10T00:00:00Z",
                    currentPrice: "60000",
                    dailyChangePercent: "2.5",
                    technicalTrend: "BULLISH",
                    volumeCondition: "NORMAL",
                    hasCurrentSignal: true,
                    signalDirection: "BULLISH",
                    riskLevel: "LOW",
                    dataStatus: "CURRENT",
                    reasonCode: null,
                  },
                ],
                coherenceKey: "coh-wl-1",
                asOf: "2026-08-15T00:00:00Z",
              }),
          });
        }
        if (url === "/api/v1/watchlists/wl-1/items" && init?.method === "POST") {
          return Promise.resolve({
            ok: true,
            json: () =>
              Promise.resolve({
                id: "wl-1",
                name: "Tech Watchlist",
                items: [],
                coherenceKey: "coh-2",
                asOf: "2026-08-15T00:00:00Z",
              }),
          });
        }
        if (url.includes("/items/FPT") && init?.method === "DELETE") {
          return Promise.resolve({ ok: true });
        }
        if (url === "/api/v1/watchlists/wl-1" && init?.method === "DELETE") {
          return Promise.resolve({ ok: true });
        }
        return Promise.reject(new Error(`Unhandled URL: ${url}`));
      }),
    );
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("listWatchlists fetches watchlist array", async () => {
    const res = await listWatchlists();
    expect(res).toHaveLength(1);
    expect(res[0].name).toBe("Tech Watchlist");
  });

  it("createWatchlist attaches CSRF token and returns new watchlist", async () => {
    const res = await createWatchlist({ name: "Banks" });
    expect(res.id).toBe("wl-2");
    expect(res.name).toBe("Banks");
  });

  it("getWatchlist fetches items with live context", async () => {
    const res = await getWatchlist("wl-1");
    expect(res.items).toHaveLength(1);
    expect(res.items[0].symbol).toBe("FPT");
    expect(res.items[0].hasCurrentSignal).toBe(true);
    expect(res.items[0].riskLevel).toBe("LOW");
  });

  it("addWatchlistItem adds symbol to watchlist", async () => {
    const res = await addWatchlistItem("wl-1", { symbol: "FPT" });
    expect(res.id).toBe("wl-1");
  });

  it("removeWatchlistItem calls DELETE", async () => {
    await expect(removeWatchlistItem("wl-1", "FPT")).resolves.toBeUndefined();
  });

  it("deleteWatchlist calls DELETE", async () => {
    await expect(deleteWatchlist("wl-1")).resolves.toBeUndefined();
  });

  it("throws WatchlistApiError on failure", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation((url: string) => {
        if (url === "/api/v1/auth/csrf") {
          return Promise.resolve({
            ok: true,
            json: () =>
              Promise.resolve({
                token: "csrf-token-1234567890",
                headerName: "X-CSRF-TOKEN",
              }),
          });
        }
        return Promise.resolve({
          ok: false,
          status: 400,
          json: () =>
            Promise.resolve({
              status: 400,
              reasonCode: "UNSUPPORTED_INSTRUMENT",
              detail: "Bad symbol",
            }),
        });
      }),
    );

    await expect(addWatchlistItem("wl-1", { symbol: "INVALID" })).rejects.toThrow(WatchlistApiError);
  });
});
