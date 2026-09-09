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
    <div className="transaction-form-card quant-terminal-card">
      <div className="tx-form-header">
        <h2 className="tx-form-title">Ghi nhận giao dịch mới</h2>
        <span className="tx-form-hint">Dữ liệu được lưu trữ trực tiếp vào sổ cái bất biến FIFO</span>
      </div>

      {error && (
        <div role="alert" className="tx-form-error-banner">
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} className="tx-form-body">
        {/* Type tabs */}
        <div className="tx-type-tabs" role="tablist" aria-label="Loại giao dịch">
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
                className={`tx-type-tab-btn ${t.toLowerCase()} ${active ? "active" : ""}`}
              >
                {labels[t]}
              </button>
            );
          })}
        </div>

        {/* Inputs Grid */}
        <div className="tx-inputs-grid">
          {(type === "BUY" || type === "SELL") && (
            <>
              <div className="tx-field-group">
                <label htmlFor="tx-input-symbol" className="tx-field-lbl">Mã cổ phiếu</label>
                <input
                  id="tx-input-symbol"
                  type="text"
                  placeholder="VD: FPT, VNM, HPG"
                  value={symbol}
                  onChange={(e) => setSymbol(e.target.value.toUpperCase())}
                  required
                  className="tx-field-input font-mono uppercase"
                />
              </div>
              <div className="tx-field-group">
                <label htmlFor="tx-input-quantity" className="tx-field-lbl">Khối lượng</label>
                <input
                  id="tx-input-quantity"
                  type="number"
                  placeholder="Số lượng"
                  value={quantity}
                  onChange={(e) => setQuantity(e.target.value)}
                  min="1"
                  step="1"
                  required
                  className="tx-field-input font-mono"
                />
              </div>
              <div className="tx-field-group">
                <label htmlFor="tx-input-price" className="tx-field-lbl">Giá khớp (VNĐ)</label>
                <input
                  id="tx-input-price"
                  type="number"
                  placeholder="Giá khớp"
                  value={price}
                  onChange={(e) => setPrice(e.target.value)}
                  min="0"
                  step="100"
                  required
                  className="tx-field-input font-mono"
                />
              </div>
              <div className="tx-field-group">
                <label htmlFor="tx-input-fee" className="tx-field-lbl">Phí giao dịch (VNĐ)</label>
                <input
                  id="tx-input-fee"
                  type="number"
                  placeholder="Phí giao dịch"
                  value={fee}
                  onChange={(e) => setFee(e.target.value)}
                  min="0"
                  className="tx-field-input font-mono"
                />
              </div>
            </>
          )}

          {(type === "DEPOSIT" || type === "WITHDRAW") && (
            <div className="tx-field-group">
              <label htmlFor="tx-input-amount" className="tx-field-lbl">Số tiền (VNĐ)</label>
              <input
                id="tx-input-amount"
                type="number"
                placeholder="Nhập số tiền giao dịch"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                min="1"
                required
                className="tx-field-input font-mono"
              />
            </div>
          )}

          <div className="tx-field-group">
            <label htmlFor="tx-input-executed-at" className="tx-field-lbl">Thời gian khớp lệnh</label>
            <input
              id="tx-input-executed-at"
              type="datetime-local"
              value={executedAt}
              onChange={(e) => setExecutedAt(e.target.value)}
              required
              className="tx-field-input font-mono"
            />
          </div>
        </div>

        <div className="tx-form-footer">
          <button
            type="submit"
            disabled={submitting}
            className={`btn-tx-submit ${type.toLowerCase()}`}
          >
            {submitting ? "Đang ghi nhận..." : `Xác nhận ${type === "BUY" ? "Mua" : type === "SELL" ? "Bán" : type === "DEPOSIT" ? "Nạp tiền" : "Rút tiền"}`}
          </button>
        </div>
      </form>
    </div>
  );
}
