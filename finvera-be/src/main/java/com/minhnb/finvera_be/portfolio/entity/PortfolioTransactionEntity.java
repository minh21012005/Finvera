package com.minhnb.finvera_be.portfolio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "portfolio_transaction")
public class PortfolioTransactionEntity {

    @Id
    private UUID id;

    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    @Column(name = "sequence_no", insertable = false, updatable = false)
    private Long sequenceNo;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "transaction_type", nullable = false, length = 16)
    private String transactionType;

    @Column(name = "instrument_id")
    private UUID instrumentId;

    @Column(name = "quantity", precision = 20, scale = 4)
    private BigDecimal quantity;

    @Column(name = "price", precision = 20, scale = 6)
    private BigDecimal price;

    @Column(name = "fee", precision = 20, scale = 6, nullable = false)
    private BigDecimal fee;

    @Column(name = "amount", precision = 20, scale = 6)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @Column(name = "entry_at", nullable = false)
    private Instant entryAt;

    @Column(name = "voids_transaction_id")
    private UUID voidsTransactionId;

    @Column(name = "void_reason", length = 200)
    private String voidReason;

    protected PortfolioTransactionEntity() {
    }

    public PortfolioTransactionEntity(
            UUID id,
            UUID portfolioId,
            String idempotencyKey,
            String transactionType,
            UUID instrumentId,
            BigDecimal quantity,
            BigDecimal price,
            BigDecimal fee,
            BigDecimal amount,
            String currency,
            Instant executedAt,
            Instant entryAt,
            UUID voidsTransactionId,
            String voidReason) {
        this.id = id;
        this.portfolioId = portfolioId;
        this.idempotencyKey = idempotencyKey;
        this.transactionType = transactionType;
        this.instrumentId = instrumentId;
        this.quantity = quantity;
        this.price = price;
        this.fee = fee != null ? fee : BigDecimal.ZERO;
        this.amount = amount;
        this.currency = currency != null ? currency : "VND";
        this.executedAt = executedAt;
        this.entryAt = entryAt;
        this.voidsTransactionId = voidsTransactionId;
        this.voidReason = voidReason;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPortfolioId() {
        return portfolioId;
    }

    public Long getSequenceNo() {
        return sequenceNo;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public UUID getInstrumentId() {
        return instrumentId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public Instant getEntryAt() {
        return entryAt;
    }

    public UUID getVoidsTransactionId() {
        return voidsTransactionId;
    }

    public String getVoidReason() {
        return voidReason;
    }
}
