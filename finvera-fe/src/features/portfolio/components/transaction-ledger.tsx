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
    <div className="transaction-ledger-section quant-terminal-card mt-6">
      <div className="ledger-header-row">
        <div className="ledger-title-group">
          <h2 className="ledger-title">Sổ Cái Giao Dịch (Bất Biến)</h2>
          <span className="ledger-sub-hint">
            Hạch toán tuần tự FIFO thời gian thực — giao dịch đã xác nhận không thể sửa đổi
          </span>
        </div>
        <span className="ledger-count-badge font-mono">
          {transactions.length} bản ghi
        </span>
      </div>

      {error && (
        <div role="alert" className="ledger-error-banner">
          {error}
        </div>
      )}

      {transactions.length === 0 ? (
        <div className="ledger-empty-state">
          Chưa có giao dịch nào được ghi nhận trong sổ cái.
        </div>
      ) : (
        <div className="port-table-wrap ledger-table-wrap">
          <table className="terminal-quant-table ledger-table">
            <thead>
              <tr>
                <th scope="col" className="text-center w-12">#</th>
                <th scope="col">Thời gian khớp</th>
                <th scope="col" className="text-center">Loại</th>
                <th scope="col">Mã CK</th>
                <th scope="col" className="text-right">Số lượng</th>
                <th scope="col" className="text-right">Giá khớp</th>
                <th scope="col" className="text-right">Phí</th>
                <th scope="col" className="text-right">Số tiền</th>
                <th scope="col" className="text-center">Trạng thái</th>
                <th scope="col" className="text-center">Hành động</th>
              </tr>
            </thead>
            <tbody>
              {transactions.map((tx) => {
                const isVoid = tx.transactionType === "VOID";
                const isTargetVoided = voidedTargetIds.has(tx.id);
                const isActionable = !isVoid && !isTargetVoided;

                const typeLabels: Record<string, { label: string; className: string }> = {
                  BUY: { label: "MUA", className: "tx-type-buy" },
                  SELL: { label: "BÁN", className: "tx-type-sell" },
                  DEPOSIT: { label: "NẠP TIỀN", className: "tx-type-deposit" },
                  WITHDRAW: { label: "RÚT TIỀN", className: "tx-type-withdraw" },
                  VOID: { label: "HỦY (VOID)", className: "tx-type-void" },
                };

                const typeInfo = typeLabels[tx.transactionType] || {
                  label: tx.transactionType,
                  className: "tx-type-other",
                };

                return (
                  <tr
                    key={tx.id}
                    className={`quant-row ${isTargetVoided ? "row-voided" : ""}`}
                  >
                    <td className="text-center font-mono text-xs text-slate-500">
                      {tx.sequenceNo}
                    </td>
                    <td className="font-mono text-xs text-slate-300">
                      {new Date(tx.executedAt).toLocaleString("vi-VN")}
                    </td>
                    <td className="text-center">
                      <span className={`tx-badge ${typeInfo.className}`}>
                        {typeInfo.label}
                      </span>
                    </td>
                    <td>
                      {tx.instrumentSymbol ? (
                        <span className="font-mono font-bold text-slate-100">
                          {tx.instrumentSymbol}
                        </span>
                      ) : (
                        <span className="text-slate-600">—</span>
                      )}
                    </td>
                    <td className="text-right font-mono text-slate-200">
                      {tx.quantity ? Number(tx.quantity).toLocaleString("vi-VN") : "—"}
                    </td>
                    <td className="text-right font-mono text-slate-200">
                      {tx.price ? `${Number(tx.price).toLocaleString("vi-VN")} đ` : "—"}
                    </td>
                    <td className="text-right font-mono text-slate-400 text-xs">
                      {tx.fee && tx.fee !== "0" ? `${Number(tx.fee).toLocaleString("vi-VN")} đ` : "0 đ"}
                    </td>
                    <td className="text-right font-mono font-semibold text-slate-100">
                      {tx.amount ? `${Number(tx.amount).toLocaleString("vi-VN")} đ` : "—"}
                    </td>
                    <td className="text-center">
                      {isVoid ? (
                        <span className="tx-void-reason-chip font-mono text-xs">
                          Hủy: {tx.voidReason}
                        </span>
                      ) : isTargetVoided ? (
                        <span className="status-pill status-pill-voided">
                          ĐÃ BỊ HỦY
                        </span>
                      ) : (
                        <span className="status-pill status-pill-valid">
                          HỢP LỆ
                        </span>
                      )}
                    </td>
                    <td className="text-center">
                      {isActionable && (
                        <div className="void-action-cell">
                          {voidingId === tx.id ? (
                            <div className="void-confirm-bar">
                              <input
                                type="text"
                                placeholder="Lý do hủy..."
                                value={reason}
                                onChange={(e) => setReason(e.target.value)}
                                className="void-reason-input"
                              />
                              <button
                                type="button"
                                disabled={submitting}
                                onClick={() => handleConfirmVoid(tx.id)}
                                className="btn-confirm-void"
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
                                className="btn-cancel-void"
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
                              className="btn-trigger-void"
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
