import { type FormEvent, useState } from "react";
import { navigate } from "../../router";
import { renewTcbsSession, TcbsRenewalApiError, type TcbsOtpMethod } from "./api/tcbs-renewal";

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
        <p style={{ color: "var(--text-secondary)", margin: 0 }}>
          Cho phép backend lấy giá thị trường thời gian thực trong tối đa 8 giờ. Chỉ chủ hệ thống mới
          thấy và dùng được trang này.
        </p>
      </header>

      <div
        className="card"
        style={{
          padding: "20px",
          background: "var(--bg-card)",
          border: "1px solid var(--border-color)",
          borderRadius: "8px",
          maxWidth: "480px",
        }}
      >
        <form onSubmit={submit} style={{ display: "flex", flexDirection: "column", gap: "14px" }}>
          <label style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
            Phương thức OTP
            <select
              value={otpMethod}
              onChange={(e) => setOtpMethod(e.target.value as TcbsOtpMethod)}
              style={{
                padding: "10px 14px",
                background: "var(--bg-main)",
                border: "1px solid var(--border-color)",
                borderRadius: "6px",
                color: "var(--text-primary)",
              }}
            >
              <option value="totp">Mã TOTP (app TCInvest)</option>
              <option value="email-sms">Email / SMS</option>
            </select>
          </label>

          <label style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
            Mã OTP hiện tại
            <input
              name="otp"
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={12}
              required
              placeholder="Nhập mã 6 số"
              style={{
                padding: "10px 14px",
                background: "var(--bg-main)",
                border: "1px solid var(--border-color)",
                borderRadius: "6px",
                color: "var(--text-primary)",
              }}
            />
          </label>

          {error ? (
            <div
              role="alert"
              className="error-banner"
              style={{
                padding: "12px",
                background: "var(--color-down-bg)",
                border: "1px solid var(--color-down-border)",
                borderRadius: "6px",
                color: "var(--color-down)",
              }}
            >
              {error}
            </div>
          ) : null}

          {success ? (
            <div
              role="status"
              style={{
                padding: "12px",
                background: "var(--color-up-bg)",
                border: "1px solid var(--color-up-border)",
                borderRadius: "6px",
                color: "var(--color-up)",
              }}
            >
              Đã xác thực thành công. Giá thời gian thực sẽ cập nhật trong vòng một chu kỳ polling.
            </div>
          ) : null}

          <button type="submit" className="btn-primary" disabled={submitting}>
            {submitting ? "Đang xác thực…" : "Xác thực"}
          </button>
        </form>
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
