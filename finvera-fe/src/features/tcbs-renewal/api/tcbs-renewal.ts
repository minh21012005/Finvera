import { getCsrf } from "../../auth/api/owner-access";

export type TcbsProviderState = "READY" | "DEGRADED" | "AUTH_REQUIRED";
export interface TcbsStatus { state: TcbsProviderState; reasonCode: string }

export class TcbsRenewalApiError extends Error {
  constructor(readonly status: number, readonly reasonCode: string, readonly detail?: string) {
    super(`TCBS renewal failed: ${reasonCode}`);
    this.name = "TcbsRenewalApiError";
  }
}

export async function getTcbsStatus(signal?: AbortSignal): Promise<TcbsStatus> {
  const response = await fetch("/api/v1/market/providers/tcbs/status", {
    credentials: "same-origin", headers: { Accept: "application/json" }, signal,
  });
  if (!response.ok) throw new TcbsRenewalApiError(response.status, "SERVER_ERROR");
  const body = await response.json() as Record<string, unknown>;
  if (!(["READY", "DEGRADED", "AUTH_REQUIRED"] as unknown[]).includes(body.state)
      || typeof body.reasonCode !== "string") throw new Error("Invalid TCBS status contract");
  return body as unknown as TcbsStatus;
}

export async function renewTcbsSession(otp: string): Promise<void> {
  const csrf = await getCsrf();
  const response = await fetch("/api/v1/market/providers/tcbs/token-renewal", {
    method: "POST", credentials: "same-origin",
    headers: { "Content-Type": "application/json", Accept: "application/json", [csrf.headerName]: csrf.token },
    body: JSON.stringify({ otp }),
  });
  if (response.ok) return;
  let reasonCode = "SERVER_ERROR";
  let detail: string | undefined;
  try {
    const problem = await response.json() as Record<string, unknown>;
    if (typeof problem.reasonCode === "string") reasonCode = problem.reasonCode;
    if (typeof problem.detail === "string") detail = problem.detail;
  } catch { /* response may be empty */ }
  throw new TcbsRenewalApiError(response.status, reasonCode, detail);
}
