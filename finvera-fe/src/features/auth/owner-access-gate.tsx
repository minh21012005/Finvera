import { type FormEvent, type ReactNode, useEffect, useState } from "react";
import { getOwnerSession, loginOwner, logoutOwner, OwnerAccessApiError, type OwnerSession } from "./api/owner-access";

type State =
  | { kind: "loading" }
  | { kind: "anonymous" }
  | { kind: "authenticated"; session: OwnerSession }
  | { kind: "error" };

export function OwnerAccessGate({ children }: { children: ReactNode }) {
  const [state, setState] = useState<State>({ kind: "loading" });

  useEffect(() => {
    const controller = new AbortController();
    getOwnerSession(controller.signal)
      .then((session) => setState({ kind: "authenticated", session }))
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        setState(error instanceof OwnerAccessApiError && error.status === 401 ? { kind: "anonymous" } : { kind: "error" });
      });
    return () => controller.abort();
  }, []);

  if (state.kind === "loading") return <main className="app-shell" aria-busy="true"><p>Đang kiểm tra phiên riêng tư…</p></main>;
  if (state.kind === "error") return <main className="app-shell"><p role="alert">Không thể kiểm tra phiên đăng nhập. Hãy thử tải lại trang.</p></main>;
  if (state.kind === "anonymous") return <LoginForm onAuthenticated={(session) => setState({ kind: "authenticated", session })} />;

  return <>
    <header className="session-bar">
      <span>Phiên riêng tư: {state.session.username}</span>
      <button type="button" onClick={() => logoutOwner().then(() => setState({ kind: "anonymous" })).catch(() => setState({ kind: "error" }))}>Đăng xuất</button>
    </header>
    {children}
  </>;
}

function LoginForm({ onAuthenticated }: { onAuthenticated: (session: OwnerSession) => void }) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const username = String(form.get("username") ?? "");
    const password = String(form.get("password") ?? "");
    setSubmitting(true);
    setError(null);
    try {
      onAuthenticated(await loginOwner(username, password));
    } catch (reason: unknown) {
      setError(reason instanceof OwnerAccessApiError && reason.status === 429
        ? "Đã vượt quá số lần thử. Hãy chờ rồi thử lại."
        : "Đăng nhập không thành công.");
    } finally {
      setSubmitting(false);
    }
  }

  return <main className="app-shell">
    <p className="eyebrow">FINVERA · PRIVATE ACCESS</p>
    <h1>Đăng nhập riêng tư</h1>
    <form className="owner-login" onSubmit={submit}>
      <label>Username<input name="username" autoComplete="username" required maxLength={128} /></label>
      <label>Password<input name="password" type="password" autoComplete="current-password" required maxLength={256} /></label>
      {error ? <p role="alert">{error}</p> : null}
      <button type="submit" disabled={submitting}>{submitting ? "Đang đăng nhập…" : "Đăng nhập"}</button>
    </form>
  </main>;
}
