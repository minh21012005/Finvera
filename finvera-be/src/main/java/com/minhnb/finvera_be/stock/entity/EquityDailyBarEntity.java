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
@Table(name = "equity_daily_bar")
public class EquityDailyBarEntity {
    @Id private UUID id;
    @Column(name = "instrument_id") private UUID instrumentId;
    @Column(name = "ingestion_record_id") private UUID ingestionRecordId;
    @Column(name = "import_batch_id") private UUID importBatchId;
    @Column(name = "trading_date") private LocalDate tradingDate;
    @Column(name = "open_price", precision = 20, scale = 6) private BigDecimal openPrice;
    @Column(name = "high_price", precision = 20, scale = 6) private BigDecimal highPrice;
    @Column(name = "low_price", precision = 20, scale = 6) private BigDecimal lowPrice;
    @Column(name = "close_price", precision = 20, scale = 6) private BigDecimal closePrice;
    @Column(name = "adjusted_close", precision = 20, scale = 6) private BigDecimal adjustedClose;
    @Column(name = "adjustment_factor", precision = 20, scale = 12) private BigDecimal adjustmentFactor;
    @Column(name = "adjustment_status") private String adjustmentStatus;
    private Long volume;
    @Column(name = "value_vnd", precision = 24, scale = 4) private BigDecimal valueVnd;
    private String source;
    @Column(name = "observed_at") private Instant observedAt;
    @Column(name = "accepted_at") private Instant acceptedAt;
    private int revision;
    @Column(name = "is_current") private boolean current;
    @Column(name = "supersedes_id") private UUID supersedesId;
    @Column(name = "quality_reason") private String qualityReason;

    protected EquityDailyBarEntity() { }

    public EquityDailyBarEntity(UUID id, UUID instrumentId, UUID ingestionRecordId, UUID importBatchId,
            LocalDate tradingDate, BigDecimal openPrice, BigDecimal highPrice, BigDecimal lowPrice,
            BigDecimal closePrice, BigDecimal adjustedClose, BigDecimal adjustmentFactor, String adjustmentStatus,
            Long volume, BigDecimal valueVnd, String source, Instant observedAt, Instant acceptedAt, int revision,
            boolean current, UUID supersedesId, String qualityReason) {
        this.id = id; this.instrumentId = instrumentId; this.ingestionRecordId = ingestionRecordId;
        this.importBatchId = importBatchId; this.tradingDate = tradingDate; this.openPrice = openPrice;
        this.highPrice = highPrice; this.lowPrice = lowPrice; this.closePrice = closePrice;
        this.adjustedClose = adjustedClose; this.adjustmentFactor = adjustmentFactor;
        this.adjustmentStatus = adjustmentStatus; this.volume = volume; this.valueVnd = valueVnd;
        this.source = source; this.observedAt = observedAt; this.acceptedAt = acceptedAt; this.revision = revision;
        this.current = current; this.supersedesId = supersedesId; this.qualityReason = qualityReason;
    }

    public UUID getId() { return id; }
    public UUID getInstrumentId() { return instrumentId; }
    public UUID getIngestionRecordId() { return ingestionRecordId; }
    public UUID getImportBatchId() { return importBatchId; }
    public LocalDate getTradingDate() { return tradingDate; }
    public BigDecimal getOpenPrice() { return openPrice; }
    public BigDecimal getHighPrice() { return highPrice; }
    public BigDecimal getLowPrice() { return lowPrice; }
    public BigDecimal getClosePrice() { return closePrice; }
    public BigDecimal getAdjustedClose() { return adjustedClose; }
    public BigDecimal getAdjustmentFactor() { return adjustmentFactor; }
    public String getAdjustmentStatus() { return adjustmentStatus; }
    public Long getVolume() { return volume; }
    public BigDecimal getValueVnd() { return valueVnd; }
    public String getSource() { return source; }
    public Instant getObservedAt() { return observedAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public int getRevision() { return revision; }
    public boolean isCurrent() { return current; }
    public void markSuperseded() { this.current = false; }
    public UUID getSupersedesId() { return supersedesId; }
    public String getQualityReason() { return qualityReason; }
}
