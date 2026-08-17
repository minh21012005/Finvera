package com.minhnb.finvera_be.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "ingestion_record")
public class MarketObservationEntity {

    @Id
    private UUID id;
    private String source;
    private String dataset;
    @Column(name = "subject_key")
    private String subjectKey;
    @Column(name = "trading_date")
    private LocalDate tradingDate;
    @Column(name = "observed_at")
    private Instant observedAt;
    @Column(name = "effective_at")
    private Instant effectiveAt;
    @Column(name = "ingested_at")
    private Instant ingestedAt;
    @Column(name = "source_sequence")
    private String sourceSequence;
    @Column(name = "payload_hash", columnDefinition = "char(64)")
    @JdbcTypeCode(Types.CHAR)
    private String payloadHash;
    private String status;
    @Column(name = "reason_code")
    private String reasonCode;
    @Column(name = "supersedes_id")
    private UUID supersedesId;

    protected MarketObservationEntity() {
    }

    public MarketObservationEntity(UUID id, String source, String dataset, String subjectKey,
            LocalDate tradingDate, Instant observedAt, Instant effectiveAt, Instant ingestedAt,
            String sourceSequence, String payloadHash, String status, String reasonCode,
            UUID supersedesId) {
        this.id = id;
        this.source = source;
        this.dataset = dataset;
        this.subjectKey = subjectKey;
        this.tradingDate = tradingDate;
        this.observedAt = observedAt;
        this.effectiveAt = effectiveAt;
        this.ingestedAt = ingestedAt;
        this.sourceSequence = sourceSequence;
        this.payloadHash = payloadHash;
        this.status = status;
        this.reasonCode = reasonCode;
        this.supersedesId = supersedesId;
    }

    public UUID getId() { return id; }
    public Instant getObservedAt() { return observedAt; }
    public String getPayloadHash() { return payloadHash; }
    public String getStatus() { return status; }
    public String getReasonCode() { return reasonCode; }
    public UUID getSupersedesId() { return supersedesId; }
}
