import { type FormEvent, useEffect, useState } from "react";
import { navigate } from "../../router";
import { getTcbsStatus, renewTcbsSession, TcbsRenewalApiError, type TcbsStatus } from "./api/tcbs-renewal";
import {
  ArrowLeft,
  ShieldCheck,
  Radio,
  Clock,
  KeyRound,
  CheckCircle2,
  AlertCircle,
  Activity,
  Lock,
} from "lucide-react";

export function TcbsRenewalPage() {
  const [status, setStatus] = useState<TcbsStatus | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [otp, setOtp] = useState("");
  const [message, setMessage] = useState<{ type: "success" | "error"; text: string } | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    const refresh = () =>
      getTcbsStatus(controller.signal)
        .then(setStatus)
        .catch(() => {
          if (!controller.signal.aborted) setStatus(null);
        });
    void refresh();
    const timer = window.setInterval(refresh, 5_000);
    return () => {
      window.clearInterval(timer);
      controller.abort();
    };
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const cleanOtp = otp.trim();
    if (!cleanOtp) return;
    setSubmitting(true);
    setMessage(null);
    try {
      await renewTcbsSession(cleanOtp);
      setStatus({ state: "DEGRADED", reasonCode: "CONNECTING" });
      setMessage({
        type: "success",
        text: "Đã xác thực. Backend đang kết nối TCBS Thesis và sẽ nhận dữ liệu tự động.",
      });
      setOtp("");
    } catch (error) {
      setMessage({
        type: "error",
        text:
          error instanceof TcbsRenewalApiError
            ? error.detail ?? "Mã OTP không hợp lệ, đã hết hạn hoặc live overlay chưa được cấu hình."
            : "Không thể xác thực TCBS lúc này.",
      });
    } finally {
      setSubmitting(false);
    }
  }

  const isConnected = status?.state === "READY";
  const isConnecting = status?.reasonCode === "CONNECTING" || status?.state === "DEGRADED";

  return (
    <main className="app-shell">
      <div className="tcbs-renewal-container">
        <header className="page-header">
          <button
            type="button"
            className="btn-link flex items-center gap-1.5 mb-3"
            onClick={() => navigate("/")}
          >
            <ArrowLeft size={15} />
            <span>Về tổng quan thị trường</span>
          </button>
          <p className="eyebrow">FINVERA · PRIVATE REALTIME DATA</p>
          <div className="flex items-center justify-between flex-wrap gap-4 mt-1">
            <div>
              <h1>Kết nối TCBS Thesis Live</h1>
              <p className="text-secondary text-sm mt-1">
                Luồng dữ liệu bảng giá và khớp lệnh thời gian thực qua kênh WebSocket riêng tư.
              </p>
            </div>
            <div className="tcbs-status-badge-container">
              {isConnected ? (
                <span className="tcbs-live-badge live">
                  <span className="live-dot-pulse"></span>
                  <span>ĐANG HOẠT ĐỘNG (READY)</span>
                </span>
              ) : isConnecting ? (
                <span className="tcbs-live-badge connecting">
                  <Activity size={14} className="animate-spin" />
                  <span>ĐANG KẾT NỐI...</span>
                </span>
              ) : (
                <span className="tcbs-live-badge expired">
                  <AlertCircle size={14} />
                  <span>CẦN XÁC THỰC LẠI</span>
                </span>
              )}
            </div>
          </div>
        </header>

        <div className="tcbs-layout-grid">
          {/* Left: Info / Telemetry Card */}
          <section className="tcbs-info-card">
            <div className="tcbs-card-header-icon">
              <Radio size={20} className="text-cyan-400" />
              <h3>Thông tin kết nối</h3>
            </div>
            <div className="tcbs-info-list">
              <div className="tcbs-info-item">
                <span className="info-label">Nguồn dữ liệu</span>
                <span className="info-value font-mono">TCBS Thesis WebSocket</span>
              </div>
              <div className="tcbs-info-item">
                <span className="info-label">Giao thức</span>
                <span className="info-value font-mono">WSS Stream (Normal)</span>
              </div>
              <div className="tcbs-info-item">
                <span className="info-label">Thời hạn phiên</span>
                <span className="info-value flex items-center gap-1.5">
                  <Clock size={13} className="text-slate-400" />
                  <span>8 giờ / lần xác thực</span>
                </span>
              </div>
              <div className="tcbs-info-item">
                <span className="info-label">Bảo mật</span>
                <span className="info-value flex items-center gap-1.5 text-emerald-400 font-semibold">
                  <ShieldCheck size={15} />
                  <span>Mã hóa End-to-End</span>
                </span>
              </div>
            </div>

            <div className="tcbs-guide-box">
              <div className="flex items-start gap-2.5">
                <KeyRound size={16} className="text-cyan-400 shrink-0 mt-0.5" />
                <div className="text-xs text-slate-300 leading-relaxed">
                  <strong>Hướng dẫn:</strong> Mở ứng dụng <strong>TCInvest</strong> trên điện thoại &gt; Chọn biểu tượng <strong>Khiên bảo mật (Smart OTP)</strong> để lấy mã 6 chữ số hiện tại.
                </div>
              </div>
            </div>
          </section>

          {/* Right: Modern OTP Authentication Panel */}
          <section className="tcbs-auth-panel">
            <div className="tcbs-card-header-icon">
              <Lock size={20} className="text-emerald-400" />
              <h3>Gia hạn phiên làm việc</h3>
            </div>

            <form className="tcbs-auth-form" onSubmit={submit}>
              <div>
                <label htmlFor="tcbs-totp-input" className="form-label flex items-center justify-between">
                  <span>Mã TOTP xác thực từ TCInvest</span>
                  <span className="text-xs text-cyan-400 font-mono">6 chữ số</span>
                </label>
                <div className="otp-input-wrapper">
                  <input
                    id="tcbs-totp-input"
                    name="otp"
                    type="text"
                    inputMode="numeric"
                    autoComplete="one-time-code"
                    maxLength={6}
                    placeholder="000000"
                    value={otp}
                    onChange={(e) => setOtp(e.target.value.replace(/\D/g, "").slice(0, 6))}
                    required
                    autoFocus
                    className="tcbs-otp-input"
                  />
                </div>
              </div>

              {message ? (
                <div
                  role="status"
                  className={`tcbs-status-message ${message.type === "success" ? "success" : "error"}`}
                >
                  {message.type === "success" ? (
                    <CheckCircle2 size={16} className="shrink-0 text-emerald-400" />
                  ) : (
                    <AlertCircle size={16} className="shrink-0 text-rose-400" />
                  )}
                  <span>{message.text}</span>
                </div>
              ) : null}

              <button
                type="submit"
                className="btn-connect-live"
                disabled={submitting || otp.length < 6}
              >
                {submitting ? (
                  <>
                    <Activity size={16} className="animate-spin" />
                    <span>Đang kết nối...</span>
                  </>
                ) : (
                  <>
                    <ShieldCheck size={16} />
                    <span>Kết nối live</span>
                  </>
                )}
              </button>
            </form>
          </section>
        </div>
      </div>
    </main>
  );
}
