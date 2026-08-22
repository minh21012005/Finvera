import { getCsrf } from "../../auth/api/owner-access";

export type TcbsOtpMethod = "totp" | "email-sms";

export class TcbsRenewalApiError extends Error {
  constructor(
    readonly status: number,
    readonly reasonCode: string,
    readonly detail?: string,
  ) {
    super(`TCBS renewal failed with HTTP ${status}: ${reasonCode}${detail ? ` - ${detail}` : ""}`);
    this.name = "TcbsRenewalApiError";
  }
}

export async function renewTcbsSession(otpMethod: TcbsOtpMethod, otp: string): Promise<void> {
  const csrf = await getCsrf();
  const response = await fetch("/api/v1/market/providers/tcbs/token-renewal", {
    method: "POST",
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify({ otpMethod, otp }),
  });
  if (!response.ok) {
    let reasonCode = "SERVER_ERROR";
    let detail: string | undefined;
    try {
      const problem = (await response.json()) as Record<string, unknown>;
      if (typeof problem.reasonCode === "string") reasonCode = problem.reasonCode;
      if (typeof problem.detail === "string") detail = problem.detail;
    } catch {
      // Non-JSON response
    }
    throw new TcbsRenewalApiError(response.status, reasonCode, detail);
  }
}
