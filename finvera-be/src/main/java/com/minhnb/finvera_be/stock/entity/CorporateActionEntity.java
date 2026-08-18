package com.minhnb.finvera_be.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "corporate_action")
public class CorporateActionEntity {
    @Id private UUID id;
    @Column(name = "instrument_id") private UUID instrumentId;
    @Column(name = "ingestion_record_id") private UUID ingestionRecordId;
    @Column(name = "action_type") private String actionType;
    @Column(name = "ex_date") private LocalDate exDate;
    @Column(name = "record_date") private LocalDate recordDate;
    @Column(name = "payment_date") private LocalDate paymentDate;
    @Column(name = "ratio_numerator", precision = 20, scale = 6) private BigDecimal ratioNumerator;
    @Column(name = "ratio_denominator", precision = 20, scale = 6) private BigDecimal ratioDenominator;
    @Column(name = "cash_amount_vnd", precision = 20, scale = 6) private BigDecimal cashAmountVnd;
    @Column(name = "adjustment_factor", precision = 20, scale = 12) private BigDecimal adjustmentFactor;
    private String source;
    @Column(name = "source_revision") private String sourceRevision;
    @Column(name = "accepted_at") private Instant acceptedAt;
    @Column(name = "supersedes_id") private UUID supersedesId;

    protected CorporateActionEntity() { }

    public CorporateActionEntity(UUID id, UUID instrumentId, UUID ingestionRecordId, String actionType,
            LocalDate exDate, LocalDate recordDate, LocalDate paymentDate, BigDecimal ratioNumerator,
            BigDecimal ratioDenominator, BigDecimal cashAmountVnd, BigDecimal adjustmentFactor, String source,
            String sourceRevision, Instant acceptedAt, UUID supersedesId) {
        this.id = id; this.instrumentId = instrumentId; this.ingestionRecordId = ingestionRecordId;
        this.actionType = actionType; this.exDate = exDate; this.recordDate = recordDate;
        this.paymentDate = paymentDate; this.ratioNumerator = ratioNumerator;
        this.ratioDenominator = ratioDenominator; this.cashAmountVnd = cashAmountVnd;
        this.adjustmentFactor = adjustmentFactor; this.source = source; this.sourceRevision = sourceRevision;
        this.acceptedAt = acceptedAt; this.supersedesId = supersedesId;
    }

    public UUID getId() { return id; }
    public UUID getInstrumentId() { return instrumentId; }
    public UUID getIngestionRecordId() { return ingestionRecordId; }
    public String getActionType() { return actionType; }
    public LocalDate getExDate() { return exDate; }
    public LocalDate getRecordDate() { return recordDate; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public BigDecimal getRatioNumerator() { return ratioNumerator; }
    public BigDecimal getRatioDenominator() { return ratioDenominator; }
    public BigDecimal getCashAmountVnd() { return cashAmountVnd; }
    public BigDecimal getAdjustmentFactor() { return adjustmentFactor; }
    public String getSource() { return source; }
    public String getSourceRevision() { return sourceRevision; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public UUID getSupersedesId() { return supersedesId; }
}
