import { getCsrf } from '../../auth/api/owner-access';
import type { DocumentType } from './documents';
import type { NewsCategory } from './news';

export interface RetrieveFilter {
  symbol?: string;
  documentType?: DocumentType;
  newsCategory?: NewsCategory;
  dateFrom?: string;
  dateTo?: string;
}

export interface AskRequest {
  query: string;
  filters?: RetrieveFilter;
}

export type SourceType = 'DOCUMENT' | 'NEWS_ARTICLE';

export interface AskCitation {
  claimText: string;
  sourceType: SourceType;
  sourceId: string;
  sourceTitle: string;
  location: string;
  source: string;
}

export interface AskFinalResult {
  answer: string;
  citations: AskCitation[];
  refused: boolean;
}

export interface AskCallbacks {
  onDelta?: (textDelta: string) => void;
  onFinal?: (finalResult: AskFinalResult) => void;
  onError?: (error: Error) => void;
}

export async function streamAskQuestion(
  request: AskRequest,
  callbacks: AskCallbacks,
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
    response = await fetch('/api/v1/research/ask', {
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
    const error = err instanceof Error ? err : new Error('Lỗi mạng khi kết nối trợ lý AI');
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
      // Ignore JSON parse error
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
      if (done) {
        break;
      }

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed.startsWith('data:')) {
          continue;
        }

        const dataStr = trimmed.substring(5).trim();
        if (!dataStr) {
          continue;
        }

        try {
          const parsed = JSON.parse(dataStr);
          if (parsed.type === 'delta' && parsed.textDelta) {
            callbacks.onDelta?.(parsed.textDelta);
          } else if (parsed.type === 'final' && parsed.final) {
            sawFinalEvent = true;
            const finalRes: AskFinalResult = {
              answer: parsed.final.answer || '',
              citations: parsed.final.citations || [],
              refused: Boolean(parsed.final.refused),
            };
            callbacks.onFinal?.(finalRes);
          }
        } catch {
          // Ignore partial or unparseable frames
        }
      }
    }
  } catch (err: unknown) {
    if (err instanceof Error && err.name === 'AbortError') {
      return;
    }
    const error = err instanceof Error ? err : new Error('Lỗi khi đọc luồng dữ liệu.');
    callbacks.onError?.(error);
    throw error;
  }

  if (!sawFinalEvent) {
    // The stream closed cleanly but never sent a `final` event (e.g. AskService.java's
    // "stream ended without final event" path) — surface it as an error so the caller
    // can leave its "answering" state rather than waiting forever for an event that will
    // never arrive.
    callbacks.onError?.(
      new Error('Luồng trả lời đã kết thúc trước khi nhận được kết quả cuối cùng.')
    );
  }
}
