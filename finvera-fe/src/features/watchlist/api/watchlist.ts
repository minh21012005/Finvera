import { getCsrf } from "../../auth/api/owner-access";

export interface WatchlistSummary {
  id: string;
  name: string;
  createdAt: string;
  itemCount: number;
}

export interface CreateWatchlistRequest {
  name: string;
}

export interface RenameWatchlistRequest {
  name: string;
}

export interface AddWatchlistItemRequest {
  symbol: string;
}

export interface WatchlistItem {
  symbol: string;
  companyName: string;
  addedAt: string;
  currentPrice: string | null;
  dailyChangePercent: string | null;
  technicalTrend: string | null;
  volumeCondition: string | null;
  hasCurrentSignal: boolean;
  signalDirection: string | null;
  riskLevel: "LOW" | "MEDIUM" | "HIGH" | null;
  dataStatus: "CURRENT" | "DELAYED" | "STALE" | "PARTIAL" | "UNAVAILABLE";
  reasonCode: string | null;
}

export interface WatchlistDetail {
  id: string;
  name: string;
  items: WatchlistItem[];
  coherenceKey: string;
  asOf: string;
}

export class WatchlistApiError extends Error {
  constructor(
    readonly status: number,
    readonly reasonCode: string,
    readonly detail?: string,
  ) {
    super(`Watchlist API error ${status}: ${reasonCode}${detail ? ` - ${detail}` : ""}`);
    this.name = "WatchlistApiError";
  }
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
    throw new WatchlistApiError(response.status, reasonCode, detail);
  }
  return (await response.json()) as T;
}

export async function listWatchlists(signal?: AbortSignal): Promise<WatchlistSummary[]> {
  const response = await fetch("/api/v1/watchlists", {
    method: "GET",
    credentials: "same-origin",
    headers: { Accept: "application/json" },
    signal,
  });
  return handleResponse<WatchlistSummary[]>(response);
}

export async function createWatchlist(
  req: CreateWatchlistRequest,
  signal?: AbortSignal,
): Promise<WatchlistSummary> {
  const csrf = await getCsrf();
  const response = await fetch("/api/v1/watchlists", {
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
  return handleResponse<WatchlistSummary>(response);
}

export async function getWatchlist(
  watchlistId: string,
  signal?: AbortSignal,
): Promise<WatchlistDetail> {
  const response = await fetch(`/api/v1/watchlists/${watchlistId}`, {
    method: "GET",
    credentials: "same-origin",
    headers: { Accept: "application/json" },
    signal,
  });
  return handleResponse<WatchlistDetail>(response);
}

export async function renameWatchlist(
  watchlistId: string,
  req: RenameWatchlistRequest,
  signal?: AbortSignal,
): Promise<WatchlistSummary> {
  const csrf = await getCsrf();
  const response = await fetch(`/api/v1/watchlists/${watchlistId}`, {
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
  return handleResponse<WatchlistSummary>(response);
}

export async function deleteWatchlist(
  watchlistId: string,
  signal?: AbortSignal,
): Promise<void> {
  const csrf = await getCsrf();
  const response = await fetch(`/api/v1/watchlists/${watchlistId}`, {
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

export async function addWatchlistItem(
  watchlistId: string,
  req: AddWatchlistItemRequest,
  signal?: AbortSignal,
): Promise<WatchlistDetail> {
  const csrf = await getCsrf();
  const response = await fetch(`/api/v1/watchlists/${watchlistId}/items`, {
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
  return handleResponse<WatchlistDetail>(response);
}

export async function removeWatchlistItem(
  watchlistId: string,
  symbol: string,
  signal?: AbortSignal,
): Promise<void> {
  const csrf = await getCsrf();
  const response = await fetch(`/api/v1/watchlists/${watchlistId}/items/${symbol}`, {
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
