package com.minhnb.finvera_be.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "index_snapshot")
public class MarketIndexSnapshotEntity {

    @Id
    private UUID id;
    @Column(name = "index_id")
    private UUID indexId;
    @Column(name = "ingestion_record_id", unique = true)
    private UUID ingestionRecordId;
    @Column(name = "trading_date")
    private LocalDate tradingDate;
    @Column(name = "observed_at")
    private Instant observedAt;
    @Column(name = "accepted_at")
    private Instant acceptedAt;
    @Column(name = "session_state")
    private String sessionState;
    @Column(name = "index_level", precision = 18, scale = 6)
    private BigDecimal indexLevel;
    @Column(name = "reference_level", precision = 18, scale = 6)
    private BigDecimal referenceLevel;
    @Column(name = "absolute_change", precision = 18, scale = 6)
    private BigDecimal absoluteChange;
    @Column(name = "percentage_change", precision = 12, scale = 6)
    private BigDecimal percentageChange;
    @Column(name = "matched_volume")
    private Long matchedVolume;
    @Column(name = "matched_value_vnd", precision = 24, scale = 4)
    private BigDecimal matchedValueVnd;
    private String source;
    private int revision;
    @Column(name = "supersedes_id")
    private UUID supersedesId;

    protected MarketIndexSnapshotEntity() {
    }

    public MarketIndexSnapshotEntity(UUID id, UUID indexId, UUID ingestionRecordId,
            LocalDate tradingDate, Instant observedAt, Instant acceptedAt, String sessionState,
            BigDecimal indexLevel, BigDecimal referenceLevel, BigDecimal absoluteChange,
            BigDecimal percentageChange, Long matchedVolume, BigDecimal matchedValueVnd,
            String source, int revision, UUID supersedesId) {
        this.id = id;
        this.indexId = indexId;
        this.ingestionRecordId = ingestionRecordId;
        this.tradingDate = tradingDate;
        this.observedAt = observedAt;
        this.acceptedAt = acceptedAt;
        this.sessionState = sessionState;
        this.indexLevel = indexLevel;
        this.referenceLevel = referenceLevel;
        this.absoluteChange = absoluteChange;
        this.percentageChange = percentageChange;
        this.matchedVolume = matchedVolume;
        this.matchedValueVnd = matchedValueVnd;
        this.source = source;
        this.revision = revision;
        this.supersedesId = supersedesId;
    }

    public UUID getId() { return id; }
    public UUID getIndexId() { return indexId; }
    public LocalDate getTradingDate() { return tradingDate; }
    public Instant getObservedAt() { return observedAt; }
    public BigDecimal getIndexLevel() { return indexLevel; }
    public BigDecimal getAbsoluteChange() { return absoluteChange; }
    public BigDecimal getMatchedValueVnd() { return matchedValueVnd; }
    public int getRevision() { return revision; }
    public UUID getSupersedesId() { return supersedesId; }
}
