// API client for Feature 007 - AI Analyst

export interface AskAnalystRequest {
  question: string;
  symbol?: string;
}

export interface ExplainRequest {
  outputType: string;
  evidenceFactors: Record<string, unknown>;
  symbol?: string;
}
