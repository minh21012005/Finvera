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
@Table(name = "equity_price_observation")
public class EquityPriceObservationEntity {
    @Id private UUID id;
    @Column(name="instrument_id") private UUID instrumentId;
    @Column(name="ingestion_record_id") private UUID ingestionRecordId;
    @Column(name="trading_date") private LocalDate tradingDate;
    @Column(name="observed_at") private Instant observedAt;
    @Column(name="matched_or_close_price", precision=20, scale=6) private BigDecimal matchedOrClosePrice;
    @Column(name="official_reference_price", precision=20, scale=6) private BigDecimal officialReferencePrice;
    @Column(name="raw_close_price", precision=20, scale=6) private BigDecimal rawClosePrice;
    @Column(name="adjusted_close_price", precision=20, scale=6) private BigDecimal adjustedClosePrice;
    @Column(name="adjustment_status") private String adjustmentStatus;
    @Column(name="quality_reason") private String qualityReason;
    protected EquityPriceObservationEntity() { }
    public EquityPriceObservationEntity(UUID id, UUID instrumentId, UUID ingestionRecordId, LocalDate tradingDate,
            Instant observedAt, BigDecimal matchedOrClosePrice, BigDecimal officialReferencePrice,
            BigDecimal rawClosePrice, BigDecimal adjustedClosePrice, String adjustmentStatus, String qualityReason) {
        this.id=id; this.instrumentId=instrumentId; this.ingestionRecordId=ingestionRecordId;
        this.tradingDate=tradingDate; this.observedAt=observedAt; this.matchedOrClosePrice=matchedOrClosePrice;
        this.officialReferencePrice=officialReferencePrice; this.rawClosePrice=rawClosePrice;
        this.adjustedClosePrice=adjustedClosePrice; this.adjustmentStatus=adjustmentStatus; this.qualityReason=qualityReason;
    }
    public UUID getId() { return id; }
}
