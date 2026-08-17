package com.minhnb.finvera_be.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Immutable evidence of a TCBS/Vnstock comparison that requires operator resolution. */
@Entity
@Table(name = "source_reconciliation_audit")
public class SourceReconciliationAuditEntity {
    @Id private UUID id;
    @Column(name = "instrument_id") private UUID instrumentId;
    @Column(name = "trading_date") private LocalDate tradingDate;
    @Column(name = "tcbs_ingestion_record_id") private UUID tcbsIngestionRecordId;
    @Column(name = "vnstock_ingestion_record_id") private UUID vnstockIngestionRecordId;
    private String decision;
    @Column(name = "policy_version") private String policyVersion;
    @Column(name = "detected_at") private Instant detectedAt;

    protected SourceReconciliationAuditEntity() { }

    public SourceReconciliationAuditEntity(UUID id, UUID instrumentId, LocalDate tradingDate,
            UUID tcbsIngestionRecordId, UUID vnstockIngestionRecordId, String decision,
            String policyVersion, Instant detectedAt) {
        this.id = id;
        this.instrumentId = instrumentId;
        this.tradingDate = tradingDate;
        this.tcbsIngestionRecordId = tcbsIngestionRecordId;
        this.vnstockIngestionRecordId = vnstockIngestionRecordId;
        this.decision = decision;
        this.policyVersion = policyVersion;
        this.detectedAt = detectedAt;
    }

    public UUID getId() { return id; }
    public UUID getInstrumentId() { return instrumentId; }
    public LocalDate getTradingDate() { return tradingDate; }
    public UUID getTcbsIngestionRecordId() { return tcbsIngestionRecordId; }
    public UUID getVnstockIngestionRecordId() { return vnstockIngestionRecordId; }
    public String getDecision() { return decision; }
    public String getPolicyVersion() { return policyVersion; }
    public Instant getDetectedAt() { return detectedAt; }
}
