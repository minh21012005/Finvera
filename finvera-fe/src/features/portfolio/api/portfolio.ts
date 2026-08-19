import { getCsrf } from "../../auth/api/owner-access";

export interface PortfolioSummary {
  id: string;
  name: string;
  createdAt: string;
  totalValue: string;
  cashBalance: string;
  totalUnrealizedPL: string;
  totalRealizedPL: string;
  asOf: string;
}

export interface CreatePortfolioRequest {
  name: string;
}

export interface RenamePortfolioRequest {
  name: string;
}

export type TransactionType = "BUY" | "SELL" | "DEPOSIT" | "WITHDRAW" | "VOID";

export interface Transaction {
  id: string;
  portfolioId: string;
  sequenceNo: number;
  transactionType: TransactionType;
  instrumentSymbol?: string | null;
  quantity?: string | null;
  price?: string | null;
  fee: string;
  amount?: string | null;
  currency: string;
  executedAt: string;
  entryAt: string;
  voidsTransactionId?: string | null;
  voidReason?: string | null;
  idempotencyKey: string;
}

export interface RecordTransactionRequest {
  transactionType: "BUY" | "SELL" | "DEPOSIT" | "WITHDRAW";
  instrumentSymbol?: string | null;
  quantity?: string | null;
  price?: string | null;
  fee?: string;
  amount?: string | null;
  executedAt: string;
}

export interface VoidTransactionRequest {
  reason: string;
}

export interface TransactionPage {
  items: Transaction[];
  totalCount: number;
  limit: number;
  offset: number;
}

export interface Position {
  instrumentSymbol: string;
  quantity: string;
  averageCostBasis: string;
  currentPrice: string | null;
  currentPriceStatus: "DEFINED" | "NOT_APPLICABLE" | "MISSING";
  unrealizedPL: string | null;
  realizedPL: string;
  allocation: string | null;
}

export interface PositionsResponse {
  positions: Position[];
  cashBalance: string;
  totalValue: string;
  coherenceKey: string;
  asOf: string;
}

export class PortfolioApiError extends Error {
  constructor(
    readonly status: number,
    readonly reasonCode: string,
    readonly detail?: string,
  ) {
    super(`Portfolio API error ${status}: ${reasonCode}${detail ? ` - ${detail}` : ""}`);
    this.name = "PortfolioApiError";
  }
}

export function generateIdempotencyKey(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return "idem-" + Date.now() + "-" + Math.random().toString(36).substring(2, 11);
}

async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    let reasonCode = "SERVER_ERROR";
    let detail: string | undefined;
    try {
      const problem = (await response.json()) as Record<string, unknown>;
      if (typeof problem.reasonCode === "string") {
        reasonCode = problem.reasonCode;
      } else if (typeof problem.code === "string") {
        reasonCode = problem.code;
      }
      if (typeof problem.detail === "string") {
        detail = problem.detail;
      }
    } catch {
      // Non-JSON response
    }
    throw new PortfolioApiError(response.status, reasonCode, detail);
  }
  return (await response.json()) as T;
}

export async function listPortfolios(signal?: AbortSignal): Promise<PortfolioSummary[]> {
  const response = await fetch("/api/v1/portfolios", {
    method: "GET",
    credentials: "same-origin",
    headers: { Accept: "application/json" },
    signal,
  });
  return handleResponse<PortfolioSummary[]>(response);
}

export async function createPortfolio(
  req: CreatePortfolioRequest,
  signal?: AbortSignal,
): Promise<PortfolioSummary> {
  const csrf = await getCsrf();
  const response = await fetch("/api/v1/portfolios", {
    method: "POST",
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify(req),
    signal,
  });
  return handleResponse<PortfolioSummary>(response);
}

export async function getPortfolio(
  portfolioId: string,
  signal?: AbortSignal,
): Promise<PortfolioSummary> {
  const response = await fetch(`/api/v1/portfolios/${portfolioId}`, {
    method: "GET",
    credentials: "same-origin",
    headers: { Accept: "application/json" },
    signal,
  });
  return handleResponse<PortfolioSummary>(response);
}

export async function renamePortfolio(
  portfolioId: string,
  req: RenamePortfolioRequest,
  signal?: AbortSignal,
): Promise<PortfolioSummary> {
  const csrf = await getCsrf();
  const response = await fetch(`/api/v1/portfolios/${portfolioId}`, {
    method: "PATCH",
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify(req),
    signal,
  });
  return handleResponse<PortfolioSummary>(response);
}

export async function deletePortfolio(
  portfolioId: string,
  signal?: AbortSignal,
): Promise<void> {
  const csrf = await getCsrf();
  const response = await fetch(`/api/v1/portfolios/${portfolioId}`, {
    method: "DELETE",
    credentials: "same-origin",
    headers: {
      [csrf.headerName]: csrf.token,
    },
    signal,
  });
  if (!response.ok) {
    await handleResponse<void>(response);
  }
}

export async function listTransactions(
  portfolioId: string,
  limit = 50,
  offset = 0,
  signal?: AbortSignal,
): Promise<TransactionPage> {
  const response = await fetch(
    `/api/v1/portfolios/${portfolioId}/transactions?limit=${limit}&offset=${offset}`,
    {
      method: "GET",
      credentials: "same-origin",
      headers: { Accept: "application/json" },
      signal,
    },
  );
  return handleResponse<TransactionPage>(response);
}

export async function recordTransaction(
  portfolioId: string,
  req: RecordTransactionRequest,
  idempotencyKey?: string,
  signal?: AbortSignal,
): Promise<Transaction> {
  const key = idempotencyKey || generateIdempotencyKey();
  const csrf = await getCsrf();
  const response = await fetch(`/api/v1/portfolios/${portfolioId}/transactions`, {
    method: "POST",
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      "Idempotency-Key": key,
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify(req),
    signal,
  });
  return handleResponse<Transaction>(response);
}

export async function voidTransaction(
  portfolioId: string,
  transactionId: string,
  req: VoidTransactionRequest,
  idempotencyKey?: string,
  signal?: AbortSignal,
): Promise<Transaction> {
  const key = idempotencyKey || generateIdempotencyKey();
  const csrf = await getCsrf();
  const response = await fetch(
    `/api/v1/portfolios/${portfolioId}/transactions/${transactionId}/void`,
    {
      method: "POST",
      credentials: "same-origin",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        "Idempotency-Key": key,
        [csrf.headerName]: csrf.token,
      },
      body: JSON.stringify(req),
      signal,
    },
  );
  return handleResponse<Transaction>(response);
}

export async function getPositions(
  portfolioId: string,
  signal?: AbortSignal,
): Promise<PositionsResponse> {
  const response = await fetch(`/api/v1/portfolios/${portfolioId}/positions`, {
    method: "GET",
    credentials: "same-origin",
    headers: { Accept: "application/json" },
    signal,
  });
  return handleResponse<PositionsResponse>(response);
}
