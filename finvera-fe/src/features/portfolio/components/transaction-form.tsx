import { useState } from "react";
import {
  generateIdempotencyKey,
  PortfolioApiError,
  recordTransaction,
  type RecordTransactionRequest,
  type Transaction,
} from "../api/portfolio";

interface TransactionFormProps {
  portfolioId: string;
  onSuccess: (tx: Transaction) => void;
}

function getLocalDatetimeString() {
  const d = new Date();
  const offset = d.getTimezoneOffset() * 60000;
  return new Date(d.getTime() - offset).toISOString().slice(0, 16);
}

export function TransactionForm({ portfolioId, onSuccess }: TransactionFormProps) {
  const [type, setType] = useState<"BUY" | "SELL" | "DEPOSIT" | "WITHDRAW">("BUY");
  const [symbol, setSymbol] = useState("");
  const [quantity, setQuantity] = useState("");
  const [price, setPrice] = useState("");
  const [fee, setFee] = useState("0");
  const [amount, setAmount] = useState("");
  const [executedAt, setExecutedAt] = useState(getLocalDatetimeString);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [currentIdempotencyKey, setCurrentIdempotencyKey] = useState(() => generateIdempotencyKey());

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);

    const execInstant = new Date(executedAt).toISOString();
    const req: RecordTransactionRequest = {
      transactionType: type,
      executedAt: execInstant,
      fee: fee.trim() || "0",
    };

    if (type === "BUY" || type === "SELL") {
      if (!symbol.trim()) {
        setError("Vui lòng nhập mã cổ phiếu.");
        setSubmitting(false);
        return;
      }
      if (!quantity.trim() || parseFloat(quantity) <= 0) {
        setError("Vui lòng nhập khối lượng hợp lệ (> 0).");
        setSubmitting(false);
        return;
      }
      if (!price.trim() || parseFloat(price) <= 0) {
        setError("Vui lòng nhập giá khớp hợp lệ (> 0).");
        setSubmitting(false);
        return;
      }
      req.instrumentSymbol = symbol.trim().toUpperCase();
      req.quantity = quantity.trim();
      req.price = price.trim();
    } else {
      if (!amount.trim() || parseFloat(amount) <= 0) {
        setError("Vui lòng nhập số tiền hợp lệ (> 0).");
        setSubmitting(false);
        return;
      }
      req.amount = amount.trim();
    }

    try {
      const recorded = await recordTransaction(portfolioId, req, currentIdempotencyKey);
      // Reset form and generate fresh key for next action
      setSymbol("");
      setQuantity("");
      setPrice("");
      setFee("0");
      setAmount("");
      setCurrentIdempotencyKey(generateIdempotencyKey());
      onSuccess(recorded);
    } catch (err) {
      if (err instanceof PortfolioApiError) {
        switch (err.reasonCode) {
          case "UNSUPPORTED_INSTRUMENT":
            setError(`Mã cổ phiếu "${symbol.toUpperCase()}" không nằm trong hệ thống giao dịch.`);
            break;
          case "INSUFFICIENT_POSITION":
            setError("Không đủ số lượng cổ phiếu khả dụng để thực hiện lệnh BÁN.");
            break;
          case "INSUFFICIENT_CASH_BALANCE":
            setError("Số dư tiền mặt không đủ để thực hiện giao dịch này.");
            break;
          case "DUPLICATE_SUBMISSION":
            setError("Giao dịch đã được ghi nhận trước đó (trùng mã Idempotency-Key).");
            break;
          default:
            setError(err.message || "Giao dịch không hợp lệ.");
        }
      } else {
        setError("Không thể ghi nhận giao dịch lúc này. Vui lòng thử lại.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="transaction-form-card card" style={{ padding: "20px", background: "var(--bg-card)", border: "1px solid var(--border-color)", borderRadius: "8px", marginBottom: "32px" }}>
      <h2 style={{ fontSize: "1.15rem", fontWeight: 700, marginBottom: "16px" }}>Ghi nhận giao dịch mới</h2>

      {error && (
        <div role="alert" style={{ marginBottom: "16px", padding: "10px 14px", background: "var(--color-down-bg)", border: "1px solid var(--color-down-border)", borderRadius: "6px", color: "var(--color-down)", fontSize: "0.9rem" }}>
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit}>
        {/* Type tabs */}
        <div style={{ display: "flex", gap: "8px", marginBottom: "16px" }}>
          {(["BUY", "SELL", "DEPOSIT", "WITHDRAW"] as const).map((t) => {
            const labels = {
              BUY: "MUA CỔ PHIẾU",
              SELL: "BÁN CỔ PHIẾU",
              DEPOSIT: "NẠP TIỀN",
              WITHDRAW: "RÚT TIỀN",
            };
            const active = type === t;
            return (
              <button
                key={t}
                type="button"
                onClick={() => {
                  setType(t);
                  setError(null);
                }}
                style={{
                  padding: "8px 16px",
                  borderRadius: "6px",
                  border: active ? "1px solid var(--color-accent)" : "1px solid var(--border-color)",
                  background: active ? "var(--color-accent-bg)" : "transparent",
                  color: active ? "var(--color-accent)" : "var(--text-secondary)",
                  fontWeight: active ? 700 : 500,
                  cursor: "pointer",
                  fontSize: "0.85rem",
                }}
              >
                {labels[t]}
              </button>
            );
          })}
        </div>

        {/* Inputs */}
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: "12px", marginBottom: "16px" }}>
          {(type === "BUY" || type === "SELL") && (
            <>
              <div>
                <label style={{ fontSize: "0.8rem", color: "var(--text-muted)", display: "block", marginBottom: "4px" }}>Mã cổ phiếu</label>
                <input
                  type="text"
                  placeholder="VD: FPT, VNM, HPG"
                  value={symbol}
                  onChange={(e) => setSymbol(e.target.value.toUpperCase())}
                  required
                  style={{ width: "100%", padding: "8px 12px", background: "var(--bg-main)", border: "1px solid var(--border-color)", borderRadius: "6px", color: "var(--text-primary)" }}
                />
              </div>
              <div>
                <label style={{ fontSize: "0.8rem", color: "var(--text-muted)", display: "block", marginBottom: "4px" }}>Khối lượng</label>
                <input
                  type="number"
                  placeholder="Số lượng"
                  value={quantity}
                  onChange={(e) => setQuantity(e.target.value)}
                  min="1"
                  step="1"
                  required
                  style={{ width: "100%", padding: "8px 12px", background: "var(--bg-main)", border: "1px solid var(--border-color)", borderRadius: "6px", color: "var(--text-primary)" }}
                />
              </div>
              <div>
                <label style={{ fontSize: "0.8rem", color: "var(--text-muted)", display: "block", marginBottom: "4px" }}>Giá khớp (VNĐ)</label>
                <input
                  type="number"
                  placeholder="Giá"
                  value={price}
                  onChange={(e) => setPrice(e.target.value)}
                  min="0"
                  step="100"
                  required
                  style={{ width: "100%", padding: "8px 12px", background: "var(--bg-main)", border: "1px solid var(--border-color)", borderRadius: "6px", color: "var(--text-primary)" }}
                />
              </div>
              <div>
                <label style={{ fontSize: "0.8rem", color: "var(--text-muted)", display: "block", marginBottom: "4px" }}>Phí giao dịch (VNĐ)</label>
                <input
                  type="number"
                  placeholder="Phí"
                  value={fee}
                  onChange={(e) => setFee(e.target.value)}
                  min="0"
                  style={{ width: "100%", padding: "8px 12px", background: "var(--bg-main)", border: "1px solid var(--border-color)", borderRadius: "6px", color: "var(--text-primary)" }}
                />
              </div>
            </>
          )}

          {(type === "DEPOSIT" || type === "WITHDRAW") && (
            <div>
              <label style={{ fontSize: "0.8rem", color: "var(--text-muted)", display: "block", marginBottom: "4px" }}>Số tiền (VNĐ)</label>
              <input
                type="number"
                placeholder="Nhập số tiền"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                min="1"
                required
                style={{ width: "100%", padding: "8px 12px", background: "var(--bg-main)", border: "1px solid var(--border-color)", borderRadius: "6px", color: "var(--text-primary)" }}
              />
            </div>
          )}

          <div>
            <label style={{ fontSize: "0.8rem", color: "var(--text-muted)", display: "block", marginBottom: "4px" }}>Thời gian khớp lệnh</label>
            <input
              type="datetime-local"
              value={executedAt}
              onChange={(e) => setExecutedAt(e.target.value)}
              required
              style={{ width: "100%", padding: "8px 12px", background: "var(--bg-main)", border: "1px solid var(--border-color)", borderRadius: "6px", color: "var(--text-primary)" }}
            />
          </div>
        </div>

        <button
          type="submit"
          disabled={submitting}
          style={{
            padding: "10px 24px",
            background: type === "BUY" || type === "DEPOSIT" ? "var(--color-up)" : "var(--color-accent)",
            color: "#0a0e17",
            fontWeight: 700,
            border: "none",
            borderRadius: "6px",
            cursor: "pointer",
          }}
        >
          {submitting ? "Đang ghi nhận..." : `Xác nhận ${type === "BUY" ? "Mua" : type === "SELL" ? "Bán" : type === "DEPOSIT" ? "Nạp tiền" : "Rút tiền"}`}
        </button>
      </form>
    </div>
  );
}
