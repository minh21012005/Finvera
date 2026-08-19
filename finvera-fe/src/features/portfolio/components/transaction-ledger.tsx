import { useState } from "react";
import {
  generateIdempotencyKey,
  PortfolioApiError,
  type Transaction,
  voidTransaction,
} from "../api/portfolio";

interface TransactionLedgerProps {
  portfolioId: string;
  transactions: Transaction[];
  onVoidSuccess: (voidedTx: Transaction) => void;
}

export function TransactionLedger({
  portfolioId,
  transactions,
  onVoidSuccess,
}: TransactionLedgerProps) {
  const [voidingId, setVoidingId] = useState<string | null>(null);
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleConfirmVoid(transactionId: string) {
    if (!reason.trim()) {
      setError("Vui lòng nhập lý do hủy giao dịch.");
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      const key = generateIdempotencyKey();
      const voidResult = await voidTransaction(
        portfolioId,
        transactionId,
        { reason: reason.trim() },
        key,
      );
      setVoidingId(null);
      setReason("");
      onVoidSuccess(voidResult);
    } catch (err) {
      if (err instanceof PortfolioApiError) {
        switch (err.reasonCode) {
          case "LOT_ALREADY_CONSUMED":
            setError("Không thể hủy lệnh MUA này vì các cổ phiếu đã được bán trong một lệnh BÁN sau đó.");
            break;
          case "ALREADY_VOIDED":
            setError("Giao dịch này đã được hủy trước đó.");
            break;
          default:
            setError(err.message || "Không thể hủy giao dịch.");
        }
      } else {
        setError("Không thể hủy giao dịch lúc này.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  // Find IDs of transactions that have been voided
  const voidedTargetIds = new Set(
    transactions
      .filter((t) => t.transactionType === "VOID" && t.voidsTransactionId)
      .map((t) => t.voidsTransactionId as string),
  );

  return (
    <div className="transaction-ledger-section">
      <h2 style={{ fontSize: "1.25rem", fontWeight: 700, marginBottom: "16px" }}>
        Sổ cái giao dịch (Bất biến)
      </h2>

      {error && (
        <div
          role="alert"
          style={{
            marginBottom: "16px",
            padding: "10px 14px",
            background: "var(--color-down-bg)",
            border: "1px solid var(--color-down-border)",
            borderRadius: "6px",
            color: "var(--color-down)",
            fontSize: "0.9rem",
          }}
        >
          {error}
        </div>
      )}

      {transactions.length === 0 ? (
        <div
          style={{
            padding: "24px",
            textAlign: "center",
            background: "var(--bg-card)",
            border: "1px solid var(--border-color)",
            borderRadius: "8px",
            color: "var(--text-secondary)",
          }}
        >
          Chưa có giao dịch nào được ghi nhận trong sổ cái.
        </div>
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table
            className="data-table"
            style={{
              width: "100%",
              borderCollapse: "collapse",
              background: "var(--bg-card)",
              border: "1px solid var(--border-color)",
              borderRadius: "8px",
              fontSize: "0.88rem",
            }}
          >
            <thead>
              <tr
                style={{
                  background: "var(--bg-header)",
                  borderBottom: "1px solid var(--border-color)",
                  textAlign: "left",
                }}
              >
                <th style={{ padding: "10px 14px" }}>#</th>
                <th style={{ padding: "10px 14px" }}>Thời gian khớp</th>
                <th style={{ padding: "10px 14px" }}>Loại</th>
                <th style={{ padding: "10px 14px" }}>Mã CK</th>
                <th style={{ padding: "10px 14px", textAlign: "right" }}>Số lượng</th>
                <th style={{ padding: "10px 14px", textAlign: "right" }}>Giá khớp</th>
                <th style={{ padding: "10px 14px", textAlign: "right" }}>Phí</th>
                <th style={{ padding: "10px 14px", textAlign: "right" }}>Số tiền</th>
                <th style={{ padding: "10px 14px" }}>Trạng thái</th>
                <th style={{ padding: "10px 14px", textAlign: "center" }}>Hành động</th>
              </tr>
            </thead>
            <tbody>
              {transactions.map((tx) => {
                const isVoid = tx.transactionType === "VOID";
                const isTargetVoided = voidedTargetIds.has(tx.id);
                const isActionable = !isVoid && !isTargetVoided;

                const typeLabels: Record<string, { label: string; color: string }> = {
                  BUY: { label: "MUA", color: "var(--color-up)" },
                  SELL: { label: "BÁN", color: "var(--color-down)" },
                  DEPOSIT: { label: "NẠP TIỀN", color: "var(--color-accent)" },
                  WITHDRAW: { label: "RÚT TIỀN", color: "var(--color-unchanged)" },
                  VOID: { label: "HỦY (VOID)", color: "var(--text-muted)" },
                };

                const typeInfo = typeLabels[tx.transactionType] || {
                  label: tx.transactionType,
                  color: "inherit",
                };

                return (
                  <tr
                    key={tx.id}
                    style={{
                      borderBottom: "1px solid var(--border-color)",
                      opacity: isTargetVoided ? 0.6 : 1,
                      textDecoration: isTargetVoided ? "line-through" : "none",
                    }}
                  >
                    <td style={{ padding: "10px 14px", color: "var(--text-muted)" }}>
                      {tx.sequenceNo}
                    </td>
                    <td style={{ padding: "10px 14px" }}>
                      {new Date(tx.executedAt).toLocaleString("vi-VN")}
                    </td>
                    <td style={{ padding: "10px 14px", fontWeight: 700, color: typeInfo.color }}>
                      {typeInfo.label}
                    </td>
                    <td style={{ padding: "10px 14px", fontWeight: 600 }}>
                      {tx.instrumentSymbol || "-"}
                    </td>
                    <td style={{ padding: "10px 14px", textAlign: "right" }}>
                      {tx.quantity ? Number(tx.quantity).toLocaleString("vi-VN") : "-"}
                    </td>
                    <td style={{ padding: "10px 14px", textAlign: "right" }}>
                      {tx.price ? `${Number(tx.price).toLocaleString("vi-VN")} đ` : "-"}
                    </td>
                    <td style={{ padding: "10px 14px", textAlign: "right" }}>
                      {tx.fee && tx.fee !== "0" ? `${Number(tx.fee).toLocaleString("vi-VN")} đ` : "-"}
                    </td>
                    <td style={{ padding: "10px 14px", textAlign: "right" }}>
                      {tx.amount ? `${Number(tx.amount).toLocaleString("vi-VN")} đ` : "-"}
                    </td>
                    <td style={{ padding: "10px 14px" }}>
                      {isVoid ? (
                        <span style={{ fontSize: "0.8rem", color: "var(--text-muted)" }}>
                          Hủy: {tx.voidReason}
                        </span>
                      ) : isTargetVoided ? (
                        <span
                          style={{
                            fontSize: "0.75rem",
                            padding: "2px 6px",
                            borderRadius: "4px",
                            background: "var(--color-down-bg)",
                            color: "var(--color-down)",
                          }}
                        >
                          ĐÃ BỊ HỦY
                        </span>
                      ) : (
                        <span
                          style={{
                            fontSize: "0.75rem",
                            padding: "2px 6px",
                            borderRadius: "4px",
                            background: "var(--color-up-bg)",
                            color: "var(--color-up)",
                          }}
                        >
                          HỢP LỆ
                        </span>
                      )}
                    </td>
                    <td style={{ padding: "10px 14px", textAlign: "center" }}>
                      {isActionable && (
                        <div>
                          {voidingId === tx.id ? (
                            <div style={{ display: "flex", gap: "6px", alignItems: "center" }}>
                              <input
                                type="text"
                                placeholder="Lý do hủy..."
                                value={reason}
                                onChange={(e) => setReason(e.target.value)}
                                style={{
                                  padding: "4px 8px",
                                  fontSize: "0.8rem",
                                  background: "var(--bg-main)",
                                  border: "1px solid var(--border-color)",
                                  borderRadius: "4px",
                                  color: "var(--text-primary)",
                                }}
                              />
                              <button
                                type="button"
                                disabled={submitting}
                                onClick={() => handleConfirmVoid(tx.id)}
                                style={{
                                  padding: "4px 8px",
                                  fontSize: "0.75rem",
                                  background: "var(--color-down)",
                                  color: "#fff",
                                  border: "none",
                                  borderRadius: "4px",
                                  cursor: "pointer",
                                }}
                              >
                                {submitting ? "..." : "Xác nhận"}
                              </button>
                              <button
                                type="button"
                                onClick={() => {
                                  setVoidingId(null);
                                  setReason("");
                                  setError(null);
                                }}
                                style={{
                                  padding: "4px 8px",
                                  fontSize: "0.75rem",
                                  background: "transparent",
                                  color: "var(--text-muted)",
                                  border: "none",
                                  cursor: "pointer",
                                }}
                              >
                                Hủy
                              </button>
                            </div>
                          ) : (
                            <button
                              type="button"
                              onClick={() => {
                                setVoidingId(tx.id);
                                setReason("");
                                setError(null);
                              }}
                              style={{
                                padding: "4px 10px",
                                fontSize: "0.75rem",
                                background: "transparent",
                                color: "var(--text-muted)",
                                border: "1px solid var(--border-color)",
                                borderRadius: "4px",
                                cursor: "pointer",
                              }}
                            >
                              Hủy GD (Void)
                            </button>
                          )}
                        </div>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
