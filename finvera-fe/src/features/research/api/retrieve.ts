import { getCsrf } from "../../auth/api/owner-access";
import { DocumentType, ErrorResponse } from "./documents";

export type SourceType = "DOCUMENT" | "NEWS_ARTICLE";

export interface RetrieveFilter {
  symbol?: string | null;
  documentType?: DocumentType | null;
  newsCategory?: string | null;
  dateFrom?: string | null;
  dateTo?: string | null;
}

export interface RetrieveRequest {
  query: string;
  filters?: RetrieveFilter;
}

export interface Passage {
  sourceType: SourceType;
  sourceId: string;
  sourceTitle: string;
  location: string;
  source: string;
  publicationDate?: string | null;
  excerpt: string;
  score: number;
}

export interface RetrieveResponse {
  passages: Passage[];
  asOf: string;
}

async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    let error: ErrorResponse;
    try {
      error = await response.json();
    } catch {
      error = {
        type: "about:blank",
        title: response.statusText,
        status: response.status,
        reasonCode: "HTTP_" + response.status,
        correlationId: "",
      };
    }
    const err = new Error(error.detail || error.title || "Request failed");
    (err as unknown as { response: ErrorResponse }).response = error;
    (err as unknown as { status: number }).status = response.status;
    throw err;
  }
  return response.json();
}

export async function retrievePassages(
  req: RetrieveRequest,
  signal?: AbortSignal,
): Promise<RetrieveResponse> {
  const csrf = await getCsrf();
  const response = await fetch("/api/v1/research/retrieve", {
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

  return handleResponse<RetrieveResponse>(response);
}
