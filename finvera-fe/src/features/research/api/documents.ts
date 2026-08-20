import { getCsrf } from "../../auth/api/owner-access";

export type DocumentType =
  | "ANNUAL_REPORT"
  | "QUARTERLY_REPORT"
  | "FINANCIAL_REPORT"
  | "ECONOMIC_REPORT"
  | "INVESTOR_PRESENTATION"
  | "CORPORATE_DISCLOSURE"
  | "OTHER";

export type IngestionStatus = "PENDING" | "PROCESSING" | "READY" | "FAILED";

export interface Document {
  id: string;
  title: string;
  symbol?: string | null;
  documentType: DocumentType;
  year: number;
  quarter?: number | null;
  source: string;
  publicationDate: string;
  ingestionStatus: IngestionStatus;
  ingestionFailureReason?: string | null;
  submittedAt: string;
  processedAt?: string | null;
}

export interface DocumentPage {
  items: Document[];
  totalCount: number;
  limit: number;
  offset: number;
}

export interface SubmitDocumentParams {
  title: string;
  symbol?: string;
  documentType: DocumentType;
  year: number;
  quarter?: number;
  source: string;
  publicationDate: string;
  file?: File | null;
  pastedText?: string;
}

export interface ErrorResponse {
  type: string;
  title: string;
  status: number;
  detail?: string;
  reasonCode: string;
  correlationId: string;
}

export function generateIdempotencyKey(): string {
  return crypto.randomUUID();
}

const DOCUMENT_TYPES: readonly DocumentType[] = [
  "ANNUAL_REPORT",
  "QUARTERLY_REPORT",
  "FINANCIAL_REPORT",
  "ECONOMIC_REPORT",
  "INVESTOR_PRESENTATION",
  "CORPORATE_DISCLOSURE",
  "OTHER",
];
const INGESTION_STATUSES: readonly IngestionStatus[] = ["PENDING", "PROCESSING", "READY", "FAILED"];

function assertDocumentShape(value: unknown): asserts value is Document {
  const d = value as Partial<Document> | null;
  if (
    !d ||
    typeof d.id !== "string" ||
    typeof d.title !== "string" ||
    !DOCUMENT_TYPES.includes(d.documentType as DocumentType) ||
    !INGESTION_STATUSES.includes(d.ingestionStatus as IngestionStatus) ||
    typeof d.submittedAt !== "string"
  ) {
    throw new Error("Phản hồi tài liệu từ máy chủ không đúng định dạng.");
  }
}

function assertDocumentPageShape(value: unknown): asserts value is DocumentPage {
  const p = value as Partial<DocumentPage> | null;
  if (!p || !Array.isArray(p.items) || typeof p.totalCount !== "number") {
    throw new Error("Phản hồi danh sách tài liệu từ máy chủ không đúng định dạng.");
  }
  p.items.forEach(assertDocumentShape);
}

async function handleResponse<T>(response: Response): Promise<T> {
  if (response.status === 204) {
    return {} as T;
  }
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

export async function submitDocument(
  params: SubmitDocumentParams,
  idempotencyKey?: string,
  signal?: AbortSignal,
): Promise<Document> {
  const key = idempotencyKey || generateIdempotencyKey();
  const csrf = await getCsrf();

  const formData = new FormData();
  formData.append("title", params.title);
  if (params.symbol) formData.append("symbol", params.symbol.toUpperCase());
  formData.append("documentType", params.documentType);
  formData.append("year", String(params.year));
  if (params.quarter !== undefined && params.quarter !== null) {
    formData.append("quarter", String(params.quarter));
  }
  formData.append("source", params.source);
  formData.append("publicationDate", params.publicationDate);

  if (params.file) {
    formData.append("file", params.file);
  }
  if (params.pastedText) {
    formData.append("pastedText", params.pastedText);
  }

  const response = await fetch("/api/v1/research/documents", {
    method: "POST",
    credentials: "same-origin",
    headers: {
      Accept: "application/json",
      "Idempotency-Key": key,
      [csrf.headerName]: csrf.token,
    },
    body: formData,
    signal,
  });

  const result = await handleResponse<Document>(response);
  assertDocumentShape(result);
  return result;
}

export async function listDocuments(
  params: {
    symbol?: string;
    documentType?: DocumentType;
    dateFrom?: string;
    dateTo?: string;
    limit?: number;
    offset?: number;
  } = {},
  signal?: AbortSignal,
): Promise<DocumentPage> {
  const query = new URLSearchParams();
  if (params.symbol) query.set("symbol", params.symbol.toUpperCase());
  if (params.documentType) query.set("documentType", params.documentType);
  if (params.dateFrom) query.set("dateFrom", params.dateFrom);
  if (params.dateTo) query.set("dateTo", params.dateTo);
  if (params.limit !== undefined) query.set("limit", String(params.limit));
  if (params.offset !== undefined) query.set("offset", String(params.offset));

  const response = await fetch(`/api/v1/research/documents?${query.toString()}`, {
    method: "GET",
    credentials: "same-origin",
    headers: { Accept: "application/json" },
    signal,
  });

  const result = await handleResponse<DocumentPage>(response);
  assertDocumentPageShape(result);
  return result;
}

export async function getDocument(documentId: string, signal?: AbortSignal): Promise<Document> {
  const response = await fetch(`/api/v1/research/documents/${documentId}`, {
    method: "GET",
    credentials: "same-origin",
    headers: { Accept: "application/json" },
    signal,
  });

  const result = await handleResponse<Document>(response);
  assertDocumentShape(result);
  return result;
}

export async function deleteDocument(documentId: string, signal?: AbortSignal): Promise<void> {
  const csrf = await getCsrf();
  const response = await fetch(`/api/v1/research/documents/${documentId}`, {
    method: "DELETE",
    credentials: "same-origin",
    headers: {
      Accept: "application/json",
      [csrf.headerName]: csrf.token,
    },
    signal,
  });

  return handleResponse<void>(response);
}
