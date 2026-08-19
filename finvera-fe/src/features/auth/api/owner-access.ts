export interface OwnerSession {
  subject: string;
  username: string;
  authenticatedAt: string;
  expiresAt: string;
}

export class OwnerAccessApiError extends Error {
  constructor(readonly status: number) {
    super(`Owner access request failed with HTTP ${status}`);
    this.name = "OwnerAccessApiError";
  }
}

export async function getOwnerSession(signal?: AbortSignal): Promise<OwnerSession> {
  const response = await fetch("/api/v1/auth/session", {
    method: "GET",
    credentials: "same-origin",
    headers: { Accept: "application/json" },
    signal,
  });
  if (!response.ok) throw new OwnerAccessApiError(response.status);
  return parseSession(await response.json());
}

export async function loginOwner(username: string, password: string): Promise<OwnerSession> {
  const csrf = await getCsrf();
  const response = await fetch("/api/v1/auth/session", {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token },
    body: JSON.stringify({ username, password }),
  });
  if (!response.ok) throw new OwnerAccessApiError(response.status);
  return getOwnerSession();
}

export async function logoutOwner(): Promise<void> {
  const csrf = await getCsrf();
  const response = await fetch("/api/v1/auth/session", {
    method: "DELETE",
    credentials: "same-origin",
    headers: { [csrf.headerName]: csrf.token },
  });
  if (!response.ok) throw new OwnerAccessApiError(response.status);
}

export async function getCsrf(): Promise<{ token: string; headerName: "X-CSRF-TOKEN" }> {
  const response = await fetch("/api/v1/auth/csrf", {
    method: "GET",
    credentials: "same-origin",
    headers: { Accept: "application/json" },
  });
  if (!response.ok) throw new OwnerAccessApiError(response.status);
  const value = await response.json() as Record<string, unknown>;
  if (typeof value.token !== "string" || value.token.length < 16 || value.headerName !== "X-CSRF-TOKEN") {
    throw new Error("Invalid CSRF contract");
  }
  return { token: value.token, headerName: "X-CSRF-TOKEN" };
}

function parseSession(value: unknown): OwnerSession {
  if (typeof value !== "object" || value === null || Array.isArray(value)) throw new Error("Invalid owner session");
  const session = value as Record<string, unknown>;
  const fields = ["subject", "username", "authenticatedAt", "expiresAt"] as const;
  if (fields.some((field) => typeof session[field] !== "string" || session[field].length === 0)) {
    throw new Error("Invalid owner session");
  }
  return {
    subject: session.subject as string,
    username: session.username as string,
    authenticatedAt: session.authenticatedAt as string,
    expiresAt: session.expiresAt as string,
  };
}
