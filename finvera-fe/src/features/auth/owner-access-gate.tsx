import { type FormEvent, type ReactNode, useEffect, useState } from "react";
import { getOwnerSession, loginOwner, logoutOwner, OwnerAccessApiError, type OwnerSession } from "./api/owner-access";
import { getTcbsStatus } from "../tcbs-renewal/api/tcbs-renewal";
import { navigate } from "../../router";

const TCBS_STATUS_POLL_MS = 60_000;

type State =
  | { kind: "loading" }
  | { kind: "anonymous" }
  | { kind: "authenticated"; session: OwnerSession }
  | { kind: "error" };

export function OwnerAccessGate({ children }: { children: ReactNode }) {
  const [state, setState] = useState<State>({ kind: "loading" });
  const [pathname, setPathname] = useState(() => window.location.pathname);
  const [tcbsAuthRequired, setTcbsAuthRequired] = useState(false);

  useEffect(() => {
    const onNavigate = () => setPathname(window.location.pathname);
    window.addEventListener("popstate", onNavigate);
    return () => window.removeEventListener("popstate", onNavigate);
  }, []);

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

  useEffect(() => {
    if (state.kind !== "authenticated") return;
    let cancelled = false;
    const check = () => {
      getTcbsStatus()
        .then((status) => { if (!cancelled) setTcbsAuthRequired(status.state === "AUTH_REQUIRED"); })
        .catch(() => { /* transient check; keep last known banner state */ });
    };
    check();
    const interval = window.setInterval(check, TCBS_STATUS_POLL_MS);
    return () => { cancelled = true; window.clearInterval(interval); };
  }, [state.kind]);

  if (state.kind === "loading") {
    return (
      <main className="app-shell" aria-busy="true">
        <div className="loading-state">
          <div className="loading-spinner"></div>
          <p>Đang kiểm tra phiên riêng tư…</p>
        </div>
      </main>
    );
  }
  if (state.kind === "error") {
    return (
      <main className="app-shell">
        <div className="error-card">
          <p role="alert">Không thể kiểm tra phiên đăng nhập. Hãy thử tải lại trang.</p>
        </div>
      </main>
    );
  }
  if (state.kind === "anonymous") {
    return <LoginForm onAuthenticated={(session) => setState({ kind: "authenticated", session })} />;
  }

  const isHome = pathname === "/";
  const isScreener = pathname.startsWith("/screener");
  const isStrategies = pathname.startsWith("/strategies");
  const isPortfolios = pathname.startsWith("/portfolios");
  const isWatchlists = pathname.startsWith("/watchlists");
  const isResearch = pathname.startsWith("/research");
  const isAnalyst = pathname.startsWith("/analyst");
  const isTcbsRenewal = pathname.startsWith("/tcbs-renewal");

  return (
    <>
      <nav className="top-nav">
        <div className="nav-brand-group">
          <a
            href="/"
            className="brand-section"
            onClick={(e) => {
              e.preventDefault();
              window.history.pushState({}, "", "/");
              window.dispatchEvent(new PopStateEvent("popstate"));
            }}
          >
            <div className="brand-icon">F</div>
            <span className="brand-logo">FINVERA</span>
            <span className="brand-tag">TERMINAL</span>
          </a>

          <div className="nav-links">
            <a
              href="/"
              className={`nav-link ${isHome ? "active" : ""}`}
              onClick={(e) => {
                e.preventDefault();
                window.history.pushState({}, "", "/");
                window.dispatchEvent(new PopStateEvent("popstate"));
              }}
            >
              <span className="nav-icon">📊</span>
              <span>Thị trường</span>
            </a>
            <a
              href="/screener"
              className={`nav-link ${isScreener ? "active" : ""}`}
              onClick={(e) => {
                e.preventDefault();
                window.history.pushState({}, "", "/screener");
                window.dispatchEvent(new PopStateEvent("popstate"));
              }}
            >
              <span className="nav-icon">🔍</span>
              <span>Bộ lọc CP</span>
            </a>
            <a
              href="/strategies"
              className={`nav-link ${isStrategies ? "active" : ""}`}
              onClick={(e) => {
                e.preventDefault();
                window.history.pushState({}, "", "/strategies");
                window.dispatchEvent(new PopStateEvent("popstate"));
              }}
            >
              <span className="nav-icon">⚡</span>
              <span>Chiến lược</span>
            </a>
            <a
              href="/portfolios"
              className={`nav-link ${isPortfolios ? "active" : ""}`}
              onClick={(e) => {
                e.preventDefault();
                window.history.pushState({}, "", "/portfolios");
                window.dispatchEvent(new PopStateEvent("popstate"));
              }}
            >
              <span className="nav-icon">💼</span>
              <span>Danh mục</span>
            </a>
            <a
              href="/watchlists"
              className={`nav-link ${isWatchlists ? "active" : ""}`}
              onClick={(e) => {
                e.preventDefault();
                window.history.pushState({}, "", "/watchlists");
                window.dispatchEvent(new PopStateEvent("popstate"));
              }}
            >
              <span className="nav-icon">⭐</span>
              <span>Watchlist</span>
            </a>
            <a
              href="/research"
              className={`nav-link ${isResearch ? "active" : ""}`}
              onClick={(e) => {
                e.preventDefault();
                window.history.pushState({}, "", "/research");
                window.dispatchEvent(new PopStateEvent("popstate"));
              }}
            >
              <span className="nav-icon">📚</span>
              <span>Nghiên cứu & RAG</span>
            </a>
            <a
              href="/analyst"
              className={`nav-link ai-nav-link ${isAnalyst ? "active" : ""}`}
              onClick={(e) => {
                e.preventDefault();
                window.history.pushState({}, "", "/analyst");
                window.dispatchEvent(new PopStateEvent("popstate"));
              }}
            >
              <span className="nav-icon">🤖</span>
              <span>AI Analyst</span>
            </a>
            <a
              href="/tcbs-renewal"
              className={`nav-link ${isTcbsRenewal ? "active" : ""}`}
              onClick={(e) => {
                e.preventDefault();
                window.history.pushState({}, "", "/tcbs-renewal");
                window.dispatchEvent(new PopStateEvent("popstate"));
              }}
            >
              <span className="nav-icon">🔑</span>
              <span>TCBS Live</span>
              {tcbsAuthRequired ? (
                <span
                  aria-label="Cần xác thực lại"
                  style={{
                    display: "inline-block", width: "8px", height: "8px", borderRadius: "50%",
                    background: "var(--color-down)", marginLeft: "4px",
                  }}
                ></span>
              ) : null}
            </a>
          </div>
        </div>

        <header className="session-bar">
          <span className="user-badge">Phiên riêng tư: {state.session.username}</span>
          <button
            type="button"
            className="btn-logout"
            onClick={() =>
              logoutOwner()
                .then(() => setState({ kind: "anonymous" }))
                .catch(() => setState({ kind: "error" }))
            }
          >
            Đăng xuất
          </button>
        </header>
      </nav>
      {tcbsAuthRequired && !isTcbsRenewal ? (
        <div
          role="alert"
          style={{
            display: "flex", alignItems: "center", justifyContent: "space-between", gap: "12px",
            padding: "10px 20px", background: "var(--color-down-bg)", borderBottom: "1px solid var(--color-down-border)",
            color: "var(--color-down)",
          }}
        >
          <span>Phiên TCBS đã hết hạn hoặc chưa xác thực — giá thời gian thực đang không cập nhật.</span>
          <button
            type="button"
            className="btn-primary"
            style={{ padding: "6px 14px", whiteSpace: "nowrap" }}
            onClick={() => navigate("/tcbs-renewal")}
          >
            Xác thực ngay
          </button>
        </div>
      ) : null}
      {children}
    </>
  );
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
      setError(
        reason instanceof OwnerAccessApiError && reason.status === 429
          ? "Đã vượt quá số lần thử. Hãy chờ rồi thử lại."
          : "Đăng nhập không thành công."
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="login-container">
      <main className="owner-login-card">
        <p className="eyebrow">FINVERA · PRIVATE ACCESS</p>
        <h1>Đăng nhập riêng tư</h1>
        <form className="owner-login" onSubmit={submit}>
          <label>
            Username
            <input name="username" autoComplete="username" required maxLength={128} />
          </label>
          <label>
            Password
            <input name="password" type="password" autoComplete="current-password" required maxLength={256} />
          </label>
          {error ? <p role="alert">{error}</p> : null}
          <button type="submit" className="btn-primary" disabled={submitting}>
            {submitting ? "Đang đăng nhập…" : "Đăng nhập"}
          </button>
        </form>
      </main>
    </div>
  );
}
