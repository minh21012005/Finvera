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
  status: 'SUCCEEDED' | 'FAILED';
  failureReason?: string | null;
  latencyMs: number;
}

export interface PublicStructuredClaim {
  claimText: string;
  toolName: string;
  sourceField: string;
  claimedValue: string;
  asOf: string;
}

export interface DocumentClaim {
  chunkId: string;
  claimText: string;
  title?: string;
  source?: string;
  pageNumber?: number;
}

export interface AnalystFinalResult {
  answer: string;
  structuredClaims: PublicStructuredClaim[];
  documentClaims: DocumentClaim[];
  refused: boolean;
  toolCalls: ToolCallEvent[];
  toolCallBoundReached: boolean;
  ruleVersion: string;
}

export interface AskAnalystCallbacks {
  onToolCall?: (toolCall: ToolCallEvent) => void;
  onDelta?: (textDelta: string) => void;
  onFinal?: (finalResult: AnalystFinalResult) => void;
  onError?: (error: Error) => void;
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
