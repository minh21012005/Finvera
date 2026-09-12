import { getCsrf } from '../../auth/api/owner-access';

export interface PriorTurn {
  question: string;
  answer: string;
}

export interface AskAnalystRequest {
  question: string;
  symbol?: string;
  priorTurns?: PriorTurn[];
}

export interface ToolCallEvent {
  sequenceNo: number;
  toolName: string;
  arguments: Record<string, unknown>;
  status: 'STARTED' | 'SUCCEEDED' | 'FAILED';
  failureReason?: string | null;
  latencyMs: number;
}

export interface PublicStructuredClaim {
  claimText: string;
  sequenceNo: number;
  toolName: string;
  sourceField: string;
  asOf: string;
}

export type SourceType = 'DOCUMENT' | 'NEWS_ARTICLE';

export interface DocumentClaim {
  claimText: string;
  sourceType: SourceType;
  sourceId: string;
  sourceTitle: string;
  location: string;
  source: string;
}

export interface AnalystFinalResult {
  answer: string;
  structuredClaims: PublicStructuredClaim[];
  documentClaims: DocumentClaim[];
  refused: boolean;
  toolCalls: ToolCallEvent[];
  toolCallBoundReached: boolean;
  ruleVersion: string;
  /** Feature 015: ONLINE = the model wrote the text; OFFLINE_TEMPLATE = deterministic template from tool data (model unavailable). */
  synthesisMode?: 'ONLINE' | 'OFFLINE_TEMPLATE' | null;
  /** Feature 015: MODEL = native function-calling chose the tools; KEYWORD_FALLBACK = the deterministic heuristic did. */
  plannerMode?: 'MODEL' | 'KEYWORD_FALLBACK' | null;
  /** Retained statement coverage after verification; PARTIAL means unsafe content was omitted. */
  claimCoverage?: 'FULL' | 'PARTIAL' | 'NONE' | null;
}

export interface AskAnalystCallbacks {
  onToolCall?: (toolCall: ToolCallEvent) => void;
  onDelta?: (textDelta: string) => void;
  onFinal?: (finalResult: AnalystFinalResult) => void;
  onError?: (error: Error) => void;
}

export interface ContextWindowInfo {
  ruleVersion: string;
  includedExchanges: number;
  omittedExchanges: number;
}

export interface ConversationSummary {
  id: string;
  title: string;
  titleSource: 'AUTO' | 'OWNER';
  createdAt: string;
  updatedAt: string;
  lastActivityAt: string;
  exchangeCount: number;
  processing: boolean;
}

export interface ConversationExchange {
  id: string;
  sequenceNo: number;
  question: string;
  symbol?: string | null;
  status: 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  final?: AnalystFinalResult | null;
  failureCode?: string | null;
  contextWindow: ContextWindowInfo;
  createdAt: string;
  completedAt?: string | null;
}

export interface ConversationPage {
  items: ConversationSummary[];
  nextCursor?: string | null;
  hasMore: boolean;
}

export interface ExchangePage {
  conversation: ConversationSummary;
  items: ConversationExchange[];
  olderCursor?: string | null;
  hasMore: boolean;
}

export interface ConversationAskRequest {
  conversationId?: string;
  clientRequestId: string;
  question: string;
  symbol?: string;
}

export interface AcceptedConversationEvent {
  type: 'accepted';
  conversationId: string;
  exchangeId: string;
  created: boolean;
  title: string;
  contextWindow: ContextWindowInfo;
}

export interface ConversationCallbacks extends AskAnalystCallbacks {
  onAccepted?: (event: AcceptedConversationEvent) => void;
  onTerminalError?: (reasonCode: string, retryable: boolean) => void;
}

async function csrfHeaders(accept?: string): Promise<Record<string, string>> {
  const csrf = await getCsrf();
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (accept) headers.Accept = accept;
  if (csrf?.token) headers[csrf.headerName] = csrf.token;
  return headers;
}

async function jsonRequest<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, { credentials: 'same-origin', ...init });
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    throw new Error(problem.detail || problem.title || `Yêu cầu thất bại (${response.status})`);
  }
  return response.json() as Promise<T>;
}

export function listConversations(cursor?: string, limit = 20): Promise<ConversationPage> {
  const query = new URLSearchParams({ limit: String(limit) });
  if (cursor) query.set('cursor', cursor);
  return jsonRequest(`/api/v1/analyst/conversations?${query}`);
}

export function getConversationExchanges(conversationId: string, before?: string, limit = 50): Promise<ExchangePage> {
  const query = new URLSearchParams({ limit: String(limit) });
  if (before) query.set('before', before);
  return jsonRequest(`/api/v1/analyst/conversations/${conversationId}/exchanges?${query}`);
}

export async function renameConversation(conversationId: string, title: string): Promise<ConversationSummary> {
  return jsonRequest(`/api/v1/analyst/conversations/${conversationId}`, {
    method: 'PATCH', headers: await csrfHeaders(), body: JSON.stringify({ title }),
  });
}

export async function deleteConversation(conversationId: string): Promise<void> {
  const response = await fetch(`/api/v1/analyst/conversations/${conversationId}`, {
    method: 'DELETE', credentials: 'same-origin', headers: await csrfHeaders(),
  });
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    throw new Error(problem.detail || problem.title || `Không thể xóa (${response.status})`);
  }
}

export async function streamConversationAsk(request: ConversationAskRequest,
  callbacks: ConversationCallbacks, signal?: AbortSignal): Promise<void> {
  let response: Response;
  try {
    response = await fetch('/api/v1/analyst/conversations/ask', {
      method: 'POST', credentials: 'same-origin', headers: await csrfHeaders('text/event-stream'),
      body: JSON.stringify(request), signal,
    });
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') return;
    const resolved = error instanceof Error ? error : new Error('Lỗi mạng khi kết nối AI Analyst');
    callbacks.onError?.(resolved); throw resolved;
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    const error = new Error(problem.detail || problem.title || `Yêu cầu thất bại (${response.status})`);
    callbacks.onError?.(error); throw error;
  }
  if (!response.body) throw new Error('Máy chủ không trả về luồng dữ liệu');

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let terminal = false;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split('\n'); buffer = lines.pop() || '';
    for (const line of lines) {
      const trimmed = line.trim(); if (!trimmed.startsWith('data:')) continue;
      try {
        const event = JSON.parse(trimmed.slice(5).trim());
        if (event.type === 'accepted') callbacks.onAccepted?.(event);
        else if (event.type === 'tool_call') callbacks.onToolCall?.(event.toolCall);
        else if (event.type === 'delta') callbacks.onDelta?.(event.textDelta);
        else if (event.type === 'final') { terminal = true; callbacks.onFinal?.(event.final); }
        else if (event.type === 'error') {
          terminal = true; callbacks.onTerminalError?.(event.reasonCode, event.retryable);
        }
      } catch { /* Ignore incomplete/malformed event lines. */ }
    }
  }
  if (!terminal && !signal?.aborted) callbacks.onError?.(new Error('Luồng kết thúc trước kết quả cuối cùng'));
}

export async function streamAskAnalyst(
  request: AskAnalystRequest,
  callbacks: AskAnalystCallbacks,
  signal?: AbortSignal
): Promise<void> {
  const csrf = await getCsrf();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
  };
  if (csrf && csrf.token) {
    headers[csrf.headerName] = csrf.token;
  }

  let response: Response;
  try {
    response = await fetch('/api/v1/analyst/ask', {
      method: 'POST',
      credentials: 'same-origin',
      headers,
      body: JSON.stringify(request),
      signal,
    });
  } catch (err: unknown) {
    if (err instanceof Error && err.name === 'AbortError') {
      return;
    }
    const error = err instanceof Error ? err : new Error('Lỗi mạng khi kết nối AI Analyst');
    callbacks.onError?.(error);
    throw error;
  }

  if (!response.ok) {
    let errorDetail = `Yêu cầu thất bại (mã ${response.status})`;
    try {
      const errJson = await response.json();
      if (errJson.detail) {
        errorDetail = errJson.detail;
      }
    } catch {
      // Ignore
    }
    const error = new Error(errorDetail);
    callbacks.onError?.(error);
    throw error;
  }

  if (!response.body) {
    const error = new Error('Phản hồi từ máy chủ không chứa luồng dữ liệu.');
    callbacks.onError?.(error);
    throw error;
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';
  let sawFinalEvent = false;

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed || !trimmed.startsWith('data:')) continue;

        const jsonStr = trimmed.slice(5).trim();
        try {
          const event = JSON.parse(jsonStr);
          if (event.type === 'tool_call' && event.toolCall) {
            callbacks.onToolCall?.(event.toolCall);
          } else if (event.type === 'delta' && event.textDelta) {
            callbacks.onDelta?.(event.textDelta);
          } else if (event.type === 'final' && event.final) {
            sawFinalEvent = true;
            callbacks.onFinal?.(event.final);
          }
        } catch {
          // ignore chunk parse issues
        }
      }
    }
  } catch (err: unknown) {
    if (err instanceof Error && err.name === 'AbortError') {
      return;
    }
    const error = err instanceof Error ? err : new Error('Lỗi luồng dữ liệu phân tích.');
    callbacks.onError?.(error);
    throw error;
  }

  if (!sawFinalEvent) {
    // The stream closed cleanly but never sent a `final` event — surface it as an
    // error so the caller can leave its "answering" state rather than waiting
    // forever for an event that will never arrive (the same bug class already found
    // and fixed in Feature 006's research/api/ask.ts).
    callbacks.onError?.(
      new Error('Luồng trả lời đã kết thúc trước khi nhận được kết quả cuối cùng.')
    );
  }
}

export interface EvidenceFactor {
  factorCode: string;
  description: string;
}

export interface ExplainRequest {
  outputType: 'SIGNAL' | 'INDICATOR_READING' | 'VALUATION_CLASSIFICATION' | 'RISK_FACTOR';
  symbol?: string;
  evidenceFactors: EvidenceFactor[];
}

export interface ExplainResponse {
  explanation: string;
  verified: boolean;
}

export async function explainOutput(request: ExplainRequest): Promise<ExplainResponse> {
  const csrf = await getCsrf();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  if (csrf && csrf.token) {
    headers[csrf.headerName] = csrf.token;
  }

  const response = await fetch('/api/v1/analyst/explanations', {
    method: 'POST',
    credentials: 'same-origin',
    headers,
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    let errorDetail = `Yêu cầu giải thích thất bại (mã ${response.status})`;
    try {
      const errJson = await response.json();
      if (errJson.detail) {
        errorDetail = errJson.detail;
      }
    } catch {
      // Ignore
    }
    throw new Error(errorDetail);
  }

  return response.json();
}
