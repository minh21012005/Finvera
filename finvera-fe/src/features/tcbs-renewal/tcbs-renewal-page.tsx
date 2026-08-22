import { type FormEvent, useState } from "react";
import { navigate } from "../../router";
import { renewTcbsSession, TcbsRenewalApiError, type TcbsOtpMethod } from "./api/tcbs-renewal";
import { KeyRound, ShieldCheck, CheckCircle2, AlertTriangle } from "lucide-react";

export function TcbsRenewalPage() {
  const [otpMethod, setOtpMethod] = useState<TcbsOtpMethod>("totp");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const otp = String(form.get("otp") ?? "").trim();
    setSubmitting(true);
    setError(null);
    setSuccess(false);
    try {
      await renewTcbsSession(otpMethod, otp);
      setSuccess(true);
      event.currentTarget.reset();
    } catch (reason: unknown) {
      setError(errorMessage(reason));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="app-shell">
      <header className="page-header" style={{ marginBottom: "24px" }}>
        <button type="button" className="back-link" onClick={() => navigate("/")}>
          ← Trang chủ
        </button>
        <p className="eyebrow">FINVERA · LIVE DATA</p>
        <h1>Xác thực phiên TCBS</h1>
        <p style={{ color: "var(--text-secondary)", margin: 0, fontSize: "0.875rem" }}>
          Cho phép backend lấy giá thị trường thời gian thực trong tối đa 8 giờ. Chỉ chủ hệ thống mới
          thấy và dùng được trang này.
        </p>
      </header>

      <div className="tcbs-card-container">
        <div className="tcbs-renewal-card">
          <div className="card-header-icon">
            <KeyRound size={24} className="text-cyan-400" />
            <div>
              <h3>Cấp quyền truy cập Real-time</h3>
              <p>Nhập mã OTP từ ứng dụng TCInvest hoặc SMS</p>
            </div>
          </div>

          <form onSubmit={submit} className="tcbs-form">
            <label>
              Phương thức OTP
              <select
                value={otpMethod}
                onChange={(e) => setOtpMethod(e.target.value as TcbsOtpMethod)}
              >
                <option value="totp">Mã TOTP (app TCInvest)</option>
                <option value="email-sms">Email / SMS</option>
              </select>
            </label>

            <label>
              Mã OTP hiện tại
              <input
                name="otp"
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={12}
                required
                placeholder="Nhập mã 6 số"
              />
            </label>

            {error ? (
              <div role="alert" className="error-banner">
                <AlertTriangle size={16} className="shrink-0" />
                <span>{error}</span>
              </div>
            ) : null}

            {success ? (
              <div role="status" className="success-banner">
                <CheckCircle2 size={16} className="shrink-0" />
                <span>Đã xác thực thành công. Giá thời gian thực sẽ cập nhật trong vòng một chu kỳ polling.</span>
              </div>
            ) : null}

            <button type="submit" className="btn-primary" disabled={submitting}>
              <ShieldCheck size={16} style={{ marginRight: 6 }} />
              {submitting ? "Đang xác thực…" : "Xác thực"}
            </button>
          </form>
        </div>
      </div>
    </main>
  );
}

function errorMessage(reason: unknown): string {
  if (reason instanceof TcbsRenewalApiError) {
    if (reason.reasonCode === "PROVIDER_AUTH_REQUIRED") {
      return "Backend chưa ở chế độ live TCBS, hoặc chưa cấu hình API key. Kiểm tra FINVERA_MARKET_PROVIDER_MODE/FINVERA_TCBS_API_KEY.";
    }
    return reason.detail ?? "Mã OTP không hợp lệ hoặc đã hết hạn. Hãy thử lại với mã mới nhất.";
  }
  return "Không thể xác thực lúc này. Kiểm tra kết nối rồi thử lại.";
}
