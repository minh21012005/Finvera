import { type FormEvent, type ReactNode, useEffect, useState } from "react";
import {
  getOwnerSession,
  loginOwner,
  logoutOwner,
  OwnerAccessApiError,
  type OwnerSession,
} from "./api/owner-access";
import {
  BarChart3,
  SlidersHorizontal,
  Zap,
  Briefcase,
  Star,
  BookOpen,
  Bot,
  LogOut,
  Radio,
} from "lucide-react";

type State =
  | { kind: "loading" }
  | { kind: "anonymous" }
  | { kind: "authenticated"; session: OwnerSession }
  | { kind: "error" };

export function OwnerAccessGate({ children }: { children: ReactNode }) {
  const [state, setState] = useState<State>({ kind: "loading" });
  const [pathname, setPathname] = useState(() => window.location.pathname);

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
    <div className="terminal-root">
      <nav className="top-nav" aria-label="Thanh điều hướng chính">
        <div className="nav-container">
          {/* Tầng 1: Brand + Market Status + Quick Ticker + User */}
          <div className="nav-top-row">
            <div className="nav-brand-group">
              <NavLink href="/" active={isHome} className="brand-section">
                <div className="brand-icon">F</div>
                <div className="brand-title-wrap">
                  <div className="brand-logo-line">
                    <span className="brand-logo">FINVERA</span>
                    <span className="brand-sublogo">Invest AI</span>
                  </div>
                  <span className="brand-tagline">VIETNAM QUANT TERMINAL</span>
                </div>
              </NavLink>
            </div>

            <div className="nav-utility-group">
              <header className="session-bar">
                <span className="user-badge">
                  <span>Phiên riêng tư: {state.session.username}</span>
                </span>
                <button
                  type="button"
                  className="btn-logout"
                  onClick={() =>
                    logoutOwner()
                      .then(() => setState({ kind: "anonymous" }))
                      .catch(() => setState({ kind: "error" }))
                  }
                >
                  <LogOut size={13} style={{ marginRight: 4 }} />
                  Đăng xuất
                </button>
              </header>
            </div>
          </div>

          {/* Tầng 2: Menu Tabs Điều Hướng */}
          <div className="nav-links-row">
            <div className="nav-links" role="tablist">
              <NavLink href="/" active={isHome}>
                <BarChart3 className="nav-icon-svg" size={15} />
                <span>Tổng quan thị trường & AI Radar</span>
              </NavLink>
              <NavLink href="/screener" active={isScreener}>
                <SlidersHorizontal className="nav-icon-svg" size={15} />
                <span>Bộ lọc & Phân tích chuyên sâu</span>
              </NavLink>
              <NavLink href="/strategies" active={isStrategies}>
                <Zap className="nav-icon-svg" size={15} />
                <span>Khuyến nghị & Tín hiệu AI</span>
              </NavLink>
              <NavLink href="/portfolios" active={isPortfolios}>
                <Briefcase className="nav-icon-svg" size={15} />
                <span>Quản trị danh mục & Rủi ro</span>
              </NavLink>
              <NavLink href="/watchlists" active={isWatchlists}>
                <Star className="nav-icon-svg" size={15} />
                <span>Theo dõi (Watchlist)</span>
              </NavLink>
              <NavLink href="/research" active={isResearch}>
                <BookOpen className="nav-icon-svg" size={15} />
                <span>Nghiên cứu & RAG</span>
              </NavLink>
              <NavLink href="/analyst" active={isAnalyst} className="ai-nav-link">
                <Bot className="nav-icon-svg" size={15} />
                <span>AI Analyst</span>
              </NavLink>
              <NavLink href="/tcbs-renewal" active={isTcbsRenewal}>
                <Radio className="nav-icon-svg" size={15} />
                <span>Live data</span>
              </NavLink>
            </div>
          </div>
        </div>
      </nav>

      <div className="terminal-content">
        {children}
      </div>

      <footer className="terminal-status-bar" role="contentinfo">
        <div className="status-bar-container">
          <div className="status-item">
            <span>Finvera Quant Terminal · Hệ thống hỗ trợ ra quyết định định lượng</span>
          </div>
          <div className="status-bar-spacer"></div>
          <div className="status-copyright">
            © 2026 Finvera. Quyền riêng tư & Bảo mật đa tầng.
          </div>
        </div>
      </footer>
    </div>
  );
}

function NavLink({
  href,
  active,
  className,
  children,
}: {
  href: string;
  active: boolean;
  className?: string;
  children: ReactNode;
}) {
  return (
    <a
      href={href}
      className={`${className ? `${className} ` : ""}nav-link ${active ? "active" : ""}`.trim()}
      onClick={(event) => {
        event.preventDefault();
        window.history.pushState({}, "", href);
        window.dispatchEvent(new PopStateEvent("popstate"));
      }}
    >
      {children}
    </a>
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
