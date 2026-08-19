export interface ConcentrationEntry {
  key: string;
  percentage: string;
}

export interface PerformancePoint {
  date: string;
  totalValue: string;
  dataStatus: "CURRENT" | "PARTIAL";
  reasonCode: string | null;
}

export interface RiskExposure {
  riskExposureScore: number | null;
  riskExposureLevel: "LOW" | "MEDIUM" | "HIGH" | null;
  coverageRatio: string;
  reasonCode: string | null;
}

export interface BenchmarkComparison {
  portfolioReturn: string | null;
  benchmarkReturn: string;
  benchmarkSymbol: string;
}

export interface PortfolioAnalytics {
  periodFrom: string;
  periodTo: string;
  periodClampedToInception: boolean;
  returnSinceInception: string | null;
  returnOverPeriod: string | null;
  returnMethodDisclosureCode: string;
  maxDrawdown: string | null;
  performanceHistory: PerformancePoint[];
  stockConcentration: ConcentrationEntry[];
  sectorConcentration: ConcentrationEntry[];
  riskExposure: RiskExposure;
  benchmark: BenchmarkComparison;
  asOf: string;
}

export class PortfolioAnalyticsApiError extends Error {
  constructor(
    readonly status: number,
    readonly reasonCode: string,
    readonly detail?: string,
  ) {
    super(`Portfolio Analytics API error ${status}: ${reasonCode}${detail ? ` - ${detail}` : ""}`);
    this.name = "PortfolioAnalyticsApiError";
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
    throw new PortfolioAnalyticsApiError(response.status, reasonCode, detail);
  }
  return (await response.json()) as T;
}

export async function getPortfolioAnalytics(
  portfolioId: string,
  from?: string,
  to?: string,
  signal?: AbortSignal,
): Promise<PortfolioAnalytics> {
  const params = new URLSearchParams();
  if (from) params.set("from", from);
  if (to) params.set("to", to);

  const query = params.toString() ? `?${params.toString()}` : "";
  const response = await fetch(`/api/v1/portfolios/${portfolioId}/analytics${query}`, {
    method: "GET",
    credentials: "same-origin",
    headers: { Accept: "application/json" },
    signal,
  });
  return handleResponse<PortfolioAnalytics>(response);
}
