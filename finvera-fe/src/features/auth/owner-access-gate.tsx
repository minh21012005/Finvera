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
  Bell,
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
  const isPositionSizing = pathname.startsWith("/position-sizing");
  const isBacktest = pathname.startsWith("/backtests");
  const isAlerts = pathname.startsWith("/alerts");

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
                <NavLink href="/tcbs-renewal" active={isTcbsRenewal} className="live-nav-link">
                  <Radio className="nav-icon-svg" size={14} />
                  <span>Dữ liệu Live</span>
                </NavLink>
                <a
                  href="/guide.html"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="live-nav-link"
                  title="Mở cẩm nang hướng dẫn sử dụng Finvera"
                >
                  <BookOpen className="nav-icon-svg" size={14} />
                  <span>Hướng dẫn</span>
                </a>
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
                  <LogOut size={13} className="mr-1 inline-block" />
                  Đăng xuất
                </button>
              </header>
            </div>
          </div>

          {/* Tầng 2: Menu Điều Hướng Phân Nhóm Dàn Đều (Quant Segmented Bar) */}
          <div className="nav-links-row">
            <div className="nav-links" role="tablist">
              {/* Nhóm 1: Thị trường & Sàng lọc */}
              <div className="nav-group">
                <NavLink href="/" active={isHome}>
                  <BarChart3 className="nav-icon-svg" size={14} />
                  <span>Thị trường</span>
                </NavLink>
                <NavLink href="/screener" active={isScreener}>
                  <SlidersHorizontal className="nav-icon-svg" size={14} />
                  <span>Bộ lọc<span className="hidden 2xl:inline"> cổ phiếu</span></span>
                </NavLink>
              </div>

              {/* Nhóm 2: Định lượng & Chiến lược */}
              <div className="nav-group">
                <NavLink href="/strategies" active={isStrategies}>
                  <Zap className="nav-icon-svg" size={14} />
                  <span>Chiến lược<span className="hidden 2xl:inline"> định lượng</span></span>
                </NavLink>
                <NavLink href="/position-sizing" active={isPositionSizing}>
                  <SlidersHorizontal className="nav-icon-svg" size={14} />
                  <span>Quy mô vị thế</span>
                </NavLink>
                <NavLink href="/backtests" active={isBacktest}>
                  <BarChart3 className="nav-icon-svg" size={14} />
                  <span>Backtesting</span>
                </NavLink>
              </div>

              {/* Nhóm 3: Quản trị Tài sản */}
              <div className="nav-group">
                <NavLink href="/portfolios" active={isPortfolios}>
                  <Briefcase className="nav-icon-svg" size={14} />
                  <span><span className="hidden 2xl:inline">Quản lý </span>Danh mục</span>
                </NavLink>
                <NavLink href="/watchlists" active={isWatchlists}>
                  <Star className="nav-icon-svg" size={14} />
                  <span>Watchlist</span>
                </NavLink>
                <NavLink href="/alerts" active={isAlerts}>
                  <Bell className="nav-icon-svg" size={14} />
                  <span>Cảnh báo</span>
                </NavLink>
              </div>

              {/* Nhóm 4: Nghiên cứu & AI Co-pilot */}
              <div className="nav-group">
                <NavLink href="/research" active={isResearch}>
                  <BookOpen className="nav-icon-svg" size={14} />
                  <span>Nghiên cứu<span className="hidden 2xl:inline"> tài liệu</span></span>
                </NavLink>
                <NavLink href="/analyst" active={isAnalyst} className="ai-nav-link">
                  <Bot className="nav-icon-svg" size={14} />
                  <span>AI <span className="hidden 2xl:inline">Financial </span>Analyst</span>
                </NavLink>
              </div>
            </div>
          </div>
        </div>
      </nav>

      <div className="terminal-content">
        {children}
      </div>

      <footer className="terminal-status-bar" role="contentinfo">
        <div className="status-bar-container">
          <div className="status-item flex items-center gap-2">
            <span className="inline-block w-2 h-2 rounded-full bg-emerald-400 shadow-[0_0_8px_#00e599]" aria-hidden="true" />
            <span className="font-mono text-xs text-slate-300">
              <strong className="text-emerald-400">Data Gateway:</strong> Trực tuyến · HSX / HNX / UPCOM
            </span>
          </div>
          <div className="status-item hidden md:flex items-center gap-2">
            <span className="font-mono text-xs text-slate-500">
              Độ trễ: &lt;14ms · Engine: FinveraQuant v4.2.8
            </span>
          </div>
          <div className="status-bar-spacer" />
          <div className="status-copyright font-mono text-xs text-slate-400">
            © 2026 Finvera Terminal · Bảo mật & Quyền riêng tư đa tầng
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
  title,
}: {
  href: string;
  active: boolean;
  className?: string;
  children: ReactNode;
  title?: string;
}) {
  return (
    <a
      href={href}
      title={title}
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
