import { type FormEvent, useEffect, useState } from "react";
import { navigate } from "../../router";
import { getTcbsStatus, renewTcbsSession, TcbsRenewalApiError, type TcbsStatus } from "./api/tcbs-renewal";

export function TcbsRenewalPage() {
  const [status, setStatus] = useState<TcbsStatus | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    const refresh = () => getTcbsStatus(controller.signal).then(setStatus).catch(() => {
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
    const form = event.currentTarget;
    const otp = String(new FormData(form).get("otp") ?? "").trim();
    setSubmitting(true); setMessage(null);
    try {
      await renewTcbsSession(otp);
      setStatus({ state: "DEGRADED", reasonCode: "CONNECTING" });
      setMessage("Đã xác thực. Backend đang kết nối TCBS Thesis và sẽ nhận dữ liệu tự động.");
      form.reset();
    } catch (error) {
      setMessage(error instanceof TcbsRenewalApiError
        ? error.detail ?? "OTP không hợp lệ, đã hết hạn hoặc live overlay chưa được cấu hình."
        : "Không thể xác thực TCBS lúc này.");
    } finally { setSubmitting(false); }
  }

  return <main className="app-shell">
    <header className="page-header">
      <button type="button" className="back-link" onClick={() => navigate("/")}>← Thị trường</button>
      <p className="eyebrow">FINVERA · PRIVATE LIVE DATA</p>
      <h1>Kết nối TCBS Thesis</h1>
      <p>Trạng thái: {status ? `${status.state} · ${status.reasonCode}` : "Chưa xác định"}</p>
    </header>
    <div className="tcbs-card-container"><div className="tcbs-renewal-card">
      <h3>Gia hạn phiên live tối đa 8 giờ</h3>
      <p>API key nằm ở backend. OTP chỉ được dùng một lần và không được lưu.</p>
      <form className="tcbs-form" onSubmit={submit}>
        <label>Mã TOTP hiện tại từ TCInvest
          <input name="otp" inputMode="numeric" autoComplete="one-time-code" maxLength={12} required />
        </label>
        {message ? <p role="status">{message}</p> : null}
        <button type="submit" className="btn-primary" disabled={submitting}>
          {submitting ? "Đang xác thực…" : "Kết nối live"}
        </button>
      </form>
    </div></div>
  </main>;
}
