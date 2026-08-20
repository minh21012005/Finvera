import { getCsrf } from "../../auth/api/owner-access";

export type NewsCategory = "COMPANY" | "SECTOR" | "MARKET" | "MACRO" | "REGULATION";
export type Sentiment = "POSITIVE" | "NEUTRAL" | "NEGATIVE";
export type ImpactLevel = "LOW" | "MEDIUM" | "HIGH";
export type Applicability = "DEFINED" | "NOT_APPLICABLE" | "MISSING";
export type IngestionStatus = "PENDING" | "PROCESSING" | "READY" | "FAILED";

export interface SubmitNewsArticleRequest {
  title: string;
  symbol?: string;
  source: string;
  publishedAt: string;
  referenceUrl?: string;
  body: string;
}

export interface NewsArticle {
  id: string;
  title: string;
  symbol?: string | null;
  source: string;
  sourceUrl?: string | null;
  publishedAt: string;
  category?: NewsCategory | null;
  categoryApplicability: Applicability;
  sentiment?: Sentiment | null;
  sentimentApplicability: Applicability;
  impactLevel?: ImpactLevel | null;
  impactApplicability: Applicability;
  sector?: string | null;
  ingestionStatus: IngestionStatus;
  ingestionFailureReason?: string | null;
  submittedAt: string;
  processedAt?: string | null;
}

export interface NewsArticlePage {
  items: NewsArticle[];
  totalCount: number;
  limit: number;
  offset: number;
}

export interface ListNewsFilter {
  symbol?: string;
  category?: NewsCategory;
  sentiment?: Sentiment;
  dateFrom?: string;
  dateTo?: string;
  limit?: number;
  offset?: number;
}

export function generateIdempotencyKey(): string {
  return crypto.randomUUID();
}

const INGESTION_STATUSES: readonly IngestionStatus[] = ["PENDING", "PROCESSING", "READY", "FAILED"];
const APPLICABILITIES: readonly Applicability[] = ["DEFINED", "NOT_APPLICABLE", "MISSING"];

function assertNewsArticleShape(value: unknown): asserts value is NewsArticle {
  const a = value as Partial<NewsArticle> | null;
  if (
    !a ||
    typeof a.id !== "string" ||
    typeof a.title !== "string" ||
    typeof a.source !== "string" ||
    typeof a.publishedAt !== "string" ||
    !INGESTION_STATUSES.includes(a.ingestionStatus as IngestionStatus) ||
    !APPLICABILITIES.includes(a.categoryApplicability as Applicability) ||
    !APPLICABILITIES.includes(a.sentimentApplicability as Applicability) ||
    !APPLICABILITIES.includes(a.impactApplicability as Applicability)
  ) {
    throw new Error("Phản hồi bài báo từ máy chủ không đúng định dạng.");
  }
}

function assertNewsArticlePageShape(value: unknown): asserts value is NewsArticlePage {
  const p = value as Partial<NewsArticlePage> | null;
  if (!p || !Array.isArray(p.items) || typeof p.totalCount !== "number") {
    throw new Error("Phản hồi danh sách bài báo từ máy chủ không đúng định dạng.");
  }
  p.items.forEach(assertNewsArticleShape);
}

export async function submitNewsArticle(
  data: SubmitNewsArticleRequest,
  idempotencyKey?: string
): Promise<NewsArticle> {
  const key = idempotencyKey || generateIdempotencyKey();
  const csrf = await getCsrf();

  const res = await fetch("/api/v1/research/news", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Idempotency-Key": key,
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify(data),
  });

  if (!res.ok) {
    const errorBody = await res.json().catch(() => ({}));
    throw new Error(errorBody.detail || errorBody.message || `Lỗi khi gửi bài báo (${res.status})`);
  }

  const result = await res.json();
  assertNewsArticleShape(result);
  return result;
}

export async function listNewsArticles(filter?: ListNewsFilter): Promise<NewsArticlePage> {
  const params = new URLSearchParams();
  if (filter?.symbol) params.set("symbol", filter.symbol);
  if (filter?.category) params.set("category", filter.category);
  if (filter?.sentiment) params.set("sentiment", filter.sentiment);
  if (filter?.dateFrom) params.set("dateFrom", filter.dateFrom);
  if (filter?.dateTo) params.set("dateTo", filter.dateTo);
  if (filter?.limit !== undefined) params.set("limit", filter.limit.toString());
  if (filter?.offset !== undefined) params.set("offset", filter.offset.toString());

  const url = `/api/v1/research/news${params.toString() ? `?${params.toString()}` : ""}`;
  const res = await fetch(url, {
    method: "GET",
    credentials: "same-origin",
    headers: {
      Accept: "application/json",
    },
  });

  if (!res.ok) {
    const errorBody = await res.json().catch(() => ({}));
    throw new Error(errorBody.detail || errorBody.message || `Lỗi khi tải danh sách tin tức (${res.status})`);
  }

  const result = await res.json();
  assertNewsArticlePageShape(result);
  return result;
}

export async function getNewsArticle(id: string): Promise<NewsArticle> {
  const res = await fetch(`/api/v1/research/news/${id}`, {
    method: "GET",
    credentials: "same-origin",
    headers: {
      Accept: "application/json",
    },
  });

  if (!res.ok) {
    const errorBody = await res.json().catch(() => ({}));
    throw new Error(errorBody.detail || errorBody.message || `Lỗi khi lấy chi tiết bài báo (${res.status})`);
  }

  const result = await res.json();
  assertNewsArticleShape(result);
  return result;
}

export async function deleteNewsArticle(id: string): Promise<void> {
  const csrf = await getCsrf();
  const res = await fetch(`/api/v1/research/news/${id}`, {
    method: "DELETE",
    credentials: "same-origin",
    headers: {
      [csrf.headerName]: csrf.token,
    },
  });

  if (!res.ok) {
    const errorBody = await res.json().catch(() => ({}));
    throw new Error(errorBody.detail || errorBody.message || `Lỗi khi xóa bài báo (${res.status})`);
  }
}
