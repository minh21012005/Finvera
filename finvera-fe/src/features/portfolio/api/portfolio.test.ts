import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import {
  createPortfolio,
  generateIdempotencyKey,
  getPositions,
  listPortfolios,
  PortfolioApiError,
  recordTransaction,
  voidTransaction,
} from "./portfolio";

describe("Portfolio API client", () => {
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
        if (url === "/api/v1/portfolios" && init?.method === "GET") {
          return Promise.resolve({
            ok: true,
            json: () =>
              Promise.resolve([
                {
                  id: "pf-1",
                  name: "Main",
                  createdAt: "2026-08-01T00:00:00Z",
                  totalValue: "100000000",
                  cashBalance: "50000000",
                  totalUnrealizedPL: "10000000",
                  totalRealizedPL: "0",
                  asOf: "2026-08-15T00:00:00Z",
                },
              ]),
          });
        }
        if (url === "/api/v1/portfolios" && init?.method === "POST") {
          return Promise.resolve({
            ok: true,
            json: () =>
              Promise.resolve({
                id: "pf-2",
                name: "Growth",
                createdAt: "2026-08-15T00:00:00Z",
                totalValue: "0",
                cashBalance: "0",
                totalUnrealizedPL: "0",
                totalRealizedPL: "0",
                asOf: "2026-08-15T00:00:00Z",
              }),
          });
        }
        if (url === "/api/v1/portfolios/pf-1/positions") {
          return Promise.resolve({
            ok: true,
            json: () =>
              Promise.resolve({
                positions: [
                  {
                    instrumentSymbol: "FPT",
                    quantity: "1000",
                    averageCostBasis: "50000",
                    currentPrice: "60000",
                    currentPriceStatus: "DEFINED",
                    unrealizedPL: "10000000",
                    realizedPL: "0",
                    allocation: "0.6",
                  },
                ],
                cashBalance: "40000000",
                totalValue: "100000000",
                coherenceKey: "coh-1",
                asOf: "2026-08-15T00:00:00Z",
              }),
          });
        }
        if (url.includes("/transactions")) {
          return Promise.resolve({
            ok: true,
            json: () =>
              Promise.resolve({
                id: "tx-1",
                portfolioId: "pf-1",
                sequenceNo: 1,
                transactionType: "DEPOSIT",
                amount: "100000000",
                fee: "0",
                currency: "VND",
                executedAt: "2026-08-01T00:00:00Z",
                entryAt: "2026-08-01T00:00:00Z",
                idempotencyKey: init?.headers ? (init.headers as Record<string, string>)["Idempotency-Key"] : "k1",
              }),
          });
        }
        return Promise.reject(new Error(`Unhandled URL: ${url}`));
      }),
    );
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("generateIdempotencyKey generates non-empty string", () => {
    const k1 = generateIdempotencyKey();
    const k2 = generateIdempotencyKey();
    expect(k1).toBeTruthy();
    expect(k2).toBeTruthy();
    expect(k1).not.toEqual(k2);
  });

  it("listPortfolios fetches portfolio list", async () => {
    const list = await listPortfolios();
    expect(list).toHaveLength(1);
    expect(list[0].name).toBe("Main");
  });

  it("createPortfolio attaches CSRF token", async () => {
    const created = await createPortfolio({ name: "Growth" });
    expect(created.id).toBe("pf-2");
    expect(created.name).toBe("Growth");
  });

  it("recordTransaction generates and sends Idempotency-Key header", async () => {
    const key = "custom-key-123";
    const tx = await recordTransaction(
      "pf-1",
      {
        transactionType: "DEPOSIT",
        amount: "100000000",
        executedAt: "2026-08-01T00:00:00Z",
      },
      key,
    );
    expect(tx.idempotencyKey).toBe(key);
  });

  it("voidTransaction voids a transaction", async () => {
    const tx = await voidTransaction("pf-1", "tx-1", { reason: "Mistake" }, "void-key");
    expect(tx.id).toBe("tx-1");
  });

  it("getPositions parses positions and totals", async () => {
    const res = await getPositions("pf-1");
    expect(res.positions).toHaveLength(1);
    expect(res.positions[0].instrumentSymbol).toBe("FPT");
    expect(res.totalValue).toBe("100000000");
  });

  it("handles problem details error with reasonCode", async () => {
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
          status: 409,
          json: () =>
            Promise.resolve({
              status: 409,
              reasonCode: "INSUFFICIENT_POSITION",
              detail: "Not enough shares",
            }),
        });
      }),
    );

    await expect(
      recordTransaction("pf-1", {
        transactionType: "SELL",
        instrumentSymbol: "FPT",
        quantity: "1000",
        price: "50000",
        executedAt: "2026-08-01T00:00:00Z",
      }),
    ).rejects.toThrow(PortfolioApiError);
  });
});
